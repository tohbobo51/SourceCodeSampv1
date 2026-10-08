package com.xyron.game.launcher.util;

import com.xyron.game.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Exchanges a Google ID token for a short-lived, single-use game login ticket. */
public final class NativeGoogleAuthApi {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private NativeGoogleAuthApi() {
    }

    public static LoginTicket exchangeIdToken(String idToken, String nonce)
            throws IOException, AuthException {
        if (idToken == null || idToken.length() < 100 || idToken.length() > 8192) {
            throw new AuthException("INVALID_TOKEN", "Token Google tidak valid.");
        }
        if (nonce == null || nonce.length() < 16 || nonce.length() > 128) {
            throw new AuthException("INVALID_NONCE", "Sesi login tidak valid. Coba lagi.");
        }

        String baseUrl = BuildConfig.AUTH_API_BASE_URL == null
                ? "" : BuildConfig.AUTH_API_BASE_URL.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.toLowerCase(Locale.US).startsWith("https://")) {
            throw new IOException("Authentication API must use HTTPS.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "/auth/google/mobile");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject request = new JSONObject();
            request.put("idToken", idToken);
            request.put("nonce", nonce);
            byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = responseStream == null ? "" : readLimited(responseStream);
            JSONObject response;
            try {
                response = responseBody.isEmpty() ? new JSONObject() : new JSONObject(responseBody);
            } catch (JSONException ignored) {
                response = new JSONObject();
            }

            if (status != HttpURLConnection.HTTP_OK) {
                String code = response.optString("code", "AUTH_SERVICE_UNAVAILABLE");
                throw new AuthException(code, messageFor(code));
            }

            String loginName = response.optString("loginName", "");
            if (!loginName.matches("AUTH[A-HJ-NP-Z2-9]{16}")) {
                throw new AuthException("INVALID_SERVER_RESPONSE",
                        "Server mengembalikan sesi login yang tidak valid.");
            }
            return new LoginTicket(loginName, response.optString("characterName", ""));
        } catch (JSONException e) {
            throw new IOException("Could not encode authentication request.", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Authentication response exceeded size limit.");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String messageFor(String code) {
        switch (code) {
            case "ACCOUNT_NOT_FOUND":
                return "Akun Google belum terdaftar di server. Hubungi admin server.";
            case "CHARACTER_NOT_FOUND":
                return "Akun belum memiliki karakter. Pendaftaran karakter belum tersedia di launcher.";
            case "GOOGLE_TOKEN_REPLAYED":
                return "Sesi Google sudah digunakan. Ulangi login.";
            case "RATE_LIMITED":
                return "Terlalu banyak percobaan login. Tunggu sebentar lalu coba lagi.";
            case "GOOGLE_CLIENT_NOT_CONFIGURED":
            case "DATABASE_NOT_CONFIGURED":
                return "Layanan login server belum dikonfigurasi.";
            case "INVALID_GOOGLE_TOKEN":
            case "INVALID_NONCE":
                return "Google tidak dapat memverifikasi sesi ini. Coba login ulang.";
            default:
                return "Layanan login tidak tersedia saat ini. Coba lagi nanti.";
        }
    }

    public static final class LoginTicket {
        public final String loginName;
        public final String characterName;

        private LoginTicket(String loginName, String characterName) {
            this.loginName = loginName;
            this.characterName = characterName;
        }
    }

    public static final class AuthException extends Exception {
        public final String code;

        private AuthException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
