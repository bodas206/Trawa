package com.patrix.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 401;
    private static final String VALIDATE_URL = "https://gnoiwuhdjyilqycfoorh.supabase.co/functions/v1/patrix-validate-key";
    private static final String TELEGRAM_URL = "https://web.telegram.org/k/#@Just_AbDoS";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout loginPanel, mainPanel;
    private EditText keyInput;
    private TextView loginStatus, deviceValue, keyValue, expiryValue, mainStatus;
    private String validatedKey;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("patrix", MODE_PRIVATE);
        bindViews();
        findViewById(R.id.login_button).setOnClickListener(v -> validateKey());
        findViewById(R.id.telegram_button).setOnClickListener(v -> openTelegram());
        findViewById(R.id.start_button).setOnClickListener(v -> requestVpn());
        String saved = prefs.getString("key", "");
        if (!saved.isEmpty()) keyInput.setText(saved);
    }

    private void bindViews() {
        loginPanel = findViewById(R.id.login_panel);
        mainPanel = findViewById(R.id.main_panel);
        keyInput = findViewById(R.id.key_input);
        loginStatus = findViewById(R.id.login_status);
        deviceValue = findViewById(R.id.device_value);
        keyValue = findViewById(R.id.key_value);
        expiryValue = findViewById(R.id.expiry_value);
        mainStatus = findViewById(R.id.main_status);
    }

    private String deviceId() {
        String existing = prefs.getString("device_id", null);
        if (existing != null) return existing;
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        prefs.edit().putString("device_id", id).apply();
        return id;
    }

    private void validateKey() {
        final String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) { loginStatus.setText("أدخل مفتاح الوصول أولًا"); return; }
        loginStatus.setText(R.string.validating);
        findViewById(R.id.login_button).setEnabled(false);
        network.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(VALIDATE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject();
                body.put("key", key);
                body.put("device_id", deviceId());
                body.put("app_version", "1.0.0");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode();
                InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String response = read(stream);
                JSONObject data = new JSONObject(response == null ? "{}" : response);
                if (status >= 200 && status < 300 && data.optBoolean("ok", false)) {
                    prefs.edit().putString("key", key).apply();
                    validatedKey = key;
                    runOnUiThread(() -> showMain(data));
                } else {
                    String error = data.optString("error", getString(R.string.invalid_key));
                    runOnUiThread(() -> { loginStatus.setText(error); findViewById(R.id.login_button).setEnabled(true); });
                }
            } catch (Exception ex) {
                runOnUiThread(() -> { loginStatus.setText("تعذر الاتصال بالخادم عبر HTTPS"); findViewById(R.id.login_button).setEnabled(true); });
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private void showMain(JSONObject data) {
        loginPanel.setVisibility(View.GONE);
        mainPanel.setVisibility(View.VISIBLE);
        String id = deviceId();
        deviceValue.setText(id);
        keyValue.setText(mask(validatedKey));
        expiryValue.setText(data.optString("expires_at", "غير محدد"));
        mainStatus.setText("تم التحقق من المفتاح عبر HTTPS");
    }

    private String mask(String key) {
        if (key == null || key.length() < 8) return "••••••••";
        return key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4);
    }

    private void openTelegram() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))); }
        catch (Exception e) { Toast.makeText(this, "افتح رابط Telegram يدويًا", Toast.LENGTH_SHORT).show(); }
    }

    private void requestVpn() {
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) startActivityForResult(prepare, VPN_REQUEST);
        else onActivityResult(VPN_REQUEST, RESULT_OK, null);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VPN_REQUEST) return;
        if (resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, PatrixVpnService.class));
            else startService(new Intent(this, PatrixVpnService.class));
            mainStatus.setText("تم منح صلاحية VPN — طبقة الاتصال جاهزة");
        } else mainStatus.setText("لم يتم منح صلاحية VPN");
    }

    @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
}
