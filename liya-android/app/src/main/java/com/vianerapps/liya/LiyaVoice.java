package com.vianerapps.liya;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

final class LiyaVoice {
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
            if (!voice.isNetworkConnectionRequired()) score += 4;
            if (name.contains("female") || name.contains("жен") || name.contains("alena")
                || name.contains("milena") || name.contains("svetlana") || name.contains("irina")) score += 20;
            if (name.contains("male") || name.contains("муж")) score -= 20;
            if (score > bestScore) { best = voice; bestScore = score; }
        }
        if (best != null) tts.setVoice(best);
    }
}
