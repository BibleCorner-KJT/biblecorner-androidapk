package today.knowingjesus.biblecorner;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;

/** Listening: the recorded chapters, from the phone when kept, else from the website; also with the screen off. */
final class Player {
    final Main m;
    final LinearLayout view;
    final ImageButton play, keep;
    final TextView title;
    final SeekBar seek;
    MediaPlayer mp;
    String tr, book, url; int ch;
    boolean active;

    Player(Main m) {
        this.m = m;
        view = new LinearLayout(m); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackgroundColor(K.dark() ? 0xff2a2030 : 0xfff6efe0); view.setPadding(K.dp(6), K.dp(2), K.dp(6), K.dp(2));
        play = btn("ic_pause", "Play or pause", new View.OnClickListener() { public void onClick(View v) { toggle(); } });
        title = K.text(m, "", 14, K.fg(), true); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        seek = new SeekBar(m);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { if (user && mp != null) try { mp.seekTo((int) ((long) mp.getDuration() * p / 1000)); } catch (Throwable t) {} }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        seek.setMax(1000);
        keep = btn("ic_download", "Keep this chapter on the phone", new View.OnClickListener() { public void onClick(View v) { keepChapter(); } });
        LinearLayout mid = new LinearLayout(m); mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(title); mid.addView(seek);
        view.addView(play, new LinearLayout.LayoutParams(K.dp(46), K.dp(46)));
        view.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        view.addView(keep, new LinearLayout.LayoutParams(K.dp(42), K.dp(46)));
        view.addView(btn("ic_close", "Stop", new View.OnClickListener() { public void onClick(View v) { stop(); } }), new LinearLayout.LayoutParams(K.dp(42), K.dp(46)));
        view.setVisibility(View.GONE);
        tick();
    }

    ImageButton btn(String ic, String desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(m); b.setImageResource(m.icon(ic)); b.setImageTintList(ColorStateList.valueOf(K.brand()));
        b.setBackgroundResource(android.R.drawable.list_selector_background); b.setContentDescription(desc); b.setOnClickListener(l);
        b.setScaleType(ImageView.ScaleType.CENTER);
        return b;
    }

    File local(String tr, String book, int ch) { return Db.get().file("audio/" + tr + "/" + book + "/" + ch + ".mp3"); }

    void chapter(final String tr, final String book, final int ch) {
        this.tr = tr; this.book = book; this.ch = ch;
        title.setText(K.bookName(book, tr) + " " + ch);
        final File f = local(tr, book, ch);
        if (f.exists()) { keep.setVisibility(View.GONE); start(f.getPath()); return; }
        keep.setVisibility(View.VISIBLE);
        K.bg(new K.Work<String>() { public String run() throws Exception {
            JSONObject r = Net.ajax("kjtb_audio", Net.map("tr", tr, "book", book, "ch", "" + ch), null);
            return r.optBoolean("success") && r.optJSONObject("data") != null ? r.getJSONObject("data").optString("url", null) : null;
        } }, new K.Done<String>() { public void done(String u, Throwable e) {
            if (e != null) { K.toast("Listening needs the internet, or the chapter kept on the phone"); return; }
            if (u == null || u.isEmpty()) { K.toast("There is no recording of this chapter in this Bible yet"); return; }
            url = u; start(u);
        } });
    }

    void start(String src) {
        try {
            release();
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setWakeMode(m, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(src);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer p) { p.start(); state(true); } });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer p) { next(); } });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer p, int a, int b) { K.toast("The recording could not be played"); stop(); return true; } });
            mp.prepareAsync();
            active = true;
            view.setVisibility(View.VISIBLE);
        } catch (Throwable t) { K.toast("The recording could not be played"); }
    }

    void toggle() {
        if (mp == null) return;
        try { if (mp.isPlaying()) { mp.pause(); state(false); } else { mp.start(); state(true); } } catch (Throwable t) {}
    }

    void control(String a) {
        if ("play".equals(a) || "pause".equals(a)) toggle();
        else if ("next".equals(a)) next();
        else if ("prev".equals(a)) { if (Db.get().chapters(tr, book).contains(ch - 1)) chapter(tr, book, ch - 1); }
    }

    void next() {
        if (tr == null) return;
        if (Db.get().chapters(tr, book).contains(ch + 1)) {
            Main.Page p = m.pages.get("bible");
            if (p instanceof BiblePage && ((BiblePage) p).book.equals(book) && ((BiblePage) p).ch == ch) ((BiblePage) p).go(book, ch + 1, 0);
            chapter(tr, book, ch + 1);
        } else stop();
    }

    /** the notification with its buttons, which keeps the sound going with the screen off */
    void state(boolean playing) {
        play.setImageResource(m.icon(playing ? "ic_pause" : "ic_play"));
        try {
            Intent it = new Intent(m, MediaService.class);
            it.setAction(MediaService.SHOW); it.putExtra("title", title.getText().toString()); it.putExtra("playing", playing);
            if (playing && !MediaService.foreground) { if (Build.VERSION.SDK_INT >= 26) m.startForegroundService(it); else m.startService(it); }
            else if (MediaService.running || playing) m.startService(it);
        } catch (Throwable t) {}
    }

    void release() { if (mp != null) { try { mp.release(); } catch (Throwable t) {} mp = null; } }

    void stop() {
        release(); active = false; view.setVisibility(View.GONE);
        try { Intent it = new Intent(m, MediaService.class); it.setAction(MediaService.STOP); m.startService(it); } catch (Throwable t) {}
    }

    void tick() {
        K.ui.postDelayed(new Runnable() { public void run() {
            try { if (mp != null && mp.isPlaying() && mp.getDuration() > 0) seek.setProgress((int) (1000L * mp.getCurrentPosition() / mp.getDuration())); } catch (Throwable t) {}
            tick();
        } }, 500);
    }

    void keepChapter() {
        if (url == null || tr == null) return;
        final String u = url, t = tr, b = book; final int c = ch;
        K.toast("Keeping the recording…");
        K.bg(new K.Work<Boolean>() { public Boolean run() throws Exception { Db.get().writeFile("audio/" + t + "/" + b + "/" + c + ".mp3", Net.get(u, null, 180000, true)); return true; } },
             new K.Done<Boolean>() { public void done(Boolean r, Throwable e) {
                 if (e == null) { keep.setVisibility(View.GONE); K.toast("Kept on the phone: " + K.bookName(b, t) + " " + c); } else K.toast("Could not keep the recording");
             } });
    }
}
