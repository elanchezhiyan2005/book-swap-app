package com.example.bookswapapp3;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class SendOtpActivity extends AppCompatActivity {
    private EditText phoneInput;
    private Button sendOtpButton;
    private ImageButton helpButton;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_otp);

        mAuth = FirebaseAuth.getInstance();
        phoneInput = findViewById(R.id.phoneInput);
        sendOtpButton = findViewById(R.id.sendOtpButton);
        helpButton = findViewById(R.id.helpButton);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Send OTP button listener
        sendOtpButton.setOnClickListener(v -> {
            String localPhoneNumber = phoneInput.getText().toString().trim();
            if (localPhoneNumber.isEmpty() || localPhoneNumber.length() < 10) {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
            } else {
                String fullPhoneNumber = "+91" + localPhoneNumber;
                sendOtp(fullPhoneNumber);
            }
        });

        // Help button listener
        helpButton.setOnClickListener(v -> {
            Toast.makeText(this, "Need help? Contact support at support@bibliobant.com", Toast.LENGTH_LONG).show();
        });
    }

    private void sendOtp(String phoneNumber) {
        progressBar.setVisibility(View.VISIBLE);
        sendOtpButton.setEnabled(false);

        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                TimeUnit.SECONDS,
                this,
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        mAuth.signInWithCredential(credential)
                                .addOnCompleteListener(task -> {
                                    progressBar.setVisibility(View.GONE);
                                    sendOtpButton.setEnabled(true);
                                    if (task.isSuccessful()) {
                                        Intent intent = new Intent(SendOtpActivity.this, HomeActivity.class);
                                        intent.putExtra("phoneNumber", phoneNumber);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        Toast.makeText(SendOtpActivity.this, "Auto-verification failed.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        progressBar.setVisibility(View.GONE);
                        sendOtpButton.setEnabled(true);
                        Toast.makeText(SendOtpActivity.this, "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        progressBar.setVisibility(View.GONE);
                        sendOtpButton.setEnabled(true);
                        Intent intent = new Intent(SendOtpActivity.this, VerifyOtpActivity.class);
                        intent.putExtra("verificationId", verificationId);
                        intent.putExtra("phoneNumber", phoneNumber);
                        startActivity(intent);
                    }
                }
        );
    }
}