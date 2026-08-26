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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

public class PersonalModeActivity extends Activity implements TextToSpeech.OnInitListener {
    private static PersonalModeActivity visibleInstance;
    private static final String PREFS = "liya_personal_private";
    private static final String PIN_HASH = "pin_hash";
    private static final int PERSONAL_SYSTEM_SPEECH = 501;
    private SharedPreferences prefs;
    private boolean unlocked;
    private int hairstyle;
    private int outfit;
    private TextView selection;
    private ImageView personalImage;
    private AnimatorSet danceSet;
    private AnimatorSet idleSet;
    private SpeechRecognizer recognizer;
    private int lastDance = 1;
    private long danceSpeed = 260;
    private Bitmap[] danceFrames;
    private Bitmap[] hairFrames;
    private final Handler danceHandler = new Handler(Looper.getMainLooper());
    private Runnable danceRunnable;
    private TextToSpeech tts;
    private boolean personalAutoListening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        tts = new TextToSpeech(this, this, LiyaVoice.GOOGLE_ENGINE);
        danceFrames = LiyaDanceFrames.load();
        hairFrames = LiyaHairFrames.load();
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(3, 8, 18));
        personalImage = new ImageView(this);
        personalImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        personalImage.setBackgroundColor(Color.rgb(3, 8, 18));
        personalImage.setImageResource(imageForOutfit(outfit));
        root.addView(personalImage, new FrameLayout.LayoutParams(-1, -1));
        personalImage.post(this::startIdleMotion);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(14));
        bottom.setBackgroundColor(Color.argb(205, 3, 8, 18));

        selection = text("Лия слушает…", 17, Color.WHITE);
        selection.setGravity(Gravity.CENTER);
        selection.setMaxLines(2);
        bottom.addView(selection, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setVisibility(View.GONE);

        LinearLayout dances = new LinearLayout(this);
        dances.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= 5; i++) {
            final int dance = i;
            dances.addView(smallButton(String.valueOf(i), v -> {
                startDance(dance);
                showAndSpeakPersonal("Включаю танец " + dance + ".");
            }), new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        menu.addView(dances);
        menu.addView(button("Сменить причёску", v -> {
            hairstyle = hairstyle % 5 + 1;
            prefs.edit().putInt("hairstyle", hairstyle).apply();
            stopDance();
            personalImage.setImageBitmap(hairFrames[hairstyle - 1]);
            showAndSpeakPersonal("Сменила причёску.");
        }));
        menu.addView(button("Сменить образ", v -> {
            stopDance();
            outfit = outfit % 7 + 1;
            prefs.edit().putInt("outfit", outfit).apply();
            personalImage.setImageResource(imageForOutfit(outfit));
            showAndSpeakPersonal("Переоделась.");
        }));
        menu.addView(button("Пляжный образ", v -> showSwimsuit()));
        menu.addView(button("Приватный образ 18+", v -> {
            stopDance();
            outfit = outfit % 7 + 1;
            prefs.edit().putInt("outfit", outfit).putBoolean("adult_private", true).apply();
            personalImage.setImageResource(imageForOutfit(outfit));
            showAndSpeakPersonal("Включила приватный взрослый образ без откровенного контента.");
        }));
        menu.addView(button("Остановить танец", v -> { stopDance(); showAndSpeakPersonal("Остановилась."); }));
        menu.addView(button("Запомнить танец", v -> {
            prefs.edit().putInt("favorite_dance", lastDance).apply();
            showAndSpeakPersonal("Запомнила танец " + lastDance + ".");
        }));
        menu.addView(button("Заблокировать", v -> {
            personalAutoListening = false;
            stopDance();
            unlocked = false;
            showUnlock();
        }));

        bottom.addView(menu);
        Button menuButton = smallButton("МЕНЮ", v -> menu.setVisibility(menu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        bottom.addView(menuButton, new LinearLayout.LayoutParams(-1, dp(48)));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bottomParams);
        // Android 15 draws apps behind the phone navigation controls by default.
        // Keep the whole personal-mode panel above that area so Menu/Lock stay tappable.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int navigationBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                navigationBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                navigationBottom = insets.getSystemWindowInsetBottom();
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bottom.getLayoutParams();
            int safeBottom = Math.max(navigationBottom, dp(8));
            if (params.bottomMargin != safeBottom) {
                params.bottomMargin = safeBottom;
                bottom.setLayoutParams(params);
            }
            return insets;
        });
        root.requestApplyInsets();
        setContentView(root);

        personalAutoListening = true;
        LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
        if (service != null) {
            // Personal mode owns the microphone while it is visible. Running the
            // accessibility recognizer at the same time makes Samsung report a busy
            // microphone and leaves the screen saying "listening" without results.
            service.stopContinuousVoice();
        }
        selection.setText("Лия слушает…");
        danceHandler.postDelayed(this::startPersonalListening, 350);
    }

    public static boolean dispatchOfflineVoiceCommand(String command) {
        PersonalModeActivity activity = visibleInstance;
        if (activity == null || !activity.unlocked) return false;
        activity.runOnUiThread(() -> activity.handlePersonalCommand(command));
        return true;
    }

    @Override protected void onResume() { super.onResume(); visibleInstance = this; }
    @Override protected void onPause() { if (visibleInstance == this) visibleInstance = null; super.onPause(); }

    private int imageForOutfit(int value) {
        switch (value) {
            case 2: return R.drawable.liya_personal_2;
            case 3: return R.drawable.liya_personal_3;
            case 4: return R.drawable.liya_personal_4;
            case 5: return R.drawable.liya_personal_5;
            case 6: return R.drawable.liya_personal_6;
            case 7: return R.drawable.liya_personal_7;
            default: return R.drawable.liya_personal_1;
        }
    }

    private void showSwimsuit() {
        stopDance();
        outfit = outfit == 6 ? 7 : 6;
        prefs.edit().putInt("outfit", outfit).apply();
        personalImage.setImageResource(imageForOutfit(outfit));
        showAndSpeakPersonal(outfit == 6 ? "Выбрала бирюзовый пляжный образ." : "Выбрала бордовый пляжный образ.");
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
        stopIdleMotion();
        lastDance = dance;
        if (prefs.getBoolean("learning", true)) prefs.edit().putInt("last_dance", dance).apply();
        final int[][] choreography = {
            {0,1,2,3,4,5,6,7},
            {0,2,4,6,7,5,3,1},
            {0,1,3,5,7,6,4,2},
            {7,6,5,4,3,2,1,0},
            {0,3,1,4,2,5,6,7}
        };
        final int[] order = choreography[Math.max(1, Math.min(5, dance)) - 1];
        final int[] position = {0};
        danceRunnable = new Runnable() {
            @Override public void run() {
                if (!unlocked || danceRunnable != this || personalImage == null) return;
                personalImage.setImageBitmap(danceFrames[order[position[0]]]);
                position[0] = (position[0] + 1) % order.length;
                danceHandler.postDelayed(this, danceSpeed);
            }
        };
        danceHandler.post(danceRunnable);
        selection.setText("Танец " + dance + " · Причёска " + hairstyle + " · Образ " + outfit);
    }

    private void stopDance() {
        if (danceRunnable != null) danceHandler.removeCallbacks(danceRunnable);
        danceRunnable = null;
        AnimatorSet running = danceSet;
        danceSet = null;
        if (running != null) running.cancel();
        if (personalImage != null) {
            personalImage.setTranslationX(0f);
            personalImage.setRotation(0f);
            personalImage.setScaleX(1f);
            personalImage.setScaleY(1f);
            personalImage.setImageResource(imageForOutfit(outfit));
            startIdleMotion();
        }
        refreshSelection();
    }

    private void startIdleMotion() {
        if (personalImage == null || danceRunnable != null || isFinishing()) return;
        stopIdleMotion();
        ObjectAnimator breathe = ObjectAnimator.ofPropertyValuesHolder(personalImage,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.018f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.018f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -dp(4)));
        breathe.setDuration(2400);
        breathe.setRepeatCount(ObjectAnimator.INFINITE);
        breathe.setRepeatMode(ObjectAnimator.REVERSE);
        ObjectAnimator sway = ObjectAnimator.ofFloat(personalImage, View.ROTATION, -0.25f, 0.25f);
        sway.setDuration(3200);
        sway.setRepeatCount(ObjectAnimator.INFINITE);
        sway.setRepeatMode(ObjectAnimator.REVERSE);
        idleSet = new AnimatorSet();
        idleSet.playTogether(breathe, sway);
        idleSet.start();
    }

    private void stopIdleMotion() {
        AnimatorSet running = idleSet;
        idleSet = null;
        if (running != null) running.cancel();
        if (personalImage != null) {
            personalImage.setScaleX(1f);
            personalImage.setScaleY(1f);
            personalImage.setTranslationY(0f);
            personalImage.setRotation(0f);
        }
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
        // Samsung may advertise on-device recognition without a Russian language pack
        // and then return error 11. Start with the system recognition service and use
        // the visible system dialog below as a compatibility fallback.
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { selection.setText("Слушаю…"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() {}
            public void onError(int error) {
                if (selection != null) selection.setText(personalSpeechError(error));
                if (error == 11) {
                    danceHandler.postDelayed(PersonalModeActivity.this::openSystemSpeechDialog, 400);
                } else if (personalAutoListening) {
                    danceHandler.postDelayed(PersonalModeActivity.this::startPersonalListening, 1200);
                }
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

    private void openSystemSpeechDialog() {
        if (!unlocked) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите с Лией");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            startActivityForResult(intent, PERSONAL_SYSTEM_SPEECH);
        } catch (Exception error) {
            showAndSpeakPersonal("На телефоне не запустилась служба распознавания речи.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PERSONAL_SYSTEM_SPEECH) return;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) {
                handlePersonalCommand(heard.get(0));
                return;
            }
        }
        if (personalAutoListening) danceHandler.postDelayed(this::startPersonalListening, 800);
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
        if (error == 11) return "Переключаюсь на совместимый микрофон…";
        return "Ошибка голоса " + error;
    }

    private void handlePersonalCommand(String raw) {
        String command = raw.toLowerCase(Locale.ROOT);
        if (command.contains("стоп")) { stopDance(); showAndSpeakPersonal("Остановилась."); }
        else if (command.contains("медленнее")) { danceSpeed = Math.min(1500, danceSpeed + 220); startDance(lastDance); showAndSpeakPersonal("Хорошо, двигаюсь медленнее."); }
        else if (command.contains("быстрее")) { danceSpeed = Math.max(100, danceSpeed - 80); startDance(lastDance); showAndSpeakPersonal("Хорошо, двигаюсь быстрее."); }
        else if (command.contains("купальник") || command.contains("пляжный образ") || command.contains("на пляж")) {
            showSwimsuit();
        } else if (command.contains("следующий образ") || command.contains("переоденься")) {
            stopDance();
            outfit = outfit % 7 + 1;
            prefs.edit().putInt("outfit", outfit).apply();
            personalImage.setImageResource(imageForOutfit(outfit));
            showAndSpeakPersonal("Переоделась. Выбрала образ " + outfit + ".");
        } else if (command.contains("пять")) { startDance(5); showAndSpeakPersonal("Включаю пятый танец."); }
        else if (command.contains("четыр")) { startDance(4); showAndSpeakPersonal("Включаю четвёртый танец."); }
        else if (command.contains("три")) { startDance(3); showAndSpeakPersonal("Включаю третий танец."); }
        else if (command.contains("два")) { startDance(2); showAndSpeakPersonal("Включаю второй танец."); }
        else if (command.contains("один")) { startDance(1); showAndSpeakPersonal("Включаю первый танец."); }
        else understandPersonalCommand(raw);
    }

    private void understandPersonalCommand(String raw) {
        selection.setText("Отвечаю…");
        prefs.edit().putString("last_request", raw).apply();
        String capabilities = "Личный голосовой режим. Поддерживается обычный дружелюбный разговор и действия: выбрать танец 1-5, остановить танец, сделать быстрее или медленнее, сменить один из 7 образов, выбрать один из 2 пляжных купальников, сменить причёску, запомнить текущий танец. Текущие предпочтения: образ " + outfit + ", причёска " + hairstyle + ", любимый танец " + prefs.getInt("favorite_dance", 1) + ".";
        LiyaAiClient.request(raw, "com.vianerapps.liya.personal", capabilities, action -> {
            switch (action.name) {
                case "personal_dance_1": startDance(1); showAndSpeakPersonal("Включаю первый танец."); break;
                case "personal_dance_2": startDance(2); showAndSpeakPersonal("Включаю второй танец."); break;
                case "personal_dance_3": startDance(3); showAndSpeakPersonal("Включаю третий танец."); break;
                case "personal_dance_4": startDance(4); showAndSpeakPersonal("Включаю четвёртый танец."); break;
                case "personal_dance_5": startDance(5); showAndSpeakPersonal("Включаю пятый танец."); break;
                case "personal_stop": stopDance(); showAndSpeakPersonal("Остановилась."); break;
                case "personal_faster": danceSpeed = Math.max(100, danceSpeed - 80); startDance(lastDance); showAndSpeakPersonal("Двигаюсь быстрее."); break;
                case "personal_slower": danceSpeed = Math.min(1500, danceSpeed + 220); startDance(lastDance); showAndSpeakPersonal("Двигаюсь медленнее."); break;
                case "personal_next_outfit":
                    stopDance();
                    outfit = outfit % 7 + 1;
                    prefs.edit().putInt("outfit", outfit).apply();
                    personalImage.setImageResource(imageForOutfit(outfit));
                    showAndSpeakPersonal("Переоделась. Выбрала образ " + outfit + ".");
                    break;
                case "personal_swimsuit":
                    showSwimsuit();
                    break;
                case "personal_next_hair":
                    hairstyle = hairstyle % 5 + 1;
                    prefs.edit().putInt("hairstyle", hairstyle).apply();
                    stopDance();
                    personalImage.setImageBitmap(hairFrames[hairstyle - 1]);
                    showAndSpeakPersonal("Сменила причёску. Выбрала вариант " + hairstyle + ".");
                    break;
                case "personal_remember":
                    prefs.edit().putInt("favorite_dance", lastDance).apply();
                    showAndSpeakPersonal("Запомнила танец " + lastDance + ".");
                    break;
                default:
                    showAndSpeakPersonal(action.explanation.isEmpty() ? "Уточните, пожалуйста, что мне сделать." : action.explanation);
            }
        }, this::showAndSpeakPersonal);
    }

    private void showAndSpeakPersonal(String value) {
        // Personal mode is voice-first: do not cover the portrait with the
        // transcript or internal AI wording. Only a short state is shown.
        if (selection != null) selection.setText("Отвечаю голосом…");
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
        if (service != null) service.setOfflineVoiceMuted(true);
        LiyaVoice.speak(this, tts, value, "liya_personal",
            () -> {
                if (service != null) service.setOfflineVoiceMuted(false);
                if (selection != null) selection.setText("Слушаю…");
                if (personalAutoListening && unlocked) danceHandler.postDelayed(this::startPersonalListening, 250);
            });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            LiyaVoice.configure(tts);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onError(String utteranceId) {
                    LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
                    if (service != null) service.setOfflineVoiceMuted(false);
                }
                @Override public void onDone(String utteranceId) {
                    LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
                    if (service != null) service.setOfflineVoiceMuted(false);
                }
            });
        }
    }

    private void refreshSelection() {
        if (selection != null) selection.setText("Причёска " + hairstyle + " из 5   ·   Образ " + outfit + " из 7");
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
            personalAutoListening = false;
            stopDance();
            if (recognizer != null) recognizer.destroy();
            recognizer = null;
            LiyaAccessibilityService service = LiyaAccessibilityService.getInstance();
            if (service != null) service.startContinuousVoice();
            unlocked = false;
            finish();
        }
    }

    @Override protected void onDestroy() {
        danceHandler.removeCallbacksAndMessages(null);
        stopIdleMotion();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
