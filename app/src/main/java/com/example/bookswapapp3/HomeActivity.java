package com.example.bookswapapp3;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView bookRecyclerView;
    private LinearLayout emptyStateLayout;
    private Button addBookPlaceholderButton;
    private ImageButton logoutButton; // Changed from Button to ImageButton
    private BookAdapter bookAdapter;
    private List<Book> bookList;
    private FirebaseFirestore db;
    private String userPhoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            redirectToAuthFlow();
            return;
        }

        db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        userPhoneNumber = prefs.getString("phoneNumber", null);
        if (userPhoneNumber == null) {
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            redirectToAuthFlow();
            return;
        }

        setupUI();
        fetchUserBooks();
    }

    private void setupUI() {
        bookRecyclerView = findViewById(R.id.bookRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        addBookPlaceholderButton = findViewById(R.id.addBookPlaceholderButton);
        logoutButton = findViewById(R.id.logoutButton); // This will now correctly reference the ImageButton

        bookList = new ArrayList<>();
        bookAdapter = new BookAdapter(bookList, userPhoneNumber);
        bookRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookRecyclerView.setAdapter(bookAdapter);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        NavigationUtils.setupBottomNavigation(this, bottomNavigationView, R.id.nav_home);

        addBookPlaceholderButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBookActivity.class);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    private void fetchUserBooks() {
        if (userPhoneNumber == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .document(userPhoneNumber)
                .collection("books")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error fetching books", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        bookList.clear();
                        for (QueryDocumentSnapshot document : value) {
                            String isbn = document.getId();
                            String title = document.getString("title");
                            String author = document.getString("author");
                            String publisher = document.getString("publisher");
                            String edition = document.getString("edition");
                            String imageUrl = document.getString("imageUrl");
                            String action = document.getString("action"); // Fetch the new action field
                            bookList.add(new Book(title, author, publisher, edition, imageUrl, isbn, action));
                        }
                        bookAdapter.notifyDataSetChanged();
                        updateUI();
                    }
                });
    }

    private void updateUI() {
        if (bookList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            bookRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            bookRecyclerView.setVisibility(View.VISIBLE);
        }
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
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("BookSwapPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Intent intent = new Intent(this, SendOtpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToAuthFlow() {
        Intent intent = new Intent(this, SendOtpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}