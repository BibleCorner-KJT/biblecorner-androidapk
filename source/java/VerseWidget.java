package today.knowingjesus.biblecorner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* 2.0: the Verse of the day on the home screen. It shows the verse kept by the app (from the page, or from
   the check every few hours); a tap opens the app at that verse. */
public class VerseWidget extends AppWidgetProvider {
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    static void store(Context c, String ref, String text, String url) {
        if (text == null || text.trim().length() == 0) return;
        c.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("w_ref", ref == null ? "" : ref).putString("w_text", text.trim())
            .putString("w_url", url == null ? "" : url).putString("w_day", today()).apply();
        refresh(c);
    }

    static boolean placed(Context c) {
        try { int[] ids = AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, VerseWidget.class)); return ids != null && ids.length > 0; } catch (Throwable t) { return false; }
    }

    static boolean stale(Context c) { return !today().equals(c.getSharedPreferences("app", Context.MODE_PRIVATE).getString("w_day", "")); }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, VerseWidget.class));
            if (ids == null || ids.length == 0) return;
            SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
            String ref = sp.getString("w_ref", ""), text = sp.getString("w_text", ""), url = sp.getString("w_url", "");
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_verse);
            if (text.length() == 0) { v.setTextViewText(R.id.w_text, c.getString(R.string.w_wait)); v.setTextViewText(R.id.w_ref, c.getString(R.string.ch_verse)); }
            else { v.setTextViewText(R.id.w_text, "“" + text + "”"); v.setTextViewText(R.id.w_ref, ref); }
            Uri target = Uri.parse(LauncherActivity.HOME);
            try { Uri t = Uri.parse(url); if (url.length() > 0 && "https".equals(t.getScheme()) && LauncherActivity.ours(t.getHost())) target = t; } catch (Throwable x) {}
            Intent open = new Intent(Intent.ACTION_VIEW, target, c, LauncherActivity.class);
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 77, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            m.updateAppWidget(new ComponentName(c, VerseWidget.class), v);
        } catch (Throwable t) {}
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        refresh(c);
        if (stale(c)) NoticeJob.soon(c);
    }

    @Override
    public void onEnabled(Context c) { refresh(c); NoticeJob.soon(c); }
}
