package today.knowingjesus.biblecorner;

import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The Holy Fathers: their writings, kept on the phone, to read like books. */
final class LibPage implements Main.Page {
    final Main m;
    final LinearLayout root;
    final TextView title;
    final ScrollView sv;
    final List<Runnable> trail = new ArrayList<>();   // where Back goes
    JSONObject meta;
    float size;

    LibPage(Main m) {
        this.m = m;
        size = K.getf("lib_size", 1f);
        title = m.barTitle("The Holy Fathers");
        root = new LinearLayout(m); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(K.bg());
        root.addView(m.bar(title,
            m.barButton("ic_minus", "Smaller text", new View.OnClickListener() { public void onClick(View v) { zoom(1 / 1.12f); } }),
            m.barButton("ic_plus", "Larger text", new View.OnClickListener() { public void onClick(View v) { zoom(1.12f); } })));
        sv = new ScrollView(m); root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        try {
            InputStream in = m.getResources().openRawResource(m.getResources().getIdentifier("fathers_meta", "raw", m.getPackageName()));
            byte[] b = new byte[in.available()]; int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } in.close();
            meta = new JSONObject(new String(b, "UTF-8"));
        } catch (Throwable t) { meta = new JSONObject(); }
    }

    public View view() { return root; }
    public void show() { if (trail.isEmpty()) home(); }
    public boolean back() {
        if (trail.size() <= 1) { trail.clear(); return false; }
        trail.remove(trail.size() - 1); trail.get(trail.size() - 1).run(); return true;
    }

    Runnable current;
    void zoom(float f) { size = Math.max(0.7f, Math.min(2.4f, size * f)); K.putf("lib_size", size); if (!trail.isEmpty()) trail.get(trail.size() - 1).run(); }

    LinearLayout list() {
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, K.dp(4), 0, K.dp(30));
        sv.removeAllViews(); sv.addView(box); sv.scrollTo(0, 0); return box;
    }

    void push(Runnable r) { trail.add(r); r.run(); }

    TextView head(LinearLayout box, String s) { TextView t = K.text(m, s, 14, K.brand(), true); t.setPadding(K.dp(16), K.dp(16), K.dp(16), K.dp(4)); box.addView(t); return t; }

    JSONObject index() {
        Object o = Db.get().readJson("library/index.json");
        JSONObject by = new JSONObject();
        if (o instanceof JSONArray) for (int i = 0; i < ((JSONArray) o).length(); i++) { JSONObject x = ((JSONArray) o).optJSONObject(i); try { by.put(x.optString("id"), x); } catch (Throwable t) {} }
        return by;
    }

    void home() {
        trail.clear();
        trail.add(new Runnable() { public void run() { drawHome(); } });
        drawHome();
    }

    void drawHome() {
        title.setText("The Holy Fathers");
        LinearLayout box = list();
        final JSONObject idx = index();
        try {
            final JSONObject last = new JSONObject(K.get("lib_last", "{}"));
            if (last.has("a") && idx.has(last.optString("a"))) {
                Button b = K.button(m, "Continue: " + last.optString("title"), true);
                b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { open(last.optString("a"), last.optString("w"), last.optInt("p")); } });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(K.dp(14), K.dp(12), K.dp(14), K.dp(4)); box.addView(b, lp);
            }
        } catch (Throwable t) {}
        if (idx.length() == 0) { TextView t = K.text(m, "The writings of the Holy Fathers are not on this phone yet. Open the menu, then Downloads.", 15, K.fg(), false); t.setPadding(K.dp(16), K.dp(16), K.dp(16), 0); box.addView(t); return; }
        JSONArray eras = meta.optJSONArray("eras"); JSONObject life = meta.optJSONObject("life");
        if (eras != null) for (int i = 0; i < eras.length(); i++) {
            JSONArray era = eras.optJSONArray(i); JSONArray ids = era.optJSONArray(2);
            boolean any = false;
            for (int k = 0; k < ids.length(); k++) if (idx.has(ids.optString(k))) any = true;
            if (!any) continue;
            head(box, era.optString(0).toUpperCase());
            for (int k = 0; k < ids.length(); k++) {
                final String a = ids.optString(k); JSONObject x = idx.optJSONObject(a); if (x == null) continue;
                box.addView(K.row(m, x.optString("name"), life != null ? life.optString(a, x.optString("when")) : x.optString("when"), new View.OnClickListener() { public void onClick(View v) {
                    push(new Runnable() { public void run() { author(a); } });
                } }));
                box.addView(K.divider(m));
            }
        }
    }

    void author(final String a) {
        JSONObject x = index().optJSONObject(a);
        if (x == null) return;
        title.setText(x.optString("name"));
        LinearLayout box = list();
        TextView about = K.text(m, meta.optJSONObject("about") != null ? meta.optJSONObject("about").optString(a) : "", 15, K.fg(), false);
        about.setPadding(K.dp(16), K.dp(12), K.dp(16), K.dp(4)); about.setLineSpacing(K.dp(2), 1.08f);
        if (about.getText().length() > 0) box.addView(about);
        Object o = Db.get().readJson("library/" + a + ".json.gz");
        if (!(o instanceof JSONObject)) { box.addView(K.text(m, "Not on this phone yet. Open the menu, then Downloads.", 15, K.dim(), false)); return; }
        JSONObject data = (JSONObject) o;
        JSONArray works = x.optJSONArray("works");
        if (works != null) for (int i = 0; i < works.length(); i++) {
            final JSONObject w = works.optJSONObject(i);
            JSONObject work = data.optJSONObject(w.optString("id"));
            final JSONArray parts = work != null ? work.optJSONArray("p") : null;
            int n = parts != null ? parts.length() : 0;
            box.addView(K.row(m, w.optString("t"), n > 1 ? n + " parts" : "", new View.OnClickListener() { public void onClick(View v) {
                if (parts != null && parts.length() > 1) push(new Runnable() { public void run() { parts(a, w.optString("id")); } });
                else open(a, w.optString("id"), 0);
            } }));
            box.addView(K.divider(m));
        }
    }

    void parts(final String a, final String wid) {
        Object o = Db.get().readJson("library/" + a + ".json.gz");
        JSONObject work = o instanceof JSONObject ? ((JSONObject) o).optJSONObject(wid) : null;
        if (work == null) return;
        title.setText(work.optString("t"));
        LinearLayout box = list();
        JSONArray parts = work.optJSONArray("p");
        for (int i = 0; i < parts.length(); i++) {
            final int n = i;
            box.addView(K.row(m, parts.optJSONObject(i).optString("t", "Part " + (i + 1)), null, new View.OnClickListener() { public void onClick(View v) { open(a, wid, n); } }));
            box.addView(K.divider(m));
        }
    }

    void open(final String a, final String wid, final int n) {
        push(new Runnable() { public void run() { read(a, wid, n); } });
    }

    void read(final String a, final String wid, int n) {
        Object o = Db.get().readJson("library/" + a + ".json.gz");
        JSONObject work = o instanceof JSONObject ? ((JSONObject) o).optJSONObject(wid) : null;
        if (work == null) return;
        final JSONArray parts = work.optJSONArray("p");
        if (parts == null || parts.length() == 0) return;
        final int k = Math.max(0, Math.min(n, parts.length() - 1));
        JSONObject p = parts.optJSONObject(k);
        String t = work.optString("t") + (parts.length() > 1 && p.optString("t").length() > 0 ? " · " + p.optString("t") : "");
        title.setText(t);
        try { K.put("lib_last", new JSONObject().put("a", a).put("w", wid).put("p", k).put("title", t).toString()); } catch (Throwable x) {}
        LinearLayout box = list();
        TextView body = new TextView(m); body.setPadding(K.dp(18), K.dp(14), K.dp(18), K.dp(20));
        body.setTextColor(K.fg()); body.setTextSize(17 * size); body.setLineSpacing(K.dp(3), 1.1f); body.setTypeface(android.graphics.Typeface.SERIF);
        body.setText(Html.fromHtml("<h2>" + K.esc(t) + "</h2>" + p.optString("h"), Html.FROM_HTML_MODE_COMPACT)); body.setTextIsSelectable(true);
        box.addView(body);
        if (parts.length() > 1) {
            LinearLayout nav = new LinearLayout(m); nav.setGravity(Gravity.CENTER); nav.setPadding(K.dp(12), 0, K.dp(12), K.dp(20));
            if (k > 0) { Button b = K.button(m, "← Before", false); b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { trail.remove(trail.size() - 1); open(a, wid, k - 1); } }); nav.addView(b); }
            TextView c = K.text(m, "  " + (k + 1) + " / " + parts.length() + "  ", 14, K.dim(), false); nav.addView(c);
            if (k < parts.length() - 1) { Button b = K.button(m, "Next →", true); b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { trail.remove(trail.size() - 1); open(a, wid, k + 1); } }); nav.addView(b); }
            box.addView(nav);
        }
    }
}
