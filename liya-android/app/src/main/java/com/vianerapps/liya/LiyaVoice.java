package com.vianerapps.liya;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

final class LiyaVoice {
    static final String GOOGLE_ENGINE = "com.google.android.tts";
    private LiyaVoice() { }

    static void configure(TextToSpeech tts) {
        if (tts == null) return;
        Locale russian = new Locale("ru", "RU");
        tts.setLanguage(russian);
        tts.setSpeechRate(0.92f);
        tts.setPitch(1.13f);

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
}
