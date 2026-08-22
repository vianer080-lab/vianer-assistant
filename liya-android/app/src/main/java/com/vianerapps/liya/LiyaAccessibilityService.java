package com.vianerapps.liya;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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

        String target = extractTarget(command);
        if (!target.isEmpty()) {
            return clickByText(target) ? "Нажимаю «" + target + "»." : "Не нашла на экране кнопку «" + target + "».";
        }
        return "Эту команду я пока не умею выполнять. Попробуйте сказать: прочитай экран, нажми, прокрути, назад или домой.";
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
