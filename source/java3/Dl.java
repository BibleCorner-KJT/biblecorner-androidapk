package today.knowingjesus.biblecorner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Fetching what the app keeps on the phone, four files at a time, with its progress. */
final class Dl {
    interface Listener { void changed(); }
    static final List<Listener> listeners = new ArrayList<>();
    static volatile boolean busy, stop;
    static final AtomicInteger done = new AtomicInteger(), total = new AtomicInteger(), errors = new AtomicInteger();
    static volatile String what = "";
    static long lastTell;

    static void tell(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastTell < 150) return;
        lastTell = now;
        K.ui.post(new Runnable() { public void run() { for (Listener l : new ArrayList<>(listeners)) l.changed(); } });
    }

    static void step(String w) { done.incrementAndGet(); what = w; tell(false); }

    /** bibles: codes; langs: the dictionary, maps and Today in these languages; fathers: their writings and commentary */
    static void start(final List<String> bibles, final List<String> langs, final boolean maps, final boolean fathers) {
        if (busy) return;
        busy = true; stop = false; done.set(0); total.set(0); errors.set(0); what = "";
        tell(true);
        new Thread() { public void run() {
            try {
                JSONObject info = Net.info(true);
                for (String tr : bibles) { if (stop) break; bible(tr); }
                if (!stop && langs != null && !langs.isEmpty()) { dictionary(langs); today(langs); }
                if (!stop && maps) maps(langs);
                if (!stop && fathers) { library(); commentary(info.optJSONArray("commentary")); study(); }
            } catch (Throwable t) { errors.incrementAndGet(); }
            busy = false; what = "";
            tell(true);
        } }.start();
    }

    private static void pool(List<Runnable> jobs) throws InterruptedException {
        ExecutorService ex = Executors.newFixedThreadPool(4);
        for (Runnable r : jobs) ex.execute(r);
        ex.shutdown(); ex.awaitTermination(2, TimeUnit.HOURS);
    }

    private static void bible(final String tr) throws Exception {
        what = "The Holy Bible · " + tr; tell(true);
        JSONObject r = Net.ajax("kjtb_data", Net.map("op", "books", "tr", tr), null);
        JSONArray books = r.optBoolean("success") ? r.optJSONArray("data") : null;
        if (books == null) { errors.incrementAndGet(); return; }
        total.addAndGet(books.length());
        List<Runnable> jobs = new ArrayList<>();
        for (int i = 0; i < books.length(); i++) {
            final int n = i; final JSONObject b = books.getJSONObject(i);
            jobs.add(new Runnable() { public void run() {
                if (stop) return;
                String code = b.optString("book_code"), name = b.optString("book", code);
                try {
                    JSONObject rr = Net.ajax("kjtb_data", Net.map("op", "offline_book", "tr", tr, "book", code), null);
                    if (rr.optBoolean("success")) Db.get().putBook(tr, code, name, n, rr.getJSONArray("data")); else errors.incrementAndGet();
                } catch (Throwable t) { errors.incrementAndGet(); }
                step("The Holy Bible · " + name);
            } });
        }
        pool(jobs);
    }

    private static void files(final List<String> rels, final String label) throws InterruptedException {
        total.addAndGet(rels.size());
        List<Runnable> jobs = new ArrayList<>();
        for (final String rel : rels) jobs.add(new Runnable() { public void run() {
            if (stop) return;
            try { Db.get().writeFile(rel, Net.file(rel)); } catch (Throwable t) { errors.incrementAndGet(); }
            step(label);
        } });
        pool(jobs);
    }

    private static void dictionary(List<String> langs) throws Exception {
        List<String> f = new ArrayList<>();
        if (!langs.contains("en")) f.add("dictionary/en.json");
        for (String l : langs) f.add("dictionary/" + l + ".json");
        f.add("dictionary/people.json");
        files(f, "Bible Dictionary");
    }

    private static void maps(List<String> langs) throws Exception {
        List<String> f = new ArrayList<>();
        for (String x : new String[]{"base.json", "places.json", "stories.json", "words.json", "chapters.json", "ident.json"}) f.add("maps/" + x);
        if (langs != null) for (String l : langs) if (!l.equals("en")) f.add("maps/names-" + l + ".json");
        f.add("maps/relief/overview.jpg");
        for (int r = 0; r < 3; r++) for (int c = 0; c < 6; c++) f.add("maps/relief/t" + r + "-" + c + ".jpg");
        files(f, "Bible Maps");
    }

    private static void library() throws Exception {
        byte[] idx = Net.file("library/index.json");
        Db.get().writeFile("library/index.json", idx);
        JSONArray a = new JSONArray(new String(idx, "UTF-8"));
        List<String> f = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) f.add("library/" + a.getJSONObject(i).getString("id") + ".json.gz");
        files(f, "The Holy Fathers");
    }

    private static void commentary(JSONArray books) throws Exception {
        if (books == null) return;
        List<String> f = new ArrayList<>();
        for (int i = 0; i < books.length(); i++) f.add("fathers/" + books.getString(i) + ".json.gz");
        files(f, "The Holy Fathers on the Bible");
    }

    /** for the reader: cross-references, the Greek and Hebrew words (with their lexicon) and the words of Christ in red */
    static void study() throws Exception {
        String[] ot = "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL".split(" ");
        List<String> f = new ArrayList<>();
        JSONObject en = K.info().optJSONObject("books") != null ? K.info().optJSONObject("books").optJSONObject("en") : null;
        for (String b : ot) { f.add("xref/" + b + ".json"); f.add("words/" + b + ".json.gz"); }
        for (String b : K.NT) { f.add("xref/" + b + ".json"); f.add("words/" + b + ".json.gz"); if (en != null && en.has(b)) f.add("../assets/red-letters/" + en.optString(b) + ".json"); }
        f.add("words/lexicon.json.gz");
        total.addAndGet(f.size());
        List<Runnable> jobs = new ArrayList<>();
        for (final String rel : f) jobs.add(new Runnable() { public void run() {
            if (stop) return;
            try { Db.get().writeFile(rel.replace("../assets/", ""), Net.file(rel)); } catch (Throwable t) {}
            step("For the reader");
        } });
        pool(jobs);
    }

    static void today(List<String> langs) {
        for (String l : langs) {
            try {
                JSONObject r = Net.ajax("kjtb_desk", Net.map("op", "today", "lang", l, "days", "62"), null);
                if (r.optBoolean("success")) Db.get().setKv("today_" + l, r.getJSONObject("data").toString());
            } catch (Throwable t) {}
            step("Today with God");
        }
    }

    static boolean haveBible() { return !Db.get().bibles().isEmpty(); }
}
