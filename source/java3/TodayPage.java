package today.knowingjesus.biblecorner;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Today with God: the verse of the day, the day in the Church and its readings, read from the Bibles on the phone. */
final class TodayPage implements Main.Page {
    final Main m;
    final LinearLayout root, box;
    final TextView title;
    final Calendar day = Calendar.getInstance();

    TodayPage(Main m) {
        this.m = m;
        title = m.barTitle("Today with God");
        box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(K.dp(12), K.dp(12), K.dp(12), K.dp(30));
        root = m.scrollPage(m.bar(title,
            m.barButton("ic_prev", "The day before", new View.OnClickListener() { public void onClick(View v) { day.add(Calendar.DAY_OF_MONTH, -1); show(); } }),
            m.barButton("ic_today", "Today", new View.OnClickListener() { public void onClick(View v) { day.setTimeInMillis(System.currentTimeMillis()); show(); } }),
            m.barButton("ic_next", "The day after", new View.OnClickListener() { public void onClick(View v) { day.add(Calendar.DAY_OF_MONTH, 1); show(); } })), box);
    }

    public View view() { return root; }
    public boolean back() { return false; }

    String bibleFor(String book) {
        for (String t : new String[]{K.get("tr", ""), K.defaultBible(K.lang()), "eng-kjv"}) if (t.length() > 0 && Db.get().findBook(t, book) != null) return t;
        for (String t : Db.get().bibles()) if (Db.get().findBook(t, book) != null) return t;
        return null;
    }

