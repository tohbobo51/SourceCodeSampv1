package com.xyron.game.launcher;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.text.InputFilter;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GoogleSignInActivity extends AppCompatActivity {
    public static final String EXTRA_LOGIN_NAME = "google_login_name";

    private static final Pattern UCP_NAME_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,29}[A-Za-z0-9]$");
    private static final Pattern CHARACTER_NAME_PATTERN =
            Pattern.compile("^[A-Za-z]{2,12}_[A-Za-z]{2,12}$");
    private static final Pattern BIRTHPLACE_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z .,'-]{1,62}$");

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
                runOnUiThread(() -> finishWithTicket(ticket));
            } catch (NativeGoogleAuthApi.AuthException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || completed) {
                        return;
                    }
                    if ("REGISTRATION_REQUIRED".equals(e.code)) {
                        showRegistrationForm(idToken, nonce, true);
                    } else if ("CHARACTER_REGISTRATION_REQUIRED".equals(e.code)) {
                        showRegistrationForm(idToken, nonce, false);
                    } else {
                        finishWithError(e.getMessage());
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> finishWithError(
                        "Tidak dapat menghubungi layanan login. Periksa koneksi lalu coba lagi."
                ));
            }
        }, "xyron-google-auth").start();
    }

    private void showRegistrationForm(String idToken, String nonce, boolean createAccount) {
        if (isFinishing() || isDestroyed() || completed) {
            return;
        }

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        fields.setPadding(padding, dp(8), padding, dp(8));

        TextView note = new TextView(this);
        note.setText("Isi data karakter roleplay. Tanggal dan tempat lahir di bawah adalah data karakter, bukan data akun Google. Tinggi/berat awal server: 175 cm / 70 kg.");
        note.setTextSize(14);
        fields.addView(note, matchWrap());

        EditText ucpName = createAccount
                ? addInput(fields, "Nama akun UCP (3–31 huruf/angka/underscore)", InputType.TYPE_CLASS_TEXT)
                : null;
        EditText characterName = addInput(fields, "Nama karakter: NamaDepan_NamaBelakang",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText birthplace = addInput(fields, "Tempat lahir karakter", InputType.TYPE_CLASS_TEXT);
        if (ucpName != null) {
            ucpName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(31)});
        }
        characterName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(23)});
        birthplace.setFilters(new InputFilter[]{new InputFilter.LengthFilter(63)});

        final String[] birthdate = {""};
        Button birthdateButton = new Button(this);
        birthdateButton.setAllCaps(false);
        birthdateButton.setText("Pilih tanggal lahir karakter");
        fields.addView(birthdateButton, matchWrap());
        birthdateButton.setOnClickListener(view -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog picker = new DatePickerDialog(
                    this,
                    (datePicker, year, month, day) -> {
                        birthdate[0] = String.format(Locale.US, "%04d-%02d-%02d",
                                year, month + 1, day);
                        birthdateButton.setText(birthdate[0]);
                    },
                    today.get(Calendar.YEAR) - 18,
                    today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH)
            );
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        Spinner gender = new Spinner(this);
        String[] genderOptions = {"Pilih gender karakter", "Laki-laki", "Perempuan"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, genderOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gender.setAdapter(adapter);
        fields.addView(gender, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(fields, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(createAccount ? "Daftar akun dan karakter" : "Daftar karakter")
                .setView(scroll)
                .setNegativeButton("Batal", (dialogInterface, which) -> finishWithError(null))
                .setPositiveButton("Daftar & masuk", null)
                .create();
        dialog.setOnCancelListener(dialogInterface -> finishWithError(null));
        dialog.show();

        Button submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        submit.setOnClickListener(view -> {
            String ucpValue = createAccount ? ucpName.getText().toString().trim() : null;
            String characterValue = characterName.getText().toString().trim();
            String birthplaceValue = birthplace.getText().toString().trim();
            boolean valid = true;

            if (createAccount && !UCP_NAME_PATTERN.matcher(ucpValue).matches()) {
                ucpName.setError("3–31 karakter; mulai dengan huruf dan akhiri dengan huruf/angka.");
                valid = false;
            }
            if (!CHARACTER_NAME_PATTERN.matcher(characterValue).matches()
                    || characterValue.length() > 23) {
                characterName.setError("Gunakan format NamaDepan_NamaBelakang (huruf Latin, maks. 23 karakter).");
                valid = false;
            }
            if (!BIRTHPLACE_PATTERN.matcher(birthplaceValue).matches()) {
                birthplace.setError("Isi tempat lahir (2–63 huruf, spasi, atau tanda baca sederhana).");
                valid = false;
            }
            if (birthdate[0].isEmpty()) {
                Toast.makeText(this, "Pilih tanggal lahir karakter.", Toast.LENGTH_SHORT).show();
                valid = false;
            }
            if (gender.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Pilih gender karakter.", Toast.LENGTH_SHORT).show();
                valid = false;
            }
            if (!valid) {
                return;
            }

            String genderValue = gender.getSelectedItemPosition() == 1 ? "Male" : "Female";
            NativeGoogleAuthApi.RegistrationData registration =
                    new NativeGoogleAuthApi.RegistrationData(
                            ucpValue, characterValue, birthplaceValue, birthdate[0], genderValue);
            submit.setEnabled(false);
            submit.setText("Memproses…");
            new Thread(() -> {
                try {
                    NativeGoogleAuthApi.LoginTicket ticket =
                            NativeGoogleAuthApi.registerIdToken(idToken, nonce, registration);
                    runOnUiThread(() -> {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        finishWithTicket(ticket);
                    });
                } catch (NativeGoogleAuthApi.AuthException e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || completed) {
                            return;
                        }
                        if (dialog.isShowing()) {
                            submit.setEnabled(true);
                            submit.setText("Daftar & masuk");
                        }
                        if ("UCP_NAME_TAKEN".equals(e.code) && ucpName != null) {
                            ucpName.setError(e.getMessage());
                        } else if ("CHARACTER_NAME_TAKEN".equals(e.code)) {
                            characterName.setError(e.getMessage());
                        } else if ("REGISTRATION_CONFLICT".equals(e.code)) {
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                        } else if ("INVALID_REGISTRATION".equals(e.code)) {
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                            finishWithError(e.getMessage());
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        finishWithError(
                                "Tidak dapat memastikan hasil pendaftaran. Silakan login Google lagi.");
                    });
                }
            }, "xyron-google-register").start();
        });
    }

    private EditText addInput(LinearLayout parent, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(inputType);
        parent.addView(input, matchWrap());
        return input;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void finishWithTicket(NativeGoogleAuthApi.LoginTicket ticket) {
        if (isFinishing() || isDestroyed() || completed) {
            return;
        }
        completed = true;
        Intent result = new Intent();
        result.putExtra(EXTRA_LOGIN_NAME, ticket.loginName);
        setResult(RESULT_OK, result);
        finish();
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
