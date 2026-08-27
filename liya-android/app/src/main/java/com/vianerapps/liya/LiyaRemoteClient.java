package com.vianerapps.liya;

import android.content.Context;
import android.content.SharedPreferences;
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

final class LiyaRemoteClient {
    private static final String ENDPOINT = "https://ceozpugxrwgblkwtxiew.supabase.co/functions/v1/liya-device";
    private static final String PREFS = "liya_vianer_link";
    private static final String TOKEN = "device_token";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean pollInFlight;

    static final class Task {
        final long id;
        final String instruction;
        final String attachmentUrl;
        final String caption;
        final String targetPackage;
        final boolean approved;
        final boolean silent;

        Task(JSONObject json) {
            id = json.optLong("id");
            instruction = json.optString("instruction");
            attachmentUrl = json.optString("attachment_url");
            caption = json.optString("caption");
            targetPackage = json.optString("target_package");
            approved = json.optBoolean("approved", false);
            silent = json.optBoolean("silent", true);
        }
    }

    static boolean isPaired(Context context) { return !prefs(context).getString(TOKEN, "").isEmpty(); }

    static void pair(Context context, String code, Consumer<String> callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject().put("action", "register").put("code", code).put("device_name", "Samsung Лия");
                JSONObject response = post(body, "");
                String token = response.optString("device_token", "");
                if (token.isEmpty()) throw new Exception(response.optString("error", "Не удалось подключить устройство"));
                prefs(context).edit().putString(TOKEN, token).putString("device_id", response.optString("device_id", "")).apply();
                MAIN.post(() -> callback.accept("Лия подключена к Vianer Assistant."));
            } catch (Exception e) { MAIN.post(() -> callback.accept("Подключение не выполнено: " + e.getMessage())); }
        }).start();
    }

    static void poll(Context context, Consumer<Task> callback) {
        String token = prefs(context).getString(TOKEN, "");
        if (token.isEmpty() || pollInFlight) return;
        pollInFlight = true;
        new Thread(() -> {
            try {
                flushPendingReport(context, token);
                JSONObject response = post(new JSONObject().put("action", "poll").put("device_token", token), token);
                JSONObject task = response.optJSONObject("task");
                if (task != null) MAIN.post(() -> callback.accept(new Task(task)));
            } catch (Exception ignored) { }
            finally { pollInFlight = false; }
        }).start();
    }

    static void report(Context context, long taskId, String status, String result, String packageName, String screen,
                       Consumer<Boolean> callback) {
        String token = prefs(context).getString(TOKEN, "");
        if (token.isEmpty()) { callback.accept(false); return; }
        JSONObject payload = new JSONObject();
        try {
            payload.put("action", "report").put("device_token", token).put("task_id", taskId)
                .put("status", status).put("result", result).put("package_name", packageName).put("screen", screen);
        } catch (Exception ignored) { }
        prefs(context).edit().putString("pending_report", payload.toString()).apply();
        new Thread(() -> reportAttempt(context, token, payload, callback, 1)).start();
    }

    private static void reportAttempt(Context context, String token, JSONObject payload,
                                      Consumer<Boolean> callback, int attempt) {
        try {
            post(payload, token);
            prefs(context).edit().remove("pending_report").apply();
            MAIN.post(() -> callback.accept(true));
        } catch (Exception error) {
            if (attempt < 3) {
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                reportAttempt(context, token, payload, callback, attempt + 1);
            } else MAIN.post(() -> callback.accept(false));
        }
    }

    private static void flushPendingReport(Context context, String token) {
        String saved = prefs(context).getString("pending_report", "");
        if (saved.isEmpty()) return;
        try {
            post(new JSONObject(saved), token);
            prefs(context).edit().remove("pending_report").apply();
        } catch (Exception ignored) { }
    }

    private static JSONObject post(JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("X-Liya-Token", token);
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getResponseCode() < 400 ? connection.getInputStream() : connection.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(); String line;
        while ((line = reader.readLine()) != null) result.append(line);
        JSONObject json = new JSONObject(result.toString());
        if (connection.getResponseCode() >= 400) throw new Exception(json.optString("error", "Ошибка сервера"));
        return json;
    }

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