    LinearLayout card(String head, String icon) {
        LinearLayout c = new LinearLayout(m); c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(K.round(K.dark() ? 0xff241c28 : 0xfffffbf0, 14)); c.setElevation(K.dp(1.5f));
        c.setPadding(K.dp(16), K.dp(14), K.dp(16), K.dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, K.dp(12));
        box.addView(c, lp);
        if (head != null) {
            LinearLayout h = new LinearLayout(m); h.setGravity(Gravity.CENTER_VERTICAL);
            if (icon != null) { android.widget.ImageView im = new android.widget.ImageView(m); im.setImageResource(m.icon(icon)); im.setImageTintList(android.content.res.ColorStateList.valueOf(0xff7a1f6b)); LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(K.dp(20), K.dp(20)); ip.setMargins(0, 0, K.dp(10), 0); h.addView(im, ip); }
            TextView t = K.text(m, head, 18, K.brand(), true); t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); h.addView(t);
            c.addView(h);
        }
        return c;
    }

    TextView line(LinearLayout c, CharSequence s, float sp, int color) {
        TextView t = new TextView(m); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(0, K.dp(6), 0, 0); t.setTextIsSelectable(true);
        c.addView(t); return t;
    }

    public void show() {
        box.removeAllViews();
        String iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.getTime());
        title.setText(new SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(day.getTime()));
        JSONObject data = Db.get().kvJson("today_" + K.lang());
        if (data.length() == 0) data = Db.get().kvJson("today_en");
        JSONObject d = null;
        JSONArray days = data.optJSONArray("days");
        if (days != null) for (int i = 0; i < days.length(); i++) if (iso.equals(days.optJSONObject(i).optString("date"))) d = days.optJSONObject(i);
        if (d == null) {
            LinearLayout c = card("Today with God", "ic_cross");
            line(c, "This day is not on this phone yet. It comes with the internet: the app keeps the next two months each time it can reach the website.", 15, K.dim());
            if (Net.online()) K.bg(new K.Work<Boolean>() { public Boolean run() { List<String> l = new java.util.ArrayList<>(); l.add(K.lang()); Dl.today(l); return true; } },
                new K.Done<Boolean>() { public void done(Boolean r, Throwable e) { if (Db.get().kvJson("today_" + K.lang()).optJSONArray("days") != null && root.isAttachedToWindow()) showOnce(); } });
            return;
        }
        LinearLayout head = card(d.optString("title", d.optString("feast", "")), "ic_cross");
        if (d.optString("feast").length() > 0) { TextView t = line(head, d.optString("feast"), 16, K.dark() ? 0xffff9a9a : 0xff8a1f2b); t.setTypeface(Typeface.DEFAULT_BOLD); }
        if (d.optString("fast").length() > 0) line(head, "🍃 " + d.optString("fast"), 15, K.dark() ? 0xffb5d68a : 0xff4f6b2a);
        if (d.optString("saints").length() > 0) line(head, d.optString("saints"), 14, K.dim());
        final JSONObject v = d.optJSONObject("votd");
        if (v != null) {
            LinearLayout c = card("Verse of the day", "ic_heart");
            String book = v.optString("book"); String tr = bibleFor(book);
            int c1 = v.optInt("c"), v1 = v.optInt("v1"), v2 = Math.max(v.optInt("v2"), v1);
            StringBuilder s = new StringBuilder();
            if (tr != null) for (Object[] r : Db.get().passage(tr, book, c1, v1, c1, v2)) { if (s.length() > 0) s.append(' '); s.append(K.strip((String) r[2])); }
            if (s.length() == 0 && K.lang().equals("en")) s.append(K.strip(v.optString("text")));
            if (s.length() > 0) { TextView t = line(c, "“" + s + "”", 19, K.fg()); t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC)); }
            refButton(c, K.bookName(book, tr) + " " + c1 + ":" + v1 + (v2 > v1 ? "–" + v2 : ""), book, c1, v1);
        }
        JSONArray rd = d.optJSONArray("readings");
        if (rd != null && rd.length() > 0) {
            LinearLayout c = card("The readings of the day", "ic_bible");
            for (int i = 0; i < rd.length(); i++) {
                JSONObject r = rd.optJSONObject(i); JSONArray refs = r.optJSONArray("refs");
                if (refs != null) for (int k = 0; k < refs.length(); k++) reading(c, r, refs.optJSONArray(k));
            }
        }
        JSONArray news = d.optJSONArray("news");
        if (news != null && news.length() > 0) {
            LinearLayout c = card("Coming soon", "ic_today");
            for (int i = 0; i < news.length(); i++) { JSONArray n = news.optJSONArray(i); line(c, ("fast".equals(n.optString(0)) ? "🍃 " : "✦ ") + n.optString(1) + (n.optString(2).length() > 0 ? " — until " + n.optString(2) : ""), 15, K.fg()); }
        }
    }

    boolean once;
    void showOnce() { if (once) return; once = true; show(); }

    void refButton(LinearLayout c, String label, final String book, final int ch, final int v) {
        TextView b = K.text(m, label + "  →", 15, K.accent(), true); b.setPadding(0, K.dp(10), 0, K.dp(2));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { m.openPassage(book, ch, v, null); } });
        c.addView(b);
    }

    void reading(LinearLayout c, JSONObject r, JSONArray ref) {
        if (ref == null) return;
        final String book = ref.optString(0); final int c1 = ref.optInt(1), v1 = ref.optInt(2), c2 = ref.optInt(3), v2 = ref.optInt(4);
        final String tr = bibleFor(book);
        String label = r.optString("src") + (r.optString("desc").length() > 0 ? " (" + r.optString("desc") + ")" : "") + " — " + K.bookName(book, tr) + " " + c1 + ":" + v1
                + (c2 == c1 ? (v2 != v1 ? "–" + v2 : "") : "–" + c2 + ":" + v2);
        final TextView head = K.text(m, "▸ " + label, 15.5f, K.brand(), true); head.setPadding(0, K.dp(12), 0, K.dp(2));
        final TextView body = new TextView(m); body.setTextColor(K.fg()); body.setTextSize(16); body.setTypeface(Typeface.SERIF); body.setLineSpacing(K.dp(2), 1.08f); body.setTextIsSelectable(true);
        SpannableStringBuilder s = new SpannableStringBuilder();
        List<Object[]> rows = tr != null ? Db.get().passage(tr, book, c1, v1, c2, v2) : new java.util.ArrayList<Object[]>();
        for (Object[] x : rows) {
            int a = s.length(); s.append(c2 != c1 ? x[0] + ":" + x[1] : "" + x[1]).append(" ");
            s.setSpan(new SuperscriptSpan(), a, s.length(), 0); s.setSpan(new RelativeSizeSpan(0.62f), a, s.length(), 0); s.setSpan(new ForegroundColorSpan(0xff2b8a7e), a, s.length(), 0); s.setSpan(new StyleSpan(Typeface.BOLD), a, s.length(), 0);
            s.append(K.strip((String) x[2])).append(" ");
        }
        if (rows.isEmpty()) s.append("This book is not in the Bibles on this phone. Open the menu, then Downloads.");
        body.setText(s);
        final boolean open = r.optBoolean("main") || r.optInt("main") == 1;
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        head.setText((open ? "▾ " : "▸ ") + label);
        final String lab = label;
        head.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { boolean o = body.getVisibility() != View.VISIBLE; body.setVisibility(o ? View.VISIBLE : View.GONE); head.setText((o ? "▾ " : "▸ ") + lab); } });
        c.addView(head); c.addView(body);
        TextView go = K.text(m, "Read in the Holy Bible  →", 14, K.accent(), true); go.setPadding(0, K.dp(6), 0, K.dp(4));
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { m.openPassage(book, c1, v1, tr); } });
        c.addView(go);
    }
}
