package com.example.bookswapapp3;

import java.util.Map;

public class BookModel {
    private String isbn;
    private String title;
    private String author;
    private String edition;
    private String publisher;
    private String imageUrl;
    private String place;
    private String geohash;
    private double latitude;
    private double longitude;
    private double distance;
    private String userPhoneNumber;
    private String formattedDistance;
    private Map<String, Object> location; // Added to match Firestore

    // Empty constructor required for Firestore
    public BookModel() {}

    public BookModel(String isbn, String title, String author, String edition, String publisher,
                     String imageUrl, String place, String geohash, double latitude, double longitude) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.edition = edition;
        this.publisher = publisher;
        this.imageUrl = imageUrl;
        this.place = place;
        this.geohash = geohash;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getGeohash() { return geohash; }
    public void setGeohash(String geohash) { this.geohash = geohash; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    public String getFormattedDistance() { return formattedDistance; }
    public void setFormattedDistance(String formattedDistance) { this.formattedDistance = formattedDistance; }
    public String getUserPhoneNumber() { return userPhoneNumber; }
    public void setUserPhoneNumber(String userPhoneNumber) { this.userPhoneNumber = userPhoneNumber; }

    // Added for Firestore mapping
    public Map<String, Object> getLocation() { return location; }
    public void setLocation(Map<String, Object> location) {
        this.location = location;
        if (location != null) {
            this.latitude = location.containsKey("latitude") ? ((Number) location.get("latitude")).doubleValue() : 0.0;
            this.longitude = location.containsKey("longitude") ? ((Number) location.get("longitude")).doubleValue() : 0.0;
        }
    }
}