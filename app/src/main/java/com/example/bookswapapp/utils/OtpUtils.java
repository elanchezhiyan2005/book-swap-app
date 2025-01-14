package com.example.bookswapapp.utils;

import android.content.Context;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OtpUtils {

    private static final String FAST2SMS_API = "rMqfT3XTX7eO6dxhoxw06J6xsQEDp5PgGV2YSJcO5EG4qFAaXfq80OV6ZFNW";

    public static String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // 6-digit OTP
        return String.valueOf(otp);
    }

    public static void sendOTP(Context context, String phoneNumber, String otp) {
        RequestQueue queue = Volley.newRequestQueue(context);

        String url = "https://www.fast2sms.com/dev/bulkV2";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Toast.makeText(context, "OTP Sent Successfully!", Toast.LENGTH_SHORT).show();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, "Failed to send OTP. Try again!", Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("authorization", FAST2SMS_API);
                params.put("sender_id", "TXTIND");
                params.put("message", "Your OTP is: " + otp);
                params.put("language", "english");
                params.put("route", "v3");
                params.put("numbers", phoneNumber);
                return params;
            }
        };

        queue.add(stringRequest);
    }
}
