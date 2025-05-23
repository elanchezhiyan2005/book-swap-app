package com.example.bookswapapp3;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Enhanced authentication check
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            redirectToAuthFlow();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        // Initialize all UI components
        Button addBookButton = findViewById(R.id.addBookButton);
        Button viewBooksButton = findViewById(R.id.viewBooksButton);
        Button messagesButton = findViewById(R.id.messagesButton);
        Button logoutButton = findViewById(R.id.logoutButton);

        // Set click listeners
        addBookButton.setOnClickListener(v ->
                startActivity(new Intent(this, AddBookActivity.class)));

        viewBooksButton.setOnClickListener(v ->
                startActivity(new Intent(this, ViewBooksActivity.class)));

        messagesButton.setOnClickListener(v ->
                startActivity(new Intent(this, MessagesActivity.class)));

        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("No", null)
                .show();
    }

    private void performLogout() {
        // Clear authentication and preferences
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("BookSwapPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        // Navigate to auth flow with clean stack
        Intent intent = new Intent(this, SendOtpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToAuthFlow() {
        Intent intent = new Intent(this, SendOtpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}