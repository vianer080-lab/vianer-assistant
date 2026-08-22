package com.vianerapps.liya;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class LiyaAccessibilityService extends AccessibilityService {
    private static LiyaAccessibilityService instance;
    private WindowManager windowManager;
    private TextView floatingButton;

    public static LiyaAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        showFloatingButton();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    public String executeVoiceCommand(String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.toLowerCase(new Locale("ru", "RU")).trim();
        command = command.replaceFirst("^(лия|лили)[, ]+", "");

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
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String packageName = root != null && root.getPackageName() != null ? root.getPackageName().toString() : "";
        if (isSensitiveScreen(packageName)) {
            callback.accept("На экране пароли или защита аккаунта. Здесь я ничего не передаю и жду ваших команд.");
            return;
        }
        LiyaAiClient.request(command, packageName, collectScreenText(), action -> callback.accept(executeAiAction(action)), callback);
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
        if (!command.contains("открой") && !command.contains("зайди") && !command.contains("запусти")) return null;
        if (containsAny(command, "ватсап", "вацап", "whatsapp")) return launch("com.whatsapp", "https://www.whatsapp.com", "WhatsApp");
        if (containsAny(command, "ютуб", "youtube")) return launch("com.google.android.youtube", "https://www.youtube.com", "YouTube");
        if (containsAny(command, "фейсбук", "facebook")) return launch("com.facebook.katana", "https://www.facebook.com", "Facebook");
        if (containsAny(command, "инстаграм", "instagram")) return launch("com.instagram.android", "https://www.instagram.com", "Instagram");
        if (containsAny(command, "тему", "temu", "тмо")) return launch("com.einnovation.temu", "https://www.temu.com", "Temu");
        if (containsAny(command, "алиэкспресс", "али экспресс", "aliexpress")) return launch("com.alibaba.aliexpresshd", "https://www.aliexpress.com", "AliExpress");
        if (containsAny(command, "телеграм", "telegram", "мастер пик", "masterpick")) return launch("org.telegram.messenger", "https://t.me/masterpick_georgia", "MasterPick в Telegram");
        if (containsAny(command, "стройго", "строиго", "stroigou")) return openUrl("https://stroigou.com/#partners", "StroiGo");
        if (containsAny(command, "аккаунт google", "аккаунт гугл", "гугл аккаунт", "мой google", "мой гугл")) {
            return openUrl("https://myaccount.google.com/", "аккаунт Google");
        }
        if (containsAny(command, "настройки google", "настройки гугл", "аккаунты телефона")) {
            Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return "Открываю аккаунты в настройках телефона.";
        }
        return null;
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
        if (floatingButton != null && windowManager != null) windowManager.removeView(floatingButton);
        floatingButton = null;
        instance = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
