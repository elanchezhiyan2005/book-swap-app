package com.example.bookswapapp3;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class ViewBookAdapter extends RecyclerView.Adapter<ViewBookAdapter.ViewHolder> implements Filterable {
    private Context context;
    private List<BookModel> bookList;
    private List<BookModel> bookListFull;

    public ViewBookAdapter(Context context, List<BookModel> bookList) {
        this.context = context;
        this.bookList = new ArrayList<>(bookList);
        this.bookListFull = new ArrayList<>(bookList);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_item_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BookModel book = bookList.get(position);
        holder.title.setText(book.getTitle());
        holder.author.setText(book.getAuthor());
        holder.place.setText(book.getPlace() != null ? book.getPlace() : "Unknown Place");

        // Load book image using Glide
        Glide.with(context).load(book.getImageUrl()).into(holder.bookImage);

        // Set action button text and color
        String action = book.getAction() != null ? book.getAction() : "N/A";
        holder.actionButton.setText(action);
        if (action.equalsIgnoreCase("Swap")) {
            holder.actionButton.setBackgroundResource(R.drawable.action_button_swap); // Green
        } else if (action.equalsIgnoreCase("Lend")) {
            holder.actionButton.setBackgroundResource(R.drawable.action_button_lend); // Blue
        } else if (action.equalsIgnoreCase("Buy")) {
            holder.actionButton.setBackgroundResource(R.drawable.action_button_buy); // Orange
        } else {
            holder.actionButton.setBackgroundResource(R.drawable.action_button_default); // Default
        }

        // Handle action button click
        holder.actionButton.setOnClickListener(v -> {
            Log.d("ActionClick", "Action clicked for book: " + book.getTitle() + ", Action: " + book.getAction());
            // Add action logic here
        });
    }

    @Override
    public int getItemCount() {
        return bookList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, author, place;
        ImageView bookImage;
        Button actionButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.bookTitleTextView);
            author = itemView.findViewById(R.id.bookAuthorTextView);
            place = itemView.findViewById(R.id.bookPlaceTextView);
            bookImage = itemView.findViewById(R.id.bookImageView);
            actionButton = itemView.findViewById(R.id.actionButton);
        }
    }

    @Override
    public Filter getFilter() {
        return bookFilter;
    }

    private final Filter bookFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<BookModel> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(bookListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (BookModel book : bookListFull) {
                    if (book.getTitle().toLowerCase().contains(filterPattern) ||
                            book.getAuthor().toLowerCase().contains(filterPattern)) {
                        filteredList.add(book);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            bookList.clear();
            bookList.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

    public void updateList(List<BookModel> newList) {
        bookList.clear();
        bookList.addAll(newList);
        bookListFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }
}