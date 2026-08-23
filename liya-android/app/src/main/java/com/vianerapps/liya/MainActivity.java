package com.vianerapps.liya;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int SYSTEM_SPEECH = 502;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);
        buildLargePrintUi();
        ensureMicrophonePermission();
        if (getIntent().getBooleanExtra("listen_now", false)) startListening();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("listen_now", false)) startListening();
    }

    private void buildLargePrintUi() {
        int pad = dp(22);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(Color.rgb(8, 17, 31));

        TextView title = text("ЛИЯ", 38, Color.WHITE);
        title.setTypeface(null, 1);
        content.addView(title);

        TextView subtitle = text("Голосовая помощница для управления телефоном", 21, Color.rgb(186, 205, 230));
        subtitle.setPadding(0, dp(8), 0, dp(22));
        content.addView(subtitle);

        status = text("Сначала включите доступ к экрану.", 20, Color.rgb(125, 211, 252));
        status.setPadding(0, 0, 0, dp(18));
        content.addView(status);

        content.addView(button("1. Включить управление экраном", v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            speak("Найдите Лию в списке и включите доступ.");
        }));
        content.addView(button("2. Слушать команду", v -> startListening()));
        content.addView(button("Открыть приложение голосом", v -> startListening()));
        content.addView(button("Лия на весь экран", v -> {
            Intent full = new Intent(this, FullscreenLiyaActivity.class);
            full.putExtra("listen_now", true);
            startActivity(full);
        }));
        content.addView(button("Перевод разговора рядом", v ->
            startActivity(new Intent(this, NearbyTranslationActivity.class))
        ));
        content.addView(button("Личное", v ->
            startActivity(new Intent(this, PersonalModeActivity.class))
        ));
        content.addView(button("Прочитать текущий экран", v -> runCommand("прочитай экран")));
        content.addView(button("Прокрутить вниз", v -> runCommand("прокрути вниз")));
        content.addView(button("Назад", v -> runCommand("назад")));

        TextView examples = text(
            "Можно сказать:\n\n«Лия, открой WhatsApp»\n«Открой YouTube»\n«Открой Facebook»\n«Открой Instagram»\n«Открой Temu»\n«Открой AliExpress»\n«Открой MasterPick»\n«Открой аккаунт Google»\n«Открой мои сохранённые пароли»\n«Найди Безопасность и покажи»\n«Прочитай экран»\n«Нажми Сохранить»\n«Введи текст: ...»",
            20,
            Color.WHITE
        );
        examples.setPadding(0, dp(22), 0, dp(30));
        content.addView(examples);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(37, 99, 235));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
        lp.setMargins(0, 0, 0, dp(14));
        button.setLayoutParams(lp);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void ensureMicrophonePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Разрешите Лии доступ к микрофону.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("На телефоне недоступно распознавание речи.");
            return;
        }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { status.setText("Слушаю…"); }
            public void onBeginningOfSpeech() { status.setText("Говорите команду"); }
            public void onRmsChanged(float rmsdB) { }
            public void onBufferReceived(byte[] buffer) { }
            public void onEndOfSpeech() { status.setText("Выполняю…"); }
            public void onError(int error) {
                status.setText(speechError(error));
                if (error == 11) openSystemSpeechDialog();
            }
            public void onPartialResults(Bundle partialResults) { }
            public void onEvent(int eventType, Bundle params) { }
            public void onResults(Bundle results) {
                ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (heard == null || heard.isEmpty()) {
                    status.setText("Команда не распознана");
                    return;
                }
                String command = heard.get(0);
                status.setText("Вы сказали: " + command);
                runCommand(command);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizer.startListening(intent);
    }

    private void openSystemSpeechDialog() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите с Лией");
        try { startActivityForResult(intent, SYSTEM_SPEECH); }
        catch (Exception error) { status.setText("Не запустилась служба распознавания речи."); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SYSTEM_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) runCommand(heard.get(0));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else status.setText("Без разрешения микрофона голосовые команды не работают.");
        }
    }

    private String speechError(int error) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return "Нет разрешения на микрофон.";
        if (error == SpeechRecognizer.ERROR_AUDIO) return "Микрофон занят другим приложением. Повторите.";
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) return "Нет связи со службой распознавания речи.";
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return "Микрофон занят. Нажмите ещё раз.";
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return "Не расслышала. Говорите после надписи «Слушаю».";
        if (error == 11) return "Переключаюсь на совместимое распознавание…";
        return "Ошибка распознавания " + error + ". Нажмите ещё раз.";
    }

    private void runCommand(String command) {
        LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
        if (service == null) {
            speak("Сначала включите Лию в специальных возможностях телефона.");
            return;
        }
        String answer = service.executeVoiceCommand(command);
        if (answer.startsWith("Эту команду я пока не умею")) {
            status.setText("Советуюсь и смотрю следующий шаг…");
            service.executeAiCommand(command, result -> {
                status.setText(result);
                speak(result);
            });
            return;
        }
        status.setText(answer);
        speak(answer);
    }

    public void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "liya");
    }

    @Override
    public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru", "RU"));
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
