package today.knowingjesus.biblecorner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;

/* 2.0: the daily reading reminder, at the time chosen in Notifications (and again after the phone restarts) */
public class Remind extends BroadcastReceiver {
    static final String FIRE = "today.knowingjesus.biblecorner.REMIND";
    /* 2.2: the admin's daily Verse of the day post (prefs p_on, p_h, p_m, p_t, p_x) */
    static final String POST = "today.knowingjesus.biblecorner.POST";
    /* 2.3: the Verse of the day, at the time chosen (prefs v_h, v_m; on while n_verse is on) */
    static final String VERSE = "today.knowingjesus.biblecorner.VERSE";
    /* 2.5: an online game going on: look every few minutes whether it is your turn (more slowly when nothing happens) */
    static final String GAME = "today.knowingjesus.biblecorner.GAME";
    static void game(Context c, boolean on) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi(c, GAME, 64)); if (!on) return;
            SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
            long quiet = System.currentTimeMillis() - sp.getLong("g_last", System.currentTimeMillis());
            long wait = quiet < 30L * 60 * 1000 ? 3L * 60 * 1000 : quiet < 6L * 60 * 60 * 1000 ? 10L * 60 * 1000 : 30L * 60 * 1000;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + wait, pi(c, GAME, 64));
        } catch (Throwable t) {}
    }

    static PendingIntent pi(Context c) { return pi(c, FIRE, 61); }
    static PendingIntent pi(Context c, String action, int code) {
        Intent i = new Intent(c, Remind.class); i.setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c) {
        one(c, FIRE, 61, "r_", 7, 30);
        one(c, POST, 62, "p_", 7, 0);
        one(c, VERSE, 63, "v_", 7, 0);
    }
    static void one(Context c, String action, int code, String p, int h, int m) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
            am.cancel(pi(c, action, code));
            if (!("v_".equals(p) ? sp.getBoolean("n_verse", true) : sp.getBoolean(p + "on", false))) return;
            Calendar k = Calendar.getInstance();
            k.set(Calendar.HOUR_OF_DAY, sp.getInt(p + "h", h)); k.set(Calendar.MINUTE, sp.getInt(p + "m", m)); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
            if (k.getTimeInMillis() <= System.currentTimeMillis() + 30000) k.add(Calendar.DAY_OF_MONTH, 1);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, k.getTimeInMillis(), pi(c, action, code));
        } catch (Throwable t) {}
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (FIRE.equals(a)) {
            SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
            String t = sp.getString("r_t", ""), x = sp.getString("r_x", "");
            if (t.length() == 0) t = c.getString(R.string.r_title);
            if (x.length() == 0) x = c.getString(R.string.r_text);
            try { NoticeJob.show(c, "remind", 600, t, x, "https://biblecorner.com/read/?kjt_app=1&kjt_appv=" + LauncherActivity.VER + "&kjt_go=plan"); } catch (Throwable e) {}
        } else if (POST.equals(a)) {
            SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
            String t = sp.getString("p_t", ""), x = sp.getString("p_x", "");
            if (t.length() == 0) t = "Verse of the day"; if (x.length() == 0) x = "Today's post is ready: look and send.";
            try { NoticeJob.show(c, "remind", 601, t, x, "https://biblecorner.com/read/?kjt_app=1&kjt_appv=" + LauncherActivity.VER + "&kjt_go=post"); } catch (Throwable e) {}
        } else if (GAME.equals(a)) {
            final PendingResult pr = goAsync(); final Context cc = c.getApplicationContext();
            new Thread() { public void run() { boolean more = true; try { more = NoticeJob.games(cc); } catch (Throwable e) {} game(cc, more); pr.finish(); } }.start();
            return;
        } else if (VERSE.equals(a)) {
            /* fetch today's verse now (if there is no internet just now, the regular check sends it later) */
            final PendingResult pr = goAsync(); final Context cc = c.getApplicationContext();
            new Thread() { public void run() { try { NoticeJob.check(cc, false); } catch (Throwable e) { NoticeJob.soon(cc); } pr.finish(); } }.start();
        } else {
            NoticeJob.schedule(c);
            try { game(c, new org.json.JSONArray(c.getSharedPreferences("app", Context.MODE_PRIVATE).getString("g_list", "[]")).length() > 0); } catch (Throwable e) {}
        }
        schedule(c);
        VerseWidget.refresh(c);
    }
}
