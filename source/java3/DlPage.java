package today.knowingjesus.biblecorner;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Downloads and language: what the app keeps on the phone. */
final class DlPage implements Main.Page {
    final Main m;
    final LinearLayout root, box, progress;
    final ProgressBar bar;
    final TextView what;

    DlPage(Main m) {
        this.m = m;
        box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, K.dp(4), 0, K.dp(30));
        root = m.scrollPage(m.bar(m.barTitle("Downloads and language")), box);
        progress = new LinearLayout(m); progress.setOrientation(LinearLayout.VERTICAL); progress.setPadding(K.dp(16), K.dp(8), K.dp(16), K.dp(8));
        progress.setBackgroundColor(K.dark() ? 0xff2a2030 : 0xfff6efe0);
        bar = new ProgressBar(m, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000);
        what = K.text(m, "", 13, K.fg(), false);
        android.widget.Button stop = K.button(m, "Stop", false);
        stop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Dl.stop = true; } });
        progress.addView(what); progress.addView(bar); progress.addView(stop, new LinearLayout.LayoutParams(-2, -2));
        root.addView(progress, 1);
        Dl.listeners.add(new Dl.Listener() { public void changed() { tick(); } });
    }

    public View view() { return root; }
    public boolean back() { return false; }

    void tick() {
        progress.setVisibility(Dl.busy ? View.VISIBLE : View.GONE);
        if (Dl.busy) { bar.setProgress((int) (1000L * Dl.done.get() / Math.max(1, Dl.total.get()))); what.setText(Dl.what.isEmpty() ? "Getting ready…" : Dl.what); }
        else if (root.isAttachedToWindow()) {
            show();
            if (!Dl.stop) K.toast(Dl.errors.get() == 0 ? "Everything is kept on the phone" : "Downloaded, with " + Dl.errors.get() + " problems: try again later");
        }
    }

    TextView heading(String s) { TextView t = K.text(m, s, 14, K.brand(), true); t.setPadding(K.dp(16), K.dp(16), K.dp(16), K.dp(6)); box.addView(t); return t; }

    static long size(File f) { if (f.isFile()) return f.length(); long t = 0; File[] k = f.listFiles(); if (k != null) for (File x : k) t += size(x); return t; }

    static String mb(long n) { return n > 1000000 ? (n / 1000000) + " MB" : (n / 1000) + " KB"; }

    public void show() {
        tick();
        box.removeAllViews();
        heading("LANGUAGE");
        String cur = K.lang(); String curName = cur;
        for (int i = 0; i < K.LANGS.length; i++) if (K.LANGS[i].equals(cur)) curName = K.LANG_NAMES[i];
        box.addView(K.row(m, curName, "The language of Today with God and the Bible Dictionary. Your Bibles can be in any language.", new View.OnClickListener() { public void onClick(View v) { chooseLang(); } }));
        heading("BIBLES ON THIS PHONE");
        final List<String> have = Db.get().bibles();
        for (final String tr : have) {
            LinearLayout r = K.row(m, K.bibleName(tr), "Touch to fetch again · touch and hold to remove", new View.OnClickListener() { public void onClick(View v) { Dl.start(one(tr), null, false, false); } });
            r.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { remove(tr); return true; } });
            box.addView(r); box.addView(K.divider(m));
        }
        heading("MORE BIBLES");
        JSONObject vers = K.info().optJSONObject("versions");
        if (vers == null) { box.addView(K.text(m, "   The list of Bibles comes with the internet.", 14, K.dim(), false)); }
        else {
            Map<String, List<String>> byLang = new TreeMap<>();
            Iterator<String> it = vers.keys();
            while (it.hasNext()) { String tr = it.next(); if (have.contains(tr)) continue; JSONArray v = vers.optJSONArray(tr); String l = v != null ? v.optString(0) : "?"; if (!byLang.containsKey(l)) byLang.put(l, new ArrayList<String>()); byLang.get(l).add(tr); }
            for (final Map.Entry<String, List<String>> e : byLang.entrySet()) {
                box.addView(K.row(m, e.getKey(), e.getValue().size() + " Bibles", new View.OnClickListener() { public void onClick(View v) { pickBible(e.getValue()); } }));
                box.addView(K.divider(m));
            }
        }
        heading("ALSO KEPT HERE");
        item("Bible Dictionary", "dictionary", new Runnable() { public void run() { Dl.start(new ArrayList<String>(), langs(), false, false); } });
        item("Bible Maps (for the dictionary's places)", "maps", new Runnable() { public void run() { Dl.start(new ArrayList<String>(), langs(), true, false); } });
        item("The Holy Fathers: writings and commentary", "library", new Runnable() { public void run() { Dl.start(new ArrayList<String>(), null, false, true); } });
        File audio = Db.get().file("audio");
        if (audio.exists()) box.addView(K.row(m, "Recordings kept here", mb(size(audio)) + " · touch to remove them", new View.OnClickListener() { public void onClick(View v) { delete(Db.get().file("audio")); show(); } }));
        android.widget.Button all = K.button(m, "Bring everything up to date", true);
        all.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Dl.start(have.isEmpty() ? one(K.defaultBible(K.lang())) : have, langs(), true, true); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(K.dp(16), K.dp(20), K.dp(16), 0); box.addView(all, lp);
    }

    static List<String> one(String s) { List<String> l = new ArrayList<>(); l.add(s); return l; }
    static List<String> langs() { return one(K.lang()); }

    void item(String title, String folder, final Runnable fetch) {
        File f = Db.get().file(folder); boolean here = f.exists() && f.list() != null && f.list().length > 0;
        box.addView(K.row(m, title, (here ? mb(size(f)) + " · touch to fetch again" : "Not yet · touch to keep it"), new View.OnClickListener() { public void onClick(View v) { fetch.run(); } }));
        box.addView(K.divider(m));
    }

    static void delete(File f) { File[] k = f.listFiles(); if (k != null) for (File x : k) delete(x); f.delete(); }

    void chooseLang() {
        new AlertDialog.Builder(m).setTitle("Language").setItems(K.LANG_NAMES, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int i) {
            String l = K.LANGS[i]; K.put("lang", l);
            List<String> b = new ArrayList<>(); String def = K.defaultBible(l);
            if (!Db.get().bibles().contains(def)) b.add(def);
            if (Net.online()) Dl.start(b, one(l), false, false); else K.toast("The new language's files come with the internet");
            m.pages.remove("dict"); m.pages.remove("today");
            show();
        } }).show();
    }

    void pickBible(final List<String> trs) {
        String[] names = new String[trs.size()]; for (int i = 0; i < trs.size(); i++) names[i] = K.bibleName(trs.get(i));
        new AlertDialog.Builder(m).setTitle("Keep on this phone").setItems(names, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int i) { Dl.start(one(trs.get(i)), null, false, false); } }).show();
    }

    void remove(final String tr) {
        if (Db.get().bibles().size() <= 1) { K.toast("Keep at least one Bible"); return; }
        new AlertDialog.Builder(m).setTitle("Remove " + K.shortBible(tr) + "?").setMessage("It can be downloaded again at any time.")
            .setPositiveButton("Remove", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Db.get().deleteBible(tr); show(); } })
            .setNegativeButton("Cancel", null).show();
    }
}
