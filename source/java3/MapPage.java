package today.knowingjesus.biblecorner;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bible Maps, drawn on the phone: the land in its real colours, every place of Scripture, and the story maps. */
final class MapPage implements Main.Page {
    static final Map<String, Integer> COL = new HashMap<>();
    static {
        COL.put("s", 0xff7a1f6b); COL.put("r", 0xff8a7a5a); COL.put("m", 0xff6d4c2f); COL.put("w", 0xff1f6f9b);
        COL.put("i", 0xff2e7d6b); COL.put("v", 0xff6b7a2e); COL.put("c", 0xffb5442c); COL.put("o", 0xff555555);
    }
    final Main m;
    final LinearLayout root, panel;
    final TextView title;
    final MapView mv;
    final ScrollView panelScroll;
    boolean ok;
    float w, h, k, c; double[] box;
    Path land, lakes, rivers, borders;
    final List<JSONObject> P = new ArrayList<>();
    final Map<String, JSONObject> BY = new HashMap<>();
    JSONArray stories;
    JSONObject W = new JSONObject(), names = new JSONObject();
    String lang = "";

    MapPage(Main m) {
        this.m = m;
        root = new LinearLayout(m); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(K.bg());
        title = m.barTitle("Bible Maps");
        root.addView(m.bar(title,
            m.barButton("ic_search", "Find a place", new View.OnClickListener() { public void onClick(View v) { find(); } }),
            m.barButton("ic_route", "Story maps", new View.OnClickListener() { public void onClick(View v) { storyList(); } }),
            m.barButton("ic_home", "The whole map", new View.OnClickListener() { public void onClick(View v) { clear(); mv.home(); } })));
        FrameLayout f = new FrameLayout(m);
        mv = new MapView(m);
        f.addView(mv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout zoom = new LinearLayout(m); zoom.setOrientation(LinearLayout.VERTICAL);
        zoom.addView(round("ic_plus", "Zoom in", 1.6f)); zoom.addView(round("ic_minus", "Zoom out", 1 / 1.6f));
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP); zp.setMargins(0, K.dp(10), K.dp(10), 0);
        f.addView(zoom, zp);
        root.addView(f, new LinearLayout.LayoutParams(-1, 0, 1));
        panel = new LinearLayout(m); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(K.dp(16), K.dp(8), K.dp(16), K.dp(12));
        panelScroll = new ScrollView(m); panelScroll.addView(panel); panelScroll.setBackgroundColor(K.card()); panelScroll.setElevation(K.dp(6));
        root.addView(panelScroll, new LinearLayout.LayoutParams(-1, -2));
        panelScroll.setVisibility(View.GONE);
    }

