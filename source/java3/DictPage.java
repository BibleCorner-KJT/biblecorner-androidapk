package today.knowingjesus.biblecorner;

import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Bible Dictionary: words of the faith, the Apostles, the Fathers, the people and the places of the Bible. */
final class DictPage implements Main.Page {
    static final String[][] KINDS = {{"", "All"}, {"place", "Places"}, {"name", "People & names"}, {"apostle", "Apostles"}, {"father", "Church Fathers"}, {"people", "Peoples"}, {"faith", "The faith"}};
    final Main m;
    final LinearLayout root, listBox;
    final ScrollView article;
    final TextView title, count;
    final EditText q;
    final Spinner kinds;
    final ListView list;
    List<JSONObject> all = new ArrayList<>(), found = new ArrayList<>();
    String lang = "";
    JSONObject open;

    DictPage(Main m) {
        this.m = m;
        root = new LinearLayout(m); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(K.bg());
        title = m.barTitle("Bible Dictionary");
        root.addView(m.bar(title));
        listBox = new LinearLayout(m); listBox.setOrientation(LinearLayout.VERTICAL);
        q = new EditText(m); q.setHint("Find a word, a name or a place"); q.setSingleLine(true);
        LinearLayout top = new LinearLayout(m); top.setPadding(K.dp(10), K.dp(4), K.dp(10), 0);
        kinds = new Spinner(m);
        List<String> names = new ArrayList<>(); for (String[] k : KINDS) names.add(k[1]);
        kinds.setAdapter(new ArrayAdapter<>(m, android.R.layout.simple_spinner_dropdown_item, names));
        top.addView(q, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(kinds, new LinearLayout.LayoutParams(-2, -2));
        listBox.addView(top);
        count = K.text(m, "", 12, K.dim(), false); count.setPadding(K.dp(16), 0, 0, K.dp(4)); listBox.addView(count);
        list = new ListView(m); list.setDivider(new android.graphics.drawable.ColorDrawable(K.line())); list.setDividerHeight(1);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() { public void onItemClick(AdapterView<?> p, View v, int i, long id) { show(found.get(i)); } });
        listBox.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(listBox, new LinearLayout.LayoutParams(-1, 0, 1));
        article = new ScrollView(m); article.setVisibility(View.GONE);
        root.addView(article, new LinearLayout.LayoutParams(-1, 0, 1));
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { K.ui.removeCallbacks(filterLater); K.ui.postDelayed(filterLater, 150); }
        });
        kinds.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> p, View v, int i, long id) { filter(); } public void onNothingSelected(AdapterView<?> p) {} });
    }

    final Runnable filterLater = new Runnable() { public void run() { filter(); } };

    final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() { return found.size(); }
        public Object getItem(int i) { return found.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout r = (LinearLayout) v;
            if (r == null) { r = K.row(m, "", "x", null); }
            JSONObject e = found.get(i);
            ((TextView) r.getChildAt(0)).setText(e.optString("t"));
            ((TextView) r.getChildAt(1)).setText(K.strip(e.optString("x"))); ((TextView) r.getChildAt(1)).setMaxLines(1);
            return r;
        }
    };

    public View view() { return root; }
    public void show() {
        if (!K.lang().equals(lang)) load();
        if (open == null) showList();
    }
    public boolean back() { if (open != null) { open = null; showList(); return true; } return false; }

    void showList() { article.setVisibility(View.GONE); listBox.setVisibility(View.VISIBLE); title.setText("Bible Dictionary"); }

    void load() {
        lang = K.lang();
        count.setText("Opening…");
        K.bg(new K.Work<List<JSONObject>>() { public List<JSONObject> run() { return entries(lang); } }, new K.Done<List<JSONObject>>() { public void done(List<JSONObject> r, Throwable e) {
            all = r != null ? r : new ArrayList<JSONObject>();
            filter();
            if (pending != null) { String p = pending; pending = null; open(p); }
        } });
    }

    static String fold(String s) { return BiblePage.fold(s); }

    void filter() {
        String t = fold(q.getText().toString().trim()), k = KINDS[Math.max(0, kinds.getSelectedItemPosition())][0];
        List<JSONObject> out = new ArrayList<>(), first = new ArrayList<>();
        for (JSONObject e : all) {
            if (k.length() > 0 && !k.equals(e.optString("g"))) continue;
            String s = e.optString("_s");
            if (t.isEmpty() || s.contains(t)) (t.length() > 0 && fold(e.optString("t")).startsWith(t) ? first : out).add(e);
        }
        first.addAll(out); found = first;
        adapter.notifyDataSetChanged();
        count.setText(all.isEmpty() ? "The Bible Dictionary is not on this phone yet. Open the menu, then Downloads." : found.size() + " found");
    }

    String pending;
    void open(String id) {
        if (all.isEmpty()) { pending = id; return; }
        for (JSONObject e : all) if (id.equals(e.optString("id"))) { show(e); return; }
        K.toast("Not in the Bible Dictionary yet");
    }

    void show(JSONObject e) {
        open = e;
        title.setText(e.optString("t"));
        String h = e.optString("h", null);
        if (h == null || h.isEmpty()) h = make(e);
        // the verses, from the Bible on the phone
        Matcher mm = Pattern.compile("<span class=\"kd-v\" data-v=\"([^\"]+)\"></span>").matcher(h);
        StringBuffer b = new StringBuffer();
        String tr = K.get("tr", K.defaultBible(K.lang()));
        while (mm.find()) {
            String[] p = mm.group(1).split("\\.");
            String txt = "";
            try {
                int c = Integer.parseInt(p[1]), v1 = Integer.parseInt(p[2]), v2 = Integer.parseInt(p[3]);
                String t = Db.get().findBook(tr, p[0]) != null ? tr : null;
                if (t == null) for (String x : Db.get().bibles()) if (Db.get().findBook(x, p[0]) != null) { t = x; break; }
                StringBuilder s = new StringBuilder();
                if (t != null) for (Object[] r : Db.get().passage(t, p[0], c, v1, c, v2)) { if (s.length() > 0) s.append(' '); s.append(K.strip((String) r[2])); }
                if (s.length() > 0) txt = "<i>“" + K.esc(s.toString()) + "”</i>";
            } catch (Throwable x) {}
            mm.appendReplacement(b, Matcher.quoteReplacement(txt));
        }
        mm.appendTail(b);
        StringBuilder src = new StringBuilder();
        JSONArray cs = e.optJSONArray("c");
        if (cs != null) for (int i = 0; i < cs.length(); i++) { JSONArray c = cs.optJSONArray(i); if (c != null && c.length() > 2 && c.optString(2).length() > 0) src.append("<br>• <a href=\"").append(K.esc(c.optString(2))).append("\">").append(K.esc(c.optString(0))).append("</a>"); }
        String html = "<h2>" + K.esc(e.optString("t")) + "</h2>" + b + (src.length() > 0 ? "<p><small><b>Sources</b>" + src + "</small></p>" : "");
        Spanned sp = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        SpannableStringBuilder s = new SpannableStringBuilder(sp);
        for (URLSpan u : s.getSpans(0, s.length(), URLSpan.class)) {
            final String href = u.getURL(); int a = s.getSpanStart(u), z = s.getSpanEnd(u);
            s.removeSpan(u);
            s.setSpan(new ClickableSpan() { public void onClick(View w) { m.link(href); } }, a, z, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        LinearLayout box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(m); t.setText(s); t.setTextColor(K.fg()); t.setTextSize(16.5f); t.setLineSpacing(K.dp(2), 1.1f);
        t.setLinkTextColor(K.accent()); t.setMovementMethod(LinkMovementMethod.getInstance()); t.setPadding(K.dp(18), K.dp(10), K.dp(18), K.dp(30));
        box.addView(t);
        String mapId = e.optString("map", "");
        if (mapId.isEmpty() && "place".equals(e.optString("g"))) {
            Object po = Db.get().readJson("maps/places.json");
            if (po instanceof JSONObject) { JSONArray P = ((JSONObject) po).optJSONArray("p"); if (P != null) for (int i = 0; i < P.length(); i++) { JSONArray r = P.optJSONArray(i); if (e.optString("id").equals(r.optString(7))) { mapId = r.optString(0); break; } } }
        }
        if (mapId.length() > 0) {
            final String pid = mapId;
            android.widget.Button mb = K.button(m, "Show on the Bible Maps", false);
            mb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.mapPlace(pid); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(K.dp(18), 0, 0, K.dp(30)); box.addView(mb, lp);
        }
        article.removeAllViews(); article.addView(box); article.scrollTo(0, 0);
        listBox.setVisibility(View.GONE); article.setVisibility(View.VISIBLE);
    }

    // ---------- the entries: the dictionary itself, the places of the maps, the people of the Bible ----------
    static JSONObject raw(String name) {
        try {
            InputStream in = K.app.getResources().openRawResource(K.app.getResources().getIdentifier(name, "raw", K.app.getPackageName()));
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            in.close(); return new JSONObject(o.toString("UTF-8"));
        } catch (Throwable t) { return new JSONObject(); }
    }

    static String fmt(String s, Object... a) { for (int i = 0; i < a.length; i++) s = s.replace("{" + i + "}", String.valueOf(a[i])); return s; }

    static String ref(String v, String l) { String[] p = v.split("\\."); return K.bookName(p[0], null) + " " + p[1] + ":" + p[2]; }

    static String link(String v, JSONObject en, String l) {
        String[] p = v.split("\\.");
        return "<a href=\"#/p/kjva/" + K.esc(en.optString(p[0], p[0])) + "/" + p[1] + "/" + p[2] + "\">" + K.esc(ref(v, l)) + "</a>";
    }

    static List<JSONObject> entries(String l) {
        List<JSONObject> E = new ArrayList<>();
        Object d = Db.get().readJson("dictionary/" + l + ".json");
        if (!(d instanceof JSONObject)) d = Db.get().readJson("dictionary/en.json");
        HashSet<String> have = new HashSet<>();
        if (d instanceof JSONObject) { JSONArray a = ((JSONObject) d).optJSONArray("entries"); if (a != null) for (int i = 0; i < a.length(); i++) { E.add(a.optJSONObject(i)); have.add(a.optJSONObject(i).optString("id")); } }
        JSONObject books = K.info().optJSONObject("books"); JSONObject en = books != null && books.optJSONObject("en") != null ? books.optJSONObject("en") : new JSONObject();
        try { for (JSONObject e : places(l, en)) if (!have.contains(e.optString("id"))) E.add(e); } catch (Throwable t) {}
        try { for (JSONObject e : people(l, en, E)) if (!have.contains(e.optString("id"))) E.add(e); } catch (Throwable t) {}
        for (JSONObject e : E) try {
            StringBuilder s = new StringBuilder(e.optString("t")); JSONArray k = e.optJSONArray("k"); if (k != null) for (int i = 0; i < k.length(); i++) s.append(' ').append(k.optString(i));
            e.put("_s", fold(s.toString()));
        } catch (Throwable t) {}
        Collections.sort(E, new Comparator<JSONObject>() { public int compare(JSONObject a, JSONObject b) { return a.optString("t").compareToIgnoreCase(b.optString("t")); } });
        return E;
    }

    static List<JSONObject> places(String l, JSONObject en) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        Object po = Db.get().readJson("maps/places.json");
        if (!(po instanceof JSONObject)) return out;
        JSONArray P = ((JSONObject) po).optJSONArray("p");
        Object io = Db.get().readJson("maps/ident.json"); JSONObject ident = io instanceof JSONObject ? (JSONObject) io : new JSONObject();
        Object no = l.equals("en") ? null : Db.get().readJson("maps/names-" + l + ".json"); JSONObject names = no instanceof JSONObject ? (JSONObject) no : new JSONObject();
        JSONObject PW = raw("place_words"); JSONObject W = PW.optJSONObject(l) != null ? PW.optJSONObject(l) : PW.optJSONObject("en");
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < P.length(); i++) {
            final JSONArray r = P.getJSONArray(i);
            if (r.optString(7).length() > 0) continue;
            String name = names.optString(r.getString(0), r.getString(1));
            String vsS = r.optString(6); final String[] vs = vsS.isEmpty() ? new String[0] : vsS.split(" ");
            String kind = W.getJSONObject("k").optString(r.getString(2), W.getJSONObject("k").optString("o"));
            String x = fmt(vs.length == 1 ? W.getString("one") : W.getString("intro"), name, kind, vs.length);
            String key = name + "|" + r.getString(2);
            String t = name + (seen.contains(key) ? " (" + (vs.length > 0 ? ref(vs[0], l) : r.getString(0)) + ")" : "");
            seen.add(key);
            JSONObject e = new JSONObject().put("id", "p-" + r.getString(0)).put("t", t).put("g", "place").put("k", new JSONArray().put(name)).put("x", x).put("map", r.getString(0))
                .put("c", new JSONArray().put(new JSONArray().put("OpenBible.info (CC BY 4.0)").put("").put("https://www.openbible.info/geo/ancient/" + r.getString(0) + "/" + r.optString(10))));
            StringBuilder h = new StringBuilder("<p>" + K.esc(x) + (vs.length > 0 ? " " + fmt(W.getString("first"), link(vs[0], en, l)) : "") + "</p>");
            JSONArray ids = ident.optJSONArray(r.getString(0));
            if (ids != null && ids.length() > 0) {
                h.append("<p><b>").append(K.esc(W.getString("where"))).append("</b><br>");
                for (int k = 0; k < Math.min(3, ids.length()); k++) {
                    JSONArray w = ids.getJSONArray(k); String type = w.optString(0), a1 = w.optString(1), y = w.length() > 3 ? w.optString(2) : "";
                    String where = type.length() > 0 ? fmt(W.optString(type, "{0}"), a1, y) : a1;
                    h.append(k > 0 ? "<br>" : "").append(K.esc(where)).append(" <small>(").append(K.esc(W.optString("c" + w.opt(w.length() - 1)))).append(")</small>");
                }
                h.append("</p>");
            }
            if (vs.length > 0) {
                h.append("<p><b>").append(K.esc(W.getString("verses"))).append("</b></p>");
                for (int k = 0; k < Math.min(4, vs.length); k++) { String[] p = vs[k].split("\\."); h.append("<blockquote><span class=\"kd-v\" data-v=\"").append(p[0]).append('.').append(p[1]).append('.').append(p[2]).append('.').append(p[2]).append('.').append(p[1]).append("\"></span> — ").append(link(vs[k], en, l)).append("</blockquote>"); }
                if (vs.length > 4) { h.append("<p>").append(K.esc(W.getString("more"))).append(": "); for (int k = 4; k < Math.min(40, vs.length); k++) h.append(k > 4 ? ", " : "").append(link(vs[k], en, l)); h.append("</p>"); }
            }
            e.put("h", h.toString());
            out.add(e);
        }
        return out;
    }

    static List<JSONObject> people(String l, JSONObject en, List<JSONObject> base) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        Object po = Db.get().readJson("dictionary/people.json");
        if (!(po instanceof JSONObject)) return out;
        JSONArray P = ((JSONObject) po).optJSONArray("p");
        JSONObject HW = raw("people_words"); JSONObject W = HW.optJSONObject(l) != null ? HW.optJSONObject(l) : HW.optJSONObject("en");
        Map<String, String> title = new HashMap<>(); Map<String, Integer> cnt = new HashMap<>();
        for (JSONObject e : base) title.put(e.optString("id"), e.optString("t"));
        for (int i = 0; i < P.length(); i++) { JSONArray r = P.getJSONArray(i); String t = r.optString(2).length() > 0 ? r.optString(2) : r.optString(1); cnt.put(t, cnt.containsKey(t) ? cnt.get(t) + 1 : 1); }
        for (int i = 0; i < P.length(); i++) {
            JSONArray r = P.getJSONArray(i); String t = r.optString(2).length() > 0 ? r.optString(2) : r.optString(1);
            if (cnt.get(t) > 1 && r.optString(5).length() > 0) t += " (" + ref(r.optString(5).split(" ")[0], l) + ")";
            title.put(r.getString(0), t);
        }
        for (int i = 0; i < P.length(); i++) {
            JSONArray r = P.getJSONArray(i);
            String g = r.optString(3).length() > 0 ? r.optString(3) : "n"; int n = r.optInt(6);
            String x = fmt(n == 1 ? W.getString(g + "1") : W.getString(g), r.optString(1), n);
            String[] vs = r.optString(5).isEmpty() ? new String[0] : r.optString(5).split(" ");
            StringBuilder h = new StringBuilder("<p>" + K.esc(x) + (vs.length > 0 ? " " + fmt(W.getString("first"), link(vs[0], en, l)) : "") + (r.optString(4).length() > 0 ? " " + K.esc(fmt(W.getString("also"), r.optString(4))) : "") + "</p>");
            String[][] fam = {{W.getString("father"), "7"}, {W.getString("mother"), "8"}, {g.equals("m") ? W.getString("wife") : g.equals("f") ? W.getString("husband") : W.getString("spouse"), "9"}, {W.getString("children"), "10"}, {W.getString("siblings"), "11"}};
            StringBuilder fh = new StringBuilder();
            for (String[] f : fam) {
                JSONArray ids = r.optJSONArray(Integer.parseInt(f[1])); StringBuilder w = new StringBuilder();
                if (ids != null) for (int k = 0; k < ids.length(); k++) { String id = ids.optString(k); if (!title.containsKey(id)) continue; if (w.length() > 0) w.append(", "); w.append("<a href=\"#kd:").append(id).append("\">").append(K.esc(title.get(id))).append("</a>"); }
                if (w.length() > 0) fh.append("<br><b>").append(K.esc(f[0])).append(":</b> ").append(w);
            }
            if (fh.length() > 0) h.append("<p><b>").append(K.esc(W.getString("fam"))).append("</b>").append(fh).append("</p>");
            if (vs.length > 0) {
                h.append("<p><b>").append(K.esc(W.getString("verses"))).append("</b></p>");
                for (int k = 0; k < Math.min(4, vs.length); k++) { String[] p = vs[k].split("\\."); h.append("<blockquote><span class=\"kd-v\" data-v=\"").append(p[0]).append('.').append(p[1]).append('.').append(p[2]).append('.').append(p[2]).append('.').append(p[1]).append("\"></span> — ").append(link(vs[k], en, l)).append("</blockquote>"); }
                if (vs.length > 4) { h.append("<p>").append(K.esc(W.getString("more"))).append(": "); for (int k = 4; k < Math.min(40, vs.length); k++) h.append(k > 4 ? ", " : "").append(link(vs[k], en, l)); h.append(n > 40 ? " …" : "").append("</p>"); }
            }
            JSONArray k = new JSONArray().put(r.optString(1)); if (r.optString(4).length() > 0) k.put(r.optString(4));
            out.add(new JSONObject().put("id", r.getString(0)).put("t", title.get(r.getString(0))).put("g", "name").put("k", k).put("x", x).put("h", h.toString())
                .put("c", new JSONArray().put(new JSONArray().put("Theographic Bible Metadata (CC BY-SA 4.0)").put("").put("https://github.com/robertrouse/theographic-bible-metadata"))));
        }
        return out;
    }

    static String make(JSONObject e) { return e.optString("h"); }
}
