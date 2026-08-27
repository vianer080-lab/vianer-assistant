package com.vianerapps.liya;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.Locale;
import java.util.Set;

final class LiyaVoice {
    static final String GOOGLE_ENGINE = "com.google.android.tts";
    private LiyaVoice() { }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static MediaPlayer activePlayer;
    private static int requestNumber;

    static void configure(TextToSpeech tts) {
        if (tts == null) return;
        Locale russian = new Locale("ru", "RU");
        tts.setLanguage(russian);
        tts.setSpeechRate(1.06f);
        tts.setPitch(1.05f);

        Set<Voice> voices = tts.getVoices();
        if (voices == null) return;
        Voice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voice voice : voices) {
            if (voice.getLocale() == null || !"ru".equals(voice.getLocale().getLanguage())) continue;
            String name = voice.getName().toLowerCase(Locale.ROOT);
            int score = 10;
            // Google's higher quality network Russian voices sound substantially
            // more natural than the single Samsung fallback voice.
            if (voice.isNetworkConnectionRequired()) score += 6;
            boolean female = name.contains("female") || name.contains("жен") || name.contains("alena")
                || name.contains("milena") || name.contains("svetlana") || name.contains("irina")
                || name.contains("-ruf-") || name.contains("-dfc-")
                || name.contains("ru-ru-x-dfc") || name.contains("ru-ru-x-ruf");
            boolean male = (name.contains("male") && !name.contains("female")) || name.contains("муж");
            if (female) score += 30;
            if (male) score -= 30;
            if (score > bestScore) { best = voice; bestScore = score; }
        }
        if (best != null) tts.setVoice(best);
    }

    static void speak(Context context, TextToSpeech fallback, String text, String utteranceId) {
        speak(context, fallback, text, utteranceId, null);
    }

    static void speak(Context context, TextToSpeech fallback, String text, String utteranceId, Runnable onDone) {
        if (text == null || text.trim().isEmpty()) { if (onDone != null) onDone.run(); return; }
        if (BuildConfig.LIYA_API_URL.isEmpty() || BuildConfig.LIYA_DEVICE_TOKEN.isEmpty()) {
            speakFallback(fallback, text, utteranceId, onDone);
            return;
        }
        final int ownRequest;
        synchronized (LiyaVoice.class) {
            ownRequest = ++requestNumber;
            stopActivePlayer();
        }
        Context app = context.getApplicationContext();
        new Thread(() -> requestCloudVoice(app, fallback, text, utteranceId, onDone, ownRequest, 1), "liya-cloud-voice").start();
    }

    private static void requestCloudVoice(Context app, TextToSpeech fallback, String text, String utteranceId,
                                          Runnable onDone, int ownRequest, int attempt) {
            File audio = null;
            try {
                URL configured = new URL(BuildConfig.LIYA_API_URL);
                String endpoint = configured.getProtocol() + "://" + configured.getAuthority() + "/api/liya/speech";
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(16000);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.LIYA_DEVICE_TOKEN);
                connection.setDoOutput(true);
                byte[] payload = new JSONObject().put("text", text).toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new Exception("HTTP " + connection.getResponseCode());
                audio = File.createTempFile("liya-voice-", ".mp3", app.getCacheDir());
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(audio)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                File ready = audio;
                MAIN.post(() -> playCloud(ownRequest, ready, fallback, text, utteranceId, onDone));
            } catch (Exception error) {
                if (audio != null) audio.delete();
                if (attempt < 3 && ownRequest == requestNumber) {
                    try { Thread.sleep(700L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    requestCloudVoice(app, fallback, text, utteranceId, onDone, ownRequest, attempt + 1);
                } else {
                    MAIN.post(() -> { if (ownRequest == requestNumber) speakFallback(fallback, text, utteranceId, onDone); });
                }
            }
    }

    private static void playCloud(int ownRequest, File audio, TextToSpeech fallback, String text, String utteranceId, Runnable onDone) {
        if (ownRequest != requestNumber) { audio.delete(); return; }
        try {
            MediaPlayer player = new MediaPlayer();
            activePlayer = player;
            player.setDataSource(audio.getAbsolutePath());
            player.setOnCompletionListener(value -> finishPlayer(value, audio, onDone));
            player.setOnErrorListener((value, what, extra) -> {
                finishPlayer(value, audio, null);
                speakFallback(fallback, text, utteranceId, onDone);
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception error) {
            audio.delete();
            speakFallback(fallback, text, utteranceId, onDone);
        }
    }

    private static void finishPlayer(MediaPlayer player, File audio, Runnable onDone) {
        try { player.release(); } catch (Exception ignored) { }
        if (activePlayer == player) activePlayer = null;
        audio.delete();
        if (onDone != null) onDone.run();
    }

    private static void stopActivePlayer() {
        if (activePlayer == null) return;
        try { activePlayer.stop(); activePlayer.release(); } catch (Exception ignored) { }
        activePlayer = null;
    }

    private static void speakFallback(TextToSpeech fallback, String text, String utteranceId, Runnable onDone) {
        if (fallback != null) fallback.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        if (onDone != null) MAIN.postDelayed(onDone, Math.max(900, Math.min(5000, text.length() * 48L)));
    }
}
