package com.vianerapps.liya;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.Manifest;
import android.hardware.biometrics.BiometricPrompt;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class PersonalModeActivity extends Activity {
    private static final String PREFS = "liya_personal_private";
    private static final String PIN_HASH = "pin_hash";
    private SharedPreferences prefs;
    private boolean unlocked;
    private int hairstyle;
    private int outfit;
    private TextView selection;
    private ImageView personalImage;
    private AnimatorSet danceSet;
    private SpeechRecognizer recognizer;
    private int lastDance = 1;
    private long danceSpeed = 720;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        hairstyle = prefs.getInt("hairstyle", 1);
        outfit = prefs.getInt("outfit", 1);
        if (prefs.getString(PIN_HASH, "").isEmpty()) showPinSetup();
        else showUnlock();
    }

    private void showPinSetup() {
        LinearLayout content = base("СОЗДАТЬ ЛИЧНЫЙ PIN", "PIN хранится только на этом телефоне.");
        EditText first = pinField("Новый PIN — минимум 4 цифры");
        EditText second = pinField("Повторите PIN");
        content.addView(first);
        content.addView(second);
        content.addView(button("СОХРАНИТЬ PIN", v -> {
            String a = first.getText().toString();
            String b = second.getText().toString();
            if (a.length() < 4 || !a.equals(b)) {
                Toast.makeText(this, "PIN должен совпадать и содержать минимум 4 цифры", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putString(PIN_HASH, hash(a)).apply();
            unlock();
        }));
        setContent(content);
    }

    private void showUnlock() {
        LinearLayout content = base("ЛИЧНОЕ", "Раздел скрыт и защищён.");
        EditText pin = pinField("Введите PIN");
        content.addView(pin);
        content.addView(button("ОТКРЫТЬ ПО PIN", v -> {
            if (hash(pin.getText().toString()).equals(prefs.getString(PIN_HASH, ""))) unlock();
            else Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show();
        }));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            content.addView(button("ОТКРЫТЬ ПО ОТПЕЧАТКУ", v -> authenticateBiometric()));
        }
        setContent(content);
    }

    private void authenticateBiometric() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        Executor executor = getMainExecutor();
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
            .setTitle("Лия — Личное")
            .setSubtitle("Подтвердите владельца телефона")
            .setNegativeButton("Отмена", executor, (dialog, which) -> {})
            .build();
        prompt.authenticate(getCancellationSignal(), executor, new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { unlock(); }
            @Override public void onAuthenticationError(int code, CharSequence message) {
                Toast.makeText(PersonalModeActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private android.os.CancellationSignal getCancellationSignal() {
        return new android.os.CancellationSignal();
    }

    private void unlock() {
        unlocked = true;
        showPersonalContent();
    }

    private void showPersonalContent() {
        LinearLayout content = base("ЛИЧНОЕ", "Изолированный режим Лии. Скриншоты и запись экрана запрещены.");

        TextView profile = text("Личный образ\\nВзрослая Лия · около 30 лет\\nСтройная худощавая фигура\\n5 причёсок · 5 образов · 5 танцев", 18, Color.WHITE);
        profile.setPadding(0, 0, 0, dp(12));
        content.addView(profile);

        personalImage = new ImageView(this);
        personalImage.setAdjustViewBounds(true);
        personalImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        personalImage.setBackgroundColor(Color.rgb(3, 8, 18));
        personalImage.setImageResource(imageForOutfit(outfit));
        content.addView(personalImage, new LinearLayout.LayoutParams(-1, dp(500)));

        selection = text("", 19, Color.rgb(125, 211, 252));
        selection.setGravity(Gravity.CENTER);
        selection.setPadding(0, dp(8), 0, dp(16));
        content.addView(selection);
        refreshSelection();

        content.addView(button("СМЕНИТЬ ПРИЧЁСКУ", v -> {
            hairstyle = hairstyle % 5 + 1;
            prefs.edit().putInt("hairstyle", hairstyle).apply();
            refreshSelection();
        }));
        content.addView(button("СМЕНИТЬ ОБРАЗ", v -> {
            stopDance();
            outfit = outfit % 5 + 1;
            prefs.edit().putInt("outfit", outfit).apply();
            personalImage.setImageResource(imageForOutfit(outfit));
            refreshSelection();
        }));

        LinearLayout dances = new LinearLayout(this);
        dances.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= 5; i++) {
            final int dance = i;
            Button item = smallButton(String.valueOf(i), v -> startDance(dance));
            dances.addView(item, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        content.addView(text("Выберите танец", 18, Color.WHITE));
        content.addView(dances);

        content.addView(button("ГОЛОСОВАЯ КОМАНДА", v -> startPersonalListening()));
        content.addView(button("СТОП", v -> stopDance()));
        content.addView(button("ЗАПОМНИТЬ ЭТОТ ТАНЕЦ", v -> {
            prefs.edit().putInt("favorite_dance", lastDance).apply();
            Toast.makeText(this, "Танец " + lastDance + " сохранён", Toast.LENGTH_SHORT).show();
        }));

        Switch learning = new Switch(this);
        learning.setText("Запоминать мои предпочтения");
        learning.setTextColor(Color.WHITE);
        learning.setTextSize(18);
        learning.setChecked(prefs.getBoolean("learning", true));
        learning.setPadding(0, dp(12), 0, dp(18));
        learning.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean("learning", checked).apply());
        content.addView(learning);

        TextView memory = text(
            "Голосовые команды: «танец один», «танец два», «стоп», «медленнее», «быстрее», «следующий образ». Личная память хранится только на телефоне.",
            16, Color.rgb(186, 205, 230)
        );
        memory.setPadding(0, 0, 0, dp(18));
        content.addView(memory);

        content.addView(button("ЗАБЛОКИРОВАТЬ", v -> {
            stopDance();
            unlocked = false;
            showUnlock();
        }));
        setContent(content);
    }

    private int imageForOutfit(int value) {
        switch (value) {
            case 2: return R.drawable.liya_personal_2;
            case 3: return R.drawable.liya_personal_3;
            case 4: return R.drawable.liya_personal_4;
            case 5: return R.drawable.liya_personal_5;
            default: return R.drawable.liya_personal_1;
        }
    }

    private Button smallButton(String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(126, 34, 206));
        button.setOnClickListener(listener);
        return button;
    }

    private void startDance(int dance) {
        if (personalImage == null) return;
        stopDance();
        lastDance = dance;
        if (prefs.getBoolean("learning", true)) prefs.edit().putInt("last_dance", dance).apply();

        List<Animator> steps = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            float direction = (i % 2 == 0) ? 1f : -1f;
            float x = direction * (10f + dance * 3f);
            float rotation = direction * (1.5f + dance * .7f);
            float scale = 1f + ((i % 3 == 0) ? .025f * dance : 0f);
            if (dance == 3) x *= .45f;
            if (dance == 4) rotation *= 1.6f;
            if (dance == 5) { x *= 1.35f; scale += .02f; }
            ObjectAnimator move = ObjectAnimator.ofPropertyValuesHolder(
                personalImage,
                PropertyValuesHolder.ofFloat(android.view.View.TRANSLATION_X, x),
                PropertyValuesHolder.ofFloat(android.view.View.ROTATION, rotation),
                PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, scale),
                PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, scale)
            );
            move.setDuration(danceSpeed);
            steps.add(move);
        }
        ObjectAnimator reset = ObjectAnimator.ofPropertyValuesHolder(
            personalImage,
            PropertyValuesHolder.ofFloat(android.view.View.TRANSLATION_X, 0f),
            PropertyValuesHolder.ofFloat(android.view.View.ROTATION, 0f),
            PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f),
            PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f)
        );
        reset.setDuration(danceSpeed);
        steps.add(reset);
        danceSet = new AnimatorSet();
        danceSet.playSequentially(steps);
        danceSet.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (danceSet == animation && unlocked) startDance(lastDance);
            }
        });
        danceSet.start();
        selection.setText("Танец " + dance + " · Причёска " + hairstyle + " · Образ " + outfit);
    }

    private void stopDance() {
        AnimatorSet running = danceSet;
        danceSet = null;
        if (running != null) running.cancel();
        if (personalImage != null) {
            personalImage.setTranslationX(0f);
            personalImage.setRotation(0f);
            personalImage.setScaleX(1f);
            personalImage.setScaleY(1f);
        }
        refreshSelection();
    }

    private void startPersonalListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            selection.setText("Разрешите Лии доступ к микрофону");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 40);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Распознавание речи недоступно", Toast.LENGTH_SHORT).show();
            return;
        }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { selection.setText("Слушаю…"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() {}
            public void onError(int error) {
                if (selection != null) selection.setText(personalSpeechError(error));
            }
            public void onPartialResults(Bundle partialResults) {}
            public void onEvent(int eventType, Bundle params) {}
            public void onResults(Bundle results) {
                ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (heard != null && !heard.isEmpty()) handlePersonalCommand(heard.get(0));
            }
        });
        android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizer.startListening(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 40) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startPersonalListening();
            else if (selection != null) selection.setText("Без разрешения микрофона голосовые команды не работают");
        }
    }

    private String personalSpeechError(int error) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return "Нет разрешения на микрофон";
        if (error == SpeechRecognizer.ERROR_AUDIO) return "Микрофон занят другим приложением";
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) return "Нет связи со службой распознавания";
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return "Микрофон занят — нажмите ещё раз";
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return "Не расслышала — говорите после слова «Слушаю»";
        return "Ошибка голоса " + error;
    }

    private void handlePersonalCommand(String raw) {
        String command = raw.toLowerCase(Locale.ROOT);
        if (command.contains("стоп")) stopDance();
        else if (command.contains("медленнее")) { danceSpeed = Math.min(1500, danceSpeed + 220); startDance(lastDance); }
        else if (command.contains("быстрее")) { danceSpeed = Math.max(320, danceSpeed - 160); startDance(lastDance); }
        else if (command.contains("следующий образ") || command.contains("переоденься")) {
            stopDance();
            outfit = outfit % 5 + 1;
            prefs.edit().putInt("outfit", outfit).apply();
            personalImage.setImageResource(imageForOutfit(outfit));
            refreshSelection();
        } else if (command.contains("пять")) startDance(5);
        else if (command.contains("четыр")) startDance(4);
        else if (command.contains("три")) startDance(3);
        else if (command.contains("два")) startDance(2);
        else if (command.contains("один")) startDance(1);
        else understandPersonalCommand(raw);
    }

    private void understandPersonalCommand(String raw) {
        selection.setText("Понимаю просьбу…");
        String capabilities = "Личный режим. Можно: выбрать танец 1-5, остановить танец, сделать быстрее или медленнее, сменить образ, сменить причёску, запомнить текущий танец.";
        LiyaAiClient.request(raw, "com.vianerapps.liya.personal", capabilities, action -> {
            switch (action.name) {
                case "personal_dance_1": startDance(1); break;
                case "personal_dance_2": startDance(2); break;
                case "personal_dance_3": startDance(3); break;
                case "personal_dance_4": startDance(4); break;
                case "personal_dance_5": startDance(5); break;
                case "personal_stop": stopDance(); break;
                case "personal_faster": danceSpeed = Math.max(320, danceSpeed - 160); startDance(lastDance); break;
                case "personal_slower": danceSpeed = Math.min(1500, danceSpeed + 220); startDance(lastDance); break;
                case "personal_next_outfit":
                    stopDance();
                    outfit = outfit % 5 + 1;
                    prefs.edit().putInt("outfit", outfit).apply();
                    personalImage.setImageResource(imageForOutfit(outfit));
                    refreshSelection();
                    break;
                case "personal_next_hair":
                    hairstyle = hairstyle % 5 + 1;
                    prefs.edit().putInt("hairstyle", hairstyle).apply();
                    refreshSelection();
                    break;
                case "personal_remember":
                    prefs.edit().putInt("favorite_dance", lastDance).apply();
                    selection.setText("Запомнила танец " + lastDance);
                    break;
                default:
                    selection.setText(action.explanation.isEmpty() ? "Уточните, что сделать" : action.explanation);
            }
        }, error -> selection.setText(error));
    }

    private void refreshSelection() {
        if (selection != null) selection.setText("Причёска " + hairstyle + " из 5   ·   Образ " + outfit + " из 5");
    }

    private LinearLayout base(String titleValue, String subtitleValue) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(30), dp(22), dp(32));
        content.setBackgroundColor(Color.rgb(5, 10, 22));
        TextView title = text(titleValue, 32, Color.WHITE);
        title.setTypeface(null, 1);
        content.addView(title);
        TextView subtitle = text(subtitleValue, 18, Color.rgb(186, 205, 230));
        subtitle.setPadding(0, dp(8), 0, dp(24));
        content.addView(subtitle);
        return content;
    }

    private EditText pinField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(Color.rgb(130, 145, 166));
        field.setTextColor(Color.WHITE);
        field.setTextSize(20);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setBackgroundColor(Color.rgb(20, 31, 51));
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(64));
        lp.setMargins(0, 0, 0, dp(14));
        field.setLayoutParams(lp);
        return field;
    }

    private Button button(String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(91, 33, 182));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(0, 0, 0, dp(13));
        button.setLayoutParams(lp);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void setContent(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private String hash(String value) {
        try {
            byte[] data = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : data) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            stopDance();
            if (recognizer != null) recognizer.destroy();
            unlocked = false;
            finish();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
