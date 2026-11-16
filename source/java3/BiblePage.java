package today.knowingjesus.biblecorner;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** The Holy Bible: reading, comparing, searching, listening, the Holy Fathers on each chapter, and your study. */
final class BiblePage implements Main.Page {
    final Main m;
    String tr, tr2, book; int ch;
    final TreeSet<Integer> sel = new TreeSet<>();
    float size;
    LinearLayout root, selBar;
    TextView text, where, trBtn, selLabel;
    ScrollView scroll;
    final GestureDetector swipe;
    final List<int[]> vpos = new ArrayList<>();   // [verse, start, end]
    float touchX, touchY;
    static final String[][] CATS = {{"law", "Law (Pentateuch)", "GEN EXO LEV NUM DEU", "#dc2626", "#fdeeee", "#991b1b"},
        {"hist", "History", "JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST", "#9ca3af", "#ffffff", "#374151"},
        {"wis", "Wisdom and Poetry", "JOB PSA PRO ECC SNG", "#f97316", "#fff2e8", "#9a3412"},
        {"major", "Major Prophets", "ISA JER LAM EZK DAN", "#2563eb", "#edf3ff", "#1e40af"},
        {"minor", "Minor Prophets", "HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL", "#ec4899", "#fdeef6", "#9d174d"},
        {"dc", "Deuterocanonical books", "", "#0d9488", "#e8f7f5", "#115e59"},
        {"gosp", "Gospels", "MAT MRK LUK JHN", "#16a34a", "#eaf7ee", "#166534"},
        {"acts", "Church History", "ACT", "#9333ea", "#f5edfd", "#6b21a8"},
        {"paul", "Epistles of St Paul", "ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB", "#facc15", "#fffbe2", "#854d0e"},
        {"gen", "General Epistles", "JAS 1PE 2PE 1JN 2JN 3JN JUD", "#92400e", "#f6eee6", "#78350f"},
        {"rev", "Prophecy (Revelation)", "REV", "#c9a227", "#fff6d8", "#7a5a00"}};

    static String[] cat(String code) {
        String c = Db.canon(code);
        for (String[] k : CATS) for (String b : k[2].split(" ")) if (b.equals(c)) return k;
        return CATS[5];
    }