    ImageButton round(String ic, String desc, final float f) {
        ImageButton b = new ImageButton(m); b.setImageResource(m.icon(ic)); b.setImageTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        b.setBackground(K.round(0xcc3e093f, 21)); b.setContentDescription(desc);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { mv.zoom(f, mv.getWidth() / 2f, mv.getHeight() / 2f); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(K.dp(42), K.dp(42)); lp.setMargins(0, 0, 0, K.dp(8)); b.setLayoutParams(lp);
        return b;
    }

    public View view() { return root; }
    public boolean back() {
        if (mv.story != null || mv.sel != null || mv.only != null) { clear(); return true; }
        return false;
    }
    public void show() { if (!ok || !lang.equals(K.lang())) load(); }

    String T(String key, Object... a) { String s = W.optString(key, key); for (int i = 0; i < a.length; i++) s = s.replace("{" + i + "}", String.valueOf(a[i])); return s; }
    String nm(JSONObject p) { return names.optString(p.optString("id"), p.optString("en")); }

    // ---------- the data ----------
    void load() {
        lang = K.lang();
        try {
            Object b = Db.get().readJson("maps/base.json"), pl = Db.get().readJson("maps/places.json");
            if (!(b instanceof JSONObject) || !(pl instanceof JSONObject)) { ok = false; message("The Bible Maps are not on this phone yet. Open the menu, then Downloads, and keep the Bible Maps."); return; }
            JSONObject B = (JSONObject) b;
            w = (float) B.getDouble("w"); h = (float) B.getDouble("h"); k = (float) B.getDouble("k"); c = (float) B.getDouble("c");
            JSONArray bx = B.getJSONArray("box"); box = new double[]{bx.getDouble(0), bx.getDouble(1), bx.getDouble(2), bx.getDouble(3)};
            land = path(B.optString("land"), true); lakes = path(B.optString("lakes"), true); rivers = path(B.optString("rivers"), false); borders = path(B.optString("borders"), false);
            P.clear(); BY.clear();
            JSONArray rows = ((JSONObject) pl).getJSONArray("p");
            for (int i = 0; i < rows.length(); i++) {
                JSONArray r = rows.getJSONArray(i);
                String vs = r.optString(6);
                JSONObject p = new JSONObject().put("id", r.getString(0)).put("en", r.getString(1)).put("t", r.getString(2)).put("x", r.getDouble(3)).put("y", r.getDouble(4))
                    .put("c", r.optInt(5)).put("v", vs).put("n", vs.isEmpty() ? 0 : vs.split(" ").length).put("d", r.optString(7)).put("j", r.optInt(8) != 0 || r.optBoolean(8));
                P.add(p); BY.put(p.getString("id"), p);
            }
            Object st = Db.get().readJson("maps/stories.json"); stories = st instanceof JSONArray ? (JSONArray) st : new JSONArray();
            Object wd = Db.get().readJson("maps/words.json");
            W = new JSONObject();
            if (wd instanceof JSONObject) { JSONObject en = ((JSONObject) wd).optJSONObject("en"), l = ((JSONObject) wd).optJSONObject(lang); copy(en, W); copy(l, W); }
            Object nn = lang.equals("en") ? null : Db.get().readJson("maps/names-" + lang + ".json"); names = nn instanceof JSONObject ? (JSONObject) nn : new JSONObject();
            ok = true; mv.loaded = false; mv.invalidate();
            panelScroll.setVisibility(View.GONE);
        } catch (Throwable t) { ok = false; message("The Bible Maps could not be opened."); }
    }

    static void copy(JSONObject a, JSONObject b) { if (a == null) return; java.util.Iterator<String> it = a.keys(); while (it.hasNext()) { String x = it.next(); try { b.put(x, a.get(x)); } catch (Throwable t) {} } }

    /** an SVG path (M L H V Z, absolute or relative) as an Android path */
    static Path path(String d, boolean close) {
        Path p = new Path();
        if (d == null || d.isEmpty()) return p;
        Matcher mm = Pattern.compile("[MmLlHhVvZz]|-?\\d*\\.?\\d+(?:e-?\\d+)?").matcher(d);
        List<String> t = new ArrayList<>(); while (mm.find()) t.add(mm.group());
        char cmd = 'M'; float x = 0, y = 0, sx = 0, sy = 0; boolean open = false;
        for (int i = 0; i < t.size(); ) {
            String s = t.get(i);
            if (Character.isLetter(s.charAt(0))) {
                cmd = s.charAt(0); i++;
                if (cmd == 'Z' || cmd == 'z') { if (open) p.close(); open = false; x = sx; y = sy; }
                continue;
            }
            try {
                if (cmd == 'M' || cmd == 'm') {
                    float a = Float.parseFloat(t.get(i)), b = Float.parseFloat(t.get(i + 1)); i += 2;
                    if (open && close) p.close();
                    if (cmd == 'm') { x += a; y += b; } else { x = a; y = b; }
                    sx = x; sy = y; p.moveTo(x, y); open = true; cmd = cmd == 'm' ? 'l' : 'L';
                } else if (cmd == 'L' || cmd == 'l') {
                    float a = Float.parseFloat(t.get(i)), b = Float.parseFloat(t.get(i + 1)); i += 2;
                    if (cmd == 'l') { x += a; y += b; } else { x = a; y = b; }
                    p.lineTo(x, y);
                } else if (cmd == 'H' || cmd == 'h') { float a = Float.parseFloat(t.get(i)); i++; x = cmd == 'h' ? x + a : a; p.lineTo(x, y); }
                else if (cmd == 'V' || cmd == 'v') { float a = Float.parseFloat(t.get(i)); i++; y = cmd == 'v' ? y + a : a; p.lineTo(x, y); }
                else i++;
            } catch (Throwable e) { i++; }
        }
        if (open && close) p.close();
        return p;
    }

    float[] U(double lon, double lat) { return new float[]{(float) ((lon - box[0]) * k * c), (float) ((box[3] - lat) * k)}; }

    // ---------- the panel under the map ----------
    void message(String s) {
        panel.removeAllViews(); panel.addView(K.text(m, s, 15, K.fg(), false)); panelScroll.setVisibility(View.VISIBLE);
    }

    void clear() { mv.story = null; mv.sel = null; mv.only = null; mv.stops.clear(); panelScroll.setVisibility(View.GONE); title.setText(T("title")); mv.invalidate(); }

    void choose(JSONObject p, boolean center) {
        mv.sel = p; mv.story = null;
        if (center) mv.center(p.optDouble("x"), p.optDouble("y"), Math.max(mv.s, p.optBoolean("j") ? 140 : 3.2f));
        mv.invalidate();
        panel.removeAllViews();
        panel.addView(K.text(m, nm(p), 20, K.brand(), true));
        panel.addView(K.text(m, T("k_" + p.optString("t")) + " · " + T("verses", p.optInt("n")), 14, K.dim(), false));
        final String dict = p.optString("d").length() > 0 ? p.optString("d") : "p-" + p.optString("id");
        android.widget.Button b = K.button(m, T("dict"), false);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openDict(dict); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, K.dp(8), 0, K.dp(4)); panel.addView(b, lp);
        String[] vs = p.optString("v").isEmpty() ? new String[0] : p.optString("v").split(" ");
        for (int i = 0; i < Math.min(12, vs.length); i++) verse(vs[i]);
        panelScroll.setVisibility(View.VISIBLE);
        panelScroll.getLayoutParams().height = Math.min(K.dp(260), (int) (root.getHeight() * 0.42f)); panelScroll.requestLayout();
        panelScroll.scrollTo(0, 0);
    }

