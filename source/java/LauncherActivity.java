package today.knowingjesus.biblecorner;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/* Bible Corner 1.8: the app shows Bible Corner in its own built-in window (no browser). The app's own files
   (its code, look and words) are inside the app and used whenever they match the website's version;
   a newer version on the website is fetched from there, so website updates still reach the app. */
public class LauncherActivity extends Activity {
    static final String VER = "3.2";
    static final String HOME = "https://biblecorner.com/read/?kjt_app=1&kjt_appv=" + VER;
    static LauncherActivity live;
    static final int REQ_FILE = 11, REQ_LOC = 12, REQ_NOTE = 13;

    WebView web;
    FrameLayout root;
    Packed packed;
    View fullView;
    WebChromeClient.CustomViewCallback fullCb;
    ValueCallback<Uri[]> fileCb;
    GeolocationPermissions.Callback geoCb;
    String geoOrigin;
    boolean shown = false;
    /* only Bible Corner's own pages get the key to the app's helpers (not a video or other page shown inside them) */
    final String key = java.util.UUID.randomUUID().toString();
    final Handler ui = new Handler(Looper.getMainLooper());
    final Speech speech = new Speech(this); /* 2.1: the phone's voice for the pages */

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        live = this;
        Window w = getWindow();
        bars(w);
        packed = new Packed(this);
        root = new FrameLayout(this);
        web = new WebView(this);
        web.setVisibility(View.INVISIBLE);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setBackgroundColor(0xfffffdf6);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        /* 2.0: the text follows the phone's text size (Settings > Display), as other apps do */
        s.setTextZoom(zoom());
        s.setGeolocationEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " BibleCornerApp/" + VER);
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(), "KJTApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener(new Downloads());
        try {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest r) { return packed.serve(r); }
            });
        } catch (Throwable t) { /* older WebView: the pages still work */ }

        if (state != null && web.restoreState(state) != null) { /* back where we were */ }
        else web.loadUrl(startUrl(getIntent()));
        NoticeJob.schedule(this);
        Remind.schedule(this);
        /* the start picture stays at most a few seconds */
        ui.postDelayed(new Runnable() { public void run() { show(); } }, 6000);
    }

    void show() { if (!shown) { shown = true; web.setVisibility(View.VISIBLE); } }

    /* 2.0: the phone's dark mode and text size */
    boolean dark() { try { return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES; } catch (Throwable t) { return false; } }
    int zoom() { try { float f = getResources().getConfiguration().fontScale; return Math.max(85, Math.min(200, Math.round(f * 100))); } catch (Throwable t) { return 100; } }
    void bars(Window w) {
        boolean d = dark();
        w.setStatusBarColor(d ? 0xff1b1220 : 0xff3e093f);
        w.setNavigationBarColor(d ? 0xff120c16 : 0xff2a0a2c);
    }
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        bars(getWindow());
        try { web.getSettings().setTextZoom(zoom()); } catch (Throwable t) {}
        web.evaluateJavascript("window.__kjtDark&&window.__kjtDark(" + dark() + ")", null);
    }

    static boolean ours(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("biblecorner.com") || host.endsWith(".biblecorner.com") || host.equals("knowingjesus.today") || host.endsWith(".knowingjesus.today");
    }

    /* the address to open: the app's start, a home-screen shortcut, a link to Bible Corner, or a friend's game link */
    String startUrl(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null || !ours(d.getHost()) || !"https".equals(d.getScheme())) {
            /* 1.9.1: the app opens in the language chosen last time */
            /* 2.0: at the very first start, the phone's own language (when Bible Corner speaks it) */
            String l = getSharedPreferences("app", MODE_PRIVATE).getString("lang", null);
            if (l == null) { l = java.util.Locale.getDefault().getLanguage(); if (l == null || !l.matches("ar|el|es|fr|hi|ru|tl|zh|fil")) l = "en"; if ("fil".equals(l)) l = "tl"; }
            return l != null && l.matches("[a-z]{2}") && !"en".equals(l) ? HOME + "&lang=" + l : HOME;
        }
        String join = d.getQueryParameter("kg_join"), frag = d.getFragment();
        if (join != null && frag != null && frag.startsWith("play-"))
            return "https://biblecorner.com/read/?kjt_app=1&kjt_appv=" + VER + "&kg_join=" + Uri.encode(join) + "&kg_game=" + Uri.encode(frag.substring(5));
        if (d.getQueryParameter("kjt_app") == null)
            d = d.buildUpon().appendQueryParameter("kjt_app", "1").appendQueryParameter("kjt_appv", VER).build();
        return d.toString();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i != null && i.getData() != null) web.loadUrl(startUrl(i));
    }

    @Override
    public void onBackPressed() {
        if (fullView != null) { hideFull(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        moveTaskToBack(true); /* keeps a recording playing */
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }

    @Override
    protected void onDestroy() {
        if (live == this) live = null;
        speech.shutdown();
        try { stopService(new Intent(this, MediaService.class)); } catch (Throwable t) {}
        try { web.destroy(); } catch (Throwable t) {}
        super.onDestroy();
    }

    /* a link that leaves Bible Corner opens in the right app (mail, phone, Telegram, YouTube, the browser) */
    boolean outside(Uri u) {
        String sc = u.getScheme() == null ? "" : u.getScheme();
        if ("app-settings".equals(sc)) { openSettings(false); return true; }
        if (("https".equals(sc) || "http".equals(sc)) && ours(u.getHost())) return false;
        try {
            Intent it;
            if ("intent".equals(sc)) it = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
            else it = new Intent(Intent.ACTION_VIEW, u);
            it.addCategory(Intent.CATEGORY_BROWSABLE);
            it.setComponent(null);
            startActivity(it);
        } catch (Throwable t) {
            toast(getString(R.string.no_app));
        }
        return true;
    }

    /* the app's page in the phone's settings (network, location, notifications) */
    void openSettings(boolean notes) {
        try {
            Intent it;
            if (notes && Build.VERSION.SDK_INT >= 26) { it = new Intent("android.settings.APP_NOTIFICATION_SETTINGS"); it.putExtra("android.provider.extra.APP_PACKAGE", getPackageName()); }
            else it = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Throwable t) {}
    }

    /* once, a moment after the first start: may the app send notifications? */
    void askNotesOnce() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("asked_note", false)) return;
        sp.edit().putBoolean("asked_note", true).apply();
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTE);
    }
    boolean askedOnce = false;

    void toast(final String t) { ui.post(new Runnable() { public void run() { Toast.makeText(LauncherActivity.this, t, Toast.LENGTH_LONG).show(); } }); }

    void inject() { String u = web.getUrl(); if (u != null && ours(Uri.parse(u).getHost())) web.evaluateJavascript(Js.PAGE.replace("@TOKEN@", key), null); }

    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (!r.isForMainFrame()) {
                String sc = r.getUrl().getScheme();
                if ("https".equals(sc) || "http".equals(sc)) return false;
            }
            return outside(r.getUrl());
        }
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return packed.serve(r); }
        @Override
        public void onPageCommitVisible(WebView v, String url) { show(); inject(); }
        @Override
        public void onPageFinished(WebView v, String url) {
            show(); inject(); CookieManager.getInstance().flush();
            if (!askedOnce && url != null && url.startsWith("https://")) { askedOnce = true; ui.postDelayed(new Runnable() { public void run() { askNotesOnce(); } }, 5000); }
        }
        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame()) { show(); v.loadDataWithBaseURL("https://biblecorner.com/", Js.offline(getString(R.string.offline_t), getString(R.string.offline_p), getString(R.string.retry), HOME, getString(R.string.net_help), getString(R.string.open_settings)), "text/html", "utf-8", null); }
        }
        @Override
        public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) { recreate(); return true; }
    }

    class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = cb;
            try { startActivityForResult(p.createIntent(), REQ_FILE); }
            catch (Throwable t) { fileCb = null; return false; }
            return true;
        }
        @Override
        public void onShowCustomView(View v, CustomViewCallback cb) {
            if (fullView != null) { cb.onCustomViewHidden(); return; }
            fullView = v; fullCb = cb;
            root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        @Override
        public void onHideCustomView() { hideFull(); }
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) { cb.invoke(origin, true, false); return; }
            geoCb = cb; geoOrigin = origin;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
    }

    void hideFull() {
        if (fullView == null) return;
        root.removeView(fullView); fullView = null;
        web.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (fullCb != null) { fullCb.onCustomViewHidden(); fullCb = null; }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCb != null) { fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); fileCb = null; }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_NOTE) web.evaluateJavascript("window.dispatchEvent(new Event('kjt-native-notes'))", null);
        if (req == REQ_LOC && geoCb != null) {
            boolean ok = false; for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            geoCb.invoke(geoOrigin, ok, false); geoCb = null;
        }
    }

    /* files from the website (the app's new version, a recording): the phone's download manager */
    class Downloads implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
            if (url == null || !(url.startsWith("https:") || url.startsWith("http:"))) return;
            try {
                String name = URLUtil.guessFileName(url, cd, mime);
                DownloadManager.Request q = new DownloadManager.Request(Uri.parse(url));
                q.setMimeType(mime);
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) q.addRequestHeader("Cookie", ck);
                q.addRequestHeader("User-Agent", ua);
                q.setTitle(name);
                q.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                q.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(q);
                toast(getString(R.string.downloading) + " " + name);
            } catch (Throwable t) { outside(Uri.parse(url)); }
        }
    }

    /* what the pages can ask of the app */
    class Bridge {
        @JavascriptInterface
        public String version() { return VER; }

        /* 2.1: the phone's voice reads aloud (Bible "Read aloud", Listen for the Holy Fathers) */
        @JavascriptInterface
        public String ttsVoices() { return speech.voices(); }
        @JavascriptInterface
        public void ttsSpeak(String id, String text, String lang, String voice, float rate) { speech.speak(id == null ? "" : id, text, lang, voice, rate); }
        @JavascriptInterface
        public void ttsStop() { speech.stop(); }

        /* 2.0: the phone's dark mode */
        @JavascriptInterface
        public boolean dark() { return LauncherActivity.this.dark(); }

        /* 2.0: the daily reading reminder: on or off, at hh:mm, with its words in the page's language */
        @JavascriptInterface
        public String reminder(String k) {
            if (!key.equals(k)) return "{}";
            android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
            return "{\"on\":" + sp.getBoolean("r_on", false) + ",\"h\":" + sp.getInt("r_h", 7) + ",\"m\":" + sp.getInt("r_m", 30) + "}";
        }
        /* 2.2: the admin's daily post reminder */
        @JavascriptInterface
        public String postReminder(String k) {
            if (!key.equals(k)) return "{}";
            android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
            return "{\"on\":" + sp.getBoolean("p_on", false) + ",\"h\":" + sp.getInt("p_h", 7) + ",\"m\":" + sp.getInt("p_m", 0) + "}";
        }
        @JavascriptInterface
        public void setPostReminder(String k, boolean on, int h, int m, String title, String text) {
            if (!key.equals(k)) return;
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("p_on", on).putInt("p_h", Math.max(0, Math.min(23, h))).putInt("p_m", Math.max(0, Math.min(59, m)))
                .putString("p_t", title == null ? "" : title).putString("p_x", text == null ? "" : text).apply();
            Remind.schedule(LauncherActivity.this);
        }
        /* 2.4: a feast or a fast into the phone's own calendar (its "new event" screen opens, filled in: all day, from y-m-d for n days) */
        @JavascriptInterface
        public void addEvent(String k, final String title, final String desc, final int y, final int m, final int d, final int days) {
            if (!key.equals(k)) return;
            ui.post(new Runnable() { public void run() {
                try {
                    java.util.Calendar a = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); a.clear(); a.set(y, m - 1, d, 0, 0, 0);
                    long b = a.getTimeInMillis(), e = b + Math.max(1, days) * 86400000L;
                    Intent i = new Intent("android.intent.action.INSERT", Uri.parse("content://com.android.calendar/events"));
                    i.putExtra("beginTime", b); i.putExtra("endTime", e); i.putExtra("allDay", true);
                    i.putExtra("title", title == null ? "" : title); i.putExtra("description", desc == null ? "" : desc);
                    startActivity(i);
                } catch (Throwable t) {
                    try { android.widget.Toast.makeText(LauncherActivity.this, "No calendar app found", android.widget.Toast.LENGTH_LONG).show(); } catch (Throwable t2) {}
                }
            } });
        }
        /* 2.3: the time of the Verse of the day notification (each morning, or whenever chosen) */
        @JavascriptInterface
        public String verseTime(String k) {
            if (!key.equals(k)) return "{}";
            android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
            return "{\"h\":" + sp.getInt("v_h", 7) + ",\"m\":" + sp.getInt("v_m", 0) + "}";
        }
        @JavascriptInterface
        public void setVerseTime(String k, int h, int m) {
            if (!key.equals(k)) return;
            getSharedPreferences("app", MODE_PRIVATE).edit().putInt("v_h", Math.max(0, Math.min(23, h))).putInt("v_m", Math.max(0, Math.min(59, m))).apply();
            Remind.schedule(LauncherActivity.this);
        }
        @JavascriptInterface
        public void setReminder(String k, boolean on, int h, int m, String title, String text) {
            if (!key.equals(k)) return;
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("r_on", on).putInt("r_h", Math.max(0, Math.min(23, h))).putInt("r_m", Math.max(0, Math.min(59, m)))
                .putString("r_t", title == null ? "" : title).putString("r_x", text == null ? "" : text).apply();
            Remind.schedule(LauncherActivity.this);
        }

        /* 2.0: the widget's verse (the page has it already: no need to wait for the next check) */
        @JavascriptInterface
        public void verse(String k, String ref, String text, String url) {
            if (!key.equals(k)) return;
            VerseWidget.store(LauncherActivity.this, ref, text, url);
        }

        /* 2.0: a new version of the app, installed from inside the app (the phone asks to confirm) */
        @JavascriptInterface
        public void install(String k, final String url) {
            if (!key.equals(k)) return;
            Updater.start(LauncherActivity.this, url);
        }

        /* 2.0: a picture (a verse card) to share: kept in Pictures/Bible Corner, then the phone's share menu */
        @JavascriptInterface
        public void shareImage(String k, String b64, String name, final String text) {
            if (!key.equals(k)) return;
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                name = name == null || name.length() == 0 ? "bible-corner.png" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (Build.VERSION.SDK_INT < 29) { save(k, b64, name, "image/png"); return; }
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Bible Corner");
                final Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                OutputStream o = getContentResolver().openOutputStream(u);
                o.write(data); o.close();
                ui.post(new Runnable() { public void run() {
                    Intent it = new Intent(Intent.ACTION_SEND);
                    it.setType("image/png");
                    it.putExtra(Intent.EXTRA_STREAM, u);
                    if (text != null && text.length() > 0) it.putExtra(Intent.EXTRA_TEXT, text);
                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(Intent.createChooser(it, null)); } catch (Throwable t) {}
                } });
            } catch (Throwable t) { toast(getString(R.string.not_saved)); }
        }

        /* vibration: a number of milliseconds, or a pattern like [30,60,30] */
        @JavascriptInterface
        public void vibrate(String k, String pattern) {
            if (!key.equals(k)) return;
            try {
                android.os.Vibrator vb = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vb == null || !vb.hasVibrator()) return;
                String[] parts = pattern.replaceAll("[\\[\\]\\s]", "").split(",");
                if (parts.length <= 1) {
                    long ms = parts[0].length() == 0 ? 0 : Math.round(Double.parseDouble(parts[0]));
                    if (ms <= 0) { vb.cancel(); return; }
                    /* 1.9.1: a short tick is the phone's own "click" (tuned for its motor, so it is felt); longer ones last at least 40 ms */
                    /* 2.4: a tap is a short, firm buzz (the phone's ready-made "click" is too faint, or missing, on many phones) */
                    if (ms <= 40) { long t = Math.max(18, Math.min(30, ms * 3)); if (Build.VERSION.SDK_INT >= 26) vb.vibrate(android.os.VibrationEffect.createOneShot(t, 255)); else vb.vibrate(t); return; }
                    ms = Math.max(ms, 40);
                    if (Build.VERSION.SDK_INT >= 26) vb.vibrate(android.os.VibrationEffect.createOneShot(ms, 255)); else vb.vibrate(ms);
                } else {
                    long[] t = new long[parts.length + 1]; t[0] = 0;
                    for (int i = 0; i < parts.length; i++) t[i + 1] = Math.max(0, Math.round(Double.parseDouble(parts[i])));
                    if (Build.VERSION.SDK_INT >= 26) {
                        int[] amp = new int[t.length]; for (int i = 0; i < t.length; i++) amp[i] = i % 2 == 1 ? 255 : 0;
                        vb.vibrate(android.os.VibrationEffect.createWaveform(t, amp, -1));
                    } else vb.vibrate(t, -1);
                }
            } catch (Throwable t) {}
        }

        /* the page's language and Bible, for notifications in the same language */
        @JavascriptInterface
        public void lang(String k, String lang, String tr) {
            if (!key.equals(k)) return;
            getSharedPreferences("app", MODE_PRIVATE).edit().putString("lang", lang == null ? "en" : lang).putString("tr", tr == null ? "" : tr).apply();
        }

        /* notifications: what is on, and whether the phone allows them */
        @JavascriptInterface
        public String notes(String k) {
            if (!key.equals(k)) return "{}";
            android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
            return "{\"allowed\":" + NoticeJob.allowed(LauncherActivity.this) + ",\"news\":" + sp.getBoolean("n_news", true) + ",\"verse\":" + sp.getBoolean("n_verse", true) + "}";
        }

        @JavascriptInterface
        public void setNotes(String k, boolean news, boolean verse) {
            if (!key.equals(k)) return;
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("n_news", news).putBoolean("n_verse", verse).apply();
            NoticeJob.schedule(LauncherActivity.this);
            Remind.schedule(LauncherActivity.this);
        }

        @JavascriptInterface
        public void askNotes(String k) {
            if (!key.equals(k)) return;
            ui.post(new Runnable() { public void run() {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED && shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS") ) { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTE); return; }
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED && !getSharedPreferences("app", MODE_PRIVATE).getBoolean("asked_note", false)) { getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("asked_note", true).apply(); requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTE); return; }
                openSettings(true);
            } });
        }

        /* 2.5: the online games going on (from the games page): the app looks at them by itself every few minutes */
        @JavascriptInterface
        public void games(String k, String json) {
            if (!key.equals(k)) return;
            try {
                android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
                org.json.JSONArray old = new org.json.JSONArray(sp.getString("g_list", "[]")), a = new org.json.JSONArray(json), out = new org.json.JSONArray();
                long now = System.currentTimeMillis();
                for (int i = 0; i < a.length() && i < 12; i++) {
                    org.json.JSONObject g = a.getJSONObject(i); long t = now;
                    for (int j = 0; j < old.length(); j++) { org.json.JSONObject o = old.optJSONObject(j); if (o != null && o.optString("c").equals(g.optString("c"))) { t = g.optLong("v") > o.optLong("v") ? now : o.optLong("t", now); break; } }
                    g.put("t", t); out.put(g);
                }
                sp.edit().putString("g_list", out.toString()).putLong("g_last", now).apply();
                Remind.game(LauncherActivity.this, out.length() > 0);
            } catch (Throwable t) {}
        }

        /* 2.5: "Your turn" while the app is in the background */
        @JavascriptInterface
        public void gameNote(String k, final String title, final String text, final String url) {
            if (!key.equals(k)) return;
            NoticeJob.show(LauncherActivity.this, "games", 700 + Math.abs(String.valueOf(url).hashCode() % 200), title, text, url);
        }

        /* a test notification now, and the Verse of the day straight away */
        @JavascriptInterface
        public void testNote(String k, final String title, final String text) {
            if (!key.equals(k)) return;
            new Thread() { public void run() {
                NoticeJob.show(LauncherActivity.this, "news", 999, title, text, HOME);
                try { getSharedPreferences("app", MODE_PRIVATE).edit().putString("n_vday", "").apply(); NoticeJob.check(LauncherActivity.this, true); } catch (Throwable t) {}
            } }.start();
        }

        @JavascriptInterface
        public void save(String k, String b64, String name, String mime) {
            if (!key.equals(k)) return;
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                name = name == null || name.length() == 0 ? "bible-corner" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Bible Corner");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    OutputStream o = getContentResolver().openOutputStream(u);
                    o.write(data); o.close();
                    toast(getString(R.string.saved) + " Download/Bible Corner/" + name);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir != null) dir.mkdirs();
                    File f = new File(dir, name);
                    FileOutputStream o = new FileOutputStream(f);
                    o.write(data); o.close();
                    toast(getString(R.string.saved) + " " + f.getAbsolutePath());
                }
            } catch (Throwable t) { toast(getString(R.string.not_saved)); }
        }

        @JavascriptInterface
        public void share(String k, final String title, final String text, final String url) {
            if (!key.equals(k)) return;
            ui.post(new Runnable() { public void run() {
                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType("text/plain");
                String body = text == null ? "" : text;
                if (url != null && url.length() > 0) body = body.length() > 0 ? body + "\n" + url : url;
                it.putExtra(Intent.EXTRA_TEXT, body);
                if (title != null && title.length() > 0) it.putExtra(Intent.EXTRA_SUBJECT, title);
                try { startActivity(Intent.createChooser(it, title)); } catch (Throwable t) {}
            } });
        }

        @JavascriptInterface
        public void copy(String k, final String text) {
            if (!key.equals(k)) return;
            ui.post(new Runnable() { public void run() {
                try { ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Bible Corner", text)); } catch (Throwable t) {}
            } });
        }

        /* a recording plays or stops: the player on the lock screen and in the notifications */
        @JavascriptInterface
        public void media(String k, final String state, final String title) {
            if (!key.equals(k) || Build.VERSION.SDK_INT < 26) return;
            ui.post(new Runnable() { public void run() {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && "play".equals(state) && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                        android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
                        if (!sp.getBoolean("asked_note", false)) { sp.edit().putBoolean("asked_note", true).apply(); requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTE); }
                    }
                    Intent it = new Intent(LauncherActivity.this, MediaService.class);
                    it.setAction("stop".equals(state) ? MediaService.STOP : MediaService.SHOW);
                    it.putExtra("title", title == null ? "" : title);
                    it.putExtra("playing", "play".equals(state));
                    if (!MediaService.running && !"play".equals(state)) return;
                    if ("play".equals(state) && !MediaService.foreground) startForegroundService(it);
                    else startService(it);
                } catch (Throwable t) {}
            } });
        }
    }

    /* the lock-screen buttons, back to the page */
    static void control(final String action) {
        final LauncherActivity a = live;
        if (a == null) return;
        a.ui.post(new Runnable() { public void run() { a.web.evaluateJavascript("window.__kjtMedia&&window.__kjtMedia('" + action + "')", null); } });
    }
}
