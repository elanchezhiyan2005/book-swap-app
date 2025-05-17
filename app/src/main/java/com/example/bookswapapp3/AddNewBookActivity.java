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

        // Set default image URL if ISBN is empty
        String imageUrl = isbn.isEmpty() ? "https://via.placeholder.com/150" : "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg";

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
        @Override
        protected JSONObject doInBackground(String... params) {
            String isbn = params[0];
            String apiUrl = "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn + "&format=json&jscmd=data";
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000); // 5s timeout
                connection.setReadTimeout(5000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("FetchBookError", "API call failed with response code: " + responseCode);
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
                return jsonResponse.optJSONObject("ISBN:" + isbn);
            } catch (Exception e) {
                Log.e("FetchBookError", "Exception in fetching book details", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONObject bookData) {
            if (bookData != null) {
                titleInput.setText(bookData.optString("title", "Unknown Title"));
                authorInput.setText(bookData.optJSONArray("authors") != null && bookData.optJSONArray("authors").length() > 0
                        ? bookData.optJSONArray("authors").optJSONObject(0).optString("name", "Unknown Author")
                        : "Unknown Author");
                publisherInput.setText(bookData.optJSONArray("publishers") != null && bookData.optJSONArray("publishers").length() > 0
                        ? bookData.optJSONArray("publishers").optJSONObject(0).optString("name", "Unknown Publisher")
                        : "Unknown Publisher");
                editionInput.setText(bookData.optString("edition", "Unknown Edition"));

                // Make fields non-editable after successful fetch
                titleInput.setEnabled(false);
                authorInput.setEnabled(false);
                publisherInput.setEnabled(false);
                editionInput.setEnabled(false);
            } else {
                Toast.makeText(AddNewBookActivity.this, "Failed to fetch details. Enter manually.", Toast.LENGTH_LONG).show();
                // Enable fields for manual entry
                titleInput.setEnabled(true);
                authorInput.setEnabled(true);
                publisherInput.setEnabled(true);
                editionInput.setEnabled(true);
                titleInput.setText("");
                authorInput.setText("");
                publisherInput.setText("");
                editionInput.setText("");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown(); // Clean up thread pool
    }
}