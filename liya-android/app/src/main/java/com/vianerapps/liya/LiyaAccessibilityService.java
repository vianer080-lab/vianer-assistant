package com.vianerapps.liya;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class LiyaAccessibilityService extends AccessibilityService {
    private static LiyaAccessibilityService instance;
    private WindowManager windowManager;
    private TextView floatingButton;
    private final Handler aiHandler = new Handler(Looper.getMainLooper());
    private boolean aiCancelled;
    private String activeAiTask = "";
    private int aiStep;
    private static final int MAX_AI_STEPS = 24;
    private String previousAiScreen = "";
    private String lastAiResult = "";
    private int unchangedAiSteps;
    private final Map<String, Integer> aiScreenVisits = new HashMap<>();
    private String lastAiActionSignature = "";
    private int repeatedAiActionCount;
    private boolean activeAiApproved;
    private SpeechRecognizer backgroundRecognizer;
    private TextToSpeech backgroundTts;
    private boolean continuousVoice;
    private boolean restartingVoice;
    private LiyaOfflineVoice offlineVoice;
    private boolean remoteTaskRunning;
    private long lastUserInteractionAt = System.currentTimeMillis();
    private static final long REMOTE_IDLE_DELAY_MS = 45_000L;
    private final Runnable remotePoll = new Runnable() {
        @Override public void run() {
            if (!remoteTaskRunning && isDeviceIdleForRemoteTask()) {
                LiyaRemoteClient.poll(LiyaAccessibilityService.this, LiyaAccessibilityService.this::runRemoteTask);
            }
            // Keep remote commands responsive while the accessibility service is alive.
            aiHandler.postDelayed(this, remoteTaskRunning ? 900 : 1500);
        }
    };

    public static LiyaAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // The service stays active without covering other apps with a floating button.
        backgroundTts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) LiyaVoice.configure(backgroundTts);
        }, LiyaVoice.GOOGLE_ENGINE);
        aiHandler.post(remotePoll);
        aiHandler.postDelayed(() -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && offlineVoice == null) {
                continuousVoice = true;
                offlineVoice = new LiyaOfflineVoice(this, this::handleBackgroundCommand, value -> { });
                offlineVoice.start();
            }
        }, 1200);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!remoteTaskRunning && event != null && event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            lastUserInteractionAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onInterrupt() { }

    public void syncRemoteNow() {
        if (!remoteTaskRunning) LiyaRemoteClient.poll(this, this::runRemoteTask);
    }

    public void setWorkNow(boolean enabled) {
        getSharedPreferences("liya_vianer_link", MODE_PRIVATE).edit().putBoolean("work_now", enabled).apply();
        lastUserInteractionAt = enabled ? 0L : System.currentTimeMillis();
        if (enabled) syncRemoteNow();
    }

    public boolean isWorkNowEnabled() {
        return getSharedPreferences("liya_vianer_link", MODE_PRIVATE).getBoolean("work_now", false);
    }

    private boolean isDeviceIdleForRemoteTask() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean interactive = power != null && power.isInteractive();
        boolean unlocked = keyguard == null || !keyguard.isKeyguardLocked();
        return interactive && unlocked && !isCallScreenOpen()
            && (isWorkNowEnabled() || System.currentTimeMillis() - lastUserInteractionAt >= REMOTE_IDLE_DELAY_MS);
    }

    private boolean isCallScreenOpen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return false;
        String pkg = root.getPackageName().toString().toLowerCase(Locale.ROOT);
        return pkg.contains("incallui") || pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("phone");
    }

    private void runRemoteTask(LiyaRemoteClient.Task task) {
        if (remoteTaskRunning) return;
        remoteTaskRunning = true;
        String instruction = task.instruction == null ? "" : task.instruction.trim();

        if (!task.attachmentUrl.isEmpty()) {
            shareRemoteAttachment(task, shareResult -> {
                if (shareResult.startsWith("Ошибка")) {
                    finishRemoteTask(task, shareResult);
                    return;
                }
                String goal = instruction.isEmpty()
                    ? "Опубликуй подготовленный материал в открытом приложении."
                    : instruction;
                aiHandler.postDelayed(
                    () -> executeAiCommand(goal, task.approved, result -> finishRemoteTask(task, result)),
                    1100
                );
            });
            return;
        }

        boolean multiStep = instruction.length() > 70;
        String answer = executeVoiceCommand(instruction);

        // Opening the requested app is only the first step of a remote task.
        // Wait for its UI and continue the original instruction on that screen.
        if (multiStep && answer.startsWith("Открываю ")) {
            aiHandler.postDelayed(
                () -> executeAiCommand(instruction, task.approved, result -> finishRemoteTask(task, result)),
                1100
            );
            return;
        }

        // Long remote instructions describe a goal, not the literal label of one button.
        // If the fast local parser cannot complete them, hand the whole goal to the screen agent.
        if (answer.startsWith("Эту команду я пока не умею") ||
            (multiStep && (answer.startsWith("Не нашла") || answer.startsWith("На этой странице не вижу")))) {
            executeAiCommand(instruction, task.approved, result -> finishRemoteTask(task, result));
        } else finishRemoteTask(task, answer);
    }

    private void finishRemoteTask(LiyaRemoteClient.Task task, String result) {
        String lowerResult = result.toLowerCase(Locale.ROOT);
        boolean needsAttention = lowerResult.contains("подтвержден") || lowerResult.contains("подтверждение")
            || lowerResult.contains("парол") || lowerResult.contains("одноразов")
            || lowerResult.contains("код") || lowerResult.contains("защит");
        boolean failed = containsAny(lowerResult,
            "не удалось", "ошибка", "не смог", "не сработал", "не нашла", "не вижу",
            "остановил", "по кругу", "много шагов", "связь со мной ещё не активирована",
            "задача остановлена", "нужен другой путь");
        String status = needsAttention ? "needs_confirmation" : failed ? "failed" : "completed";
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String packageName = root != null && root.getPackageName() != null ? root.getPackageName().toString() : "";
        String reportText = "Задание №" + task.id + ": "
            + ("completed".equals(status) ? "выполнено. " : "failed".equals(status) ? "не выполнено. " : "нужна помощь. ")
            + result;
        if (needsAttention) showAttentionNotification(result);
        else if (!task.silent) speakBackground(reportText, continuousVoice);
        LiyaRemoteClient.report(this, task.id, status, reportText, packageName, collectScreenText(), saved -> {
            if (!saved) showAttentionNotification("Не удалось сохранить отчёт по заданию №" + task.id + ". Лия повторит отправку при следующем запуске.");
            if (task.silent && "completed".equals(status)) performGlobalAction(GLOBAL_ACTION_HOME);
            remoteTaskRunning = false;
            // The queue belongs to the accessibility service, not to ChatGPT. Take the next task immediately.
            lastUserInteractionAt = 0L;
            aiHandler.postDelayed(this::syncRemoteNow, 800L);
        });
    }

    private void showAttentionNotification(String result) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channelId = "liya_attention";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Лия — требуется действие", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Пароль, код или подтверждение, которое Лия не вводит сама");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("attention_message", result);
        PendingIntent pending = PendingIntent.getActivity(this, 401, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Лие нужна ваша помощь")
            .setContentText(result)
            .setStyle(new Notification.BigTextStyle().bigText(result))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build();
        manager.notify(401, notification);
    }

    private void shareRemoteAttachment(LiyaRemoteClient.Task task, Consumer<String> callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw new Exception("Нужна Android 10 или новее");
                }
                URL url = new URL(task.attachmentUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                String mime = connection.getContentType();
                if (mime == null || !mime.startsWith("image/")) mime = "image/png";
                String extension = mime.contains("jpeg") ? ".jpg" : ".png";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "MasterPick-" + task.id + extension);
                values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MasterPick");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri media = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (media == null) throw new Exception("не удалось создать файл");
                try (InputStream input = connection.getInputStream(); OutputStream output = getContentResolver().openOutputStream(media)) {
                    if (output == null) throw new Exception("не удалось открыть файл");
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(media, values, null, null);

                String target = task.targetPackage;
                if (target.isEmpty()) {
                    String lower = task.instruction.toLowerCase(Locale.ROOT);
                    if (lower.contains("facebook") || lower.contains("фейсбук")) target = "com.facebook.katana";
                    else target = "com.instagram.android";
                }
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType(mime);
                share.putExtra(Intent.EXTRA_STREAM, media);
                if (!task.caption.isEmpty()) share.putExtra(Intent.EXTRA_TEXT, task.caption);
                share.setClipData(ClipData.newRawUri("MasterPick", media));
                share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (getPackageManager().getLaunchIntentForPackage(target) != null) share.setPackage(target);
                startActivity(share);
                aiHandler.post(() -> callback.accept("Материал загружен и открыт для публикации."));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "неизвестная ошибка" : error.getMessage();
                aiHandler.post(() -> callback.accept("Ошибка загрузки материала: " + message));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    public String startContinuousVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return "Сначала разрешите Лии доступ к микрофону.";
        }
        continuousVoice = true;
        if (offlineVoice == null) { offlineVoice = new LiyaOfflineVoice(this, this::handleBackgroundCommand, this::speakOfflineState); offlineVoice.start(); }
        offlineVoice.setActive(true);
        return "Локальный голос запускается без системных сигналов.";
    }

    public void setOfflineVoiceMuted(boolean muted) {
        if (offlineVoice != null) offlineVoice.pause(muted);
    }

    private void speakOfflineState(String value) { LiyaVoice.speak(this, backgroundTts, value, "liya_offline_state"); }

    public void stopContinuousVoice() {
        continuousVoice = false;
        // Keep remote task polling alive when only voice mode is stopped.
        if (backgroundRecognizer != null) {
            backgroundRecognizer.cancel();
            backgroundRecognizer.destroy();
            backgroundRecognizer = null;
        }
        if (offlineVoice != null) { offlineVoice.stop(); offlineVoice = null; }
    }

    private void listenInBackground() {
        if (!continuousVoice || restartingVoice) return;
        restartingVoice = true;
        aiHandler.postDelayed(() -> {
            restartingVoice = false;
            if (!continuousVoice) return;
            if (backgroundRecognizer != null) backgroundRecognizer.destroy();
            backgroundRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            backgroundRecognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle params) { }
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float rmsdB) { }
                public void onBufferReceived(byte[] buffer) { }
                public void onEndOfSpeech() { }
                public void onPartialResults(Bundle results) { }
                public void onEvent(int type, Bundle params) { }
                public void onError(int error) {
                    if (!continuousVoice) return;
                    long delay = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1600 : 700;
                    aiHandler.postDelayed(LiyaAccessibilityService.this::listenInBackground, delay);
                }
                public void onResults(Bundle results) {
                    ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (heard == null || heard.isEmpty()) { listenInBackground(); return; }
                    handleBackgroundCommand(heard.get(0));
                }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
            try { backgroundRecognizer.startListening(intent); }
            catch (Exception error) { aiHandler.postDelayed(this::listenInBackground, 1200); }
        }, 250);
    }

    private void handleBackgroundCommand(String command) {
        if (PersonalModeActivity.dispatchOfflineVoiceCommand(command)) return;
        if (FullscreenLiyaActivity.dispatchOfflineVoiceCommand(command)) return;
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.contains("перестань слушать") || lower.contains("выключи голос")) {
            continuousVoice = false;
            speakBackground("Голосовое управление выключено.", false);
            return;
        }
        String answer = executeVoiceCommand(command);
        if (answer.startsWith("Эту команду я пока не умею")) {
            executeAiCommand(command, result -> speakBackground(result, true));
        } else {
            speakBackground(answer, true);
        }
    }

    private void speakBackground(String text, boolean resume) {
        if (offlineVoice != null) offlineVoice.pause(true);
        LiyaVoice.speak(this, backgroundTts, text, "liya_background", () -> {
            if (resume && continuousVoice && offlineVoice != null) offlineVoice.pause(false);
        });
    }

    public String executeVoiceCommand(String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.toLowerCase(new Locale("ru", "RU")).trim();
        command = command.replaceFirst("^(лия|лили)[, ]+", "");

        if (command.equals("стоп") || command.contains("остановись") || command.contains("прекрати")) {
            stopAiTask();
            return "Остановилась. Больше ничего не нажимаю.";
        }
        if (command.contains("на весь экран") || command.contains("покажись полностью")) {
            Intent full = new Intent(this, FullscreenLiyaActivity.class);
            full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(full);
            return "Открываюсь на весь экран.";
        }
        if (command.contains("свернись") || command.contains("маленький режим")) {
            sendBroadcast(new Intent(FullscreenLiyaActivity.ACTION_CLOSE).setPackage(getPackageName()));
            return "Сворачиваюсь в маленькую кнопку.";
        }

        String opened = openRequestedApp(command);
        if (opened != null) return opened;

        String requestedItem = extractRequestedItem(command);
        if (!requestedItem.isEmpty()) {
            return clickByText(requestedItem)
                ? "Нашла «" + requestedItem + "» и открываю на экране."
                : "На этой странице не вижу пункта «" + requestedItem + "». Скажите: прочитай экран.";
        }
        if (command.contains("прочитай") || command.contains("что на экране")) {
            String text = collectScreenText();
            return text.isEmpty() ? "На экране нет доступного для чтения текста." : text;
        }
        if (command.equals("назад") || command.contains("вернись назад")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return "Возвращаюсь назад.";
        }
        if (command.equals("домой") || command.contains("главный экран")) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            return "Открываю главный экран.";
        }
        if (command.contains("прокрути вниз") || command.contains("листай вниз")) {
            return scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ? "Прокручиваю вниз." : "Здесь не получилось прокрутить вниз.";
        }
        if (command.contains("прокрути вверх") || command.contains("листай вверх")) {
            return scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ? "Прокручиваю вверх." : "Здесь не получилось прокрутить вверх.";
        }

        String input = extractInputText(command);
        if (!input.isEmpty()) {
            return setTextInFocusedField(input)
                ? "Ввожу продиктованный текст."
                : "Сначала откройте поле для текста.";
        }

        String target = extractTarget(command);
        if (!target.isEmpty()) {
            return clickByText(target) ? "Нажимаю «" + target + "»." : "Не нашла на экране кнопку «" + target + "».";
        }
        return "Эту команду я пока не умею выполнять. Скажите: открой приложение, прочитай экран, нажми, введи текст, прокрути, назад или домой.";
    }

    public void executeAiCommand(String command, Consumer<String> callback) {
        executeAiCommand(command, false, callback);
    }

    public void executeAiCommand(String command, boolean approved, Consumer<String> callback) {
        aiCancelled = false;
        activeAiTask = command;
        activeAiApproved = approved;
        aiStep = 0;
        previousAiScreen = "";
        lastAiResult = "Начало задачи";
        unchangedAiSteps = 0;
        aiScreenVisits.clear();
        lastAiActionSignature = "";
        repeatedAiActionCount = 0;
        requestNextAiStep(callback);
    }

    private void requestNextAiStep(Consumer<String> callback) {
        if (aiCancelled) {
            callback.accept("Задача остановлена.");
            return;
        }
        if (aiStep >= MAX_AI_STEPS) {
            activeAiTask = "";
            callback.accept("Я выполнила много шагов, но не смогла надёжно завершить задачу. Остановилась, чтобы не нажать лишнее.");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String packageName = root != null && root.getPackageName() != null ? root.getPackageName().toString() : "";
        if (isSensitiveScreen(packageName)) {
            activeAiTask = "";
            callback.accept("На экране пароли или защита аккаунта. Здесь я ничего не передаю и жду ваших команд.");
            return;
        }
        String currentScreen = collectScreenText();
        String normalizedCurrent = normalizeScreen(currentScreen);
        int visits = aiScreenVisits.getOrDefault(normalizedCurrent, 0) + 1;
        aiScreenVisits.put(normalizedCurrent, visits);
        if (!normalizedCurrent.isEmpty() && visits >= 3) {
            activeAiTask = "";
            callback.accept("Я вернулась на тот же экран несколько раз и остановилась, чтобы не ходить по кругу. Нужен другой путь или ваше уточнение.");
            return;
        }
        if (!previousAiScreen.isEmpty() && normalizeScreen(previousAiScreen).equals(normalizeScreen(currentScreen))) unchangedAiSteps++;
        else unchangedAiSteps = 0;
        if (unchangedAiSteps >= 3) lastAiResult = "Экран не изменился после нескольких попыток. Выбери другой путь, прокрутку или кнопку.";
        String memory = getSharedPreferences("liya_agent_memory", MODE_PRIVATE).getString("last_success", "");
        aiStep++;
        LiyaAiClient.request(activeAiTask, packageName, currentScreen, previousAiScreen, lastAiResult, memory, aiStep, activeAiApproved, action -> {
            String signature = action.name + "|" + action.target + "|" + action.text;
            if (signature.equals(lastAiActionSignature) && unchangedAiSteps > 0) repeatedAiActionCount++;
            else repeatedAiActionCount = 0;
            lastAiActionSignature = signature;
            if (repeatedAiActionCount >= 1) {
                activeAiTask = "";
                callback.accept("Эта кнопка не сработала. Я не буду нажимать её снова и снова; остановилась на текущем экране.");
                return;
            }
            String result = executeAiAction(action);
            previousAiScreen = currentScreen;
            lastAiResult = result;
            if (isTerminalAiAction(action.name) || aiCancelled) {
                if ("done".equals(action.name)) rememberSuccessfulScenario(packageName, result);
                activeAiTask = "";
                callback.accept(result);
                return;
            }
            aiHandler.postDelayed(() -> requestNextAiStep(callback), action.name.startsWith("scroll") ? 450 : 280);
        }, error -> {
            activeAiTask = "";
            callback.accept(error);
        });
    }

    private boolean isTerminalAiAction(String action) {
        return "done".equals(action) || "ask_confirmation".equals(action) || "speak".equals(action);
    }

    private void stopAiTask() {
        aiCancelled = true;
        activeAiTask = "";
        activeAiApproved = false;
        // Do not remove the remote polling and voice callbacks owned by this handler.
    }

    private String normalizeScreen(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private void rememberSuccessfulScenario(String packageName, String result) {
        String record = "Задача: " + activeAiTask + "; приложение: " + packageName + "; результат: " + result;
        getSharedPreferences("liya_agent_memory", MODE_PRIVATE).edit().putString("last_success", record).apply();
    }

    private String executeAiAction(LiyaAiClient.Action action) {
        switch (action.name) {
            case "click": return clickByText(action.target) ? action.explanation : "Не нашла кнопку «" + action.target + "».";
            case "type": return setTextInFocusedField(action.text) ? action.explanation : "Не вижу активного поля для текста.";
            case "scroll_down": scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); return action.explanation;
            case "scroll_up": scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); return action.explanation;
            case "back": performGlobalAction(GLOBAL_ACTION_BACK); return action.explanation;
            case "home": performGlobalAction(GLOBAL_ACTION_HOME); return action.explanation;
            case "ask_confirmation": return "Нужно ваше подтверждение: " + action.explanation;
            case "done": return action.explanation;
            default: return action.text.isEmpty() ? action.explanation : action.text;
        }
    }

    private boolean isSensitiveScreen(String packageName) {
        String text = collectScreenText().toLowerCase(Locale.ROOT);
        return text.contains("парол") || text.contains("password") || text.contains("pin-код") || text.contains("пин-код")
            || text.contains("номер карты") || text.contains("card number") || text.contains("одноразовый код")
            || packageName.contains("credentialmanager");
    }

    private String openRequestedApp(String command) {
        if (containsAny(command, "мои пароли", "сохраненные пароли", "сохранённые пароли", "менеджер паролей", "google password")) {
            return openUrl("https://passwords.google.com/", "сохранённые пароли Google");
        }
        String googleQuery = extractGoogleQuery(command);
        if (!googleQuery.isEmpty()) {
            return openUrl("https://www.google.com/search?q=" + Uri.encode(googleQuery), "результаты поиска Google");
        }
        if (!command.contains("открой") && !command.contains("зайди") && !command.contains("запусти")) return null;
        if (containsAny(command, "ватсап бизнес", "whatsapp business")) return launchFirst(new String[]{"com.whatsapp.w4b", "com.whatsapp"}, "https://www.whatsapp.com/business/", "WhatsApp Business");
        if (containsAny(command, "ватсап", "вацап", "whatsapp")) return launchFirst(new String[]{"com.whatsapp", "com.whatsapp.w4b"}, "https://www.whatsapp.com", "WhatsApp");
        if (containsAny(command, "ютуб", "youtube")) return launch("com.google.android.youtube", "https://www.youtube.com", "YouTube");
        if (containsAny(command, "фейсбук", "facebook")) return launch("com.facebook.katana", "https://www.facebook.com", "Facebook");
        if (containsAny(command, "инстаграм", "instagram")) return launch("com.instagram.android", "https://www.instagram.com", "Instagram");
        if (containsAny(command, "тему", "temu", "тмо")) return launch("com.einnovation.temu", "https://www.temu.com", "Temu");
        if (containsAny(command, "алиэкспресс", "али экспресс", "aliexpress")) return launch("com.alibaba.aliexpresshd", "https://www.aliexpress.com", "AliExpress");
        if (containsAny(command, "телеграм", "telegram", "мастер пик", "masterpick")) return launchFirst(new String[]{"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"}, containsAny(command, "мастер пик", "masterpick") ? "https://t.me/masterpick_georgia" : "tg://resolve", containsAny(command, "мастер пик", "masterpick") ? "MasterPick в Telegram" : "Telegram");
        if (containsAny(command, "стройго", "строиго", "stroigou")) return openUrl("https://stroigou.com/#partners", "StroiGo");
        if (containsAny(command, "аккаунт google", "аккаунт гугл", "гугл аккаунт", "мой google", "мой гугл")) {
            return openUrl("https://myaccount.google.com/", "аккаунт Google");
        }
        if (containsAny(command, "google", "гугл", "chrome", "хром", "браузер")) {
            return launchFirst(new String[]{"com.android.chrome", "com.google.android.googlequicksearchbox"}, "https://www.google.com/", "Google");
        }
        if (containsAny(command, "настройки google", "настройки гугл", "аккаунты телефона")) {
            Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return "Открываю аккаунты в настройках телефона.";
        }
        return null;
    }

    private String extractGoogleQuery(String command) {
        String[] prefixes = {"найди в google ", "найди в гугле ", "поищи в google ", "поищи в гугле ", "google найди ", "гугл найди ", "поиск в google ", "поиск в гугле "};
        for (String prefix : prefixes) {
            int index = command.indexOf(prefix);
            if (index >= 0) return command.substring(index + prefix.length()).trim();
        }
        return "";
    }

    private String extractRequestedItem(String command) {
        if (!command.contains("найди")) return "";
        int start = command.indexOf("найди") + "найди".length();
        String target = command.substring(start)
            .replace("пожалуйста", "")
            .replace("и покажи на экране", "")
            .replace("и покажи", "")
            .replace("покажи", "")
            .trim();
        return target;
    }

    private String launch(String packageName, String fallbackUrl, String label) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return openUrl(fallbackUrl, label);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return "Открываю " + label + ".";
    }

    private String launchFirst(String[] packageNames, String fallbackUrl, String label) {
        for (String packageName : packageNames) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return "Открываю " + label + ".";
            }
        }
        return openUrl(fallbackUrl, label);
    }

    private String openUrl(String url, String label) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return "Открываю " + label + ".";
    }

    private boolean containsAny(String value, String... variants) {
        for (String variant : variants) if (value.contains(variant)) return true;
        return false;
    }

    private String extractInputText(String command) {
        String[] prefixes = {"введи текст ", "напиши текст ", "введи ", "напечатай ", "напиши "};
        for (String prefix : prefixes) {
            int index = command.indexOf(prefix);
            if (index >= 0) return command.substring(index + prefix.length()).replaceFirst("^[:—-]+\\s*", "").trim();
        }
        return "";
    }

    private boolean setTextInFocusedField(String value) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null || !field.isEditable()) field = findEditable(root);
        if (field == null) return false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findEditable(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String extractTarget(String command) {
        String[] prefixes = {"нажми на ", "нажать на ", "нажми ", "открой ", "выбери "};
        for (String prefix : prefixes) {
            int index = command.indexOf(prefix);
            if (index >= 0) return command.substring(index + prefix.length()).trim();
        }
        return "";
    }

    private boolean clickByText(String target) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(target);
        for (AccessibilityNodeInfo node : matches) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    private boolean scroll(int action) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        return scrollNode(root, action);
    }

    private boolean scrollNode(AccessibilityNodeInfo node, int action) {
        if (node.isScrollable() && node.performAction(action)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && scrollNode(child, action)) return true;
        }
        return false;
    }

    private String collectScreenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        collect(root, parts);
        String joined = String.join(". ", parts);
        return joined.length() > 1800 ? joined.substring(0, 1800) + ". На экране есть ещё текст." : joined;
    }

    private void collect(AccessibilityNodeInfo node, List<String> parts) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        addUnique(parts, text);
        addUnique(parts, description);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collect(child, parts);
        }
    }

    private void addUnique(List<String> parts, CharSequence value) {
        if (value == null) return;
        String clean = value.toString().trim();
        if (!clean.isEmpty() && !parts.contains(clean)) parts.add(clean);
    }

    private void showFloatingButton() {
        if (floatingButton != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingButton = new TextView(this);
        floatingButton.setText("Л");
        floatingButton.setTextSize(24);
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setGravity(Gravity.CENTER);
        floatingButton.setBackgroundColor(Color.rgb(37, 99, 235));
        floatingButton.setContentDescription("Открыть Лию");
        floatingButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("listen_now", true);
            startActivity(intent);
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dp(58),
            dp(58),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        params.x = dp(6);
        windowManager.addView(floatingButton, params);
    }

    @Override
    public void onDestroy() {
        stopAiTask();
        stopContinuousVoice();
        if (backgroundTts != null) backgroundTts.shutdown();
        if (floatingButton != null && windowManager != null) windowManager.removeView(floatingButton);
        floatingButton = null;
        instance = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
