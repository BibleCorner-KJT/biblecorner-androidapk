package today.knowingjesus.biblecorner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bible Corner – KJT: a whole app of its own, that works without the internet. */
public class Main extends Activity {
    static final String VER = "3.1";
    static Main live;

    interface Page { View view(); void show(); boolean back(); }

    final Map<String, Page> pages = new HashMap<>();
    final List<String> history = new ArrayList<>();
    FrameLayout content;
    LinearLayout nav;
    Player player;
    String current;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        K.init(this); K.act = this; live = this;
        Window w = getWindow();
        w.setStatusBarColor(0xff3e093f); w.setNavigationBarColor(K.dark() ? 0xff17121a : 0xfffbf8fc);
        if (Build.VERSION.SDK_INT >= 26 && !K.dark()) w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(K.bg());
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        player = new Player(this); root.addView(player.view);
        nav = navBar(); root.addView(nav);
        setContentView(root);
        show("bible", false);   // the app always opens on the Holy Bible
        handle(getIntent());
        K.ui.postDelayed(new Runnable() { public void run() { firstRun(); } }, 300);
        K.ui.postDelayed(new Runnable() { public void run() { offerContinue(); } }, 700);
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); }

    @Override protected void onDestroy() { if (live == this) live = null; super.onDestroy(); }

    void handle(Intent i) {
        if (i == null) return;
        String p = i.getStringExtra("page");
        if ("listen".equals(p)) { show("bible", true); BiblePage bp = (BiblePage) page("bible"); player.chapter(bp.tr, bp.book, bp.ch); }
        else if (p != null) show(p, true);
    }

    // ---------- the bottom bar: Read · Listen · (the app: the menu) · Today · Study ----------
    final Map<String, View> navItems = new HashMap<>();

    LinearLayout navBar() {
        LinearLayout n = new LinearLayout(this); n.setOrientation(LinearLayout.HORIZONTAL); n.setGravity(Gravity.CENTER_VERTICAL);
        n.setBackgroundColor(K.card()); n.setElevation(K.dp(8));
        n.setPadding(0, K.dp(4), 0, K.dp(4));
        String[][] items = {{"bible", "Read", "ic_bible"}, {"listen", "Listen", "ic_listen"}, {"more", "", ""}, {"today", "Today", "ic_today"}, {"study", "Study", "ic_study"}};
        for (final String[] it : items) {
            LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER);
            if (it[0].equals("more")) {
                ImageView im = new ImageView(this); im.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
                im.setBackground(K.round(0xff3e093f, 30)); im.setPadding(K.dp(2), K.dp(2), K.dp(2), K.dp(2)); im.setElevation(K.dp(4));
                im.setContentDescription("Menu");
                b.addView(im, new LinearLayout.LayoutParams(K.dp(54), K.dp(54)));
                b.setTranslationY(-K.dp(10));
            } else {
                ImageView im = new ImageView(this); im.setImageResource(icon(it[2]));
                b.addView(im, new LinearLayout.LayoutParams(K.dp(24), K.dp(24)));
                TextView t = K.text(this, it[1], 11.5f, K.dim(), false); t.setGravity(Gravity.CENTER); b.addView(t);
            }
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (it[0].equals("listen")) { BiblePage bp = (BiblePage) page("bible"); player.chapter(bp.tr, bp.book, bp.ch); return; }
                show(it[0], true);
            } });
            b.setBackgroundResource(android.R.drawable.list_selector_background);
            n.addView(b, new LinearLayout.LayoutParams(0, K.dp(56), 1));
            navItems.put(it[0], b);
        }
        return n;
    }

    void markNav() {
        for (Map.Entry<String, View> e : navItems.entrySet()) {
            if (e.getKey().equals("more")) continue;
            LinearLayout b = (LinearLayout) e.getValue();
            int c = e.getKey().equals(current) ? K.brand() : K.dim();
            ((ImageView) b.getChildAt(0)).setImageTintList(ColorStateList.valueOf(c));
            ((TextView) b.getChildAt(1)).setTextColor(c);
        }
    }

    int icon(String name) { return getResources().getIdentifier(name, "drawable", getPackageName()); }

    // ---------- the pages (each made the first time it is opened) ----------
    Page page(String key) {
        Page p = pages.get(key);
        if (p != null) return p;
        switch (key) {
            case "today": p = new TodayPage(this); break;
            case "study": p = new StudyPage(this); break;
            case "more": p = new MorePage(this); break;
            case "fathers": p = new LibPage(this); break;
            case "dict": p = new DictPage(this); break;
            case "downloads": p = new DlPage(this); break;
            case "maps": p = new MapPage(this); break;
            default: p = new BiblePage(this); key = "bible";
        }
        pages.put(key, p);
        return p;
    }

    void show(String key, boolean remember) {
        Page p = page(key);
        if (current != null && remember && !current.equals(key)) { history.remove(key); history.add(current); }
        current = pages.containsKey(key) ? key : "bible";
        content.removeAllViews();
        View v = p.view();
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        p.show();
        if (!key.equals("downloads")) K.put("page", key);
        markNav();
    }

    @Override public void onBackPressed() {
        Page p = pages.get(current);
        if (p != null && p.back()) return;
        if (!history.isEmpty()) { String k = history.remove(history.size() - 1); show(k, false); return; }
        super.onBackPressed();
    }

    /** a small bubble: back to the very verse where you stopped reading */
    void offerContinue() {
        final JSONObject p; try { p = new JSONObject(K.get("place", "{}")); } catch (Throwable t) { return; }
        if (!p.has("book") || !Db.get().bibles().contains(p.optString("tr"))) return;
        final BiblePage b = (BiblePage) page("bible");
        if (p.optString("book").equals(b.book) && p.optInt("ch") == b.ch && p.optInt("v") <= 2) return;
        final LinearLayout card = new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(K.round(0xf03e093f, 14)); card.setElevation(K.dp(8)); card.setPadding(K.dp(16), K.dp(6), K.dp(6), K.dp(6));
        TextView t = K.text(this, "Continue reading where you left off: " + K.bookName(p.optString("book"), p.optString("tr")) + " " + p.optInt("ch") + ":" + p.optInt("v"), 14, 0xffffffff, false);
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Button go = new android.widget.Button(this); go.setText("Continue"); go.setAllCaps(false); go.setTextColor(0xfff3c548); go.setBackgroundResource(android.R.drawable.list_selector_background);
        card.addView(go);
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); lp.setMargins(K.dp(12), 0, K.dp(12), K.dp(16));
        content.addView(card, lp);
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            content.removeView(card);
            b.tr = p.optString("tr"); K.put("tr", b.tr);
            show("bible", true); b.go(p.optString("book"), p.optInt("ch"), 0); b.scrollToVerse(p.optInt("v"));
        } });
        K.ui.postDelayed(new Runnable() { public void run() { content.removeView(card); } }, 10000);
    }

    // ---------- helpers the pages use ----------
    /** a page's top bar: a title (or a widget) and buttons */
    LinearLayout bar(View title, View... buttons) {
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.HORIZONTAL); b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackgroundColor(0xff3e093f); b.setPadding(K.dp(6), 0, K.dp(4), 0);
        b.addView(title, new LinearLayout.LayoutParams(0, K.dp(56), 1));
        for (View x : buttons) b.addView(x, new LinearLayout.LayoutParams(K.dp(46), K.dp(56)));
        return b;
    }

    TextView barTitle(String s) {
        TextView t = K.text(this, s, 19, 0xffffffff, true); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(K.dp(10), 0, 0, 0);
        t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    ImageButton barButton(String ic, String desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(this); b.setImageResource(icon(ic)); b.setImageTintList(ColorStateList.valueOf(0xffffffff));
        b.setBackgroundResource(android.R.drawable.list_selector_background); b.setContentDescription(desc); b.setOnClickListener(l);
        b.setScaleType(ImageView.ScaleType.CENTER);
        return b;
    }

    /** a page with a bar and something that scrolls under it */
    LinearLayout scrollPage(LinearLayout bar, View inner) {
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setBackgroundColor(K.bg());
        p.addView(bar);
        ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.addView(inner);
        p.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        return p;
    }

    void openPassage(String book, int ch, int v, String tr) {
        BiblePage b = (BiblePage) page("bible");
        show("bible", true);
        b.open(book, ch, v, tr);
    }

    void mapChapter(String book, int ch) { show("maps", true); ((MapPage) page("maps")).chapter(book, ch); }
    void mapPlace(String id) { show("maps", true); ((MapPage) page("maps")).place(id); }

    void openDict(String id) { show("dict", true); ((DictPage) page("dict")).open(id); }

    /** the parts that live on the website's pages (Bible Maps, You Today, Prayer, Good Fun …), in the app's own window */
    void openWeb(String path) {
        Intent i = new Intent(this, LauncherActivity.class);
        String l = K.lang();
        i.setAction(Intent.ACTION_VIEW); i.setData(Uri.parse(Net.site() + path + (l.equals("en") ? "" : (path.contains("?") ? "&" : "?") + "lang=" + l)));
        startActivity(i);
    }

    void link(String href) {
        if (href == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#/(?:p|read)/[^/]+/([^/]+)/(\\d+)(?:/(\\d+))?").matcher(href);
        if (m.find()) { openPassage(Uri.decode(m.group(1)), Integer.parseInt(m.group(2)), m.group(3) != null ? Integer.parseInt(m.group(3)) : 0, null); return; }
        m = java.util.regex.Pattern.compile("#kd:([\\w-]+)").matcher(href);
        if (m.find()) { openDict(m.group(1)); return; }
        if (href.startsWith("#")) return;
        String url = href.startsWith("http") ? href : Net.site() + (href.startsWith("/") ? "" : "/") + href;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Throwable t) {}
    }

    // ---------- the first time: what to keep on the phone ----------
    void firstRun() {
        if (Dl.haveBible()) { background(); return; }
        final LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(K.dp(20), K.dp(8), K.dp(20), 0);
        TextView t = K.text(this, "The app keeps the Holy Bible, the Holy Fathers, the Bible Dictionary and the Bible Maps on this phone, so you can read them without the internet. Choose your languages:", 15, K.fg(), false);
        box.addView(t);
        final List<CheckBox> checks = new ArrayList<>();
        String sys = K.lang();
        for (int i = 0; i < K.LANGS.length; i++) {
            CheckBox c = new CheckBox(this); c.setText(K.LANG_NAMES[i]); c.setTag(K.LANGS[i]); c.setChecked(K.LANGS[i].equals("en") || K.LANGS[i].equals(sys));
            box.addView(c); checks.add(c);
        }
        final CheckBox all = new CheckBox(this); all.setText("Also the Holy Fathers' writings and commentary (about 60 MB)"); all.setChecked(true); box.addView(all);
        ScrollView sv = new ScrollView(this); sv.addView(box);
        new AlertDialog.Builder(this).setTitle("Welcome to Bible Corner").setView(sv)
            .setPositiveButton("Download", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                List<String> langs = new ArrayList<>();
                for (CheckBox c : checks) if (c.isChecked()) langs.add((String) c.getTag());
                if (langs.isEmpty()) langs.add("en");
                String first = "en"; for (String l : langs) if (!l.equals("en")) { first = l; break; }
                K.put("lang", first);
                if (!Net.online()) { K.toast("Downloading needs the internet. Open Downloads in the menu when you are online."); return; }
                final List<String> bibles = new ArrayList<>();
                final List<String> ls = langs; final boolean fathers = all.isChecked();
                K.bg(new K.Work<Boolean>() { public Boolean run() { Net.info(true); for (String l : ls) bibles.add(K.defaultBible(l)); return true; } },
                     new K.Done<Boolean>() { public void done(Boolean r, Throwable e) { Dl.start(bibles, ls, true, fathers); show("downloads", true); } });
            } })
            .setNegativeButton("Later", null).show();
    }

    /** now and then: what the website offers, Today with God, your study, and the app's own update */
    void background() {
        if (!Net.online()) return;
        K.bg(new K.Work<JSONObject>() { public JSONObject run() { return Net.info(true); } }, new K.Done<JSONObject>() { public void done(JSONObject i, Throwable e) {
            if (i == null) return;
            if (i.optJSONObject("user") != null) Study.sync(null);
            String d = Db.get().kv("today_" + K.lang());
            long fetched = 0; try { fetched = Long.parseLong(Db.get().kv("today_at_" + K.lang())); } catch (Throwable t) {}
            if (d == null || System.currentTimeMillis() - fetched > 3L * 86400000L) {
                K.bg.execute(new Runnable() { public void run() { List<String> l = new ArrayList<>(); l.add(K.lang()); Dl.today(l); Db.get().setKv("today_at_" + K.lang(), "" + System.currentTimeMillis()); } });
            }
            JSONObject a = i.optJSONObject("android");
            if (a != null && newer(a.optString("ver"), VER) && !a.optString("ver").equals(K.get("skip_update", ""))) offerUpdate(a.optString("ver"), a.optString("url"));
        } });
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = 0, q = 0;
            try { p = i < x.length ? Integer.parseInt(x[i]) : 0; } catch (Throwable t) {}
            try { q = i < y.length ? Integer.parseInt(y[i]) : 0; } catch (Throwable t) {}
            if (p != q) return p > q;
        }
        return false;
    }

    void offerUpdate(final String ver, final String url) {
        new AlertDialog.Builder(this).setTitle("Bible Corner " + ver).setMessage("A new version of the app is ready. Install it now?")
            .setPositiveButton("Install", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                K.toast("Downloading the new version…"); Updater.start(Main.this, url);
            } })
            .setNegativeButton("Not now", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) { K.put("skip_update", ver); } })
            .show();
    }

    @Override protected void onResume() { super.onResume(); K.act = this; }
}
