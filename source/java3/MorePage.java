package today.knowingjesus.biblecorner;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The menu: everything else in the app. */
final class MorePage implements Main.Page {
    final Main m;
    final LinearLayout root, box;

    MorePage(Main m) {
        this.m = m;
        box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(K.dp(12), K.dp(12), K.dp(12), K.dp(30));
        TextView brand = m.barTitle("KJT ☦  Bible Corner"); brand.setTextColor(0xfff3c548); brand.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        root = m.scrollPage(m.bar(brand), box);
    }

    public View view() { return root; }
    public boolean back() { return false; }

    public void show() {
        box.removeAllViews();
        section("Read and learn");
        grid(new Object[][]{
            {"ic_cross", "The Holy Fathers", "Their writings, like books", "m.show(\"fathers\", true)"},
            {"ic_dict", "Bible Dictionary", "Names, places, people", "m.show(\"dict\", true)"},
            {"ic_map", "Bible Maps", "Places and journeys", "m.show(\"maps\", true)"},
            {"ic_today", "You Today", "God's word for your day", "m.openWeb(\"/you-today/\")"},
        });
        section("Pray and rest");
        grid(new Object[][]{
            {"ic_heart", "Prayer", "Pray, and ask for prayer", "m.openWeb(\"/the-faith/prayer/\")"},
            {"ic_route", "Good Fun", "Games for grown-ups", "m.openWeb(\"/fun-and-games/\")"},
            {"ic_pin", "Find a Church", "Orthodox churches near you", "m.openWeb(\"/find-a-church/\")"},
            {"ic_globe", "The website", "knowingjesus.today", "m.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Net.site())))"},
        });
        section("This app");
        grid(new Object[][]{
            {"ic_download", "Downloads and language", "What the phone keeps", "m.show(\"downloads\", true)"},
            {"ic_refresh", "Look for an update", "Bible Corner " + Main.VER, "update()"},
        });
        TextView t = K.text(m, "Bible Corner – KJT " + Main.VER + " · knowingjesus.today\nPlaces: OpenBible.info (CC BY 4.0) · People: Theographic Bible Metadata (CC BY-SA 4.0) · Maps: Natural Earth", 12, K.dim(), false);
        t.setGravity(Gravity.CENTER); t.setPadding(0, K.dp(18), 0, 0); box.addView(t);
    }

    void section(String s) { TextView t = K.text(m, s, 14, K.brand(), true); t.setPadding(K.dp(4), K.dp(14), 0, K.dp(6)); box.addView(t); }

    void grid(Object[][] items) {
        GridLayout g = new GridLayout(m); g.setColumnCount(2);
        for (final Object[] it : items) {
            LinearLayout c = new LinearLayout(m); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(K.dp(14), K.dp(14), K.dp(14), K.dp(14));
            c.setBackground(K.round(K.card(), 14)); c.setElevation(K.dp(1.5f));
            ImageView im = new ImageView(m); im.setImageResource(m.icon((String) it[0])); im.setImageTintList(ColorStateList.valueOf(K.dark() ? 0xffe0a8d8 : 0xff7a1f6b));
            c.addView(im, new LinearLayout.LayoutParams(K.dp(26), K.dp(26)));
            TextView a = K.text(m, (String) it[1], 15.5f, K.fg(), true); a.setPadding(0, K.dp(8), 0, 0); c.addView(a);
            c.addView(K.text(m, (String) it[2], 12.5f, K.dim(), false));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { act((String) it[3]); } });
            c.setForeground(m.getDrawable(android.R.drawable.list_selector_background));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0; lp.setMargins(K.dp(5), K.dp(5), K.dp(5), K.dp(5));
            g.addView(c, lp);
        }
        box.addView(g);
    }

    void act(String a) {
        if (a.startsWith("m.show(\"fathers")) m.show("fathers", true);
        else if (a.startsWith("m.show(\"dict")) m.show("dict", true);
        else if (a.startsWith("m.show(\"downloads")) m.show("downloads", true);
        else if (a.startsWith("m.show(\"maps")) m.show("maps", true);
        else if (a.startsWith("m.openWeb(")) m.openWeb(a.replaceAll("^m.openWeb\\(\"|\"\\)$", ""));
        else if (a.startsWith("update")) update();
        else m.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Net.site())));
    }

    void update() {
        if (!Net.online()) { K.toast("Looking for an update needs the internet"); return; }
        K.bg(new K.Work<org.json.JSONObject>() { public org.json.JSONObject run() { return Net.info(true); } }, new K.Done<org.json.JSONObject>() { public void done(org.json.JSONObject i, Throwable e) {
            org.json.JSONObject a = i == null ? null : i.optJSONObject("android");
            if (a != null && Main.newer(a.optString("ver"), Main.VER)) m.offerUpdate(a.optString("ver"), a.optString("url"));
            else K.toast("This is the newest Bible Corner (" + Main.VER + ")");
        } });
    }
}
