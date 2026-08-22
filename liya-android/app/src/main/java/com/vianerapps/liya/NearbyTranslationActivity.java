package com.vianerapps.liya;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class NearbyTranslationActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MODE_SELLER = 1;
    private static final int MODE_USER = 2;

    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private Translator sellerToRussian;
    private Translator russianToSeller;
    private TextView status;
    private TextView original;
    private TextView translation;
    private Button georgianButton;
    private Button englishButton;
    private Button italianButton;
    private Button outputModeButton;
    private String sourceLanguage = TranslateLanguage.GEORGIAN;
    private String sourceLocale = "ka-GE";
    private Locale sellerVoiceLocale = Locale.forLanguageTag("ka-GE");
    private int listeningMode = 0;
    private String detectedLanguageTag;
    private boolean soundOutput = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);
        buildUi();
        selectLanguage(TranslateLanguage.GEORGIAN, "ka-GE", Locale.forLanguageTag("ka-GE"), "Грузинский");
        ensureMicrophonePermission();
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, dp(40));
        content.setBackgroundColor(Color.rgb(8, 17, 31));

        Button back = secondaryButton("← Назад", v -> finish());
        content.addView(back);

        TextView title = text("ПЕРЕВОД РЯДОМ", 31, Color.WHITE);
        title.setTypeface(null, 1);
        title.setPadding(0, dp(12), 0, dp(6));
        content.addView(title);
        TextView help = text("Русский перевод — в наушник. Ваш ответ — через динамик телефона.", 19, Color.rgb(186, 205, 230));
        help.setPadding(0, 0, 0, dp(18));
        content.addView(help);

        content.addView(text("Язык собеседника", 21, Color.WHITE));
        georgianButton = languageButton("Грузинский", v -> selectLanguage(TranslateLanguage.GEORGIAN, "ka-GE", Locale.forLanguageTag("ka-GE"), "Грузинский"));
        englishButton = languageButton("English", v -> selectLanguage(TranslateLanguage.ENGLISH, "en-US", Locale.US, "English"));
        italianButton = languageButton("Italiano", v -> selectLanguage(TranslateLanguage.ITALIAN, "it-IT", Locale.ITALIAN, "Italiano"));
        content.addView(georgianButton);
        content.addView(englishButton);
        content.addView(italianButton);

        outputModeButton = secondaryButton("Перевод: ЗВУКОМ", v -> toggleOutputMode());
        content.addView(outputModeButton);

        status = text("Подготавливаю переводчик…", 19, Color.rgb(125, 211, 252));
        status.setPadding(0, dp(10), 0, dp(14));
        content.addView(status);

        content.addView(primaryButton("СЛУШАТЬ СОБЕСЕДНИКА", v -> startListening(MODE_SELLER)));
        content.addView(primaryButton("ОТВЕТИТЬ ПО-РУССКИ", v -> startListening(MODE_USER)));

        original = resultBox("Здесь появится услышанная фраза", Color.rgb(148, 163, 184));
        translation = resultBox("Здесь появится перевод", Color.WHITE);
        content.addView(original);
        content.addView(translation);

        TextView note = text("При первом выборе языка телефон загрузит модель перевода. Это может занять несколько минут.", 16, Color.rgb(148, 163, 184));
        note.setPadding(0, dp(14), 0, 0);
        content.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void selectLanguage(String language, String recognizerLocale, Locale voiceLocale, String label) {
        configureLanguage(language, recognizerLocale, voiceLocale, label, null);
    }

    private void configureLanguage(String language, String recognizerLocale, Locale voiceLocale, String label, Runnable afterReady) {
        closeTranslators();
        sourceLanguage = language;
        sourceLocale = recognizerLocale;
        sellerVoiceLocale = voiceLocale;

        TranslatorOptions toRussian = new TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build();
        TranslatorOptions fromRussian = new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.RUSSIAN)
            .setTargetLanguage(sourceLanguage)
            .build();
        sellerToRussian = Translation.getClient(toRussian);
        russianToSeller = Translation.getClient(fromRussian);
        highlightLanguage(label);
        status.setText("Загружаю модель: " + label + " ↔ русский…");

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        sellerToRussian.downloadModelIfNeeded(conditions)
            .addOnSuccessListener(v -> russianToSeller.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(done -> {
                    status.setText("Готово. Нажмите, кого слушать.");
                    if (afterReady != null) afterReady.run();
                })
                .addOnFailureListener(e -> status.setText("Не удалось загрузить модель. Проверьте интернет.")))
            .addOnFailureListener(e -> status.setText("Не удалось загрузить модель. Проверьте интернет."));
    }

    private void startListening(int mode) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ensureMicrophonePermission();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("На телефоне недоступно распознавание речи.");
            return;
        }
        listeningMode = mode;
        detectedLanguageTag = null;
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { status.setText(mode == MODE_SELLER ? "Слушаю собеседника…" : "Говорите по-русски…"); }
            public void onBeginningOfSpeech() { }
            public void onRmsChanged(float rmsdB) { }
            public void onBufferReceived(byte[] buffer) { }
            public void onEndOfSpeech() { status.setText("Перевожу…"); }
            public void onError(int error) { status.setText("Не расслышала. Нажмите кнопку и повторите."); }
            public void onPartialResults(Bundle partialResults) { }
            public void onEvent(int eventType, Bundle params) { }
            @Override
            public void onLanguageDetection(Bundle results) {
                if (Build.VERSION.SDK_INT >= 34) {
                    String detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE);
                    if (detected != null) detectedLanguageTag = detected;
                }
            }
            public void onResults(Bundle results) {
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values == null || values.isEmpty()) {
                    status.setText("Речь не распознана.");
                    return;
                }
                String heard = values.get(0);
                if (mode == MODE_SELLER && applyDetectedLanguage(heard)) return;
                translate(heard, mode);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, mode == MODE_SELLER ? sourceLocale : "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        if (mode == MODE_SELLER && Build.VERSION.SDK_INT >= 34) {
            ArrayList<String> allowedLanguages = new ArrayList<>();
            allowedLanguages.add("ka-GE");
            allowedLanguages.add("en-US");
            allowedLanguages.add("it-IT");
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowedLanguages);
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowedLanguages);
        }
        recognizer.startListening(intent);
    }

    private boolean applyDetectedLanguage(String heard) {
        if (detectedLanguageTag == null) return false;
        String tag = detectedLanguageTag.toLowerCase(Locale.ROOT);
        if (tag.startsWith("ka") && !sourceLocale.startsWith("ka")) {
            configureLanguage(TranslateLanguage.GEORGIAN, "ka-GE", Locale.forLanguageTag("ka-GE"), "Грузинский", () -> translate(heard, MODE_SELLER));
            return true;
        }
        if (tag.startsWith("en") && !sourceLocale.startsWith("en")) {
            configureLanguage(TranslateLanguage.ENGLISH, "en-US", Locale.US, "English", () -> translate(heard, MODE_SELLER));
            return true;
        }
        if (tag.startsWith("it") && !sourceLocale.startsWith("it")) {
            configureLanguage(TranslateLanguage.ITALIAN, "it-IT", Locale.ITALIAN, "Italiano", () -> translate(heard, MODE_SELLER));
            return true;
        }
        return false;
    }

    private void translate(String heard, int mode) {
        original.setText("Услышала:\n" + heard);
        Translator translator = mode == MODE_SELLER ? sellerToRussian : russianToSeller;
        translator.translate(heard)
            .addOnSuccessListener(result -> {
                translation.setText("Перевод:\n" + result);
                status.setText("Перевод готов.");
                if (soundOutput) {
                    if (mode == MODE_SELLER) speakInHeadphones(result);
                    else speakToSeller(result);
                } else {
                    status.setText("Перевод показан крупным текстом.");
                }
            })
            .addOnFailureListener(e -> status.setText("Перевод не выполнен. Проверьте загрузку языка."));
    }

    private void speakInHeadphones(String value) {
        tts.setLanguage(new Locale("ru", "RU"));
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "russian_translation");
    }

    private void speakToSeller(String value) {
        int available = tts.isLanguageAvailable(sellerVoiceLocale);
        if (available < TextToSpeech.LANG_AVAILABLE) {
            status.setText("Перевод показан крупно. Голос этого языка не установлен на телефоне.");
            return;
        }
        tts.setLanguage(sellerVoiceLocale);
        File audioFile = new File(getCacheDir(), "liya_seller_reply.wav");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String utteranceId) { }
            public void onError(String utteranceId) { runOnUiThread(() -> status.setText("Не удалось озвучить перевод.")); }
            public void onDone(String utteranceId) {
                if ("seller_reply".equals(utteranceId)) playThroughPhoneSpeaker(audioFile);
            }
        });
        int result = tts.synthesizeToFile(value, null, audioFile, "seller_reply");
        if (result == TextToSpeech.ERROR) status.setText("Не удалось подготовить голос. Покажите продавцу текст на экране.");
    }

    private void playThroughPhoneSpeaker(File audioFile) {
        runOnUiThread(() -> {
            try {
                MediaPlayer player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
                AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
                for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        player.setPreferredDevice(device);
                        break;
                    }
                }
                player.setDataSource(audioFile.getAbsolutePath());
                player.setOnCompletionListener(done -> {
                    done.release();
                    status.setText("Ответ произнесён через динамик телефона.");
                });
                player.prepare();
                player.start();
            } catch (Exception error) {
                status.setText("Не удалось включить динамик. Покажите продавцу крупный перевод.");
            }
        });
    }

    private void ensureMicrophonePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 20);
        }
    }

    private void toggleOutputMode() {
        soundOutput = !soundOutput;
        outputModeButton.setText(soundOutput ? "Перевод: ЗВУКОМ" : "Перевод: ТЕКСТОМ");
        status.setText(soundOutput
            ? "Звуковой перевод включён. Текст тоже останется на экране."
            : "Звук выключен. Перевод будет показан крупным текстом.");
    }

    private void highlightLanguage(String selected) {
        if (georgianButton == null) return;
        georgianButton.setAlpha("Грузинский".equals(selected) ? 1f : .55f);
        englishButton.setAlpha("English".equals(selected) ? 1f : .55f);
        italianButton.setAlpha("Italiano".equals(selected) ? 1f : .55f);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView resultBox(String value, int color) {
        TextView view = text(value, 23, color);
        view.setBackgroundColor(Color.rgb(17, 28, 46));
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        view.setLayoutParams(params);
        return view;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button button = languageButton(label, listener);
        button.setTextSize(21);
        button.setAlpha(1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(76));
        params.setMargins(0, 0, 0, dp(14));
        button.setLayoutParams(params);
        return button;
    }

    private Button languageButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(19);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(37, 99, 235));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(60));
        params.setMargins(0, dp(7), 0, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    private Button secondaryButton(String label, View.OnClickListener listener) {
        Button button = languageButton(label, listener);
        button.setBackgroundColor(Color.rgb(30, 41, 59));
        return button;
    }

    @Override
    public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru", "RU"));
    }

    private void closeTranslators() {
        if (sellerToRussian != null) sellerToRussian.close();
        if (russianToSeller != null) russianToSeller.close();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        closeTranslators();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
