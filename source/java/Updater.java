package today.knowingjesus.biblecorner;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* 2.0: a new version of the app, from the website, installed from inside the app. Android asks the person to
   confirm (and, the first time, to allow Bible Corner to install updates). Only files from Bible Corner's own
   website, signed with the same key as this app, can be installed over it. */
public class Updater extends BroadcastReceiver {
    static final String DONE = "today.knowingjesus.biblecorner.INSTALLED";
    static volatile boolean busy = false;

    static void say(final String js) { final LauncherActivity a = LauncherActivity.live; if (a == null) return; a.ui.post(new Runnable() { public void run() { a.web.evaluateJavascript(js, null); } }); }

    static void start(final Context c, final String url) {
        if (busy) return;
        Uri u; try { u = Uri.parse(url); } catch (Throwable t) { return; }
        if (u == null || !"https".equals(u.getScheme()) || !LauncherActivity.ours(u.getHost()) || u.getPath() == null || !u.getPath().endsWith(".apk")) return;
        busy = true;
        new Thread() { public void run() {
            PackageInstaller.Session s = null;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(20000); h.setReadTimeout(30000);
                h.setRequestProperty("User-Agent", "BibleCornerApp/" + LauncherActivity.VER);
                if (h.getResponseCode() != 200) throw new Exception("http " + h.getResponseCode());
                long len = h.getContentLength();
                PackageInstaller pi = c.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(sp);
                s = pi.openSession(id);
                InputStream in = h.getInputStream(); OutputStream out = s.openWrite("app.apk", 0, len > 0 ? len : -1);
                byte[] buf = new byte[65536]; int n; long got = 0; int last = -1;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); got += n; if (len > 0) { int p = (int) (got * 100 / len); if (p != last) { last = p; say("window.__kjtUpd&&window.__kjtUpd(" + p + ")"); } } }
                in.close(); s.fsync(out); out.close();
                Intent done = new Intent(c, Updater.class); done.setAction(DONE);
                PendingIntent back = PendingIntent.getBroadcast(c, 62, done, (android.os.Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0) | PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(back.getIntentSender());
                s.close(); s = null;
                say("window.__kjtUpd&&window.__kjtUpd('ask')");
            } catch (Throwable t) {
                try { if (s != null) s.abandon(); } catch (Throwable x) {}
                say("window.__kjtUpd&&window.__kjtUpd('fail')");
            }
            busy = false;
        } }.start();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { c.startActivity(confirm); } catch (Throwable t) {} }
        } else if (st != PackageInstaller.STATUS_SUCCESS) say("window.__kjtUpd&&window.__kjtUpd('fail')");
    }
}
