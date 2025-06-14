package com.example.bookswapapp3;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ch.hsr.geohash.GeoHash;

public class ViewBooksActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ViewBookAdapter adapter;
    private List<BookModel> bookList = new ArrayList<>();
    private List<BookModel> bookListFull = new ArrayList<>(); // For search
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private double userLat = 0.0, userLon = 0.0;
    private int selectedDistance = 2; // Default 2km
    private String selectedAction = "All"; // Default action filter
    private Button applyFiltersButton;
    private ImageButton backButton;
    private EditText searchEditText;
    private Spinner actionSpinner;
    private Spinner distanceSpinner;
    private ProgressBar loadingProgressBar;
    private View loadingOverlay;
    private TextView errorMessage;
    private BottomNavigationView bottomNavigation;

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) return;
            for (Location location : locationResult.getLocations()) {
                userLat = location.getLatitude();
                userLon = location.getLongitude();
                Log.d("LocationUpdate", "User Location: " + userLat + ", " + userLon);
                // Do not fetch books here; wait for "Apply Filters" button press
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_books);

        recyclerView = findViewById(R.id.recyclerViewBooks);
        applyFiltersButton = findViewById(R.id.btn_apply_filters);
        backButton = findViewById(R.id.backButton);
        searchEditText = findViewById(R.id.searchEditText);
        actionSpinner = findViewById(R.id.actionSpinner);
        distanceSpinner = findViewById(R.id.distanceSpinner);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        errorMessage = findViewById(R.id.errorMessage);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViewBookAdapter(this, bookList);
        recyclerView.setAdapter(adapter);

        // Enable smooth scrolling with OvershootInterpolator
        recyclerView.setLayoutAnimation(
                android.view.animation.AnimationUtils.loadLayoutAnimation(
                        this, R.anim.layout_animation_fall_down));
        recyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator() {
            @Override
            public void onAnimationFinished(@NonNull RecyclerView.ViewHolder viewHolder) {
                super.onAnimationFinished(viewHolder);
                recyclerView.smoothScrollBy(0, 0, new OvershootInterpolator());
            }
        });

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Verify user phone number
        String currentUserPhone = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE).getString("phoneNumber", null);
        Log.d("AuthDebug", "ViewBooks - Current User Phone: " + currentUserPhone);
        if (currentUserPhone == null) {
            Log.e("AuthError", "No user phone number found!");
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Setup Action Spinner
        ArrayAdapter<CharSequence> actionAdapter = ArrayAdapter.createFromResource(
                this, R.array.action_options, android.R.layout.simple_spinner_item);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);
        actionSpinner.setSelection(0); // Default to "All"
        actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAction = parent.getItemAtPosition(position).toString();
                // Do not fetch books here; wait for "Apply Filters" button press
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Setup Distance Spinner
        ArrayAdapter<CharSequence> distanceAdapter = ArrayAdapter.createFromResource(
                this, R.array.distance_options, android.R.layout.simple_spinner_item);
        distanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        distanceSpinner.setAdapter(distanceAdapter);
        distanceSpinner.setSelection(0); // Default to "2km"
        distanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String distanceStr = parent.getItemAtPosition(position).toString().replace("km", "");
                selectedDistance = Integer.parseInt(distanceStr);
                // Do not fetch books here; wait for "Apply Filters" button press
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Back button to HomeActivity
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ViewBooksActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });

        // Search functionality
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterBooks(s.toString());
            }
        });

        // Apply Filters button to fetch books
        applyFiltersButton.setOnClickListener(v -> fetchBooks());

        // Bottom Navigation
        bottomNavigation.setSelectedItemId(R.id.nav_browse);
        bottomNavigation.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_browse) {
                return true;
            } else if (itemId == R.id.nav_add) {
                startActivity(new Intent(this, AddNewBookActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_chat) {
                Toast.makeText(this, "Chat functionality not implemented yet", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Toast.makeText(this, "Profile functionality not implemented yet", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        checkGPSEnabled();
    }

    private void checkGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isGPS) {
            Toast.makeText(this, "Please enable GPS!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    private void fetchBooks() {
        // Show loading overlay with spinner
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingProgressBar.setVisibility(View.VISIBLE);
        errorMessage.setVisibility(View.GONE);
        bookList.clear();

        Set<String> bookIds = new HashSet<>();
        String currentUserPhone = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE).getString("phoneNumber", null);
        Log.d("FirestoreDebug", "Current User Phone: " + currentUserPhone);
        if (currentUserPhone == null) {
            Log.e("AuthError", "No user phone number found!");
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
            return;
        }

        if (userLat == 0.0 || userLon == 0.0) {
            Log.e("LocationError", "User location not available!");
            Toast.makeText(this, "Unable to get your location. Please try again.", Toast.LENGTH_LONG).show();
            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
            return;
        }

        GeoHash centerHash = GeoHash.withCharacterPrecision(userLat, userLon, 5);
        GeoHash[] neighbors = centerHash.getAdjacent();
        List<String> geohashList = new ArrayList<>();
        geohashList.add(centerHash.toBase32());
        for (GeoHash neighbor : neighbors) {
            geohashList.add(neighbor.toBase32());
        }
        Collections.sort(geohashList);
        String startHash = geohashList.get(0);
        String endHash = geohashList.get(geohashList.size() - 1);
        Log.d("FirestoreDebug", "Geohash Range: " + startHash + " to " + endHash);

        db.collectionGroup("books")
                .whereGreaterThanOrEqualTo("geohash", startHash)
                .whereLessThanOrEqualTo("geohash", endHash)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<BookModel> tempList = new ArrayList<>();
                    Log.d("FirestoreDebug", "Fetched " + querySnapshot.size() + " books");
                    for (QueryDocumentSnapshot bookDoc : querySnapshot) {
                        BookModel book = bookDoc.toObject(BookModel.class);
                        String bookUserPhone = book.getUserPhoneNumber();
                        Log.d("FirestoreDebug", "Book: " + book.getTitle() + ", UserPhone: " + bookUserPhone);
                        if (bookUserPhone != null && bookUserPhone.equals(currentUserPhone)) {
                            Log.d("FirestoreDebug", "Skipping own book: " + book.getTitle());
                            continue;
                        }
                        if (book.getTitle() == null || book.getAuthor() == null) {
                            Log.e("FirestoreError", "Book missing fields: " + bookDoc.getId());
                            continue;
                        }
                        if (!bookIds.contains(bookDoc.getId()) && book.getLatitude() != 0 && book.getLongitude() != 0) {
                            bookIds.add(bookDoc.getId());
                            double distance = calculateDistance(userLat, userLon, book.getLatitude(), book.getLongitude());
                            if (distance <= selectedDistance * 1000) {
                                // Apply action filter
                                if (selectedAction.equals("All") ||
                                        (selectedAction.equals("Swap") && book.getAction() != null && book.getAction().equalsIgnoreCase("Swap")) ||
                                        (selectedAction.equals("Lend") && book.getAction() != null && book.getAction().equalsIgnoreCase("Lend")) ||
                                        (selectedAction.equals("Buy") && book.getAction() != null && book.getAction().equalsIgnoreCase("Buy"))) {
                                    book.setDistance(distance);
                                    tempList.add(book);
                                }
                            }
                        }
                    }
                    bookList.clear();
                    if (!tempList.isEmpty()) {
                        bookList.addAll(tempList);
                        bookListFull = new ArrayList<>(tempList);
                        adapter.updateList(bookList);
                    } else {
                        errorMessage.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "No books from other users found within " + selectedDistance + "km!", Toast.LENGTH_SHORT).show();
                    }
                    // Hide loading overlay and spinner
                    loadingOverlay.setVisibility(View.GONE);
                    loadingProgressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirestoreError", "Error fetching books: " + e.getMessage());
                    Toast.makeText(this, "Failed to fetch books", Toast.LENGTH_SHORT).show();
                    loadingOverlay.setVisibility(View.GONE);
                    loadingProgressBar.setVisibility(View.GONE);
                    errorMessage.setVisibility(View.VISIBLE);
                });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of Earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // Distance in meters
    }

    private void filterBooks(String query) {
        adapter.getFilter().filter(query);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }
}