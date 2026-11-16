package today.knowingjesus.biblecorner;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Talking to the website. The sign-in is kept as the website's cookies, on this phone only. */
final class Net {
    static String site() { return K.get("site", K.SITE); }

    private static synchronized JSONObject jar() {
        try { return new JSONObject(K.get("cookies", "{}")); } catch (Throwable t) { return new JSONObject(); }
    }

    private static synchronized void keep(Map<String, List<String>> headers) {
        List<String> set = headers.get("Set-Cookie");
        if (set == null) set = headers.get("set-cookie");
        if (set == null) return;
        JSONObject j = jar();
        try {
            for (String c : set) {
                String first = c.split(";", 2)[0]; int eq = first.indexOf('=');
                if (eq <= 0) continue;
                String name = first.substring(0, eq).trim(), val = first.substring(eq + 1).trim();
                boolean gone = val.isEmpty() || val.equals("deleted") || c.toLowerCase().contains("max-age=0") || c.toLowerCase().contains("expires=thu, 01 jan 1970") || c.toLowerCase().contains("expires=thu, 01-jan-1970");
                if (gone) j.remove(name); else j.put(name, val);
            }
        } catch (Throwable t) {}
        K.put("cookies", j.toString());
    }

    static synchronized void signedOut() { K.put("cookies", "{}"); }

    private static String cookieHeader() {
        JSONObject j = jar(); StringBuilder b = new StringBuilder();
        Iterator<String> it = j.keys();
        while (it.hasNext()) { String k = it.next(); if (b.length() > 0) b.append("; "); b.append(k).append('=').append(j.optString(k)); }
        return b.toString();
    }

    static boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) K.app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Throwable t) { return true; }
    }

    static byte[] get(String url, String post, int timeoutMs, boolean strict) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(20000); h.setReadTimeout(timeoutMs);
        h.setRequestProperty("User-Agent", "BibleCornerApp/" + Main.VER + " (Android)");
        h.setRequestProperty("Accept-Encoding", "identity");
        boolean ours = url.startsWith(site());
        if (ours) { String c = cookieHeader(); if (c.length() > 0) h.setRequestProperty("Cookie", c); }
        if (post != null) {
            h.setDoOutput(true); h.setRequestMethod("POST");
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            OutputStream o = h.getOutputStream(); o.write(post.getBytes("UTF-8")); o.close();
        }
        int code = h.getResponseCode();
        if (ours) keep(h.getHeaderFields());
        if (code >= 400 && (strict || code >= 500)) throw new Exception("HTTP " + code);
        InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        if (in != null) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close(); }
        return b.toByteArray();
    }

    static String form(Map<String, String> m) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return b.toString();
    }

    /** admin-ajax.php?action=…  (query: in the address; post: sent as a form) */
    static JSONObject ajax(String action, Map<String, String> query, Map<String, String> post) throws Exception {
        String url = site() + "/wp-admin/admin-ajax.php?action=" + action + (query != null && !query.isEmpty() ? "&" + form(query) : "");
        byte[] r = get(url, post == null ? null : form(post), 60000, false);
        String s = new String(r, "UTF-8").trim();
        if (s.equals("0") || s.equals("-1") || s.isEmpty()) return new JSONObject().put("success", false);
        return new JSONObject(s);
    }

    /** a file of the website's data folder (Bibles' helpers, dictionary, maps, the Holy Fathers) */
    static byte[] file(String rel) throws Exception {
        String base = K.info().optString("data", site() + "/wp-content/plugins/kjt-local-bible/data/");
        return get(base + rel, null, 120000, true);
    }

    static JSONObject info(boolean refresh) {
        JSONObject cur = K.info();
        if (!refresh && cur.length() > 0) return cur;
        try {
            JSONObject r = ajax("kjtb_desk", map("op", "info"), null);
            if (r.optBoolean("success")) { Db.get().setKv("info", r.getJSONObject("data").toString()); return K.info(); }
        } catch (Throwable t) {}
        return cur;
    }

    static Map<String, String> map(String... kv) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