    BiblePage(Main m) {
        this.m = m;
        tr = K.get("tr", ""); tr2 = K.get("tr2", ""); book = K.get("book", "JHN"); ch = K.geti("ch", 1); size = K.getf("size", 1f);
        root = new LinearLayout(m); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(K.bg());
        trBtn = m.barTitle(""); trBtn.setTextSize(14); trBtn.setPadding(K.dp(10), 0, K.dp(4), 0);
        trBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseBible(false); } });
        where = m.barTitle(""); where.setGravity(Gravity.CENTER);
        where.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickBook(); } });
        LinearLayout title = new LinearLayout(m); title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(trBtn, new LinearLayout.LayoutParams(-2, -1));
        title.addView(where, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(m.bar(title,
            m.barButton("ic_search", "Search the Bible", new View.OnClickListener() { public void onClick(View v) { search(); } }),
            m.barButton("ic_cross", "The Holy Fathers on this chapter", new View.OnClickListener() { public void onClick(View v) { fathers(); } }),
            m.barButton("ic_map", "Places of this chapter on the map", new View.OnClickListener() { public void onClick(View v) { m.mapChapter(book, ch); } }),
            m.barButton("ic_more", "More", new View.OnClickListener() { public void onClick(View v) { more(); } })));
        FrameLayout body = new FrameLayout(m);
        scroll = new ScrollView(m);
        text = new TextView(m);
        text.setPadding(K.dp(18), K.dp(14), K.dp(18), K.dp(90));
        text.setTextColor(K.fg()); text.setLineSpacing(K.dp(3), 1.12f);
        text.setTypeface(Typeface.SERIF);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        text.setHighlightColor(0x00000000);
        scroll.addView(text);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        // previous and next chapter: round buttons at the bottom corners, or a swipe
        body.addView(roundButton("ic_prev", "Previous chapter", -1), corner(Gravity.BOTTOM | Gravity.START));
        body.addView(roundButton("ic_next", "Next chapter", 1), corner(Gravity.BOTTOM | Gravity.END));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        swipe = new GestureDetector(m, new GestureDetector.SimpleOnGestureListener() {
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if (Math.abs(dx) > K.dp(90) && Math.abs(dx) > 2 * Math.abs(dy) && Math.abs(vx) > 600) { step(dx < 0 ? 1 : -1); return true; }
                return false;
            }
        });
        View.OnTouchListener sw = new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent e) { if (v == text) { touchX = e.getX(); touchY = e.getY(); } swipe.onTouchEvent(e); return false; } };
        text.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
            int off = text.getOffsetForPosition(touchX, touchY);
            for (int[] p : vpos) if (off >= p[1] && off < p[2]) { if (!sel.contains(p[0])) { sel.clear(); sel.add(p[0]); draw(true, 0); } menu(); return true; }
            return false;
        } });
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() { public void onScrollChange(View v, int x, int y, int ox, int oy) { K.ui.removeCallbacks(keepPlace); K.ui.postDelayed(keepPlace, 700); } });
        scroll.setOnTouchListener(sw); text.setOnTouchListener(sw);
        selBar = selectionBar();
        root.addView(selBar);
        Study.listeners.add(new Study.Listener() { public void changed() { if (root.isAttachedToWindow()) draw(true, 0); } });
    }

    /** the verse at the top of the screen, kept to continue reading there */
    final Runnable keepPlace = new Runnable() { public void run() {
        if (text.getLayout() == null) return;
        int off = text.getLayout().getOffsetForHorizontal(text.getLayout().getLineForVertical(scroll.getScrollY() + K.dp(30)), 0);
        for (int[] p : vpos) if (off < p[2]) { try { K.put("place", new JSONObject().put("tr", tr).put("book", book).put("ch", ch).put("v", p[0]).toString()); } catch (Throwable t) {} return; }
    } };

    FrameLayout.LayoutParams corner(int g) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(K.dp(44), K.dp(44), g); p.setMargins(K.dp(12), 0, K.dp(12), K.dp(12)); return p;
    }

    ImageButton roundButton(String ic, String desc, final int d) {
        ImageButton b = new ImageButton(m); b.setImageResource(m.icon(ic)); b.setImageTintList(ColorStateList.valueOf(0xffffffff));
        b.setBackground(K.round(0xcc3e093f, 22)); b.setElevation(K.dp(3)); b.setContentDescription(desc);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { step(d); } });
        return b;
    }

    public View view() { return root; }
    public void show() {
        List<String> have = Db.get().bibles();
        if (!have.contains(tr)) { String d = K.defaultBible(K.lang()); tr = have.contains(d) ? d : (have.isEmpty() ? d : have.get(0)); }
        String b = Db.get().findBook(tr, book); if (b != null) book = b;
        draw(false, 0);
    }
    public boolean back() { if (!sel.isEmpty()) { sel.clear(); draw(true, 0); return true; } return false; }

    // ---------- moving ----------
    void go(String b, int c, int v) {
        book = b; ch = c; sel.clear(); if (v > 0) sel.add(v);
        K.put("book", b); K.puti("ch", c); K.put("tr", tr);
        draw(false, v);
        if (Study.user() != null) try { Study.send(new JSONObject().put("op", "position").put("translation", tr).put("book", b).put("chapter", c).put("verse", v > 0 ? v : 1), false); } catch (Throwable t) {}
    }

    void open(String name, int c, int v, String withTr) {
        if (withTr != null && Db.get().bibles().contains(withTr)) tr = withTr;
        String code = codeFor(name);
        String b = Db.get().findBook(tr, code);
        if (b == null) for (String t : Db.get().bibles()) { b = Db.get().findBook(t, code); if (b != null) { tr = t; break; } }
        go(b != null ? b : code, c, v);
    }

    /** 'Exodus', '1 Chronicles', 'JHN' or a name in another language -> the book's code */
    static String codeFor(String name) {
        String n = name.trim();
        if (n.matches("[1-4A-Za-z]{3}")) return Db.canon(n);
        JSONObject books = K.info().optJSONObject("books");
        if (books != null) {
            java.util.Iterator<String> ls = books.keys();
            while (ls.hasNext()) {
                JSONObject b = books.optJSONObject(ls.next()); if (b == null) continue;
                java.util.Iterator<String> it = b.keys();
                while (it.hasNext()) { String c = it.next(); if (b.optString(c).equalsIgnoreCase(n)) return c; }
            }
        }
        return Db.canon(n);
    }

    void step(int d) {
        List<Integer> chs = Db.get().chapters(tr, book);
        if (chs.contains(ch + d)) { go(book, ch + d, 0); return; }
        List<String[]> bks = Db.get().books(tr);
        for (int i = 0; i < bks.size(); i++) if (bks.get(i)[0].equals(book)) {
            int j = i + d;
            if (j >= 0 && j < bks.size()) { List<Integer> c = Db.get().chapters(tr, bks.get(j)[0]); if (!c.isEmpty()) go(bks.get(j)[0], d > 0 ? c.get(0) : c.get(c.size() - 1), 0); }
            return;
        }
    }

    // ---------- the chapter ----------
    void draw(boolean keep, final int scrollTo) {
        final int y = scroll.getScrollY();
        String name = K.bookName(book, tr);
        where.setText(name + " " + ch + " ▾");
        trBtn.setText(K.shortBible(tr) + " ▾");
        boolean rtl = K.rtl(tr);
        text.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18 * size);
        List<Object[]> rows = Db.get().chapter(tr, book, ch);
        Map<Integer, Object> other = new java.util.HashMap<>();
        if (tr2.length() > 0 && !tr2.equals(tr)) {
            String b2 = Db.get().findBook(tr2, book);
            if (b2 != null) for (Object[] r : Db.get().chapter(tr2, b2, ch)) other.put((Integer) r[0], r[1]);
        }
        Map<Integer, JSONObject> marks = Study.inChapter(tr, book, ch);
        JSONObject red = redLetters();
        vpos.clear();
        SpannableStringBuilder s = new SpannableStringBuilder();
        int a = s.length();
        s.append(name).append(" ").append(String.valueOf(ch)).append("\n");
        s.setSpan(new RelativeSizeSpan(1.45f), a, s.length(), 0); s.setSpan(new StyleSpan(Typeface.BOLD), a, s.length(), 0); s.setSpan(new ForegroundColorSpan(K.brand()), a, s.length(), 0);
        if (rows.isEmpty()) s.append("\nThis chapter is not on this phone yet. Open the menu, then Downloads, to keep this Bible.\n");
        int pos = -1;
        for (Object[] r : rows) {
            final int v = (Integer) r[0];
            int start = s.length();
            if (v == scrollTo) pos = start;
            s.append(" ").append(String.valueOf(v)).append(" ");
            s.setSpan(new SuperscriptSpan(), start, s.length(), 0); s.setSpan(new RelativeSizeSpan(0.6f), start, s.length(), 0);
            s.setSpan(new ForegroundColorSpan(K.dark() ? 0xff5fc4b5 : 0xff2b8a7e), start, s.length(), 0); s.setSpan(new StyleSpan(Typeface.BOLD), start, s.length(), 0);
            int t0 = s.length();
            String plain = K.strip((String) r[1]);
            s.append(plain);
            JSONArray rl = red != null ? red.optJSONArray("" + v) : null;
            if (rl != null && plain.equals(rl.optString(0).trim())) {
                JSONArray rs = rl.optJSONArray(1);
                if (rs != null) for (int i = 0; i < rs.length(); i++) { JSONArray ra = rs.optJSONArray(i); int rx = Math.min(plain.length(), ra.optInt(0)), ry = Math.min(plain.length(), ra.optInt(1)); if (ry > rx) s.setSpan(new ForegroundColorSpan(K.dark() ? 0xffff8a80 : 0xffb3261e), t0 + rx, t0 + ry, 0); }
            }
            JSONObject rec = marks.get(v);
            if (rec != null) {
                s.setSpan(new BackgroundColorSpan(K.color(rec.optString("color", "yellow"))), t0, s.length(), 0);
                if (K.dark()) s.setSpan(new ForegroundColorSpan(0xff1d1a1f), t0, s.length(), 0);
                if (rec.optString("note").length() > 0 && rec.optInt("verse") == v) {
                    int n0 = s.length(); s.append(" ✎\n").append(rec.optString("note").trim()).append("\n");
                    s.setSpan(new ForegroundColorSpan(0xffc08a00), n0, s.length(), 0); s.setSpan(new StyleSpan(Typeface.ITALIC), n0, s.length(), 0); s.setSpan(new RelativeSizeSpan(0.82f), n0, s.length(), 0);
                }
            }
            if (sel.contains(v)) s.setSpan(new BackgroundColorSpan(K.sel()), start, s.length(), 0);
            if (other.containsKey(v)) {
                int o0 = s.length(); s.append("\n").append(K.strip((String) other.get(v)));
                s.setSpan(new StyleSpan(Typeface.ITALIC), o0, s.length(), 0); s.setSpan(new ForegroundColorSpan(K.dim()), o0, s.length(), 0);
            }
            s.setSpan(new ClickableSpan() {
                public void onClick(View w) { choose(v); }
                public void updateDrawState(TextPaint p) { p.setUnderlineText(false); }
            }, start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            vpos.add(new int[]{v, start, s.length()});
            s.append(other.isEmpty() ? " " : "\n\n");
        }
        chapterNames(s);
        text.setText(s);
        updateBar();
        final int p = pos;
        if (!keep) scrollTo(p, 10);
        if (keep) scroll.post(new Runnable() { public void run() { scroll.scrollTo(0, y); } });
    }

    void scrollTo(final int off, final int tries) {
        scroll.postDelayed(new Runnable() { public void run() {
            if (off < 0) { scroll.scrollTo(0, 0); return; }
            if (text.getLayout() == null) { if (tries > 0) scrollTo(off, tries - 1); return; }
            scroll.scrollTo(0, Math.max(0, text.getLayout().getLineTop(text.getLayout().getLineForOffset(off)) - K.dp(40)));
        } }, 60);
    }

    void scrollToVerse(int v) { for (int[] p : vpos) if (p[0] == v) { scrollTo(p[1], 10); return; } }

    JSONObject redLetters() {
        if (!tr.startsWith("eng-kjv")) return null;
        JSONObject en = K.info().optJSONObject("books") != null ? K.info().optJSONObject("books").optJSONObject("en") : null;
        if (en == null || !en.has(Db.canon(book))) return null;
        Object o = Db.get().readJson("red-letters/" + en.optString(Db.canon(book)) + ".json");
        return o instanceof JSONObject ? ((JSONObject) o).optJSONObject("" + ch) : null;
    }

    /** the people and places named in this chapter, each a link to the dictionary */
    void chapterNames(SpannableStringBuilder s) {
        String key = " " + Db.canon(book) + "." + ch + ".";
        List<String[]> found = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        try {
            Object pl = Db.get().readJson("maps/places.json");
            if (pl instanceof JSONObject) { JSONArray P = ((JSONObject) pl).optJSONArray("p"); for (int i = 0; i < P.length(); i++) { JSONArray r = P.getJSONArray(i); if ((" " + r.optString(6)).contains(key)) { String id = r.optString(7).length() > 0 ? r.optString(7) : "p-" + r.optString(0); if (seen.add(id)) found.add(new String[]{id, r.optString(1)}); } } }
            Object pe = Db.get().readJson("dictionary/people.json");
            if (pe instanceof JSONObject) { JSONArray P = ((JSONObject) pe).optJSONArray("p"); for (int i = 0; i < P.length(); i++) { JSONArray r = P.getJSONArray(i); if ((" " + r.optString(5)).contains(key) && seen.add(r.optString(0))) found.add(new String[]{r.optString(0), r.optString(2).length() > 0 ? r.optString(2) : r.optString(1)}); } }
        } catch (Throwable t) {}
        if (found.isEmpty()) return;
        int a = s.length(); s.append("\n\nIn this chapter: ");
        s.setSpan(new ForegroundColorSpan(K.dim()), a, s.length(), 0); s.setSpan(new RelativeSizeSpan(0.85f), a, s.length(), 0);
        for (int i = 0; i < Math.min(60, found.size()); i++) {
            final String id = found.get(i)[0]; int b = s.length(); s.append(found.get(i)[1]);
            s.setSpan(new ClickableSpan() { public void onClick(View w) { m.openDict(id); } }, b, s.length(), 0);
            s.setSpan(new RelativeSizeSpan(0.85f), b, s.length(), 0); s.setSpan(new ForegroundColorSpan(K.accent()), b, s.length(), 0);
            if (i < Math.min(60, found.size()) - 1) { int c = s.length(); s.append(" · "); s.setSpan(new RelativeSizeSpan(0.85f), c, s.length(), 0); }
        }
    }

    /** a long press on a verse: everything you can do with it */
    void menu() {
        final String[] items = {"Copy", "Bookmark", "Highlight…", "Write a note", "Put in a Study Pad", "The Holy Fathers on it", "Cross-references", "Greek and Hebrew words", "Compare with another Bible", "Listen to this chapter", "Places on the map"};
        new AlertDialog.Builder(m).setTitle(ref()).setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int i) {
            switch (i) {
                case 0: copy(); break;
                case 1: mark(null); break;
                case 2: new AlertDialog.Builder(m).setTitle("Highlight").setItems(K.COLORS, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d2, int c) { mark(K.COLORS[c]); } }).show(); break;
                case 3: note(); break;
                case 4: pads(); break;
                case 5: fathers(); break;
                case 6: xref(); break;
                case 7: words(); break;
                case 8: chooseBible(true); break;
                case 9: m.player.chapter(tr, book, ch); break;
                default: m.mapChapter(book, ch);
            }
        } }).show();
    }

    /** a panel whose links stay in the app */
    void linkSheet(String title, String html) {
        final Dialog d = new Dialog(m, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(K.bg());
        box.addView(m.bar(m.barTitle(title), m.barButton("ic_close", "Close", new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } })));
        ScrollView sv = new ScrollView(m);
        TextView t = new TextView(m); t.setPadding(K.dp(18), K.dp(12), K.dp(18), K.dp(40)); t.setTextColor(K.fg()); t.setTextSize(16 * size); t.setLineSpacing(K.dp(2), 1.1f);
        SpannableStringBuilder sp = new SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        for (android.text.style.URLSpan u : sp.getSpans(0, sp.length(), android.text.style.URLSpan.class)) {
            final String href = u.getURL(); int a = sp.getSpanStart(u), z = sp.getSpanEnd(u); sp.removeSpan(u);
            sp.setSpan(new ClickableSpan() { public void onClick(View w) {
                if (href.startsWith("strong:")) { d.dismiss(); lexicon(href.substring(7)); return; }
                d.dismiss(); m.link(href);
            } }, a, z, 0);
        }
        t.setText(sp); t.setMovementMethod(LinkMovementMethod.getInstance()); t.setLinkTextColor(K.accent());
        sv.addView(t); box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        d.setContentView(box); d.show();
    }

    String[] refText(String ref) {
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("([1-4A-Z]{3}) (\\d+):(\\d+)(?:-(\\d+))?").matcher(ref);
        if (!mm.find()) return new String[]{ref, "", ""};
        String b = mm.group(1); int c = Integer.parseInt(mm.group(2)), v1 = Integer.parseInt(mm.group(3)), v2 = mm.group(4) != null ? Integer.parseInt(mm.group(4)) : v1;
        String t = Db.get().findBook(tr, b) != null ? tr : null;
        if (t == null) for (String x : Db.get().bibles()) if (Db.get().findBook(x, b) != null) { t = x; break; }
        StringBuilder txt = new StringBuilder();
        if (t != null) for (Object[] r : Db.get().passage(t, b, c, v1, c, v2)) { if (txt.length() > 0) txt.append(' '); txt.append(K.strip((String) r[2])); }
        return new String[]{K.bookName(b, t != null ? t : tr) + " " + c + ":" + v1 + (v2 > v1 ? "–" + v2 : ""), txt.toString(), "#/p/kjva/" + b + "/" + c + "/" + v1};
    }

    void xref() {
        Object o = Db.get().readJson("xref/" + Db.canon(book) + ".json");
        StringBuilder h = new StringBuilder();
        if (!(o instanceof JSONObject)) h.append("<p>The cross-references are not on this phone yet. Open the menu, then Downloads, and bring everything up to date.</p>");
        else {
            JSONObject d = (JSONObject) o;
            List<Integer> vs = new ArrayList<>(sel);
            if (vs.isEmpty()) { for (int[] p : vpos) if (d.has(ch + ":" + p[0])) vs.add(p[0]); h.append("<p><small>The whole chapter. Choose a verse to see only its own.</small></p>"); }
            for (int v : vs) {
                JSONArray refs = d.optJSONArray(ch + ":" + v);
                h.append("<p><b>v. ").append(v).append("</b></p>");
                if (refs != null) for (int i = 0; i < refs.length(); i++) {
                    String[] x = refText(refs.optString(i)); String tx = x[1].length() > 220 ? x[1].substring(0, 220) + "…" : x[1];
                    h.append("<p><a href=\"").append(x[2]).append("\">").append(K.esc(x[0])).append("</a> <small>").append(K.esc(tx)).append("</small></p>");
                }
            }
        }
        linkSheet("Cross-references · " + K.bookName(book, tr) + " " + ch, h.toString());
    }

    void words() {
        Object o = Db.get().readJson("words/" + Db.canon(book) + ".json.gz");
        StringBuilder h = new StringBuilder();
        if (!(o instanceof JSONObject)) h.append("<p>The words of the original are not on this phone yet. Open the menu, then Downloads, and bring everything up to date.</p>");
        else if (sel.isEmpty()) h.append("<p>Choose a verse (tap it) to see its words in the original language.</p>");
        else {
            JSONObject c = ((JSONObject) o).optJSONObject("" + ch);
            for (int v : sel) {
                h.append("<p><b>").append(K.esc(K.bookName(book, tr))).append(" ").append(ch).append(":").append(v).append("</b></p>");
                JSONArray ws = c != null ? c.optJSONArray("" + v) : null;
                if (ws == null) { h.append("<p><small>Not available for this verse.</small></p>"); continue; }
                for (int i = 0; i < ws.length(); i++) {
                    JSONArray w = ws.optJSONArray(i);
                    h.append("<p><b>").append(K.esc(w.optString(0))).append("</b> <i>").append(K.esc(w.optString(1))).append("</i> — ").append(K.esc(w.optString(2)))
                     .append(" <a href=\"strong:").append(K.esc(w.optString(3))).append("\"><small>").append(K.esc(w.optString(3))).append("</small></a></p>");
                }
            }
            h.append("<p><small>The Greek (Byzantine text) and Hebrew words, with their meanings and Strong's numbers: STEPBible.org (CC BY 4.0). Tap a number for its meaning.</small></p>");
        }
        linkSheet("Greek and Hebrew words", h.toString());
    }

    void lexicon(String num) {
        Object o = Db.get().readJson("words/lexicon.json.gz");
        JSONArray e = o instanceof JSONObject ? ((JSONObject) o).optJSONArray(num) : null;
        String h = e == null ? "<p>" + K.esc(num) + " is not in the lexicon on this phone.</p>"
            : "<h2>" + K.esc(e.optString(0)) + "</h2><p><i>" + K.esc(e.optString(1)) + "</i> — <b>" + K.esc(e.optString(2)) + "</b> <small>" + K.esc(num) + "</small></p><p>" + K.esc(e.optString(3)).replace("; ", "<br>") + "</p><p><small>STEPBible.org brief lexicons (CC BY 4.0).</small></p>";
        linkSheet(num, h);
    }

    void choose(int v) {
        if (sel.contains(v)) sel.remove(v);
        else if (!sel.isEmpty() && (v == sel.first() - 1 || v == sel.last() + 1)) sel.add(v);   // the next verse joins the choice
        else { sel.clear(); sel.add(v); }
        draw(true, 0);
    }

    // ---------- what to do with the chosen verses ----------
    LinearLayout selectionBar() {
        LinearLayout b = new LinearLayout(m); b.setOrientation(LinearLayout.VERTICAL); b.setBackgroundColor(K.card()); b.setElevation(K.dp(6));
        b.setPadding(K.dp(10), K.dp(6), K.dp(10), K.dp(6));
        LinearLayout top = new LinearLayout(m); top.setGravity(Gravity.CENTER_VERTICAL);
        selLabel = K.text(m, "", 15, K.brand(), true);
        top.addView(selLabel, new LinearLayout.LayoutParams(0, -2, 1));
        for (int i = 0; i < K.COLORS.length; i++) {
            final String c = K.COLORS[i];
            View d = new View(m); d.setBackground(K.round(K.COLOR_VALUES[i], 13)); d.setContentDescription("Highlight " + c);
            d.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { mark(c); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(K.dp(26), K.dp(26)); lp.setMargins(K.dp(4), 0, K.dp(4), 0);
            top.addView(d, lp);
        }
        b.addView(top);
        LinearLayout acts = new LinearLayout(m); acts.setGravity(Gravity.CENTER);
        String[][] items = {{"ic_copy", "Copy"}, {"ic_study", "Bookmark"}, {"ic_note", "Note"}, {"ic_tag", "Study Pads"}, {"ic_cross", "Fathers"}, {"ic_route", "Cross-refs"}, {"ic_globe", "Words"}, {"ic_more", "More"}, {"ic_close", "Clear"}};
        for (final String[] it : items) {
            LinearLayout x = new LinearLayout(m); x.setOrientation(LinearLayout.VERTICAL); x.setGravity(Gravity.CENTER);
            android.widget.ImageView im = new android.widget.ImageView(m); im.setImageResource(m.icon(it[0])); im.setImageTintList(ColorStateList.valueOf(K.brand()));
            x.addView(im, new LinearLayout.LayoutParams(K.dp(22), K.dp(22)));
            TextView t = K.text(m, it[1], 11, K.dim(), false); t.setGravity(Gravity.CENTER); x.addView(t);
            x.setBackgroundResource(android.R.drawable.list_selector_background);
            x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { act(it[1]); } });
            acts.addView(x, new LinearLayout.LayoutParams(K.dp(64), K.dp(52)));
        }
        HorizontalScrollView hs = new HorizontalScrollView(m); hs.setHorizontalScrollBarEnabled(false); hs.addView(acts);
        b.addView(hs);
        b.setVisibility(View.GONE);
        return b;
    }

    void updateBar() {
        if (sel.isEmpty()) { selBar.setVisibility(View.GONE); return; }
        selLabel.setText(ref());
        selBar.setVisibility(View.VISIBLE);
    }

    String ref() {
        int a = sel.first(), b = sel.last();
        return K.bookName(book, tr) + " " + ch + ":" + a + (b > a ? "–" + b : "");
    }

    void act(String what) {
        switch (what) {
            case "Copy": copy(); break;
            case "Bookmark": mark(null); break;
            case "Note": note(); break;
            case "Study Pads": pads(); break;
            case "Fathers": fathers(); break;
            case "Cross-refs": xref(); break;
            case "Words": words(); break;
            case "More": menu(); break;
            default: sel.clear(); draw(true, 0);
        }
    }

    String chosenText() {
        StringBuilder b = new StringBuilder();
        for (Object[] r : Db.get().chapter(tr, book, ch)) { int v = (Integer) r[0]; if (v >= sel.first() && v <= sel.last()) { if (b.length() > 0) b.append(' '); b.append(K.strip((String) r[1])); } }
        return "“" + b + "”\n— " + ref() + " (" + K.shortBible(tr) + ")";
    }

    void copy() {
        ((ClipboardManager) m.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Bible", chosenText()));
        K.toast("Copied");
    }

    JSONObject mark(String color) {
        int a = sel.first(), b = sel.last();
        JSONObject r = Study.save(tr, book, ch, a, b > a ? b : 0, color);
        K.toast((color != null ? "Highlighted: " : "Bookmarked: ") + ref());
        return r;
    }

    JSONObject record() { JSONObject r = Study.inChapter(tr, book, ch).get(sel.first()); return r != null ? r : mark(null); }

    void note() {
        final JSONObject r = record();
        final EditText e = new EditText(m); e.setText(r.optString("note")); e.setMinLines(4); e.setGravity(Gravity.TOP);
        FrameLayout f = new FrameLayout(m); f.setPadding(K.dp(20), K.dp(6), K.dp(20), 0); f.addView(e);
        new AlertDialog.Builder(m).setTitle("Note · " + ref()).setView(f)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Study.setNote(r, e.getText().toString().trim()); } })
            .setNegativeButton("Cancel", null).show();
    }

    void pads() {
        final JSONObject r = record();
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(K.dp(20), K.dp(6), K.dp(20), 0);
        final List<CheckBox> checks = new ArrayList<>();
        List<String> mine = Study.tags(r);
        for (JSONObject p : Db.get().pads()) { CheckBox c = new CheckBox(m); c.setText(p.optString("tag")); c.setChecked(mine.contains(p.optString("tag"))); box.addView(c); checks.add(c); }
        final EditText e = new EditText(m); e.setHint("New Study Pad (one word)"); e.setSingleLine(true); box.addView(e);
        ScrollView sv = new ScrollView(m); sv.addView(box);
        new AlertDialog.Builder(m).setTitle("Study Pads · " + ref()).setView(sv)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                List<String> t = new ArrayList<>(); for (CheckBox c : checks) if (c.isChecked()) t.add(c.getText().toString());
                String n = e.getText().toString().trim().toLowerCase().replaceAll("[^\\p{L}\\p{N}_-]", ""); if (n.length() > 30) n = n.substring(0, 30);
                if (n.length() > 0) t.add(n);
                Study.setTags(r, t);
            } })
            .setNegativeButton("Cancel", null).show();
    }

    // ---------- choosing a Bible, a book, a chapter ----------
    void chooseBible(final boolean compare) {
        final List<String> have = Db.get().bibles();
        List<String> names = new ArrayList<>();
        if (compare) names.add("No comparison");
        for (String t : have) names.add(K.bibleName(t));
        new AlertDialog.Builder(m).setTitle(compare ? "Compare with" : "The Bible").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int i) {
                if (compare) { tr2 = i == 0 ? "" : have.get(i - 1); K.put("tr2", tr2); draw(true, 0); return; }
                tr = have.get(i); K.put("tr", tr);
                String b = Db.get().findBook(tr, book);
                List<String[]> bk = Db.get().books(tr);
                go(b != null ? b : (bk.isEmpty() ? book : bk.get(0)[0]), ch, 0);
            } }).show();
    }

    static String fold(String s) { return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(); }

    void pickBook() {
        final Dialog d = new Dialog(m, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        if (K.dark()) d.getContext().setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(K.bg());
        final EditText q = new EditText(m); q.setHint("Type a book…"); q.setSingleLine(true); q.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout head = new LinearLayout(m); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(K.dp(8), K.dp(4), K.dp(8), 0);
        ImageButton close = new ImageButton(m); close.setImageResource(m.icon("ic_close")); close.setImageTintList(ColorStateList.valueOf(K.brand()));
        close.setBackgroundResource(android.R.drawable.list_selector_background);
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        head.addView(q, new LinearLayout.LayoutParams(0, -2, 1)); head.addView(close, new LinearLayout.LayoutParams(K.dp(48), K.dp(48)));
        box.addView(head);
        final ScrollView sv = new ScrollView(m);
        final LinearLayout list = new LinearLayout(m); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(K.dp(10), 0, K.dp(10), K.dp(20));
        sv.addView(list); box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        final List<Object[]> buttons = new ArrayList<>();
        List<String[]> bks = Db.get().books(tr);
        GridLayout legend = new GridLayout(m); legend.setColumnCount(2); legend.setPadding(K.dp(4), K.dp(4), 0, K.dp(4));
        java.util.HashSet<String> present = new java.util.HashSet<>(); for (String[] b : bks) present.add(cat(b[0])[0]);
        for (String[] k : CATS) {
            if (!present.contains(k[0])) continue;
            SpannableStringBuilder l = new SpannableStringBuilder("■ " + k[1]); l.setSpan(new ForegroundColorSpan(android.graphics.Color.parseColor(k[3])), 0, 1, 0);
            TextView lt = K.text(m, "", 12, K.dim(), false); lt.setText(l); lt.setPadding(0, 0, K.dp(12), K.dp(2)); legend.addView(lt);
        }
        list.addView(legend);
        for (int part = 0; part < 2; part++) {
            GridLayout g = new GridLayout(m); g.setColumnCount(3);
            int n = 0;
            for (final String[] b : bks) {
                if (K.nt(Db.canon(b[0])) != (part == 1)) continue;
                n++;
                String nm = K.bookName(b[0], tr);
                Button x = K.button(m, nm, false); x.setTextSize(13); x.setSingleLine(true); x.setEllipsize(android.text.TextUtils.TruncateAt.END);
                String[] k = cat(b[0]);
                android.graphics.drawable.GradientDrawable bg = K.round(K.dark() ? (android.graphics.Color.parseColor(k[3]) & 0x00ffffff) | 0x33000000 : android.graphics.Color.parseColor(k[4]), 10);
                bg.setStroke(K.dp(b[0].equals(book) ? 2.5f : 1.2f), android.graphics.Color.parseColor(b[0].equals(book) ? "#3e093f" : k[3]));
                x.setBackground(bg); x.setTextColor(K.dark() ? 0xfff2eaf4 : android.graphics.Color.parseColor(k[5]));
                x.setPadding(K.dp(4), 0, K.dp(4), 0);
                x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chapters(d, list, b[0]); } });
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0; lp.setMargins(K.dp(3), K.dp(3), K.dp(3), K.dp(3));
                g.addView(x, lp);
                buttons.add(new Object[]{fold(nm + " " + b[1] + " " + b[0]), x, b[0]});
            }
            if (n > 0) {
                TextView h = K.text(m, part == 0 ? "Old Testament" : "New Testament", 15, K.brand(), true); h.setPadding(K.dp(4), K.dp(12), 0, K.dp(4));
                list.addView(h); list.addView(g);
            }
        }
        if (bks.isEmpty()) list.addView(K.text(m, "This Bible is not on this phone yet. Open the menu, then Downloads.", 15, K.fg(), false));
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { String t = fold(e.toString().trim()); for (Object[] o : buttons) ((View) o[1]).setVisibility(t.isEmpty() || ((String) o[0]).contains(t) ? View.VISIBLE : View.GONE); }
        });
        q.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, android.view.KeyEvent e) {
            for (Object[] o : buttons) if (((View) o[1]).getVisibility() == View.VISIBLE) { chapters(d, list, (String) o[2]); return true; }
            return true;
        } });
        d.setContentView(box);
        d.show();
    }

    void chapters(final Dialog d, LinearLayout list, final String b) {
        List<Integer> chs = Db.get().chapters(tr, b);
        if (chs.size() == 1) { verses(d, list, b, chs.get(0)); return; }
        list.removeAllViews();
        TextView h = K.text(m, K.bookName(b, tr), 20, K.brand(), true); h.setPadding(K.dp(4), K.dp(12), 0, K.dp(8)); list.addView(h);
        GridLayout g = new GridLayout(m); g.setColumnCount(6);
        for (final int c : chs) {
            Button x = K.button(m, "" + c, b.equals(book) && c == ch); x.setPadding(0, 0, 0, 0); x.setTextSize(15);
            x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { verses(d, list, b, c); } });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0; lp.setMargins(K.dp(3), K.dp(3), K.dp(3), K.dp(3));
            g.addView(x, lp);
        }
        list.addView(g);
    }

    void verses(final Dialog d, LinearLayout list, final String b, final int c) {
        list.removeAllViews();
        TextView h = K.text(m, K.bookName(b, tr) + " " + c, 20, K.brand(), true); h.setPadding(K.dp(4), K.dp(12), 0, K.dp(8)); list.addView(h);
        Button whole = K.button(m, "The whole chapter", true);
        whole.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); go(b, c, 0); } });
        list.addView(whole);
        TextView t = K.text(m, "Or go to a verse:", 14, K.dim(), false); t.setPadding(K.dp(4), K.dp(12), 0, K.dp(4)); list.addView(t);
        GridLayout g = new GridLayout(m); g.setColumnCount(6);
        for (Object[] r : Db.get().chapter(tr, b, c)) {
            final int v = (Integer) r[0];
            Button x = K.button(m, "" + v, false); x.setPadding(0, 0, 0, 0); x.setTextSize(15);
            x.setOnClickListener(new View.OnClickListener() { public void onClick(View w) { d.dismiss(); go(b, c, v); } });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0; lp.setMargins(K.dp(3), K.dp(3), K.dp(3), K.dp(3));
            g.addView(x, lp);
        }
        list.addView(g);
    }

    void more() {
        String[] items = {"Compare with another Bible…", "Map of this chapter", "Larger text", "Smaller text", "Usual text size"};
        new AlertDialog.Builder(m).setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int i) {
            if (i == 0) chooseBible(true);
            else if (i == 1) m.mapChapter(book, ch);
            else { size = i == 4 ? 1f : Math.max(0.7f, Math.min(2.4f, size * (i == 2 ? 1.12f : 1 / 1.12f))); K.putf("size", size); draw(true, 0); }
        } }).show();
    }

    // ---------- the Holy Fathers on this chapter ----------
    void fathers() {
        StringBuilder h = new StringBuilder();
        Object o = Db.get().readJson("fathers/" + Db.canon(book) + ".json.gz");
        JSONObject names = K.info().optJSONObject("fathers");
        if (!(o instanceof JSONObject)) h.append("<p>The Holy Fathers' commentary is not on this phone yet. Open the menu, then Downloads.</p>");
        else try {
            JSONObject d = (JSONObject) o;
            JSONArray items = d.getJSONObject("e").optJSONArray("" + ch), au = d.getJSONArray("a"), src = d.getJSONArray("s");
            List<JSONArray> rows = new ArrayList<>();
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONArray r = items.getJSONArray(i); int v1 = r.getInt(0), v2 = Math.max(r.getInt(1), v1);
                boolean ok = sel.isEmpty() || v1 == 0;
                for (int w : sel) if (w >= v1 && w <= v2) ok = true;
                if (ok) rows.add(r);
            }
            if (rows.isEmpty()) h.append("<p>Nothing on ").append(sel.isEmpty() ? "this chapter" : "these verses").append(".</p>");
            else if (!sel.isEmpty()) h.append("<p><small>On the verses you chose.</small></p>");
            for (JSONArray r : rows) {
                String a = au.optString(r.getInt(2)); JSONArray nm = names != null ? names.optJSONArray(a) : null;
                int v1 = r.getInt(0), v2 = Math.max(r.getInt(1), v1);
                String s = src.optString(r.getInt(3)); if (s.contains("Catena Aurea")) s = "from the Catena Aurea";
                h.append("<h3>").append(K.esc(nm != null ? nm.optString(0) : a)).append("</h3><p><small>")
                 .append(nm != null && nm.optString(1).length() > 0 ? K.esc(nm.optString(1)) + " · " : "")
                 .append(v1 > 0 ? "v. " + v1 + (v2 > v1 ? "–" + v2 : "") : "the chapter").append("</small></p><blockquote><i>")
                 .append(K.esc(r.getString(4).replaceFirst("^\\(in loco\\.?\\)\\s*", ""))).append("</i></blockquote><p><small>").append(K.esc(s)).append("</small></p>");
            }
        } catch (Throwable t) { h.append("<p>Could not read the commentary.</p>"); }
        sheet("The Holy Fathers · " + K.bookName(book, tr) + " " + ch, h.toString());
    }

    /** a panel that slides up with a text */
    void sheet(String title, String html) {
        final Dialog d = new Dialog(m, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(K.bg());
        ImageButton close = m.barButton("ic_close", "Close", new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        box.addView(m.bar(m.barTitle(title), close));
        ScrollView sv = new ScrollView(m);
        TextView t = new TextView(m); t.setPadding(K.dp(18), K.dp(12), K.dp(18), K.dp(40)); t.setTextColor(K.fg()); t.setTextSize(16 * size); t.setLineSpacing(K.dp(2), 1.1f);
        t.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)); t.setTextIsSelectable(true);
        sv.addView(t); box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        d.setContentView(box); d.show();
    }

    // ---------- search ----------
    void search() {
        final Dialog d = new Dialog(m, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(K.bg());
        final EditText q = new EditText(m); q.setHint("Search words in " + K.shortBible(tr)); q.setSingleLine(true); q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setText(K.get("last_search", ""));
        LinearLayout head = new LinearLayout(m); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(K.dp(8), K.dp(4), K.dp(8), 0);
        ImageButton close = new ImageButton(m); close.setImageResource(m.icon("ic_close")); close.setImageTintList(ColorStateList.valueOf(K.brand()));
        close.setBackgroundResource(android.R.drawable.list_selector_background);
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        head.addView(q, new LinearLayout.LayoutParams(0, -2, 1)); head.addView(close, new LinearLayout.LayoutParams(K.dp(48), K.dp(48)));
        box.addView(head);
        final LinearLayout list = new LinearLayout(m); list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(m); sv.addView(list); box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        q.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, android.view.KeyEvent e) {
            final String s = q.getText().toString().trim(); if (s.isEmpty()) return true;
            K.put("last_search", s);
            list.removeAllViews(); list.addView(K.text(m, "Searching…", 14, K.dim(), false));
            K.bg(new K.Work<List<Object[]>>() { public List<Object[]> run() { return Db.get().search(tr, s, 400); } }, new K.Done<List<Object[]>>() { public void done(List<Object[]> res, Throwable err) {
                list.removeAllViews();
                TextView n = K.text(m, res == null || res.isEmpty() ? "Nothing found" : res.size() + (res.size() >= 400 ? "+ found" : " found"), 14, K.dim(), false); n.setPadding(K.dp(16), K.dp(8), 0, K.dp(4)); list.addView(n);
                if (res != null) for (final Object[] r : res) {
                    list.addView(K.row(m, K.bookName((String) r[0], tr) + " " + r[1] + ":" + r[2], K.strip((String) r[3]), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); go((String) r[0], (Integer) r[1], (Integer) r[2]); } }));
                    list.addView(K.divider(m));
                }
            } });
            return true;
        } });
        d.setContentView(box); d.show();
        q.requestFocus();
    }
}
