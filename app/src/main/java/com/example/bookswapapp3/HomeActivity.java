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

        // Check if user is signed in
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, SendOtpActivity.class));
            finish();
            return;
        }

        // Toolbar Section
        Button homeButton = findViewById(R.id.homeButton);
        Button profileButton = findViewById(R.id.profileButton);
        Button logoutButton = findViewById(R.id.logoutButton);

        // Buttons Section
        Button addBookButton = findViewById(R.id.addBookButton);
        Button viewBooksButton = findViewById(R.id.viewBooksButton);
        Button messagesButton = findViewById(R.id.messagesButton);

        // Button Click Listeners
        addBookButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, AddBookActivity.class);
            startActivity(intent);
        });

        viewBooksButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ViewBooksActivity.class);
            startActivity(intent);
        });

        messagesButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MessagesActivity.class);
            startActivity(intent);
        });

        // Toolbar Button Click Listeners
        homeButton.setOnClickListener(v ->
                Toast.makeText(HomeActivity.this, "Home clicked", Toast.LENGTH_SHORT).show()
        );
        profileButton.setOnClickListener(v ->
                Toast.makeText(HomeActivity.this, "Profile clicked", Toast.LENGTH_SHORT).show()
        );

        // Logout Button Click Listener
        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    private void showLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logout")
                .setMessage("Do you want to logout?")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut(); // Sign out from Firebase
                    SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
                    prefs.edit().remove("phoneNumber").apply(); // Clear stored phone number
                    Intent intent = new Intent(HomeActivity.this, SendOtpActivity.class);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}