package today.knowingjesus.biblecorner;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import org.json.JSONArray;
import java.util.Map;
import org.json.JSONObject;

/* The app's own files, inside the app (assets/www). A file is taken from here when the website asks for
   the same version (its ?ver=); for any other version the website's file is used. */
class Packed {
    final Context ctx;
    final Map<String, String> base = new HashMap<String, String>();   /* "/wp-content/plugins/kjt-local-bible/" -> "4.43.0" */
    final Map<String, String> exact = new HashMap<String, String>();  /* a file asked for with its own ?ver= (or none: "") */

    Packed(Context c) {
        ctx = c;
        try {
            InputStream in = c.getAssets().open("www/versions.json");
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close();
            JSONObject j = new JSONObject(new String(b.toByteArray(), "UTF-8"));
            JSONObject a = j.getJSONObject("base"), e = j.getJSONObject("exact");
            java.util.Iterator<String> it = a.keys(); while (it.hasNext()) { String k = it.next(); base.put(k, a.getString(k)); }
            it = e.keys(); while (it.hasNext()) { String k = it.next(); exact.put(k, e.getString(k)); }
        } catch (Throwable t) { /* nothing packed: everything comes from the website */ }
    }

    WebResourceResponse serve(WebResourceRequest r) {
        try {
            if (!"GET".equalsIgnoreCase(r.getMethod())) return null;
            Uri u = r.getUrl();
            if (!"https".equals(u.getScheme()) || !LauncherActivity.ours(u.getHost())) return null;
            String path = u.getPath();
            if (path != null && path.endsWith("/admin-ajax.php") && "kjtb_data".equals(u.getQueryParameter("action"))) return bible(u);
            if (path != null && path.startsWith("/__app/") && path.indexOf("..") < 0) {
                try { return new WebResourceResponse(mime(path), "utf-8", 200, "OK", new HashMap<String, String>(), ctx.getAssets().open("www" + path)); } catch (Throwable t) { return null; }
            }
            if (path == null || path.indexOf("..") >= 0 || !path.startsWith("/wp-content/")) return null;
            String ver = u.getQueryParameter("ver"); if (ver == null) ver = u.getQueryParameter("v");
            boolean ok = false;
            if (exact.containsKey(path)) ok = exact.get(path).equals(ver == null ? "" : ver);
            else if (ver != null) for (Map.Entry<String, String> e : base.entrySet()) if (path.startsWith(e.getKey()) && e.getValue().equals(ver)) { ok = true; break; }
            if (!ok) return null;
            InputStream in;
            try { in = ctx.getAssets().open("www" + path); } catch (Throwable t) { return null; }
            String mime = mime(path);
            Map<String, String> h = new HashMap<String, String>();
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Cache-Control", "public, max-age=31536000");
            h.put("X-KJT-Packed", "1");
            return new WebResourceResponse(mime, mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json") ? "utf-8" : null, 200, "OK", h, in);
        } catch (Throwable t) { return null; }
    }

    /* 1.9: two Bibles are inside the app (the King James Version and the Arabic Van Dyke): with no internet,
       and before a Bible is saved on the phone, they are read from here */
    static final String[] BUILT_IN = {"eng-kjv", "arb-vd"};
    String lastKey = null; JSONArray lastRows = null;

    boolean online() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) return false;
            android.net.NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) { return true; }
    }

    String read(String asset) throws java.io.IOException {
        InputStream in = ctx.getAssets().open(asset);
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close();
        return new String(b.toByteArray(), "UTF-8");
    }

    WebResourceResponse json(String body) throws java.io.IOException {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*"); h.put("Cache-Control", "no-store"); h.put("X-KJT-Built-In", "1");
        return new WebResourceResponse("application/json", "utf-8", 200, "OK", h, new java.io.ByteArrayInputStream(body.getBytes("UTF-8")));
    }

    WebResourceResponse bible(Uri u) {
        try {
            String tr = u.getQueryParameter("tr"), op = u.getQueryParameter("op"), book = u.getQueryParameter("book");
            boolean have = false; for (String b : BUILT_IN) if (b.equals(tr)) have = true;
            if (!have || op == null || online()) return null;
            String dir = "www/__app/bibles/" + tr + "/";
            if ("books".equals(op)) return json(read(dir + "books.json"));
            if (book == null || !book.matches("[A-Z0-9]{2,4}")) return null;
            if ("offline_book".equals(op)) return json(read(dir + book + ".json"));
            if (!"chapters".equals(op) && !"chapter".equals(op)) return null;
            JSONArray rows;
            if ((tr + book).equals(lastKey)) rows = lastRows;
            else { rows = new JSONObject(read(dir + book + ".json")).getJSONArray("data"); lastKey = tr + book; lastRows = rows; }
            JSONArray out = new JSONArray();
            if ("chapters".equals(op)) {
                LinkedHashSet<Integer> seen = new LinkedHashSet<Integer>();
                for (int i = 0; i < rows.length(); i++) seen.add(Integer.parseInt(rows.getJSONObject(i).getString("chapter")));
                for (Integer c : seen) out.put(c.intValue());
            } else {
                String c = u.getQueryParameter("chapter"); if (c == null) c = "1";
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject v = rows.getJSONObject(i);
                    if (!c.equals(v.getString("chapter"))) continue;
                    JSONObject o = new JSONObject(); o.put("verse", v.getString("verse")); o.put("text", v.getString("text")); o.put("book", v.optString("book")); out.put(o);
                }
            }
            JSONObject res = new JSONObject(); res.put("success", true); res.put("data", out);
            return json(res.toString());
        } catch (Throwable t) { return null; }
    }

    static String mime(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".txt")) return "text/plain";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }
}