    void verse(String ref) {
        Matcher mm = Pattern.compile("([1-4A-Z]{3})\\.(\\d+)\\.(\\d+)(?:-(\\d+))?").matcher(ref);
        if (!mm.find()) return;
        final String b = mm.group(1); final int c = Integer.parseInt(mm.group(2)), v1 = Integer.parseInt(mm.group(3)); int v2 = mm.group(4) != null ? Integer.parseInt(mm.group(4)) : v1;
        String tr = K.get("tr", ""); if (Db.get().findBook(tr, b) == null) { tr = null; for (String t : Db.get().bibles()) if (Db.get().findBook(t, b) != null) { tr = t; break; } }
        StringBuilder s = new StringBuilder();
        if (tr != null) for (Object[] r : Db.get().passage(tr, b, c, v1, c, v2)) { if (s.length() > 0) s.append(' '); s.append(K.strip((String) r[2])); }
        final String t = tr;
        panel.addView(K.row(m, K.bookName(b, tr) + " " + c + ":" + v1 + (v2 > v1 ? "–" + v2 : ""), s.toString(), new View.OnClickListener() { public void onClick(View v) { m.openPassage(b, c, v1, t); } }));
    }

    void find() {
        if (!ok) return;
        final EditText q = new EditText(m); q.setHint(T("search")); q.setSingleLine(true);
        FrameLayout f = new FrameLayout(m); f.setPadding(K.dp(20), K.dp(6), K.dp(20), 0); f.addView(q);
        new AlertDialog.Builder(m).setTitle("Find a place").setView(f).setPositiveButton("Find", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int x) {
            String t = BiblePage.fold(q.getText().toString().trim()); if (t.isEmpty()) return;
            final List<JSONObject> hits = new ArrayList<>();
            for (JSONObject p : P) if (BiblePage.fold(nm(p)).startsWith(t) || BiblePage.fold(p.optString("en")).startsWith(t)) hits.add(p);
            if (hits.isEmpty()) for (JSONObject p : P) if (BiblePage.fold(nm(p)).contains(t)) hits.add(p);
            java.util.Collections.sort(hits, new java.util.Comparator<JSONObject>() { public int compare(JSONObject a, JSONObject b) { return b.optInt("n") - a.optInt("n"); } });
            if (hits.isEmpty()) { K.toast(T("nores")); return; }
            if (hits.size() == 1) { choose(hits.get(0), true); return; }
            String[] items = new String[Math.min(60, hits.size())];
            for (int i = 0; i < items.length; i++) items[i] = nm(hits.get(i)) + " · " + T("verses", hits.get(i).optInt("n"));
            new AlertDialog.Builder(m).setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d2, int i) { choose(hits.get(i), true); } }).show();
        } }).setNegativeButton("Cancel", null).show();
    }

    // ---------- the places of a chapter ----------
    void chapter(String book, int ch) {
        if (!ok) load();
        if (!ok) return;
        String key = " " + Db.canon(book) + "." + ch + ".";
        mv.only = new HashSet<>(); mv.story = null; mv.sel = null;
        List<JSONObject> pts = new ArrayList<>();
        for (JSONObject p : P) if ((" " + p.optString("v")).contains(key)) { mv.only.add(p.optString("id")); pts.add(p); }
        title.setText(T("chapter", K.bookName(book, null) + " " + ch));
        panel.removeAllViews();
        panel.addView(K.text(m, T("chapter", K.bookName(book, null) + " " + ch), 17, K.brand(), true));
        if (pts.isEmpty()) panel.addView(K.text(m, T("nochap"), 14, K.dim(), false));
        for (final JSONObject p : pts) panel.addView(K.row(m, nm(p), T("k_" + p.optString("t")), new View.OnClickListener() { public void onClick(View v) { choose(p, true); } }));
        panelScroll.setVisibility(View.VISIBLE);
        panelScroll.getLayoutParams().height = Math.min(K.dp(220), (int) (root.getHeight() * 0.35f)); panelScroll.requestLayout();
        mv.fitPoints(pts);
    }

    void place(String id) { if (!ok) load(); JSONObject p = BY.get(id); if (p != null) { mv.pending = p; choose(p, true); } }

    // ---------- story maps ----------
    void storyList() {
        if (!ok || stories == null) return;
        String[] items = new String[stories.length()];
        for (int i = 0; i < items.length; i++) items[i] = T("s_" + stories.optJSONObject(i).optString("id"));
        new AlertDialog.Builder(m).setTitle(T("stories")).setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int i) { story(stories.optJSONObject(i)); } }).show();
    }

    void story(JSONObject st) {
        mv.story = st; mv.sel = null; mv.only = null; mv.stops.clear(); mv.step = -1;
        int i = 0;
        JSONArray tribes = st.optJSONArray("tribes");
        mv.tribePaths.clear();
        if (tribes != null) for (int n = 0; n < tribes.length(); n++) {
            JSONObject t = tribes.optJSONObject(n);
            mv.tribePaths.add(path(t.optString("d"), true));
            try { mv.stops.add(new JSONObject().put("x", t.optDouble("x")).put("y", t.optDouble("y")).put("i", i++).put("r", t.optString("r")).put("label", T("t_" + t.optString("k"))).put("tribe", n)); } catch (Throwable e) {}
        }
        JSONArray legs = st.optJSONArray("legs");
        if (legs != null) for (int li = 0; li < legs.length(); li++) {
            JSONArray leg = legs.optJSONArray(li);
            String col = st.optJSONArray("cs") != null ? st.optJSONArray("cs").optString(li) : st.optString("c");
            for (int n = 0; n < leg.length(); n++) {
                JSONObject p = leg.optJSONObject(n);
                String lab = p.has("k") ? T("l_" + p.optString("k")) : (BY.containsKey(p.optString("id")) ? nm(BY.get(p.optString("id"))) : "");
                try { mv.stops.add(new JSONObject().put("x", p.optDouble("x")).put("y", p.optDouble("y")).put("i", i++).put("r", p.optString("r")).put("id", p.optString("id")).put("n", p.optString("n")).put("leg", li).put("col", col).put("label", lab)); } catch (Throwable e) {}
            }
        }
        title.setText(T("s_" + st.optString("id")));
        mv.fitPoints(mv.stops);
        stepPanel();
    }

    void stepPanel() {
        final JSONObject st = mv.story;
        panel.removeAllViews();
        LinearLayout nav = new LinearLayout(m); nav.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Button pv = K.button(m, "←", false), nx = K.button(m, mv.step < 0 ? T("start") + " →" : "→", true);
        TextView where = K.text(m, mv.step < 0 ? T("i_" + st.optString("id")) : T("step", mv.step + 1, mv.stops.size()), 14, K.dim(), false);
        where.setPadding(K.dp(10), 0, K.dp(10), 0); where.setMaxLines(3);
        pv.setEnabled(mv.step > 0);
        pv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { go(mv.step - 1); } });
        nx.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { go(mv.step + 1); } });
        nav.addView(pv); nav.addView(where, new LinearLayout.LayoutParams(0, -2, 1)); nav.addView(nx);
        panel.addView(nav);
        if (mv.step >= 0) {
            JSONObject p = mv.stops.get(mv.step);
            panel.addView(K.text(m, p.optString("label"), 18, K.brand(), true));
            if (p.optString("n").length() > 0) panel.addView(K.text(m, T(p.optString("n")), 14, K.fg(), false));
            if (p.optString("r").length() > 0) verse(p.optString("r"));
        }
        panelScroll.setVisibility(View.VISIBLE);
        panelScroll.getLayoutParams().height = -2; panelScroll.requestLayout();
    }

    void go(int i) {
        if (i < 0 || i >= mv.stops.size()) return;
        mv.step = i; JSONObject p = mv.stops.get(i);
        boolean jer = "jer".equals(mv.story.optString("zoom"));
        mv.center(p.optDouble("x"), p.optDouble("y"), jer ? Math.max(mv.s, 160) : (mv.story.optJSONArray("tribes") != null ? mv.s : Math.max(mv.s, 2.2f)));
        stepPanel();
    }

    // ---------- the map itself ----------
    final class MapView extends View {
        float s = 1, cx, cy; boolean loaded;
        JSONObject sel, story, pending; HashSet<String> only;
        final List<JSONObject> stops = new ArrayList<>();
        final List<Path> tribePaths = new ArrayList<>();
        int step = -1;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ScaleGestureDetector scale;
        final GestureDetector gest;
        final LruCache<String, Bitmap> tiles = new LruCache<String, Bitmap>(6) {};
        Bitmap overview;
        final List<JSONObject> visible = new ArrayList<>();

        MapView(Context c) {
            super(c);
            text.setTypeface(Typeface.DEFAULT); halo.setStyle(Paint.Style.STROKE); halo.setStrokeWidth(K.dp(3)); halo.setColor(0xd9ffffff); halo.setStrokeJoin(Paint.Join.ROUND);
            scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) { zoom(d.getScaleFactor(), d.getFocusX(), d.getFocusY()); return true; }
            });
            gest = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                public boolean onDown(MotionEvent e) { return true; }
                public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) { cx += dx / s; cy += dy / s; invalidate(); return true; }
                public boolean onDoubleTap(MotionEvent e) { zoom(2f, e.getX(), e.getY()); return true; }
                public boolean onSingleTapConfirmed(MotionEvent e) { tap(e.getX(), e.getY()); return true; }
            });
        }

        @Override public boolean onTouchEvent(MotionEvent e) { scale.onTouchEvent(e); if (!scale.isInProgress()) gest.onTouchEvent(e); return true; }

        float sx(double x) { return (float) ((x - cx) * s + getWidth() / 2f); }
        float sy(double y) { return (float) ((y - cy) * s + getHeight() / 2f); }
        boolean inView(double x, double y, float m) { float a = sx(x), b = sy(y); return a > -m && a < getWidth() + m && b > -m && b < getHeight() + m; }

        void fit(float x, float y, float bw, float bh) {
            if (getWidth() == 0) return;
            s = Math.max(0.05f, Math.min(900, Math.min(getWidth() / Math.max(bw, 1e-3f), getHeight() / Math.max(bh, 1e-3f)) * 0.9f));
            cx = x + bw / 2; cy = y + bh / 2; invalidate();
        }

        void home() {
            if (!ok) return;
            if (getHeight() > getWidth() * 1.15f) { float[] a = U(27.5, 39.5), b = U(45.5, 26.5); fit(a[0], a[1], b[0] - a[0], b[1] - a[1]); }
            else fit(0, 0, w, h);
        }

        void fitPoints(final List<JSONObject> pts) {
            post(new Runnable() { public void run() {
                if (pts.isEmpty()) { home(); return; }
                float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
                for (JSONObject p : pts) { float x = (float) p.optDouble("x"), y = (float) p.optDouble("y"); x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
                float bw = Math.max(x1 - x0, 30), bh = Math.max(y1 - y0, 30);
                fit((x0 + x1) / 2 - bw / 2, (y0 + y1) / 2 - bh / 2, bw, bh);
            } });
        }

        void center(double x, double y, float ns) { cx = (float) x; cy = (float) y; s = Math.max(0.05f, Math.min(900, ns)); invalidate(); }

        void zoom(float f, float px, float py) {
            float ns = Math.max(0.05f, Math.min(900, s * f));
            float mx = (px - getWidth() / 2f) / s + cx, my = (py - getHeight() / 2f) / s + cy;
            s = ns; cx = mx - (px - getWidth() / 2f) / ns; cy = my - (py - getHeight() / 2f) / ns; invalidate();
        }

        void tap(float x, float y) {
            JSONObject best = null; float bd = K.dp(22) * K.dp(22);
            if (story != null) {
                for (JSONObject p : stops) { float dx = sx(p.optDouble("x")) - x, dy = sy(p.optDouble("y")) - y; if (dx * dx + dy * dy < bd) { best = p; bd = dx * dx + dy * dy; } }
                if (best != null) go(best.optInt("i"));
                return;
            }
            for (JSONObject p : visible) { float dx = sx(p.optDouble("x")) - x, dy = sy(p.optDouble("y")) - y; if (dx * dx + dy * dy < bd) { best = p; bd = dx * dx + dy * dy; } }
            if (best != null) choose(best, false);
        }

        Bitmap bitmap(String name, int sample) {
            File f = Db.get().file("maps/relief/" + name);
            if (!f.exists()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = sample; o.inPreferredConfig = Bitmap.Config.RGB_565;
            try { return BitmapFactory.decodeFile(f.getPath(), o); } catch (Throwable t) { return null; }
        }

        @Override protected void onDraw(Canvas cv) {
            cv.drawColor(0xffdbe7ee);
            if (!ok) return;
            if (!loaded) { loaded = true; if (pending == null && story == null && only == null) home(); }
            cv.save();
            cv.translate(getWidth() / 2f, getHeight() / 2f); cv.scale(s, s); cv.translate(-cx, -cy);
            float lw = 1 / s;
            // the land in its real colours
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xffefe6cf); paint.setPathEffect(null);
            cv.drawPath(land, paint);
            cv.save(); cv.clipPath(land);
            if (overview == null) overview = bitmap("overview.jpg", 1);
            if (overview != null) cv.drawBitmap(overview, null, new RectF(0, 0, w, h), null);
            if (s > 0.9f) {
                float tw = w / 6, th = h / 3;
                float x0 = cx - getWidth() / 2f / s, y0 = cy - getHeight() / 2f / s, x1 = cx + getWidth() / 2f / s, y1 = cy + getHeight() / 2f / s;
                for (int r = 0; r < 3; r++) for (int c = 0; c < 6; c++) {
                    if (c * tw > x1 || (c + 1) * tw < x0 || r * th > y1 || (r + 1) * th < y0) continue;
                    String key = "t" + r + "-" + c + ".jpg";
                    Bitmap b = tiles.get(key);
                    if (b == null) { b = bitmap(key, 1); if (b != null) tiles.put(key, b); }
                    if (b != null) cv.drawBitmap(b, null, new RectF(c * tw, r * th, (c + 1) * tw + lw, (r + 1) * th + lw), null);
                }
            }
            cv.restore();
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.8f * lw); paint.setColor(0x99a89878); paint.setPathEffect(new DashPathEffect(new float[]{3 * lw, 3 * lw}, 0));
            cv.drawPath(borders, paint); paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xffcfe0ea); cv.drawPath(lakes, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(0xff5f95bb); paint.setStrokeWidth(1.4f * lw); paint.setStrokeCap(Paint.Cap.ROUND); cv.drawPath(rivers, paint);
            // a story
            if (story != null) {
                for (int n = 0; n < tribePaths.size(); n++) {
                    boolean on = step >= 0 && stops.get(step).optInt("tribe", -1) == n;
                    paint.setStyle(Paint.Style.FILL); paint.setColor(Color.HSVToColor(on ? 140 : 80, new float[]{(n * 47) % 360, 0.55f, 0.8f})); cv.drawPath(tribePaths.get(n), paint);
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth((on ? 2.5f : 1f) * lw); paint.setColor(Color.HSVToColor(new float[]{(n * 47) % 360, 0.5f, 0.45f})); cv.drawPath(tribePaths.get(n), paint);
                }
                if (!story.optBoolean("nolines")) {
                    JSONObject prev = null;
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.6f * lw); paint.setPathEffect(new DashPathEffect(new float[]{7 * lw, 4 * lw}, 0));
                    for (JSONObject p : stops) {
                        if (p.has("tribe")) continue;
                        if (prev != null && prev.optInt("leg") == p.optInt("leg")) {
                            paint.setColor(Color.parseColor(p.optString("col", story.optString("c"))) & 0xd9ffffff | 0xd9000000);
                            cv.drawLine((float) prev.optDouble("x"), (float) prev.optDouble("y"), (float) p.optDouble("x"), (float) p.optDouble("y"), paint);
                        }
                        prev = p;
                    }
                    paint.setPathEffect(null);
                }
            }
            cv.restore();
            // pins and labels, in screen sizes
            visible.clear();
            List<float[]> taken = new ArrayList<>();
            text.setTextSize(K.dp(11.5f));
            if (story == null) {
                int mn = only != null ? 0 : (s < 0.22f ? 12 : s < 0.5f ? 5 : s < 1.2f ? 2 : 0);
                List<JSONObject> cand = new ArrayList<>();
                for (JSONObject p : P) {
                    String id = p.optString("id");
                    if (only != null && !only.contains(id)) continue;
                    if (only == null && "r".equals(p.optString("t"))) continue;
                    if (p.optBoolean("j") && s < 60 && only == null) continue;
                    if (p.optInt("n") < mn && p != sel) continue;
                    if (!inView(p.optDouble("x"), p.optDouble("y"), 40)) continue;
                    cand.add(p);
                }
                java.util.Collections.sort(cand, new java.util.Comparator<JSONObject>() { public int compare(JSONObject a, JSONObject b) { return b.optInt("n") - a.optInt("n"); } });
                for (JSONObject p : cand) {
                    visible.add(p);
                    float x = sx(p.optDouble("x")), y = sy(p.optDouble("y"));
                    paint.setStyle(Paint.Style.FILL); paint.setColor(0xf2ffffff); cv.drawCircle(x, y, K.dp(3.6f), paint);
                    Integer col = COL.get(p.optString("t")); paint.setColor(col != null ? col : 0xff555555); cv.drawCircle(x, y, K.dp(2.6f), paint);
                }
                if (sel != null && inView(sel.optDouble("x"), sel.optDouble("y"), 40)) {
                    float x = sx(sel.optDouble("x")), y = sy(sel.optDouble("y"));
                    paint.setColor(0x73f3c548); cv.drawCircle(x, y, K.dp(11), paint); paint.setColor(0xff3e093f); cv.drawCircle(x, y, K.dp(6), paint); paint.setColor(0xfff3c548); cv.drawCircle(x, y, K.dp(3), paint);
                    label(cv, nm(sel), x + K.dp(9), y, 0xff3e093f, true, taken);
                }
                int n = 0;
                for (JSONObject p : visible) { if (p == sel) continue; if (n++ > 160) break; label(cv, nm(p), sx(p.optDouble("x")) + K.dp(7), sy(p.optDouble("y")), 0xff3a2e3f, false, taken); }
            } else {
                text.setTextSize(K.dp(10.5f));
                for (JSONObject p : stops) {
                    if (p.has("tribe")) { label(cv, p.optString("label"), sx(p.optDouble("x")) - text.measureText(p.optString("label")) / 2, sy(p.optDouble("y")), 0xff3e093f, true, taken); continue; }
                    float x = sx(p.optDouble("x")), y = sy(p.optDouble("y")); boolean on = p.optInt("i") == step;
                    paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor(p.optString("col", story.optString("c")))); cv.drawCircle(x, y, K.dp(on ? 11 : 9), paint);
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(K.dp(on ? 2 : 1.5f)); paint.setColor(0xffffffff); cv.drawCircle(x, y, K.dp(on ? 11 : 9), paint);
                    text.setColor(0xffffffff); text.setFakeBoldText(true); String num = "" + (p.optInt("i") + 1);
                    cv.drawText(num, x - text.measureText(num) / 2, y + text.getTextSize() / 3, text); text.setFakeBoldText(false);
                }
                text.setTextSize(K.dp(11.5f));
                for (JSONObject p : stops) if (!p.has("tribe") && (p.optInt("i") == step || s > 2.5f)) label(cv, p.optString("label"), sx(p.optDouble("x")) + K.dp(12), sy(p.optDouble("y")) - K.dp(10), 0xff3e093f, true, taken);
            }
        }

        void label(Canvas cv, String t, float x, float y, int color, boolean bold, List<float[]> taken) {
            if (t == null || t.isEmpty()) return;
            text.setFakeBoldText(bold);
            float tw = text.measureText(t), th = text.getTextSize();
            float[] r = {x, y - th * 0.8f, x + tw, y + th * 0.3f};
            if (r[0] < 2 || r[2] > getWidth() - 2 || r[1] < 2 || r[3] > getHeight() - 2) return;
            for (float[] o : taken) if (r[0] < o[2] && r[2] > o[0] && r[1] < o[3] && r[3] > o[1]) return;
            taken.add(r);
            halo.setTextSize(th); halo.setFakeBoldText(bold);
            cv.drawText(t, x, y + th * 0.3f, halo);
            text.setColor(color); cv.drawText(t, x, y + th * 0.3f, text);
            text.setFakeBoldText(false);
        }
    }
}
