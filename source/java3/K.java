package today.knowingjesus.biblecorner;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small things the whole app uses: the website, settings, colours, sizes, work in the background. */
final class K {
    static final String SITE = "https://knowingjesus.today";
    static final String[] LANGS = {"en", "ar", "el", "ru", "es", "fr", "hi", "tl", "zh"};
    static final String[] LANG_NAMES = {"English", "العربية", "Ελληνικά", "Русский", "Español", "Français", "हिन्दी", "Tagalog", "中文"};
    static final String[][] DEFAULT_BIBLES = {{"en", "eng-kjv"}, {"ar", "arb-vd"}, {"el", "grcorth"}, {"ru", "russyn"}, {"es", "spaRV1909"}, {"fr", "fraLSG"}, {"hi", "hin2017"}, {"tl", "tglulb"}, {"zh", "cmn-cu89s"}};
    static final String[] NT = "MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV".split(" ");

    static Context app;
    static SharedPreferences prefs;
    static final Handler ui = new Handler(Looper.getMainLooper());
    static final ExecutorService bg = Executors.newFixedThreadPool(4);
    static float density = 2;

    static void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences("kjt3", Context.MODE_PRIVATE);
        density = app.getResources().getDisplayMetrics().density;
    }

    // settings
    static String get(String k, String d) { return prefs.getString(k, d); }
    static int geti(String k, int d) { return prefs.getInt(k, d); }
    static float getf(String k, float d) { return prefs.getFloat(k, d); }
    static void put(String k, String v) { prefs.edit().putString(k, v).apply(); }
    static void puti(String k, int v) { prefs.edit().putInt(k, v).apply(); }
    static void putf(String k, float v) { prefs.edit().putFloat(k, v).apply(); }

    static String lang() {
        String l = get("lang", "");
        if (l.length() > 0) return l;
        String sys = Locale.getDefault().getLanguage();
        for (String x : LANGS) if (x.equals(sys)) return x;
        return "en";
    }

    static String defaultBible(String l) {
        try { JSONObject d = info().optJSONObject("defaults"); if (d != null && d.has(l)) return d.getString(l); } catch (Throwable t) {}
        for (String[] b : DEFAULT_BIBLES) if (b[0].equals(l)) return b[1];
        return "eng-kjv";
    }

    static boolean nt(String code) { for (String b : NT) if (b.equals(code)) return true; return false; }

    // what the website said about itself (Bibles, book names, the Holy Fathers ...)
    static JSONObject info() { return Db.get().kvJson("info"); }

    static String bookName(String code, String tr) {
        try {
            JSONObject books = info().optJSONObject("books");
            String l = lang();
            if (books != null) {
                JSONObject m = books.optJSONObject(l);
                if (m != null && m.has(code)) return m.getString(code);
            }
        } catch (Throwable t) {}
        if (tr != null) { String n = Db.get().bookName(tr, code); if (n != null) return n; }
        try { JSONObject m = info().optJSONObject("books").optJSONObject("en"); if (m != null && m.has(code)) return m.getString(code); } catch (Throwable t) {}
        return code;
    }

    static String bibleName(String tr) {
        try { JSONArray v = info().optJSONObject("versions").optJSONArray(tr); if (v != null) return v.getString(1); } catch (Throwable t) {}
        return tr;
    }

    static String shortBible(String tr) {
        String n = bibleName(tr); int i = n.indexOf(" · "); return i > 0 ? n.substring(0, i) : n;
    }

    static boolean rtl(String tr) {
        try { JSONArray v = info().optJSONObject("versions").optJSONArray(tr); if (v != null && v.length() > 2) return "rtl".equals(v.getString(2)); } catch (Throwable t) {}
        return tr.startsWith("arb") || tr.startsWith("heb") || tr.startsWith("urd") || tr.startsWith("pes");
    }

    // looks
    static boolean dark() { return (app.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    static int bg() { return dark() ? 0xff17121a : 0xfffbf8fc; }
    static int card() { return dark() ? 0xff241c28 : 0xffffffff; }
    static int fg() { return dark() ? 0xffece4ef : 0xff221a26; }
    static int dim() { return dark() ? 0xffa99bb0 : 0xff6d5f74; }
    static int brand() { return dark() ? 0xffe9c8f0 : 0xff3e093f; }
    static int gold() { return 0xffc99a1d; }
    static int accent() { return dark() ? 0xff8cc4e8 : 0xff2b6a8e; }
    static int line() { return dark() ? 0x22ffffff : 0x18000000; }
    static int sel() { return dark() ? 0xff6b4d78 : 0xffe9d8f3; }
    static final String[] COLORS = {"yellow", "green", "blue", "pink", "purple"};
    static final int[] COLOR_VALUES = {0xfff6d365, 0xff9be3a4, 0xff9cc9f5, 0xfff7a8c4, 0xffc9a7f0};
    static int color(String name) { for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(name)) return COLOR_VALUES[i]; return COLOR_VALUES[0]; }

    static int dp(float v) { return Math.round(v * density); }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s, 22, brand(), true); t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); return t;
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }

    static Button button(Context c, String s, boolean main) {
        Button b = new Button(c);
        b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(main ? 0xffffffff : brand());
        b.setBackground(round(main ? 0xff3e093f : (dark() ? 0xff33283a : 0xfff1e8f4), 22));
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44));
        return b;
    }

    /** a flat tappable row: a title and a smaller line under it */
    static LinearLayout row(Context c, String title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(c); r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(11), dp(16), dp(11));
        TextView a = text(c, title, 16, fg(), false); r.addView(a);
        if (sub != null && sub.length() > 0) { TextView b = text(c, sub, 13, dim(), false); b.setMaxLines(3); b.setEllipsize(TextUtils.TruncateAt.END); r.addView(b); }
        if (click != null) { r.setOnClickListener(click); r.setBackgroundResource(android.R.drawable.list_selector_background); }
        return r;
    }

    static View divider(Context c) {
        View v = new View(c); v.setBackgroundColor(line());
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.6f))));
        return v;
    }

    static void toast(final String s) { ui.post(new Runnable() { public void run() { Toast.makeText(app, s, Toast.LENGTH_SHORT).show(); } }); }

    interface Work<T> { T run() throws Exception; }
    interface Done<T> { void done(T result, Throwable error); }

    /** work in the background, then the answer back on the screen */
    static <T> void bg(final Work<T> w, final Done<T> d) {
        bg.execute(new Runnable() { public void run() {
            T r = null; Throwable e = null;
            try { r = w.run(); } catch (Throwable t) { e = t; }
            final T rr = r; final Throwable ee = e;
            if (d != null) ui.post(new Runnable() { public void run() { d.done(rr, ee); } });
        } });
    }

    static String strip(String html) { return html == null ? "" : html.replaceAll("<[^>]+>", "").trim(); }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    static int gravityStart() { return Gravity.START | Gravity.CENTER_VERTICAL; }

    static Activity act;
}
