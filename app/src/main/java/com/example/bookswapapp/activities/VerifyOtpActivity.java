package com.example.bookswapapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bookswapapp.R;

public class VerifyOtpActivity extends AppCompatActivity {

    private EditText editTextOTP;
    private Button btnVerifyOTP;

    private String receivedOTP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);

        editTextOTP = findViewById(R.id.editTextOTP);
        btnVerifyOTP = findViewById(R.id.btnVerifyOTP);

        // Get OTP from Intent
        receivedOTP = getIntent().getStringExtra("otp");

        btnVerifyOTP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String enteredOTP = editTextOTP.getText().toString().trim();

                if (enteredOTP.equals(receivedOTP)) {
                    Toast.makeText(VerifyOtpActivity.this, "OTP Verified Successfully!", Toast.LENGTH_SHORT).show();

                    // Navigate to HomeActivity
                    Intent intent = new Intent(VerifyOtpActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(VerifyOtpActivity.this, "Invalid OTP. Try again!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
