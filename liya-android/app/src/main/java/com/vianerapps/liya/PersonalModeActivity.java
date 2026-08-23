package com.vianerapps.liya;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executor;

public class PersonalModeActivity extends Activity {
    private static final String PREFS = "liya_personal_private";
    private static final String PIN_HASH = "pin_hash";
    private SharedPreferences prefs;
    private boolean unlocked;
    private int hairstyle;
    private int outfit;
    private TextView selection;

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

        TextView profile = text("Личный образ\nВзрослая Лия · около 30 лет\nСтройная худощавая фигура\nОтдельный закрытый гардероб", 18, Color.WHITE);
        profile.setPadding(0, 0, 0, dp(18));
        content.addView(profile);

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
            outfit = outfit % 5 + 1;
            prefs.edit().putInt("outfit", outfit).apply();
            refreshSelection();
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
            "Личная память хранится только на телефоне. Лия сможет запоминать выбранные причёски, образы и будущие сценарии движений. Основной режим и защита приложения не изменяются.",
            16, Color.rgb(186, 205, 230)
        );
        memory.setPadding(0, 0, 0, dp(18));
        content.addView(memory);

        content.addView(button("ЗАБЛОКИРОВАТЬ", v -> {
            unlocked = false;
            showUnlock();
        }));
        setContent(content);
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
            unlocked = false;
            finish();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
