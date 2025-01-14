package com.example.bookswapapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bookswapapp.R;
import com.example.bookswapapp.utils.OtpUtils;

public class SendOtpActivity extends AppCompatActivity {

    private EditText editTextPhone;
    private Button btnSendOTP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_otp);

        editTextPhone = findViewById(R.id.editTextPhone);
        btnSendOTP = findViewById(R.id.btnSendOTP);

        btnSendOTP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = editTextPhone.getText().toString().trim();

                if (phoneNumber.isEmpty() || phoneNumber.length() != 10) {
                    Toast.makeText(SendOtpActivity.this, "Enter a valid phone number!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String otp = OtpUtils.generateOTP(); // Generate OTP
                OtpUtils.sendOTP(SendOtpActivity.this, phoneNumber, otp); // Send OTP

                // Pass OTP to VerifyOtpActivity
                Intent intent = new Intent(SendOtpActivity.this, VerifyOtpActivity.class);
                intent.putExtra("phoneNumber", phoneNumber);
                intent.putExtra("otp", otp);
                startActivity(intent);
            }
        });
    }
}
