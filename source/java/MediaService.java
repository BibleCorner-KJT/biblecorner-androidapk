package today.knowingjesus.biblecorner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

/* While a recording plays: the player in the notifications and on the lock screen
   (play, pause, previous and next chapter), and the phone keeps the recording playing with the screen off. */
public class MediaService extends Service {
    static final String SHOW = "today.knowingjesus.biblecorner.SHOW", STOP = "today.knowingjesus.biblecorner.STOP", ACT = "today.knowingjesus.biblecorner.ACT";
    static boolean running = false, foreground = false;
    static final int ID = 7;
    MediaSession session;
    String title = "";
    boolean playing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("player", getString(R.string.player), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        session = new MediaSession(this, "BibleCorner");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { LauncherActivity.control("play"); }
            @Override public void onPause() { LauncherActivity.control("pause"); }
            @Override public void onSkipToNext() { LauncherActivity.control("next"); }
            @Override public void onSkipToPrevious() { LauncherActivity.control("prev"); }
            @Override public void onStop() { LauncherActivity.control("pause"); }
        });
        session.setActive(true);
    }

    PendingIntent act(String a, int code) {
        Intent i = new Intent(this, MediaService.class); i.setAction(ACT); i.putExtra("a", a);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    Notification build() {
        MediaMetadata.Builder mb = new MediaMetadata.Builder();
        mb.putString(MediaMetadata.METADATA_KEY_TITLE, title);
        mb.putString(MediaMetadata.METADATA_KEY_ARTIST, "Bible Corner");
        session.setMetadata(mb.build());
        PlaybackState.Builder pb = new PlaybackState.Builder();
        pb.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP);
        pb.setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f);
        session.setPlaybackState(pb.build());
        Intent open = new Intent(this, LauncherActivity.class); open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Notification.Builder n = new Notification.Builder(this, "player");
        n.setSmallIcon(R.drawable.ic_stat);
        n.setContentTitle(title.length() > 0 ? title : "Bible Corner");
        n.setContentText(getString(R.string.app_name));
        n.setContentIntent(PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        n.setDeleteIntent(act("close", 9));
        n.setVisibility(Notification.VISIBILITY_PUBLIC);
        n.setOngoing(playing);
        n.setShowWhen(false);
        n.addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), getString(R.string.prev), act("prev", 2)).build());
        n.addAction(playing
            ? new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), getString(R.string.pause), act("pause", 3)).build()
            : new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_play), getString(R.string.play), act("play", 4)).build());
        n.addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), getString(R.string.next), act("next", 5)).build());
        n.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return n.build();
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if (STOP.equals(a)) { finish(); return START_NOT_STICKY; }
        if (ACT.equals(a)) {
            String what = i.getStringExtra("a");
            if ("close".equals(what)) { LauncherActivity.control("pause"); finish(); return START_NOT_STICKY; }
            LauncherActivity.control(what);
            return START_NOT_STICKY;
        }
        if (SHOW.equals(a)) {
            String t = i.getStringExtra("title"); if (t != null && t.length() > 0) title = t;
            playing = i.getBooleanExtra("playing", false);
            Notification n = build();
            if (playing) {
                if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, 2 /* media playback */); else startForeground(ID, n);
                foreground = true;
            } else {
                if (foreground) { stopForeground(Service.STOP_FOREGROUND_DETACH); foreground = false; }
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID, n);
            }
        }
        return START_NOT_STICKY;
    }

    void finish() {
        if (foreground) { stopForeground(Service.STOP_FOREGROUND_REMOVE); foreground = false; }
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent root) { finish(); super.onTaskRemoved(root); }

    @Override
    public void onDestroy() { running = false; foreground = false; try { session.release(); } catch (Throwable t) {} super.onDestroy(); }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
