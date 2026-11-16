package today.knowingjesus.biblecorner;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/* 2.1: the phone's own voice (Android text-to-speech) for the pages: "Read aloud" in the Bible and Listen for the
   Holy Fathers. The page sends a piece of text with an id; it hears back when that piece starts and ends. */
class Speech {
    final LauncherActivity a;
    TextToSpeech tts;
    boolean ready;

    Speech(LauncherActivity a) { this.a = a; }

    /* on the main thread */
    void init() {
        if (tts != null) return;
        tts = new TextToSpeech(a, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                ready = status == TextToSpeech.SUCCESS;
                if (!ready) return;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) { if (id.endsWith("#0")) tell("start", base(id), ""); }
                    public void onDone(String id) { if (last(id)) tell("end", base(id), ""); }
                    public void onError(String id) { tell("error", base(id), "synthesis-failed"); }
                    public void onStop(String id, boolean interrupted) { tell("error", base(id), "interrupted"); }
                });
                tell("ready", "", "");
            }
        });
    }

    static String base(String id) { int i = id.indexOf('#'); return i < 0 ? id : id.substring(0, i); }
    static boolean last(String id) { String[] p = id.split("#"); if (p.length < 3) return true; try { return Integer.parseInt(p[1]) == Integer.parseInt(p[2]) - 1; } catch (Throwable t) { return true; } }

    void tell(final String ev, final String id, final String x) {
        final String js = "window.__kjtTts&&window.__kjtTts('" + ev + "','" + id.replaceAll("[^A-Za-z0-9_-]", "") + "','" + x + "')";
        a.ui.post(new Runnable() { public void run() { try { a.web.evaluateJavascript(js, null); } catch (Throwable t) {} } });
    }

    /* the voices of the phone that are installed: [{n: name, l: "en-GB", o: works offline, d: the default}] */
    String voices() {
        if (!ready) { a.ui.post(new Runnable() { public void run() { init(); } }); return "[]"; }
        try {
            JSONArray out = new JSONArray(); String def = "";
            try { Voice d = tts.getDefaultVoice(); if (d != null) def = d.getName(); } catch (Throwable t) {}
            for (Voice v : tts.getVoices()) {
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                JSONObject o = new JSONObject();
                o.put("n", v.getName()); o.put("l", v.getLocale().toLanguageTag()); o.put("o", !v.isNetworkConnectionRequired()); o.put("d", v.getName().equals(def));
                out.put(o);
            }
            return out.toString();
        } catch (Throwable t) { return "[]"; }
    }

    void speak(final String id, final String text, final String lang, final String voice, final float rate) {
        a.ui.post(new Runnable() { public void run() {
            init();
            if (!ready) { tell("error", id, "not-allowed"); return; }
            try {
                Voice pick = null;
                if (voice != null && voice.length() > 0) for (Voice v : tts.getVoices()) if (voice.equals(v.getName())) { pick = v; break; }
                if (pick != null) tts.setVoice(pick); else if (lang != null && lang.length() > 0) tts.setLanguage(Locale.forLanguageTag(lang.replace('_', '-')));
                tts.setSpeechRate(Math.max(0.3f, Math.min(3f, rate)));
                /* long text in pieces the engine accepts, at the end of a sentence where it can */
                int max = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 100);
                java.util.ArrayList<String> parts = new java.util.ArrayList<String>();
                String s = text == null ? "" : text;
                while (s.length() > max) {
                    int k = Math.max(s.lastIndexOf(". ", max), Math.max(s.lastIndexOf("? ", max), s.lastIndexOf("! ", max)));
                    if (k < max / 3) k = s.lastIndexOf(' ', max); if (k < max / 3) k = max - 1;
                    parts.add(s.substring(0, k + 1)); s = s.substring(k + 1);
                }
                parts.add(s);
                for (int i = 0; i < parts.size(); i++) tts.speak(parts.get(i), TextToSpeech.QUEUE_ADD, null, id + "#" + i + "#" + parts.size());
            } catch (Throwable t) { tell("error", id, "synthesis-failed"); }
        } });
    }

    void stop() { a.ui.post(new Runnable() { public void run() { try { if (tts != null) tts.stop(); } catch (Throwable t) {} } }); }

    void shutdown() { try { if (tts != null) tts.shutdown(); } catch (Throwable t) {} tts = null; ready = false; }
}
