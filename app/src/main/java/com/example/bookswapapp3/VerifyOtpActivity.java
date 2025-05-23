package com.example.bookswapapp3;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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
    private EditText otpInput;
    private Button verifyButton;
    private TextView messageText;
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

        messageText = findViewById(R.id.messageText);
        otpInput = findViewById(R.id.otpInput);
        verifyButton = findViewById(R.id.verifyButton);

        messageText.setText("Enter the OTP sent to your phone");

        verifyButton.setOnClickListener(v -> {
            String otp = otpInput.getText().toString().trim();
            if (otp.isEmpty() || otp.length() != 6) {
                Toast.makeText(this, "Enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show();
            } else {
                verifyOtp(otp);
            }
        });
    }

    private void verifyOtp(String otp) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
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
                .putBoolean("isLoggedIn", true)  // Added login flag
                .putString("phoneNumber", phoneNumber)
                .apply();

        // Clear back stack and launch HomeActivity
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, SendOtpActivity.class);
        intent.putExtra("phoneNumber", phoneNumber.replace("+91", ""));
        startActivity(intent);
        finish();
    }
}