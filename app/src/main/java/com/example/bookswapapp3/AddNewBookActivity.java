package com.example.bookswapapp3;

import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;
import ch.hsr.geohash.GeoHash;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddNewBookActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private RadioGroup isbnRadioGroup;
    private EditText isbnInput, titleInput, authorInput, publisherInput, editionInput, placeNameInput;
    private LinearLayout isbnFetchLayout;
    private Button fetchDetailsButton, saveButton, cancelButton;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private double latitude, longitude;
    private String userPhoneNumber;
    private ExecutorService executorService;
    private String fetchedImageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_book);

        // Initialize Firestore and location client
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newFixedThreadPool(2); // Thread pool for background tasks

        // Get user phone number from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        userPhoneNumber = prefs.getString("phoneNumber", null);

        // Initialize UI components
        isbnRadioGroup = findViewById(R.id.isbnRadioGroup);
        isbnInput = findViewById(R.id.isbnInput);
        isbnFetchLayout = findViewById(R.id.isbnFetchLayout);
        fetchDetailsButton = findViewById(R.id.fetchDetailsButton);
        titleInput = findViewById(R.id.titleInput);
        authorInput = findViewById(R.id.authorInput);
        publisherInput = findViewById(R.id.publisherInput);
        editionInput = findViewById(R.id.editionInput);
        placeNameInput = findViewById(R.id.placeNameInput);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);

        // Disable input fields initially
        titleInput.setEnabled(false);
        authorInput.setEnabled(false);
        publisherInput.setEnabled(false);
        editionInput.setEnabled(false);
        isbnFetchLayout.setVisibility(View.GONE);

        // Radio button listener for ISBN option
        isbnRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioYes) {
                isbnFetchLayout.setVisibility(View.VISIBLE);
                titleInput.setEnabled(false);
                authorInput.setEnabled(false);
                publisherInput.setEnabled(false);
                editionInput.setEnabled(false);
            } else {
                isbnFetchLayout.setVisibility(View.GONE);
                isbnInput.setText("");
                titleInput.setEnabled(true);
                authorInput.setEnabled(true);
                publisherInput.setEnabled(true);
                editionInput.setEnabled(true);
                fetchedImageUrl = null;
            }
        });

        // Fetch details button listener
        fetchDetailsButton.setOnClickListener(v -> {
            String isbnCode = isbnInput.getText().toString().trim();
            if (isbnCode.isEmpty()) {
                Toast.makeText(this, "Please enter a valid ISBN", Toast.LENGTH_SHORT).show();
            } else {
                new FetchBookDetailsTask().execute(isbnCode);
            }
        });

        // Save button listener
        saveButton.setOnClickListener(v -> getLocationAndSaveBook());

        // Cancel button listener
        cancelButton.setOnClickListener(v -> {
            titleInput.setText("");
            authorInput.setText("");
            publisherInput.setText("");
            editionInput.setText("");
            placeNameInput.setText("");
            isbnInput.setText("");
            isbnRadioGroup.clearCheck();
            finish();
        });
    }

    private void getLocationAndSaveBook() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fetchCurrentLocation();
        }
    }

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                            saveBookDetails();
                        } else {
                            Toast.makeText(this, "Failed to get location. Enable GPS and try again.", Toast.LENGTH_SHORT).show();
                            Log.e("LocationError", "Location is null");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error fetching location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("LocationError", "Error getting location", e);
                    });
        }
    }

    private void saveBookDetails() {
        String title = titleInput.getText().toString().trim();
        String author = authorInput.getText().toString().trim();
        String isbn = isbnInput.getText().toString().trim();
        String publisher = publisherInput.getText().toString().trim();
        String edition = editionInput.getText().toString().trim();
        String placeName = placeNameInput.getText().toString().trim();

        // Validate inputs
        if (title.isEmpty() || author.isEmpty() || publisher.isEmpty() || edition.isEmpty() || placeName.isEmpty()) {
            Toast.makeText(this, "Please fill in all details", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate userPhoneNumber
        if (userPhoneNumber == null || userPhoneNumber.isEmpty()) {
            Toast.makeText(this, "User session not found. Please log in again.", Toast.LENGTH_LONG).show();
            Log.e("SaveBookError", "userPhoneNumber is null or empty");
            return;
        }

        // Validate location
        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(this, "Location not fetched. Ensure GPS is enabled.", Toast.LENGTH_LONG).show();
            Log.e("SaveBookError", "Location not set: lat=" + latitude + ", lon=" + longitude);
            return;
        }

        final String imageUrl;
        if (fetchedImageUrl != null && !fetchedImageUrl.isEmpty()) {
            imageUrl = fetchedImageUrl;
        } else if (!isbn.isEmpty()) {
            imageUrl = "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg";
        } else {
            imageUrl = "https://via.placeholder.com/150";
        }

        // Save to Firestore in a background thread
        executorService.execute(() -> {
            try {
                GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, 5);
                String geohashString = geoHash.toBase32();

                Map<String, Object> book = new HashMap<>();
                book.put("title", title);
                book.put("author", author);
                book.put("isbn", isbn);
                book.put("publisher", publisher);
                book.put("edition", edition);
                book.put("imageUrl", imageUrl);
                book.put("location", new HashMap<String, Object>() {{
                    put("latitude", latitude);
                    put("longitude", longitude);
                }});
                book.put("place", placeName);
                book.put("geohash", geohashString);
                book.put("userPhoneNumber", userPhoneNumber);

                db.collection("users").document(userPhoneNumber)
                        .collection("books").document(isbn.isEmpty() ? db.collection("books").document().getId() : isbn)
                        .set(book)
                        .addOnSuccessListener(aVoid -> runOnUiThread(() -> {
                            Toast.makeText(this, "Book added successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        }))
                        .addOnFailureListener(e -> runOnUiThread(() -> {
                            Toast.makeText(this, "Failed to add book: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("FirestoreError", "Failed to save book", e);
                        }));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error saving book: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("SaveBookError", "Exception in saving book", e);
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchBookDetailsTask extends AsyncTask<String, Void, JSONObject> {
       private String currentIsbn;
       @Override
        protected JSONObject doInBackground(String... params) {
         currentIsbn = params[0];
         String apiUrl = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + currentIsbn +
                "&key=" + BuildConfig.BOOKS_API_KEY;
         Log.d("BOOKS_API_KEY", BuildConfig.BOOKS_API_KEY);

            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e("FetchBookError", "API call failed: " + connection.getResponseCode());
                    return null;
                }

                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                StringBuilder response = new StringBuilder();
                int data;
                while ((data = reader.read()) != -1) {
                    response.append((char) data);
                }
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                if (jsonResponse.has("items") && jsonResponse.getJSONArray("items").length() > 0) {
                    return jsonResponse.getJSONArray("items")
                            .getJSONObject(0)
                            .getJSONObject("volumeInfo");
                }
                return null;

            } catch (Exception e) {
                Log.e("FetchBookError", "Exception in fetching details", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONObject volumeInfo) {
            try {
                if (volumeInfo != null) {
                    // Parse title
                    titleInput.setText(volumeInfo.optString("title", "Unknown Title"));

                    // Parse authors
                    if (volumeInfo.has("authors")) {
                        JSONArray authors = volumeInfo.getJSONArray("authors");
                        StringBuilder authorBuilder = new StringBuilder();
                        for (int i = 0; i < authors.length(); i++) {
                            if (i > 0) authorBuilder.append(", ");
                            authorBuilder.append(authors.getString(i));
                        }
                        authorInput.setText(authorBuilder.toString());
                    } else {
                        authorInput.setText("Unknown Author");
                    }

                    // Parse publisher
                    publisherInput.setText(volumeInfo.optString("publisher", "Unknown Publisher"));

                    // Parse published date as edition
                    String publishedDate = volumeInfo.optString("publishedDate", "");
                    editionInput.setText(publishedDate.isEmpty() ?
                            "Unknown Edition" : "Edition: " + publishedDate);

                    // Handle image URL
                    fetchedImageUrl = "https://via.placeholder.com/150";
                    if (volumeInfo.has("imageLinks")) {
                        JSONObject imageLinks = volumeInfo.getJSONObject("imageLinks");
                        String thumbnail = imageLinks.optString("thumbnail", "");
                        if (!thumbnail.isEmpty()) {
                            fetchedImageUrl = thumbnail
                                    .replace("http://", "https://")
                                    .replace("&edge=curl", "");
                        }
                    }

                    // Fallback to Open Library if needed
                    if (fetchedImageUrl.equals("https://via.placeholder.com/150")) {
                        fetchedImageUrl = "https://covers.openlibrary.org/b/isbn/" + currentIsbn + "-L.jpg";
                    }

                    // Lock fields
                    titleInput.setEnabled(false);
                    authorInput.setEnabled(false);
                    publisherInput.setEnabled(false);
                    editionInput.setEnabled(false);

                } else {
                    showManualEntryFallback();
                }
            } catch (Exception e) {
                Log.e("ParseError", "Error parsing response", e);
                showManualEntryFallback();
            }
        }

        private void showManualEntryFallback() {
            runOnUiThread(() -> {
                Toast.makeText(AddNewBookActivity.this,
                        "Failed to fetch details. Enter manually.",
                        Toast.LENGTH_LONG).show();

                titleInput.setEnabled(true);
                authorInput.setEnabled(true);
                publisherInput.setEnabled(true);
                editionInput.setEnabled(true);

                titleInput.setText("");
                authorInput.setText("");
                publisherInput.setText("");
                editionInput.setText("");
                fetchedImageUrl = null;
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown(); // Clean up thread pool
    }
}