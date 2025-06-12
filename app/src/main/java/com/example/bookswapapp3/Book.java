package com.example.bookswapapp3;

public class Book {
    private String id;
    private String title;
    private String author;
    private String publisher;
    private String edition;
    private String imageUrl;
    private String isbn;
    private String action;

    // Required empty constructor for Firestore
    public Book() {}

    public Book(String title, String author, String publisher, String edition, String imageUrl,String isbn,String action) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.edition = edition;
        this.imageUrl = imageUrl;
        this.isbn = isbn;
        this.action = action;

    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getIsbn() { return isbn; } // Added
    public String getAction() { return action; }
    // Setters (if needed)
    public void setIsbn(String isbn) { this.isbn = isbn; }


}
