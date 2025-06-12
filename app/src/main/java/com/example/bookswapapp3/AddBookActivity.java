package com.example.bookswapapp3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AddBookActivity extends AppCompatActivity {

    private Button addNewBookButton;
    private ImageButton backButton;
    private ImageButton helpButton;
    private String userPhoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);

        // Get user's phone number from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        userPhoneNumber = prefs.getString("phoneNumber", null);
        if (userPhoneNumber == null) {
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        // Initialize UI components
        addNewBookButton = findViewById(R.id.addNewBookButton);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);

        // Setup Bottom Navigation
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        NavigationUtils.setupBottomNavigation(this, bottomNavigationView, R.id.nav_add);

        // Add New Book Button
        addNewBookButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddNewBookActivity.class);
            intent.putExtra("phoneNumber", userPhoneNumber);
            startActivityForResult(intent, 1);
        });

        // Back Button
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Help Button (placeholder)
        helpButton.setOnClickListener(v -> {
            Toast.makeText(this, "Help: Use this page to add a new book to your collection.", Toast.LENGTH_LONG).show();
        });
    }
}