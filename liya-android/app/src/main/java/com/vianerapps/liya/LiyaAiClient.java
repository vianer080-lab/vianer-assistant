package com.vianerapps.liya;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class LiyaAiClient {
    public static final class Action {
        public final String name;
        public final String target;
        public final String text;
        public final String explanation;

        Action(JSONObject json) {
            name = json.optString("action");
            target = json.optString("target");
            text = json.optString("text");
            explanation = json.optString("explanation", "Выполняю следующий шаг.");
        }
    }

    public static void request(String command, String packageName, String screen, Consumer<Action> success, Consumer<String> failure) {
        request(command, packageName, screen, "", "", "", 0, false, success, failure);
    }

    public static void request(String command, String packageName, String screen, String previousScreen,
                               String lastResult, String memory, int step, boolean approved,
                               Consumer<Action> success, Consumer<String> failure) {
        if (BuildConfig.LIYA_API_URL.isEmpty() || BuildConfig.LIYA_DEVICE_TOKEN.isEmpty()) {
            failure.accept("Связь со мной ещё не активирована на сервере.");
            return;
        }
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.LIYA_API_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.LIYA_DEVICE_TOKEN);
                connection.setDoOutput(true);
                JSONObject payload = new JSONObject();
                payload.put("command", command);
                payload.put("packageName", packageName);
                payload.put("screen", screen);
                payload.put("previousScreen", previousScreen);
                payload.put("lastResult", lastResult);
                payload.put("memory", memory);
                payload.put("step", step);
                payload.put("approved", approved);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new Exception("HTTP " + connection.getResponseCode());
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                Action action = new Action(new JSONObject(response.toString()));
                new Handler(Looper.getMainLooper()).post(() -> success.accept(action));
            } catch (Exception error) {
                new Handler(Looper.getMainLooper()).post(() -> failure.accept("Не удалось связаться со мной. Проверьте интернет."));
            }
        }).start();
    }

    private LiyaAiClient() { }
}
