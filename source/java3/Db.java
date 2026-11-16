package today.knowingjesus.biblecorner;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Everything kept on the phone: the Bibles (in a database), small settings, your study, and the files (dictionary, maps, the Holy Fathers). */
final class Db extends SQLiteOpenHelper {
    private static Db one;
    static synchronized Db get() { if (one == null) one = new Db(); return one; }

    private final Map<String, Object> memo = new HashMap<>();
    private final Map<String, List<String[]>> bookMemo = new HashMap<>();
    final File files;

    private Db() {
        super(K.app, "kjt3.db", null, 1);
        files = new File(K.app.getFilesDir(), "kjt");
        files.mkdirs();
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE verse (tr TEXT, book TEXT, ch INTEGER, v INTEGER, text TEXT, PRIMARY KEY (tr, book, ch, v))");
        d.execSQL("CREATE TABLE book (tr TEXT, code TEXT, name TEXT, ord INTEGER, PRIMARY KEY (tr, code))");
        d.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT)");
        d.execSQL("CREATE TABLE record (id TEXT PRIMARY KEY, j TEXT)");
        d.execSQL("CREATE TABLE pad (tag TEXT PRIMARY KEY, j TEXT)");
        d.execSQL("CREATE TABLE outbox (n INTEGER PRIMARY KEY AUTOINCREMENT, j TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase d, int a, int b) {}

    static String canon(String code) {
        String c = code == null ? "" : code.toUpperCase().replace(" ", "").replace(".", "");
        switch (c) {
            case "MAR": return "MRK"; case "JOH": case "JN": return "JHN"; case "JAM": return "JAS";
            case "1JO": return "1JN"; case "2JO": return "2JN"; case "3JO": return "3JN"; case "PHI": return "PHP";
            case "EZE": return "EZK"; case "JOE": return "JOL"; case "SOL": return "SNG"; case "NAH": return "NAM";
            case "ZCH": return "ZEC"; case "PS": case "PSM": return "PSA"; case "JDE": return "JUD";
        }
        return c;
    }

    // ---------- small settings ----------
    synchronized String kv(String k) {
        if (memo.containsKey(k)) return (String) memo.get(k);
        Cursor c = getReadableDatabase().rawQuery("SELECT v FROM kv WHERE k=?", new String[]{k});
        String v = c.moveToFirst() ? c.getString(0) : null; c.close();
        memo.put(k, v);
        return v;
    }

    JSONObject kvJson(String k) {
        String s = kv(k);
        if (memo.containsKey("#" + k)) return (JSONObject) memo.get("#" + k);
        JSONObject o; try { o = s == null ? new JSONObject() : new JSONObject(s); } catch (Throwable t) { o = new JSONObject(); }
        synchronized (this) { memo.put("#" + k, o); }
        return o;
    }

    synchronized void setKv(String k, String v) {
        ContentValues cv = new ContentValues(); cv.put("k", k); cv.put("v", v);
        getWritableDatabase().insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        memo.put(k, v); memo.remove("#" + k);
    }

    // ---------- the Bibles ----------
    void putBook(String tr, String code, String name, int ord, JSONArray rows) throws Exception {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            d.delete("verse", "tr=? AND book=?", new String[]{tr, code});
            android.database.sqlite.SQLiteStatement st = d.compileStatement("INSERT OR REPLACE INTO verse VALUES (?,?,?,?,?)");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                int ch = r.optInt("chapter", -1), v = r.optInt("verse", -1);
                if (ch < 0 || v < 0) continue;
                st.bindString(1, tr); st.bindString(2, code); st.bindLong(3, ch); st.bindLong(4, v); st.bindString(5, r.optString("text"));
                st.executeInsert();
            }
            ContentValues cv = new ContentValues(); cv.put("tr", tr); cv.put("code", code); cv.put("name", name); cv.put("ord", ord);
            d.insertWithOnConflict("book", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
        synchronized (this) { bookMemo.remove(tr); }
    }

    List<String> bibles() {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT tr FROM book", null);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    void deleteBible(String tr) {
        getWritableDatabase().delete("verse", "tr=?", new String[]{tr});
        getWritableDatabase().delete("book", "tr=?", new String[]{tr});
        synchronized (this) { bookMemo.remove(tr); }
    }

    /** [code, name] in the Bible's order */
    synchronized List<String[]> books(String tr) {
        List<String[]> m = bookMemo.get(tr);
        if (m != null) return m;
        m = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT code, name FROM book WHERE tr=? ORDER BY ord", new String[]{tr});
        while (c.moveToNext()) m.add(new String[]{c.getString(0), c.getString(1)});
        c.close();
        bookMemo.put(tr, m);
        return m;
    }

    String bookName(String tr, String code) { for (String[] b : books(tr)) if (b[0].equals(code)) return b[1]; return null; }

    String findBook(String tr, String code) {
        if (tr == null) return null;
        String c = canon(code);
        for (String[] b : books(tr)) if (canon(b[0]).equals(c)) return b[0];
        return null;
    }

    List<Integer> chapters(String tr, String book) {
        List<Integer> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT ch FROM verse WHERE tr=? AND book=? ORDER BY ch", new String[]{tr, book});
        while (c.moveToNext()) out.add(c.getInt(0));
        c.close();
        return out;
    }

    /** [verse, text] */
    List<Object[]> chapter(String tr, String book, int ch) {
        List<Object[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT v, text FROM verse WHERE tr=? AND book=? AND ch=? ORDER BY v", new String[]{tr, book, "" + ch});
        while (c.moveToNext()) out.add(new Object[]{c.getInt(0), c.getString(1)});
        c.close();
        return out;
    }

    /** [chapter, verse, text] */
    List<Object[]> passage(String tr, String book, int c1, int v1, int c2, int v2) {
        List<Object[]> out = new ArrayList<>();
        String b = findBook(tr, book);
        if (b == null) return out;
        Cursor c = getReadableDatabase().rawQuery("SELECT ch, v, text FROM verse WHERE tr=? AND book=? AND ch BETWEEN ? AND ? ORDER BY ch, v", new String[]{tr, b, "" + c1, "" + c2});
        while (c.moveToNext()) {
            int ch = c.getInt(0), v = c.getInt(1);
            if ((ch > c1 || v >= v1) && (ch < c2 || v <= v2)) out.add(new Object[]{ch, v, c.getString(2)});
        }
        c.close();
        return out;
    }

    /** [book, chapter, verse, text] */
    List<Object[]> search(String tr, String q, int limit) {
        List<Object[]> out = new ArrayList<>();
        String[] words = q.trim().split("\\s+");
        StringBuilder w = new StringBuilder("tr=?");
        List<String> args = new ArrayList<>(); args.add(tr);
        for (String x : words) { if (x.isEmpty()) continue; w.append(" AND text LIKE ?"); args.add("%" + x + "%"); }
        Cursor c = getReadableDatabase().rawQuery("SELECT book, ch, v, text FROM verse WHERE " + w + " LIMIT " + limit, args.toArray(new String[0]));
        while (c.moveToNext()) out.add(new Object[]{c.getString(0), c.getInt(1), c.getInt(2), c.getString(3)});
        c.close();
        // in the Bible's order of books
        final List<String[]> bk = books(tr);
        java.util.Collections.sort(out, new java.util.Comparator<Object[]>() { public int compare(Object[] a, Object[] b) {
            int ia = 0, ib = 0;
            for (int i = 0; i < bk.size(); i++) { if (bk.get(i)[0].equals(a[0])) ia = i; if (bk.get(i)[0].equals(b[0])) ib = i; }
            if (ia != ib) return ia - ib;
            if (!a[1].equals(b[1])) return (Integer) a[1] - (Integer) b[1];
            return (Integer) a[2] - (Integer) b[2];
        } });
        return out;
    }

    // ---------- your study ----------
    List<JSONObject> records() {
        List<JSONObject> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT j FROM record", null);
        while (c.moveToNext()) try { out.add(new JSONObject(c.getString(0))); } catch (Throwable t) {}
        c.close();
        return out;
    }

    void putRecords(JSONArray rs, boolean replace) throws Exception {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            if (replace) d.delete("record", null, null);
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.getJSONObject(i);
                ContentValues cv = new ContentValues(); cv.put("id", r.getString("id")); cv.put("j", r.toString());
                d.insertWithOnConflict("record", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    void delRecord(String id) { getWritableDatabase().delete("record", "id=?", new String[]{id}); }

    List<JSONObject> pads() {
        List<JSONObject> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT j FROM pad ORDER BY tag", null);
        while (c.moveToNext()) try { out.add(new JSONObject(c.getString(0))); } catch (Throwable t) {}
        c.close();
        return out;
    }

    void putPads(JSONArray ps, boolean replace) throws Exception {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            if (replace) d.delete("pad", null, null);
            for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.getJSONObject(i);
                ContentValues cv = new ContentValues(); cv.put("tag", p.optString("tag")); cv.put("j", p.toString());
                d.insertWithOnConflict("pad", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    void queue(JSONObject job) { ContentValues cv = new ContentValues(); cv.put("j", job.toString()); getWritableDatabase().insert("outbox", null, cv); }

    /** [n, job] */
    List<Object[]> outbox() {
        List<Object[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT n, j FROM outbox ORDER BY n", null);
        while (c.moveToNext()) try { out.add(new Object[]{c.getLong(0), new JSONObject(c.getString(1))}); } catch (Throwable t) {}
        c.close();
        return out;
    }

    void done(long n) { getWritableDatabase().delete("outbox", "n=?", new String[]{"" + n}); }

    // ---------- files ----------
    File file(String rel) { return new File(files, rel); }

    void writeFile(String rel, byte[] data) throws Exception {
        File f = file(rel); f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".part");
        FileOutputStream o = new FileOutputStream(tmp); o.write(data); o.close();
        if (!tmp.renameTo(f)) throw new Exception("could not keep " + rel);
        synchronized (this) { memo.remove("file:" + rel); }
    }

    /** a kept file's text (gzip is opened); null when it is not here */
    String readText(String rel) {
        File f = file(rel);
        if (!f.exists()) return null;
        try {
            InputStream in = new FileInputStream(f);
            if (rel.endsWith(".gz")) in = new GZIPInputStream(in);
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toString("UTF-8");
        } catch (Throwable t) { return null; }
    }

    /** a kept JSON file, read once and remembered */
    Object readJson(String rel) {
        synchronized (this) { if (memo.containsKey("file:" + rel)) return memo.get("file:" + rel); }
        String s = readText(rel);
        Object o = null;
        try { if (s != null) o = s.trim().startsWith("[") ? new JSONArray(s) : new JSONObject(s); } catch (Throwable t) {}
        synchronized (this) { if (o != null && !rel.startsWith("library/")) memo.put("file:" + rel, o); }
        return o;
    }
}
