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
import com.google.android.material.snackbar.Snackbar;
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
    private FrameLayout loadingOverlay;
    private ProgressBar loadingProgressBar;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private double latitude, longitude;
    private String userPhoneNumber;
    private ExecutorService executorService;
    private String fetchedImageUrl;
    private volatile boolean isFinishing;
    private String selectedAction;
    private boolean isSaveValid = false; // New flag to track validation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_book);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newFixedThreadPool(2);
        isFinishing = false;

        SharedPreferences prefs = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE);
        userPhoneNumber = prefs.getString("phoneNumber", null);
        if (userPhoneNumber == null) {
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

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
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);

        titleInput.setEnabled(false);
        authorInput.setEnabled(false);
        publisherInput.setEnabled(false);
        editionInput.setEnabled(false);
        yearInput.setEnabled(false);
        genreInput.setEnabled(true);
        placeNameInput.setEnabled(true);
        isbnFetchLayout.setVisibility(View.VISIBLE);

        checkLocationPermission();

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBookActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

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

        actionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.actionSwap) selectedAction = "Swap";
            else if (checkedId == R.id.actionSell) selectedAction = "Sell";
            else if (checkedId == R.id.actionLend) selectedAction = "Lend";
        });

        actionRadioGroup.check(R.id.actionSwap);
        selectedAction = "Swap";

        fetchDetailsButton.setOnClickListener(v -> {
            String isbnCode = isbnInput.getText().toString().trim();
            if (isbnCode.isEmpty()) {
                Toast.makeText(this, "Please enter a valid ISBN", Toast.LENGTH_SHORT).show();
            } else {
                showLoadingOverlay();
                fetchDetailsButton.setEnabled(false);
                new FetchBookDetailsTask().execute(isbnCode);
            }
        });

        saveButton.setOnClickListener(v -> {
            if (validateInputs()) {
                isSaveValid = true; // Set flag to allow save
                getLocationAndSaveBook();
            } else {
                isSaveValid = false; // Ensure save is blocked
            }
        });

        cancelButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBookActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else if (!isLocationEnabled()) {
            promptToEnableLocation();
        }
    }

    private boolean validateInputs() {
        String title = titleInput.getText().toString().trim();
        String author = authorInput.getText().toString().trim();
        String isbn = isbnInput.getText().toString().trim();
        String publisher = publisherInput.getText().toString().trim();
        String edition = editionInput.getText().toString().trim();
        String placeName = placeNameInput.getText().toString().trim();
        String year = yearInput.getText().toString().trim();
        String genre = genreInput.getText().toString().trim();

        if (title.isEmpty() || author.isEmpty() || publisher.isEmpty() || edition.isEmpty() ||
                placeName.isEmpty() || year.isEmpty() || genre.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Please fill in all details", Snackbar.LENGTH_SHORT).show();
            Log.w("InputValidation", "Missing fields: title=" + title + ", author=" + author + ", publisher=" + publisher +
                    ", edition=" + edition + ", place=" + placeName + ", year=" + year + ", genre=" + genre);
            return false;
        }
        if (selectedAction == null) {
            Snackbar.make(findViewById(android.R.id.content), "Please select an action (Swap, Sell, or Lend)", Snackbar.LENGTH_SHORT).show();
            Log.w("InputValidation", "No action selected");
            return false;
        }
        return true;
    }

    private void getLocationAndSaveBook() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(findViewById(android.R.id.content), "Location permission required", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Grant", v -> checkLocationPermission()).show();
            Log.e("LocationPermission", "Permission not granted");
            return;
        }
        if (!isLocationEnabled()) {
            promptToEnableLocation();
        } else {
            fetchCurrentLocation();
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Log.e("LocationManager", "LocationManager is null");
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
                    Snackbar.make(findViewById(android.R.id.content), "Location services are required to save the book.", Snackbar.LENGTH_SHORT).show();
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
                Snackbar.make(findViewById(android.R.id.content), "Location services are still disabled. Please enable GPS to proceed.", Snackbar.LENGTH_SHORT).show();
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
                            if (isSaveValid) saveBookDetails(); // Only proceed if validation passed
                        } else {
                            showErrorSnackbar("Failed to get location. Ensure GPS is enabled and try again.");
                            Log.e("LocationError", "Location is null");
                        }
                    })
                    .addOnFailureListener(e -> {
                        showErrorSnackbar("Error fetching location: " + e.getMessage());
                        Log.e("LocationError", "Error getting location", e);
                    });
        }
    }

    private void saveBookDetails() {
        Log.d("SaveBook", "Starting saveBookDetails with isSaveValid=" + isSaveValid);
        if (!isSaveValid) {
            hideLoadingOverlay();
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fetchDetailsButton.setEnabled(true);
            showErrorSnackbar("Save aborted due to invalid input.");
            return;
        }

        String title = titleInput.getText().toString().trim();
        String author = authorInput.getText().toString().trim();
        String isbn = isbnInput.getText().toString().trim();
        String publisher = publisherInput.getText().toString().trim();
        String edition = editionInput.getText().toString().trim();
        String placeName = placeNameInput.getText().toString().trim();
        String year = yearInput.getText().toString().trim();
        String genre = genreInput.getText().toString().trim();

        if (latitude == 0.0 && longitude == 0.0) {
            hideLoadingOverlay();
            showErrorSnackbar("Location not fetched. Ensure GPS is enabled.");
            Log.e("SaveBookError", "Location not set: lat=" + latitude + ", lon=" + longitude);
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fetchDetailsButton.setEnabled(true);
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

        showLoadingOverlay();
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        fetchDetailsButton.setEnabled(false);

        if (isFinishing) {
            hideLoadingOverlay();
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fetchDetailsButton.setEnabled(true);
            showErrorSnackbar("Cannot save book: Activity is closing.");
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
                            hideLoadingOverlay();
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
                        .addOnFailureListener(e -> {
                            hideLoadingOverlay();
                            showErrorSnackbar("Failed to add book: " + e.getMessage()).setAction("Retry", v -> {
                                if (validateInputs()) {
                                    isSaveValid = true;
                                    getLocationAndSaveBook();
                                }
                            });
                            Log.e("FirestoreError", "Batched write failed", e);
                            runOnUiThread(() -> {
                                saveButton.setEnabled(true);
                                cancelButton.setEnabled(true);
                                fetchDetailsButton.setEnabled(true);
                            });
                        });

            } catch (Exception e) {
                hideLoadingOverlay();
                showErrorSnackbar("Error saving book: " + e.getMessage()).setAction("Retry", v -> {
                    if (validateInputs()) {
                        isSaveValid = true;
                        getLocationAndSaveBook();
                    }
                });
                Log.e("SaveBookError", "Exception in saving book", e);
                runOnUiThread(() -> {
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
                }
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Location permission denied", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Grant", v -> checkLocationPermission()).show();
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
            hideLoadingOverlay();
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
                        editionInput.setText(publishDate.isEmpty() ? "Unknown Edition" : "Edition: " + publishDate);
                        if (!publishDate.isEmpty()) {
                            String[] dateParts = publishDate.split("-");
                            if (dateParts.length > 0) yearInput.setText(dateParts[0]);
                            else yearInput.setText("");
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
                            if (!thumbnail.isEmpty()) fetchedImageUrl = thumbnail;
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
                        editionInput.setText(publishedDate.isEmpty() ? "Unknown Edition" : "Edition: " + publishedDate);
                        if (!publishedDate.isEmpty()) {
                            String[] dateParts = publishedDate.split("-");
                            if (dateParts.length > 0) yearInput.setText(dateParts[0]);
                            else yearInput.setText("");
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
                                fetchedImageUrl = thumbnail.replace("http://", "https://").replace("&edge=curl", "");
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
                Snackbar.make(findViewById(android.R.id.content), "Failed to fetch details from both APIs. Enter manually.", Snackbar.LENGTH_LONG).show();
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

    private void showLoadingOverlay() {
        runOnUiThread(() -> {
            loadingOverlay.setVisibility(View.VISIBLE);
            loadingProgressBar.setVisibility(View.VISIBLE);
        });
    }

    private void hideLoadingOverlay() {
        runOnUiThread(() -> {
            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
        });
    }

    private Snackbar showErrorSnackbar(String message) {
        return Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE);
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