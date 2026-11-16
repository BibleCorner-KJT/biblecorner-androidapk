package android.speech.tts;
public class TextToSpeech {
  public static int SUCCESS = 0, ERROR = -1, QUEUE_FLUSH = 0, QUEUE_ADD = 1;
  public interface OnInitListener { void onInit(int status); }
  public static class Engine { public static String KEY_FEATURE_NOT_INSTALLED = "notInstalled"; }
  public TextToSpeech(android.content.Context c, OnInitListener l) {}
  public int speak(CharSequence text, int queueMode, android.os.Bundle params, String utteranceId) { return 0; }
  public int stop() { return 0; }
  public void shutdown() {}
  public int setSpeechRate(float r) { return 0; }
  public int setLanguage(java.util.Locale l) { return 0; }
  public java.util.Set<Voice> getVoices() { return null; }
  public Voice getDefaultVoice() { return null; }
  public int setVoice(Voice v) { return 0; }
  public static int getMaxSpeechInputLength() { return 4000; }
  public int setOnUtteranceProgressListener(UtteranceProgressListener l) { return 0; }
}
