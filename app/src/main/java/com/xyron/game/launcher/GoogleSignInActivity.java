package com.xyron.game.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.xyron.game.BuildConfig;
import com.xyron.game.launcher.util.NativeGoogleAuthApi;

import java.io.IOException;
import java.security.SecureRandom;

public final class GoogleSignInActivity extends AppCompatActivity {
    public static final String EXTRA_LOGIN_NAME = "google_login_name";

    private boolean completed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        beginSignIn();
    }

    private void beginSignIn() {
        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (webClientId == null || webClientId.trim().isEmpty()) {
            finishWithError("Login Google belum dikonfigurasi untuk aplikasi ini.");
            return;
        }

        try {
            byte[] nonceBytes = new byte[32];
            new SecureRandom().nextBytes(nonceBytes);
            String nonce = Base64.encodeToString(
                    nonceBytes,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );

            GetSignInWithGoogleOption googleOption = new GetSignInWithGoogleOption.Builder(
                    webClientId.trim()
            ).setNonce(nonce).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build();

            CredentialManager.create(this).getCredentialAsync(
                    this,
                    request,
                    null,
                    ContextCompat.getMainExecutor(this),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse response) {
                            Credential credential = response.getCredential();
                            if (!(credential instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(credential.getType())) {
                                finishWithError("Google tidak mengembalikan kredensial login yang valid.");
                                return;
                            }

                            try {
                                GoogleIdTokenCredential googleCredential =
                                        GoogleIdTokenCredential.createFrom(
                                                ((CustomCredential) credential).getData()
                                        );
                                exchangeGoogleToken(googleCredential.getIdToken(), nonce);
                            } catch (RuntimeException e) {
                                finishWithError("Tidak dapat membaca hasil login Google. Coba lagi.");
                            }
                        }

                        @Override
                        public void onError(GetCredentialException error) {
                            finishWithError("Login Google dibatalkan atau gagal. Silakan coba lagi.");
                        }
                    }
            );
        } catch (RuntimeException e) {
            finishWithError("Login Google tidak dapat dimulai. Silakan coba lagi.");
        }
    }

    private void exchangeGoogleToken(String idToken, String nonce) {
        Toast.makeText(this, "Memverifikasi akun Google…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NativeGoogleAuthApi.LoginTicket ticket =
                        NativeGoogleAuthApi.exchangeIdToken(idToken, nonce);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || completed) {
                        return;
                    }
                    completed = true;
                    Intent result = new Intent();
                    result.putExtra(EXTRA_LOGIN_NAME, ticket.loginName);
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (NativeGoogleAuthApi.AuthException e) {
                runOnUiThread(() -> finishWithError(e.getMessage()));
            } catch (IOException e) {
                runOnUiThread(() -> finishWithError(
                        "Tidak dapat menghubungi layanan login. Periksa koneksi lalu coba lagi."
                ));
            }
        }, "xyron-google-auth").start();
    }

    private void finishWithError(String message) {
        if (completed || isFinishing()) {
            return;
        }
        completed = true;
        setResult(RESULT_CANCELED);
        if (message != null && !message.trim().isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
