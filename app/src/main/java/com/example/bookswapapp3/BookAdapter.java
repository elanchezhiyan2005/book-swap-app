package com.example.bookswapapp3;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;
import android.widget.Toast;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    private final List<Book> bookList;
    private String userPhoneNumber; // To know which user's books to delete
    private FirebaseFirestore db;
    private Context context;

    public BookAdapter(List<Book> bookList,String userPhoneNumber) {
        this.bookList = bookList;
        this.userPhoneNumber = userPhoneNumber;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
        this.context = parent.getContext();
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = bookList.get(position);
        holder.titleText.setText(book.getTitle());
        holder.authorText.setText(book.getAuthor());
        holder.publisherText.setText(book.getPublisher());

        // Load book image using Glide
        Glide.with(holder.itemView.getContext())
                .load(book.getImageUrl())
                .placeholder(R.drawable.placeholder_image) // Placeholder while loading
                .error(R.drawable.error_image) // Image to display if loading fails
                .into(holder.bookImageView);
        // Delete button click listener
        holder.deleteButton.setOnClickListener(v -> {
            if (userPhoneNumber == null) {
                Toast.makeText(holder.itemView.getContext(), "User not found", Toast.LENGTH_SHORT).show();
                return;
            }
            // Show confirmation dialog
            new AlertDialog.Builder(context)
                    .setTitle("Delete Book")
                    .setMessage("Do you really want to delete \"" + book.getTitle() + "\"?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // User confirmed, proceed with deletion
                        db.collection("users")
                                .document(userPhoneNumber)
                                .collection("books")
                                .document(book.getIsbn())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(context, "Book deleted successfully!", Toast.LENGTH_SHORT).show();
                                    // Firestore listener in AddBookActivity will update the UI
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(context, "Failed to delete book: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        // User canceled, do nothing
                        dialog.dismiss();
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return bookList.size();
    }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, authorText, publisherText;
        ImageView bookImageView;
        Button deleteButton;// Added ImageView for book cover

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.titleText);
            authorText = itemView.findViewById(R.id.authorText);
            publisherText = itemView.findViewById(R.id.publisherText);
            bookImageView = itemView.findViewById(R.id.bookImageView);
            deleteButton = itemView.findViewById(R.id.deleteButton);// ImageView in layout
        }
    }
}
