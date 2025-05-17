package com.example.bookswapapp3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class AddBookActivity extends AppCompatActivity {

    private RecyclerView bookRecyclerView;
    private TextView emptyStateMessage;
    private BookAdapter bookAdapter;
    private List<Book> bookList;
    private FirebaseFirestore db;
    private String userPhoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get user's phone number from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        userPhoneNumber = prefs.getString("phoneNumber", null);
        if (userPhoneNumber == null) {
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Toolbar Buttons
        Button backButton = findViewById(R.id.backButton);
        Button addBookButton = findViewById(R.id.addBookButton);

        // RecyclerView and Empty State Message
        bookRecyclerView = findViewById(R.id.bookRecyclerView);
        emptyStateMessage = findViewById(R.id.emptyStateMessage);

        // Initialize Book List
        bookList = new ArrayList<>();

        // Setup RecyclerView
        bookAdapter = new BookAdapter(bookList, userPhoneNumber); // Pass userPhoneNumber
        bookRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookRecyclerView.setAdapter(bookAdapter);

        // Fetch books from Firestore
        fetchUserBooks();

        // Toolbar Button Actions
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(AddBookActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });

        addBookButton.setOnClickListener(v -> {
            Intent intent = new Intent(AddBookActivity.this, AddNewBookActivity.class);
            intent.putExtra("phoneNumber", userPhoneNumber);
            startActivityForResult(intent, 1);
        });
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
                            String isbn = document.getId(); // Get ISBN from document ID
                            String title = document.getString("title");
                            String author = document.getString("author");
                            String publisher = document.getString("publisher");
                            String edition = document.getString("edition");
                            String imageUrl = document.getString("imageUrl");
                            bookList.add(new Book(title, author, publisher, edition, imageUrl, isbn));
                        }
                        bookAdapter.notifyDataSetChanged();
                        updateUI();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Firestore listener updates UI automatically
    }

    private void updateUI() {
        if (bookList.isEmpty()) {
            emptyStateMessage.setVisibility(View.VISIBLE);
            bookRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateMessage.setVisibility(View.GONE);
            bookRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}