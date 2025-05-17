package com.example.bookswapapp3;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaTasksClient;

public class BibliobantApplication extends Application {
    // Toggle for development vs production
    private static final boolean IS_DEBUG_MODE = true; // Set to false for production

    // Replace this with your actual reCAPTCHA Enterprise site key
    private static final String RECAPTCHA_SITE_KEY = "6Lf0jjwrAAAAAP7xInI1CYRe3O00lsIuK0IShJoS";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Initialize Firebase App Check
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        if (IS_DEBUG_MODE) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
        }

        // Initialize reCAPTCHA Enterprise
        Recaptcha.getTasksClient(this, RECAPTCHA_SITE_KEY)
                .addOnSuccessListener(client -> {
                    Log.d("Recaptcha", "reCAPTCHA initialized successfully.");
                    // Optionally save this client for use later
                })
                .addOnFailureListener(e -> {
                    Log.e("Recaptcha", "Failed to initialize reCAPTCHA: " + e.getMessage());
                });
    }
}
