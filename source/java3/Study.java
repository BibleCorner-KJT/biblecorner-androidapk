package today.knowingjesus.biblecorner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Your study: bookmarks (with colours and notes) and Study Pads, kept here and in step with My KJT. */
final class Study {
    interface Listener { void changed(); }
    static final List<Listener> listeners = new ArrayList<>();
    static volatile boolean syncing;

    static void changed() { K.ui.post(new Runnable() { public void run() { for (Listener l : new ArrayList<>(listeners)) l.changed(); } }); }

    static JSONObject user() { return K.info().optJSONObject("user"); }

    static String rid(String tr, String book, int ch, int v) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((tr + "|" + book + "|" + ch + "|" + v).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder(); for (byte x : h) b.append(String.format("%02x", x)); return b.toString();
        } catch (Throwable t) { return tr + book + ch + v; }
    }

    static long now() { return System.currentTimeMillis() / 1000; }

    /** verse -> its record, for one chapter */
    static Map<Integer, JSONObject> inChapter(String tr, String book, int ch) {
        Map<Integer, JSONObject> out = new HashMap<>();
        for (JSONObject r : Db.get().records()) {
            if (!tr.equals(r.optString("translation")) || !book.equals(r.optString("book"))) continue;
            int c1 = r.optInt("chapter"), v1 = Math.max(1, r.optInt("verse", 1));
            int c2 = r.optInt("chapter_end", 0) > 0 ? r.optInt("chapter_end") : c1;
            int v2 = r.optInt("verse_end", 0) > 0 ? r.optInt("verse_end") : v1;
            if (ch < c1 || ch > c2) continue;
            for (int v = (ch > c1 ? 1 : v1); v <= (ch == c2 ? v2 : 400); v++) out.put(v, r);
        }
        return out;
    }

    static JSONObject save(String tr, String book, int ch, int v, int vEnd, String color) {
        try {
            String id = rid(tr, book, ch, v);
            JSONObject old = null;
            for (JSONObject r : Db.get().records()) if (id.equals(r.optString("id"))) old = r;
            JSONObject r = old != null ? new JSONObject(old.toString()) : new JSONObject();
            r.put("id", id).put("translation", tr).put("book", book).put("chapter", ch).put("verse", v)
             .put("note", old != null ? old.optString("note") : "").put("created", old != null ? old.optLong("created", now()) : now()).put("updated", now());
            if (vEnd > v) r.put("verse_end", vEnd);
            if (color != null) r.put("color", color);
            Db.get().putRecords(new JSONArray().put(r), false);
            JSONObject post = new JSONObject().put("op", "save").put("translation", tr).put("book", book).put("chapter", ch).put("verse", v).put("note", r.optString("note"));
            if (r.has("verse_end")) post.put("verse_end", r.getInt("verse_end"));
            if (r.has("color")) post.put("color", r.getString("color"));
            send(post, false);
            changed();
            return r;
        } catch (Throwable t) { return new JSONObject(); }
    }

    static void setNote(JSONObject r, String note) {
        try {
            r = new JSONObject(r.toString()).put("note", note).put("updated", now());
            Db.get().putRecords(new JSONArray().put(r), false);
            send(new JSONObject().put("op", "update_note").put("id", r.getString("id")).put("note", note), false);
            changed();
        } catch (Throwable t) {}
    }

    static void setTags(JSONObject r, List<String> tags) {
        try {
            TreeSet<String> s = new TreeSet<>(); for (String t : tags) if (t != null && t.length() > 0) s.add(t);
            JSONArray a = new JSONArray(); for (String t : s) a.put(t);
            r = new JSONObject(r.toString()).put("tags", a).put("updated", now());
            Db.get().putRecords(new JSONArray().put(r), false);
            StringBuilder j = new StringBuilder(); for (String t : s) { if (j.length() > 0) j.append(' '); j.append(t); }
            send(new JSONObject().put("op", "update_tags").put("id", r.getString("id")).put("tags", j.toString()), false);
            List<String> have = new ArrayList<>(); for (JSONObject p : Db.get().pads()) have.add(p.optString("tag"));
            JSONArray add = new JSONArray();
            for (String t : s) if (!have.contains(t)) add.put(new JSONObject().put("tag", t).put("color", "").put("updated", now()).put("items", new JSONArray()));
            if (add.length() > 0) Db.get().putPads(add, false);
            changed();
        } catch (Throwable t) {}
    }

    static void delete(JSONObject r) {
        try { Db.get().delRecord(r.getString("id")); send(new JSONObject().put("op", "delete").put("id", r.getString("id")), false); changed(); } catch (Throwable t) {}
    }

    static List<String> tags(JSONObject r) {
        List<String> out = new ArrayList<>(); JSONArray a = r.optJSONArray("tags");
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    // the website: now, or as soon as it can be reached
    static void send(JSONObject post, boolean pads) {
        if (user() == null) return;
        try { Db.get().queue(new JSONObject().put("pads", pads).put("post", post)); } catch (Throwable t) {}
        K.bg.execute(new Runnable() { public void run() { flush(); } });
    }

    static synchronized boolean flush() {
        JSONObject i = K.info();
        if (i.optString("nonce_study").isEmpty() || !Net.online()) return false;
        for (Object[] job : Db.get().outbox()) {
            JSONObject j = (JSONObject) job[1];
            try {
                JSONObject p = j.getJSONObject("post");
                Map<String, String> post = new HashMap<>();
                java.util.Iterator<String> it = p.keys();
                while (it.hasNext()) { String k = it.next(); post.put(k, p.get(k).toString()); }
                post.put("nonce", i.getString("nonce_study"));
                boolean pads = j.optBoolean("pads");
                if (!pads && i.optJSONObject("user") != null) post.put("clientUser", "" + i.getJSONObject("user").optInt("id"));
                JSONObject r = Net.ajax(pads ? "kjtb_studypads" : "kjtb_study", null, post);
                Db.get().done((Long) job[0]);
                JSONObject d = r.optJSONObject("data");
                if (!pads && r.optBoolean("success") && d != null && d.optJSONArray("records") != null) Db.get().putRecords(d.getJSONArray("records"), true);
            } catch (Throwable t) { return false; }
        }
        return true;
    }

    interface Done { void done(String result); }

    /** send what waits, then take the website's copy (shared by all your devices) */
    static void sync(final Done done) {
        if (syncing) return;
        syncing = true;
        K.bg(new K.Work<String>() { public String run() throws Exception {
            JSONObject i = Net.info(true);
            if (i.optJSONObject("user") == null) return "guest";
            flush();
            JSONObject r = Net.ajax("kjtb_study", null, Net.map("op", "list", "nonce", i.getString("nonce_study"), "clientUser", "" + i.getJSONObject("user").optInt("id")));
            if (r.optBoolean("success")) Db.get().putRecords(r.getJSONObject("data").optJSONArray("records") != null ? r.getJSONObject("data").getJSONArray("records") : new JSONArray(), true);
            JSONObject p = Net.ajax("kjtb_studypads", null, Net.map("op", "get", "nonce", i.getString("nonce_study")));
            if (p.optBoolean("success") && p.optJSONObject("data") != null) Db.get().putPads(p.getJSONObject("data").optJSONArray("pads") != null ? p.getJSONObject("data").getJSONArray("pads") : new JSONArray(), true);
            Db.get().setKv("synced", "" + now());
            return "ok";
        } }, new K.Done<String>() { public void done(String r, Throwable e) {
            syncing = false; changed();
            if (done != null) done.done(e != null ? "net" : r);
        } });
    }

    static void signIn(final String login, final String password, final Done done) {
        K.bg(new K.Work<String>() { public String run() throws Exception {
            JSONObject i = Net.info(true);
            JSONObject r = Net.ajax("kjtb_app_pw", null, Net.map("log", login, "pwd", password, "nonce", i.optString("nonce_pw")));
            if (!r.optBoolean("success")) {
                JSONObject d = r.optJSONObject("data"); String code = d != null ? d.optString("code") : "";
                return "wrong".equals(code) ? "wrong" : "limit".equals(code) ? "limit" : "error";
            }
            // what was kept here as a guest goes up to the account
            List<JSONObject> mine = Db.get().records();
            Net.info(true);
            for (JSONObject x : mine) {
                JSONObject post = new JSONObject().put("op", "save").put("translation", x.optString("translation")).put("book", x.optString("book"))
                        .put("chapter", x.optInt("chapter")).put("verse", x.optInt("verse")).put("note", x.optString("note"));
                if (x.has("verse_end")) post.put("verse_end", x.optInt("verse_end"));
                if (x.has("color")) post.put("color", x.optString("color"));
                Db.get().queue(new JSONObject().put("pads", false).put("post", post));
                if (x.optJSONArray("tags") != null && x.getJSONArray("tags").length() > 0) {
                    StringBuilder j = new StringBuilder(); JSONArray a = x.getJSONArray("tags");
                    for (int k = 0; k < a.length(); k++) { if (j.length() > 0) j.append(' '); j.append(a.getString(k)); }
                    Db.get().queue(new JSONObject().put("pads", false).put("post", new JSONObject().put("op", "update_tags").put("id", x.getString("id")).put("tags", j.toString())));
                }
            }
            return "ok";
        } }, new K.Done<String>() { public void done(String r, Throwable e) {
            if (e != null) { done.done("net"); return; }
            if ("ok".equals(r)) sync(new Done() { public void done(String x) { done.done("ok"); } });
            else done.done(r);
        } });
    }

    static void signOut(final Runnable done) {
        K.bg(new K.Work<Boolean>() { public Boolean run() throws Exception {
            JSONObject i = K.info();
            try { if (!i.optString("nonce_out").isEmpty()) Net.ajax("kjtb_app_out", null, Net.map("nonce", i.getString("nonce_out"))); } catch (Throwable t) {}
            Net.signedOut();
            Db.get().putRecords(new JSONArray(), true); Db.get().putPads(new JSONArray(), true);
            for (Object[] j : Db.get().outbox()) Db.get().done((Long) j[0]);
            Net.info(true);
            return true;
        } }, new K.Done<Boolean>() { public void done(Boolean r, Throwable e) { changed(); done.run(); } });
    }
}
