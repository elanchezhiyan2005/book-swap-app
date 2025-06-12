package com.example.bookswapapp3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;

public class VerifyOtpActivity extends AppCompatActivity {
    private EditText otpInput1, otpInput2, otpInput3, otpInput4, otpInput5, otpInput6;
    private Button verifyButton;
    private ImageButton backButton;
    private TextView messageText, resendOtpText;
    private ProgressBar progressBar;
    private String verificationId;
    private String phoneNumber;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        verificationId = getIntent().getStringExtra("verificationId");
        phoneNumber = getIntent().getStringExtra("phoneNumber");

        // Initialize UI elements
        messageText = findViewById(R.id.messageText);
        otpInput1 = findViewById(R.id.otpInput1);
        otpInput2 = findViewById(R.id.otpInput2);
        otpInput3 = findViewById(R.id.otpInput3);
        otpInput4 = findViewById(R.id.otpInput4);
        otpInput5 = findViewById(R.id.otpInput5);
        otpInput6 = findViewById(R.id.otpInput6);
        verifyButton = findViewById(R.id.verifyButton);
        backButton = findViewById(R.id.backButton);
        resendOtpText = findViewById(R.id.resendOtpText);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Set phone number in message text
        messageText.setText("We sent a verification code to " + phoneNumber);

        // Set up auto-focusing for OTP fields
        setupOtpInputs();

        // Verify button listener
        verifyButton.setOnClickListener(v -> {
            String otp = getOtpFromInputs();
            if (otp.length() != 6) {
                Toast.makeText(this, "Enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show();
            } else {
                verifyOtp(otp);
            }
        });

        // Back button listener
        backButton.setOnClickListener(v -> onBackPressed());

        // Resend OTP listener
        resendOtpText.setOnClickListener(v -> {
            resendOtp();
        });
    }

    // Combine OTP digits from all fields
    private String getOtpFromInputs() {
        return otpInput1.getText().toString() +
                otpInput2.getText().toString() +
                otpInput3.getText().toString() +
                otpInput4.getText().toString() +
                otpInput5.getText().toString() +
                otpInput6.getText().toString();
    }

    // Set up auto-focusing and navigation for OTP fields
    private void setupOtpInputs() {
        EditText[] otpInputs = {otpInput1, otpInput2, otpInput3, otpInput4, otpInput5, otpInput6};

        for (int i = 0; i < otpInputs.length; i++) {
            final int index = i;
            EditText currentInput = otpInputs[i];

            // Auto-focus to next field on input
            currentInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < otpInputs.length - 1) {
                        otpInputs[index + 1].requestFocus();
                    }
                }
            });

            // Handle backspace and arrow key navigation
            currentInput.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (currentInput.getText().toString().isEmpty() && index > 0) {
                        otpInputs[index - 1].setText("");
                        otpInputs[index - 1].requestFocus();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (index > 0) {
                        otpInputs[index - 1].requestFocus();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (index < otpInputs.length - 1) {
                        otpInputs[index + 1].requestFocus();
                    }
                }
                return false;
            });
        }
    }

    private void verifyOtp(String otp) {
        progressBar.setVisibility(View.VISIBLE);
        verifyButton.setEnabled(false);
        resendOtpText.setEnabled(false);

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    verifyButton.setEnabled(true);
                    resendOtpText.setEnabled(true);

                    if (task.isSuccessful()) {
                        handleSuccessfulSignIn(phoneNumber);
                    } else {
                        Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleSuccessfulSignIn(String phoneNumber) {
        DocumentReference userRef = db.collection("users").document(phoneNumber);
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                userRef.set(new HashMap<String, Object>() {{
                    put("phoneNumber", phoneNumber);
                }}).addOnFailureListener(e -> {
                    Toast.makeText(VerifyOtpActivity.this, "Failed to save user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });

        // Save session state with both flag and phone number
        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isLoggedIn", true)
                .putString("phoneNumber", phoneNumber)
                .apply();

        // Clear back stack and launch HomeActivity
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void resendOtp() {
        progressBar.setVisibility(View.VISIBLE);
        resendOtpText.setEnabled(false);
        verifyButton.setEnabled(false);

        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                java.util.concurrent.TimeUnit.SECONDS,
                this,
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        // Auto-verification not expected here, but handle if it happens
                        signInWithPhoneAuthCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        progressBar.setVisibility(View.GONE);
                        resendOtpText.setEnabled(true);
                        verifyButton.setEnabled(true);
                        Toast.makeText(VerifyOtpActivity.this, "Failed to resend OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(String newVerificationId, PhoneAuthProvider.ForceResendingToken token) {
                        progressBar.setVisibility(View.GONE);
                        resendOtpText.setEnabled(true);
                        verifyButton.setEnabled(true);
                        verificationId = newVerificationId;
                        Toast.makeText(VerifyOtpActivity.this, "OTP resent successfully", Toast.LENGTH_SHORT).show();

                        // Clear OTP input fields
                        otpInput1.setText("");
                        otpInput2.setText("");
                        otpInput3.setText("");
                        otpInput4.setText("");
                        otpInput5.setText("");
                        otpInput6.setText("");
                        otpInput1.requestFocus();
                    }
                }
        );
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, SendOtpActivity.class);
        intent.putExtra("phoneNumber", phoneNumber.replace("+91", ""));
        startActivity(intent);
        finish();
    }
}