package com.example.bookswapapp3;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
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
import java.util.concurrent.TimeUnit;

public class AddNewBookActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_CODE_LOCATION_SETTINGS = 2;
    private ImageButton backButton;
    private RadioGroup isbnRadioGroup, actionRadioGroup;
    private EditText isbnInput, titleInput, authorInput, publisherInput, editionInput, placeNameInput, yearInput, genreInput;
    private LinearLayout isbnFetchLayout;
    private Button fetchDetailsButton, saveButton, cancelButton;
    private ProgressBar loadingSpinner;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private double latitude, longitude;
    private String userPhoneNumber;
    private ExecutorService executorService;
    private String fetchedImageUrl;
    private volatile boolean isFinishing;
    private String selectedAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_book);

        // Initialize Firestore and location client
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newFixedThreadPool(2);
        isFinishing = false;

        // Get user phone number from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        userPhoneNumber = prefs.getString("phoneNumber", null);
        if (userPhoneNumber == null) {
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize UI components
        backButton = findViewById(R.id.backButton);
        isbnRadioGroup = findViewById(R.id.isbnRadioGroup);
        actionRadioGroup = findViewById(R.id.actionRadioGroup);
        isbnInput = findViewById(R.id.isbnInput);
        isbnFetchLayout = findViewById(R.id.isbnFetchLayout);
        fetchDetailsButton = findViewById(R.id.fetchDetailsButton);
        titleInput = findViewById(R.id.titleInput);
        authorInput = findViewById(R.id.authorInput);
        publisherInput = findViewById(R.id.publisherInput);
        editionInput = findViewById(R.id.editionInput);
        placeNameInput = findViewById(R.id.placeNameInput);
        yearInput = findViewById(R.id.yearInput);
        genreInput = findViewById(R.id.genreInput);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        // Disable input fields initially (except Genre and Location)
        titleInput.setEnabled(false);
        authorInput.setEnabled(false);
        publisherInput.setEnabled(false);
        editionInput.setEnabled(false);
        yearInput.setEnabled(false);
        genreInput.setEnabled(true);
        placeNameInput.setEnabled(true);
        isbnFetchLayout.setVisibility(View.VISIBLE);

        // Back Button
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBookActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // ISBN Radio Button Listener
        isbnRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioYes) {
                isbnFetchLayout.setVisibility(View.VISIBLE);
                titleInput.setEnabled(false);
                authorInput.setEnabled(false);
                publisherInput.setEnabled(false);
                editionInput.setEnabled(false);
                yearInput.setEnabled(false);
                genreInput.setEnabled(true);
                placeNameInput.setEnabled(true);
            } else {
                isbnFetchLayout.setVisibility(View.GONE);
                isbnInput.setText("");
                titleInput.setEnabled(true);
                authorInput.setEnabled(true);
                publisherInput.setEnabled(true);
                editionInput.setEnabled(true);
                yearInput.setEnabled(true);
                genreInput.setEnabled(true);
                placeNameInput.setEnabled(true);
                fetchedImageUrl = null;
            }
        });

        // Action Radio Button Listener
        actionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.actionSwap) {
                selectedAction = "Swap";
            } else if (checkedId == R.id.actionSell) {
                selectedAction = "Sell";
            } else if (checkedId == R.id.actionLend) {
                selectedAction = "Lend";
            }
        });

        // Default action
        actionRadioGroup.check(R.id.actionSwap);
        selectedAction = "Swap";

        // Fetch Details Button Listener
        fetchDetailsButton.setOnClickListener(v -> {
            String isbnCode = isbnInput.getText().toString().trim();
            if (isbnCode.isEmpty()) {
                Toast.makeText(this, "Please enter a valid ISBN", Toast.LENGTH_SHORT).show();
            } else {
                loadingSpinner.setVisibility(View.VISIBLE);
                fetchDetailsButton.setEnabled(false);
                new FetchBookDetailsTask().execute(isbnCode);
            }
        });

        // Save Button Listener
        saveButton.setOnClickListener(v -> getLocationAndSaveBook());

        // Cancel Button Listener
        cancelButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBookActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void getLocationAndSaveBook() {
        // First, check if location permissions are granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Check if GPS is enabled
            if (!isLocationEnabled()) {
                promptToEnableLocation();
            } else {
                fetchCurrentLocation();
            }
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void promptToEnableLocation() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Location")
                .setMessage("Location services are disabled. Please enable GPS to proceed.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(intent, REQUEST_CODE_LOCATION_SETTINGS);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Location services are required to save the book.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LOCATION_SETTINGS) {
            if (isLocationEnabled()) {
                fetchCurrentLocation();
            } else {
                Toast.makeText(this, "Location services are still disabled. Please enable GPS to proceed.", Toast.LENGTH_SHORT).show();
            }
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
                            Toast.makeText(this, "Failed to get location. Ensure GPS is enabled and try again.", Toast.LENGTH_SHORT).show();
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
        String year = yearInput.getText().toString().trim();
        String genre = genreInput.getText().toString().trim();

        // Validate inputs
        if (title.isEmpty() || author.isEmpty() || publisher.isEmpty() || edition.isEmpty() || placeName.isEmpty() || year.isEmpty() || genre.isEmpty()) {
            Toast.makeText(this, "Please fill in all details", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedAction == null) {
            Toast.makeText(this, "Please select an action (Swap, Sell, or Lend)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userPhoneNumber == null || userPhoneNumber.isEmpty()) {
            Toast.makeText(this, "User session not found. Please log in again.", Toast.LENGTH_LONG).show();
            Log.e("SaveBookError", "userPhoneNumber is null or empty");
            return;
        }

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

        // Show loading spinner and disable buttons
        runOnUiThread(() -> {
            loadingSpinner.setVisibility(View.VISIBLE);
            saveButton.setEnabled(false);
            cancelButton.setEnabled(false);
            fetchDetailsButton.setEnabled(false);
        });

        if (isFinishing) {
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                saveButton.setEnabled(true);
                cancelButton.setEnabled(true);
                fetchDetailsButton.setEnabled(true);
                Toast.makeText(this, "Cannot save book: Activity is closing.", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        executorService.execute(() -> {
            try {
                GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, 5);
                String geohashString = geoHash.toBase32();

                Map<String, Object> userBook = new HashMap<>();
                userBook.put("title", title);
                userBook.put("author", author);
                userBook.put("isbn", isbn);
                userBook.put("publisher", publisher);
                userBook.put("edition", edition);
                userBook.put("year", year);
                userBook.put("genre", genre);
                userBook.put("action", selectedAction);
                userBook.put("imageUrl", imageUrl);
                userBook.put("location", new HashMap<String, Object>() {{
                    put("latitude", latitude);
                    put("longitude", longitude);
                }});
                userBook.put("place", placeName);
                userBook.put("geohash", geohashString);
                userBook.put("userPhoneNumber", userPhoneNumber);
                userBook.put("timestamp", FieldValue.serverTimestamp());

                Map<String, Object> globalBook = new HashMap<>();
                globalBook.put("title", title);
                globalBook.put("author", author);
                globalBook.put("isbn", isbn);
                globalBook.put("publisher", publisher);
                globalBook.put("edition", edition);
                globalBook.put("year", year);
                globalBook.put("genre", genre);
                globalBook.put("imageUrl", imageUrl);
                globalBook.put("timestamp", FieldValue.serverTimestamp());

                String documentId = isbn.isEmpty() ? db.collection("books").document().getId() : isbn;

                WriteBatch batch = db.batch();
                batch.set(
                        db.collection("users").document(userPhoneNumber).collection("books").document(documentId),
                        userBook
                );
                batch.set(
                        db.collection("books").document(documentId),
                        globalBook
                );

                batch.commit()
                        .addOnSuccessListener(aVoid -> runOnUiThread(() -> {
                            loadingSpinner.setVisibility(View.GONE);

                            Toast toast = new Toast(this);
                            View toastView = getLayoutInflater().inflate(R.layout.toast_success, (ViewGroup) findViewById(android.R.id.content), false);
                            toast.setView(toastView);
                            toast.setDuration(Toast.LENGTH_LONG);
                            toast.show();

                            saveButton.setEnabled(true);
                            cancelButton.setEnabled(true);
                            fetchDetailsButton.setEnabled(true);

                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                isFinishing = true;
                                Intent intent = new Intent(this, HomeActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                finish();
                            }, 2000);
                        }))
                        .addOnFailureListener(e -> runOnUiThread(() -> {
                            loadingSpinner.setVisibility(View.GONE);
                            Toast.makeText(this, "Failed to add book: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("FirestoreError", "Batched write failed", e);
                            saveButton.setEnabled(true);
                            cancelButton.setEnabled(true);
                            fetchDetailsButton.setEnabled(true);
                        }));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving book: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("SaveBookError", "Exception in saving book", e);
                    saveButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    fetchDetailsButton.setEnabled(true);
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!isLocationEnabled()) {
                    promptToEnableLocation();
                } else {
                    fetchCurrentLocation();
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchBookDetailsTask extends AsyncTask<String, Void, JSONObject> {
        private String currentIsbn;
        private boolean usingGoogleBooks = false;

        @Override
        protected JSONObject doInBackground(String... params) {
            currentIsbn = params[0];
            String openLibraryUrl = "https://openlibrary.org/api/books?bibkeys=ISBN:" + currentIsbn + "&format=json&jscmd=data";
            try {
                URL url = new URL(openLibraryUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e("FetchBookError", "Open Library API call failed: " + connection.getResponseCode());
                } else {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    StringBuilder response = new StringBuilder();
                    int data;
                    while ((data = reader.read()) != -1) {
                        response.append((char) data);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if (jsonResponse.has("ISBN:" + currentIsbn)) {
                        return jsonResponse.getJSONObject("ISBN:" + currentIsbn);
                    }
                    Log.d("FetchBookInfo", "Open Library API returned no data for ISBN: " + currentIsbn);
                }
            } catch (Exception e) {
                Log.e("FetchBookError", "Exception in fetching details from Open Library", e);
            }

            usingGoogleBooks = true;
            String googleBooksUrl = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + currentIsbn +
                    "&key=" + BuildConfig.BOOKS_API_KEY;
            Log.d("BOOKS_API_KEY", BuildConfig.BOOKS_API_KEY);

            try {
                URL url = new URL(googleBooksUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e("FetchBookError", "Google Books API call failed: " + connection.getResponseCode());
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
                Log.e("FetchBookError", "Exception in fetching details from Google Books", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONObject bookInfo) {
            loadingSpinner.setVisibility(View.GONE);
            fetchDetailsButton.setEnabled(true);

            try {
                if (bookInfo != null) {
                    if (!usingGoogleBooks) {
                        titleInput.setText(bookInfo.optString("title", "Unknown Title"));

                        if (bookInfo.has("authors")) {
                            JSONArray authors = bookInfo.getJSONArray("authors");
                            StringBuilder authorBuilder = new StringBuilder();
                            for (int i = 0; i < authors.length(); i++) {
                                if (i > 0) authorBuilder.append(", ");
                                authorBuilder.append(authors.getJSONObject(i).getString("name"));
                            }
                            authorInput.setText(authorBuilder.length() > 0 ? authorBuilder.toString() : "Unknown Author");
                        } else {
                            authorInput.setText("Unknown Author");
                        }

                        if (bookInfo.has("publishers")) {
                            JSONArray publishers = bookInfo.getJSONArray("publishers");
                            StringBuilder publisherBuilder = new StringBuilder();
                            for (int i = 0; i < publishers.length(); i++) {
                                if (i > 0) publisherBuilder.append(", ");
                                publisherBuilder.append(publishers.getJSONObject(i).getString("name"));
                            }
                            publisherInput.setText(publisherBuilder.length() > 0 ? publisherBuilder.toString() : "Unknown Publisher");
                        } else {
                            publisherInput.setText("Unknown Publisher");
                        }

                        String publishDate = bookInfo.optString("publish_date", "");
                        editionInput.setText(publishDate.isEmpty() ?
                                "Unknown Edition" : "Edition: " + publishDate);

                        if (!publishDate.isEmpty()) {
                            String[] dateParts = publishDate.split("-");
                            if (dateParts.length > 0) {
                                yearInput.setText(dateParts[0]);
                            } else {
                                yearInput.setText("");
                            }
                        } else {
                            yearInput.setText("");
                        }

                        if (bookInfo.has("subjects")) {
                            JSONArray subjects = bookInfo.getJSONArray("subjects");
                            StringBuilder genreBuilder = new StringBuilder();
                            for (int i = 0; i < subjects.length(); i++) {
                                if (i > 0) genreBuilder.append(", ");
                                genreBuilder.append(subjects.getJSONObject(i).getString("name"));
                            }
                            genreInput.setText(genreBuilder.length() > 0 ? genreBuilder.toString() : "");
                        } else {
                            genreInput.setText("");
                        }

                        fetchedImageUrl = "https://via.placeholder.com/150";
                        if (bookInfo.has("cover")) {
                            JSONObject cover = bookInfo.getJSONObject("cover");
                            String thumbnail = cover.optString("large", "");
                            if (!thumbnail.isEmpty()) {
                                fetchedImageUrl = thumbnail;
                            }
                        }
                    } else {
                        titleInput.setText(bookInfo.optString("title", "Unknown Title"));

                        if (bookInfo.has("authors")) {
                            JSONArray authors = bookInfo.getJSONArray("authors");
                            StringBuilder authorBuilder = new StringBuilder();
                            for (int i = 0; i < authors.length(); i++) {
                                if (i > 0) authorBuilder.append(", ");
                                authorBuilder.append(authors.getString(i));
                            }
                            authorInput.setText(authorBuilder.toString());
                        } else {
                            authorInput.setText("Unknown Author");
                        }

                        publisherInput.setText(bookInfo.optString("publisher", "Unknown Publisher"));

                        String publishedDate = bookInfo.optString("publishedDate", "");
                        editionInput.setText(publishedDate.isEmpty() ?
                                "Unknown Edition" : "Edition: " + publishedDate);

                        if (!publishedDate.isEmpty()) {
                            String[] dateParts = publishedDate.split("-");
                            if (dateParts.length > 0) {
                                yearInput.setText(dateParts[0]);
                            } else {
                                yearInput.setText("");
                            }
                        } else {
                            yearInput.setText("");
                        }

                        if (bookInfo.has("categories")) {
                            JSONArray categories = bookInfo.getJSONArray("categories");
                            StringBuilder genreBuilder = new StringBuilder();
                            for (int i = 0; i < categories.length(); i++) {
                                if (i > 0) genreBuilder.append(", ");
                                genreBuilder.append(categories.getString(i));
                            }
                            genreInput.setText(genreBuilder.toString());
                        } else {
                            genreInput.setText("");
                        }

                        fetchedImageUrl = "https://via.placeholder.com/150";
                        if (bookInfo.has("imageLinks")) {
                            JSONObject imageLinks = bookInfo.getJSONObject("imageLinks");
                            String thumbnail = imageLinks.optString("thumbnail", "");
                            if (!thumbnail.isEmpty()) {
                                fetchedImageUrl = thumbnail
                                        .replace("http://", "https://")
                                        .replace("&edge=curl", "");
                            }
                        }
                    }

                    if (fetchedImageUrl.equals("https://via.placeholder.com/150")) {
                        fetchedImageUrl = "https://covers.openlibrary.org/b/isbn/" + currentIsbn + "-L.jpg";
                    }

                    titleInput.setEnabled(false);
                    authorInput.setEnabled(false);
                    publisherInput.setEnabled(false);
                    editionInput.setEnabled(false);
                    yearInput.setEnabled(false);
                    genreInput.setEnabled(true);
                    placeNameInput.setEnabled(true);

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
                        "Failed to fetch details from both APIs. Enter manually.",
                        Toast.LENGTH_LONG).show();

                titleInput.setEnabled(true);
                authorInput.setEnabled(true);
                publisherInput.setEnabled(true);
                editionInput.setEnabled(true);
                yearInput.setEnabled(true);
                genreInput.setEnabled(true);
                placeNameInput.setEnabled(true);

                titleInput.setText("");
                authorInput.setText("");
                publisherInput.setText("");
                editionInput.setText("");
                yearInput.setText("");
                genreInput.setText("");
                fetchedImageUrl = null;
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isFinishing = true;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    Log.w("AddNewBookActivity", "ExecutorService did not terminate in time, forced shutdown");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                Log.e("AddNewBookActivity", "ExecutorService shutdown interrupted", e);
            }
        }
    }
}