package today.knowingjesus.biblecorner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/* Notifications: every few hours (when there is internet) the app asks Bible Corner for news sent from
   WordPress (Tools > App notifications) and, once each morning, for the Verse of the day in the app's language. */
public class NoticeJob extends JobService {
    static final int JOB = 42, SOON = 43;

    /* 2.0: once, as soon as there is internet (the home-screen verse needs today's verse) */
    static void soon(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            js.schedule(new JobInfo.Builder(SOON, new ComponentName(c, NoticeJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(6L * 60 * 60 * 1000).build());
        } catch (Throwable t) {}
    }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js.getPendingJob(JOB) != null) return;
            JobInfo ji = new JobInfo.Builder(JOB, new ComponentName(c, NoticeJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(3L * 60 * 60 * 1000)
                .setPersisted(true)
                .build();
            js.schedule(ji);
        } catch (Throwable t) {}
    }

    @Override
    public boolean onStartJob(final JobParameters p) {
        new Thread() { public void run() { try { check(NoticeJob.this, false); } catch (Throwable t) {} jobFinished(p, false); } }.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }

    static void check(Context c, boolean now) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
        boolean news = sp.getBoolean("n_news", true), verse = sp.getBoolean("n_verse", true);
        boolean widget = VerseWidget.placed(c) && VerseWidget.stale(c);
        if (!news && !verse && !widget) return;
        String lang = sp.getString("lang", "en"), tr = sp.getString("tr", "");
        long since = sp.getLong("n_since", -1);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        /* 2.3: not before the time chosen for it (7:00 unless changed) */
        Calendar cal = Calendar.getInstance();
        boolean wantVerse = verse && !today.equals(sp.getString("n_vday", "")) && (now || cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) >= sp.getInt("v_h", 7) * 60 + sp.getInt("v_m", 0));
        String u = "https://biblecorner.com/wp-admin/admin-ajax.php?action=kjtb_app_notices&since=" + since + "&lang=" + Uri.encode(lang) + "&tr=" + Uri.encode(tr) + (wantVerse ? "&votd=1" : widget ? "&votd=1" : "");
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(15000); h.setReadTimeout(20000);
        h.setRequestProperty("User-Agent", "BibleCornerApp/" + LauncherActivity.VER);
        if (h.getResponseCode() != 200) return;
        InputStream in = h.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close();
        JSONObject j = new JSONObject(new String(b.toByteArray(), "UTF-8")).getJSONObject("data");
        long last = j.optLong("last", since);
        SharedPreferences.Editor e = sp.edit();
        if (since >= 0 && news) {
            JSONArray a = j.optJSONArray("notices");
            if (a != null) for (int i = 0; i < a.length() && i < 5; i++) {
                JSONObject o = a.getJSONObject(i);
                show(c, "news", 1000 + (int) (o.optLong("id") % 100000), o.optString("title"), o.optString("text"), o.optString("url"));
            }
        }
        e.putLong("n_since", Math.max(last, 0));
        JSONObject v = j.optJSONObject("votd");
        if (wantVerse && v != null && v.optString("text").length() > 0) { show(c, "verse", 500, v.optString("ref"), v.optString("text"), v.optString("url")); e.putString("n_vday", today); }
        e.apply();
        if (v != null && v.optString("text").length() > 0) VerseWidget.store(c, v.optString("ref"), v.optString("text"), v.optString("url"));
    }

    /* 2.4: the channels vibrate (a channel's sound and vibration cannot be changed once made, so they have new names; the old ones go) */
    static final long[] BUZZ = { 0, 220, 120, 220 };
    static void channels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String[][] cs = { { "news", "" + R.string.ch_news }, { "verse", "" + R.string.ch_verse }, { "remind", "" + R.string.ch_remind }, { "games", "" + R.string.ch_games } };
        for (String[] x : cs) {
            try { nm.deleteNotificationChannel(x[0]); } catch (Throwable t) {}
            NotificationChannel ch = new NotificationChannel(x[0] + "2", c.getString(Integer.parseInt(x[1])), NotificationManager.IMPORTANCE_DEFAULT);
            ch.enableVibration(true); ch.setVibrationPattern(BUZZ);
            nm.createNotificationChannel(ch);
        }
    }

    static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT < 26) return false;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return false;
        return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled();
    }

    /* 2.5: an online game: is it your turn? (the games going on, from the page: prefs g_list; looked at every few minutes
       while there are any, by Remind's GAME alarm; the page itself tells at once while it is open) */
    static boolean games(Context c) {
        SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
        JSONArray a; try { a = new JSONArray(sp.getString("g_list", "[]")); } catch (Throwable t) { a = new JSONArray(); }
        JSONArray keep = new JSONArray(); long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject g = a.optJSONObject(i); if (g == null) continue;
            if (g.optLong("t", now) < now - 24L * 60 * 60 * 1000 && g.optLong("seen", 0) < now - 24L * 60 * 60 * 1000) continue; /* 24 hours without a move: the site has ended it */
            String f = g.optString("f");
            try {
                Uri fu = Uri.parse(f); if (!"https".equals(fu.getScheme()) || !LauncherActivity.ours(fu.getHost())) continue;
                HttpURLConnection h = (HttpURLConnection) new URL(f + "?n=" + now).openConnection();
                h.setConnectTimeout(15000); h.setReadTimeout(20000); h.setUseCaches(false);
                h.setRequestProperty("User-Agent", "BibleCornerApp/" + LauncherActivity.VER);
                int code = h.getResponseCode();
                if (code == 404) continue; /* the game is over */
                if (code != 200) { keep.put(g); continue; }
                InputStream in = h.getInputStream(); ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close();
                JSONObject d = new JSONObject(new String(b.toByteArray(), "UTF-8"));
                int seat = g.optInt("s"); JSONArray ids = d.optJSONArray("ids");
                if (ids != null && g.optString("i").length() > 0 && ids.optString(seat).length() > 0 && !ids.optString(seat).equals(g.optString("i"))) continue; /* moved to another device */
                long v = d.optLong("v");
                if (v > g.optLong("v") && d.optInt("by", -1) != seat && d.has("by")) {
                    show(c, "games", 700 + Math.abs(g.optString("c").hashCode() % 200), g.optString("n"), c.getString(R.string.g_turn), g.optString("u"));
                    g.put("seen", now); sp.edit().putLong("g_last", now).apply();
                }
                g.put("v", Math.max(v, g.optLong("v"))); keep.put(g);
            } catch (Throwable t) { keep.put(g); }
        }
        sp.edit().putString("g_list", keep.toString()).apply();
        return keep.length() > 0;
    }

    static void show(Context c, String ch, int id, String title, String text, String url) {
        if (!allowed(c)) return;
        channels(c);
        Uri target = Uri.parse(LauncherActivity.HOME);
        try { Uri t = Uri.parse(url); if (url != null && url.length() > 0 && "https".equals(t.getScheme()) && LauncherActivity.ours(t.getHost())) target = t; } catch (Throwable x) {}
        Intent open = new Intent(Intent.ACTION_VIEW, target, c, LauncherActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nb = new Notification.Builder(c, ch + "2");
        nb.setSmallIcon(R.drawable.ic_stat);
        nb.setContentTitle(title);
        nb.setContentText(text);
        nb.setStyle(new Notification.BigTextStyle().bigText(text));
        nb.setContentIntent(pi);
        nb.setAutoCancel(true);
        nb.setColor(0xff3e093f);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, nb.build());
    }
}
