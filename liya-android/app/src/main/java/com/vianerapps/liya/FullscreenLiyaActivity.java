package com.vianerapps.liya;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class FullscreenLiyaActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int SYSTEM_SPEECH = 503;
    public static final String ACTION_CLOSE = "com.vianerapps.liya.CLOSE_FULLSCREEN";
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private ImageView liya;
    private TextView status;

    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this, LiyaVoice.GOOGLE_ENGINE);
        buildUi();
        registerReceiver(closeReceiver, new IntentFilter(ACTION_CLOSE), RECEIVER_NOT_EXPORTED);
        if (getIntent().getBooleanExtra("listen_now", false)) startListening();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(3, 9, 21));

        liya = new ImageView(this);
        // Reuse the verified personal portrait; the old fullscreen PNG was damaged.
        liya.setImageResource(R.drawable.liya_personal_1);
        liya.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(liya, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(105, 0, 0, 0));
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(-1, dp(190), Gravity.BOTTOM);
        root.addView(shade, shadeParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(18), dp(12), dp(18), dp(18));
        status = new TextView(this);
        status.setText("ЛИЯ ГОТОВА");
        status.setTextSize(25);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(null, 1);
        controls.addView(status, new LinearLayout.LayoutParams(-1, dp(54)));

        Button listen = button("ГОВОРИТЬ С ЛИЕЙ", v -> startListening());
        controls.addView(listen);
        Button collapse = button("СВЕРНУТЬ", v -> finish());
        controls.addView(collapse);
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(-1, dp(190), Gravity.BOTTOM);
        root.addView(controls, controlsParams);
        setContentView(root);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(19);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(20, 102, 205));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.setMargins(0, dp(4), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 30);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showAndSpeak("Распознавание речи недоступно.");
            return;
        }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { setState("СЛУШАЮ…", 1.03f); }
            public void onBeginningOfSpeech() { setState("ГОВОРИТЕ…", 1.05f); }
            public void onRmsChanged(float rmsdB) { liya.setAlpha(Math.min(1f, .88f + Math.max(0, rmsdB) / 80f)); }
            public void onBufferReceived(byte[] buffer) { }
            public void onEndOfSpeech() { setState("ДУМАЮ…", 1f); }
            public void onError(int error) {
                setState(speechError(error), 1f);
                if (error == 11) openSystemSpeechDialog();
            }
            public void onPartialResults(Bundle partialResults) { }
            public void onEvent(int eventType, Bundle params) { }
            public void onResults(Bundle results) {
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values == null || values.isEmpty()) { setState("НЕ РАССЛЫШАЛА", 1f); return; }
                runCommand(values.get(0));
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
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
        catch (Exception error) { showAndSpeak("Не запустилась служба распознавания речи."); }
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
        if (requestCode == 30) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else setState("НУЖЕН ДОСТУП К МИКРОФОНУ", 1f);
        }
    }

    private String speechError(int error) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return "НЕТ ДОСТУПА К МИКРОФОНУ";
        if (error == SpeechRecognizer.ERROR_AUDIO) return "МИКРОФОН ЗАНЯТ";
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) return "НЕТ СВЯЗИ С РАСПОЗНАВАНИЕМ";
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return "МИКРОФОН ЗАНЯТ — ПОВТОРИТЕ";
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return "ГОВОРИТЕ ПОСЛЕ СЛОВА «СЛУШАЮ»";
        if (error == 11) return "ПЕРЕКЛЮЧАЮ РАСПОЗНАВАНИЕ…";
        return "ОШИБКА ГОЛОСА " + error;
    }

    private void runCommand(String command) {
        if (command.toLowerCase(Locale.ROOT).contains("свернись")) { finish(); return; }
        LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
        if (service == null) { showAndSpeak("Включите Лию в специальных возможностях."); return; }
        String answer = service.executeVoiceCommand(command);
        if (answer.startsWith("Эту команду я пока не умею")) {
            setState("СОВЕТУЮСЬ…", 1f);
            service.executeAiCommand(command, this::showAndSpeak);
        } else showAndSpeak(answer);
    }

    private void showAndSpeak(String text) {
        setState(text, 1f);
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "liya_fullscreen");
    }

    private void setState(String text, float scale) {
        status.setText(text);
        liya.animate().scaleX(scale).scaleY(scale).alpha(1f).setDuration(240).start();
    }

    @Override public void onInit(int result) { if (result == TextToSpeech.SUCCESS) LiyaVoice.configure(tts); }

    @Override
    protected void onDestroy() {
        unregisterReceiver(closeReceiver);
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
