package com.example.bookswapapp3;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
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
    private List<BookModel> allBooks = new ArrayList<>(); // All books before pagination
    private List<BookModel> originalAllBooks = new ArrayList<>(); // Store unfiltered books for live search
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private double userLat = 0.0, userLon = 0.0;
    private boolean isLocationPending = false; // Track if we're waiting for a location
    private boolean isFetchingBooks = false; // Track if we're currently fetching books
    private int selectedDistance = 2; // Default 2km
    private String selectedAction = "All"; // Default action filter
    private String currentQuery = ""; // Current search query
    private Button applyFiltersButton;
    private ImageButton backButton;
    private EditText searchEditText;
    private Spinner actionSpinner;
    private Spinner distanceSpinner;
    private ProgressBar loadingProgressBar;
    private View loadingOverlay;
    private TextView errorMessage;
    private BottomNavigationView bottomNavigation;
    private LinearLayout paginationContainer;

    private static final int BOOKS_PER_PAGE = 10;
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private int currentPage = 1;
    private int totalPages = 1;
    private DocumentSnapshot lastVisibleDocument = null;

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) return;
            for (Location location : locationResult.getLocations()) {
                userLat = location.getLatitude();
                userLon = location.getLongitude();
                Log.d("LocationUpdate", "User Location: " + userLat + ", " + userLon);
                // If we were waiting for a location, fetch books now
                if (isLocationPending) {
                    isLocationPending = false;
                    fetchBooks();
                }
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
        paginationContainer = findViewById(R.id.paginationContainer);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViewBookAdapter(this, bookList);
        recyclerView.setAdapter(adapter);

        // Add layout animation for RecyclerView items
        LayoutAnimationController animation = android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_item_slide_in);
        recyclerView.setLayoutAnimation(animation);

        // Enable smooth scrolling animation
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

        // Search functionality with live filtering
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s.toString().trim();
                currentPage = 1; // Reset to page 1 on new search query
                filterBooks(); // Filter locally instead of fetching from Firestore
            }
        });

        // Apply Filters button to fetch books
        applyFiltersButton.setOnClickListener(v -> {
            currentPage = 1; // Reset to page 1 when applying filters
            fetchBooksWithLocationCheck();
        });

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
                startActivity(new Intent(this, AddBookActivity.class));
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
            // Try to get the last known location immediately
            getLastKnownLocation();
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000)
                .setSmallestDisplacement(10); // Require at least 10m movement for updates

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            userLat = location.getLatitude();
                            userLon = location.getLongitude();
                            Log.d("LocationUpdate", "Last Known Location: " + userLat + ", " + userLon);
                            // If we were waiting for a location, fetch books now
                            if (isLocationPending) {
                                isLocationPending = false;
                                fetchBooks();
                            }
                        } else {
                            Log.d("LocationUpdate", "Last known location not available");
                        }
                    })
                    .addOnFailureListener(this, e -> {
                        Log.e("LocationError", "Failed to get last known location: " + e.getMessage());
                    });
        }
    }

    private void fetchBooksWithLocationCheck() {
        // Reset UI state before fetching
        errorMessage.setVisibility(View.GONE); // Hide error message
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingProgressBar.setVisibility(View.VISIBLE);

        // Check if we have a location
        if (userLat == 0.0 || userLon == 0.0) {
            // If we don't have a location yet, try to get the last known location
            getLastKnownLocation();
            // If we still don't have a location, wait for the callback
            if (userLat == 0.0 || userLon == 0.0) {
                isLocationPending = true;
                Toast.makeText(this, "Fetching your location, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // If we have a location, proceed with fetching books
        fetchBooks();
    }

    private void fetchBooks() {
        isFetchingBooks = true; // Set flag to indicate fetching is in progress
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingProgressBar.setVisibility(View.VISIBLE);
        errorMessage.setVisibility(View.GONE); // Ensure error message is hidden

        Set<String> bookIds = new HashSet<>();
        String currentUserPhone = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE).getString("phoneNumber", null);
        Log.d("FirestoreDebug", "Current User Phone: " + currentUserPhone);
        if (currentUserPhone == null) {
            Log.e("AuthError", "No user phone number found!");
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
            isFetchingBooks = false;
            return;
        }

        if (userLat == 0.0 || userLon == 0.0) {
            Log.e("LocationError", "User location not available!");
            Toast.makeText(this, "Unable to get your location. Please try again.", Toast.LENGTH_LONG).show();
            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
            isFetchingBooks = false;
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

        Query query = db.collectionGroup("books")
                .whereGreaterThanOrEqualTo("geohash", startHash)
                .whereLessThanOrEqualTo("geohash", endHash);

        query.get().addOnSuccessListener(querySnapshot -> {
            List<BookModel> tempList = new ArrayList<>();
            Log.d("FirestoreDebug", "Fetched " + querySnapshot.size() + " books");
            for (QueryDocumentSnapshot bookDoc : querySnapshot) {
                BookModel book = bookDoc.toObject(BookModel.class);
                String bookUserPhone = book.getUserPhoneNumber();
                Log.d("FirestoreDebug", "Book: " + book.getTitle() + ", Author: " + book.getAuthor() + ", UserPhone: " + bookUserPhone);
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

            // Update the lists only after fetching is complete
            originalAllBooks.clear();
            allBooks.clear();
            bookList.clear();
            originalAllBooks.addAll(tempList); // Store the unfiltered list
            allBooks.addAll(tempList);
            bookListFull = new ArrayList<>(tempList);

            // Reset search query and clear search bar
            currentQuery = "";
            searchEditText.setText("");

            // Calculate total pages
            totalPages = (int) Math.ceil((double) allBooks.size() / BOOKS_PER_PAGE);
            if (totalPages == 0) totalPages = 1; // Ensure at least 1 page

            // Load the current page
            loadPage(currentPage);

            // Update pagination bar
            updatePaginationBar();

            // Check if no books were found *after* all updates are done
            if (allBooks.isEmpty()) {
                errorMessage.setVisibility(View.VISIBLE);
                Toast.makeText(this, "No books from other users found within " + selectedDistance + "km!", Toast.LENGTH_SHORT).show();
            } else {
                errorMessage.setVisibility(View.GONE); // Explicitly hide the error message if books are found
            }

            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
            Log.d("UIDebug", "Loading overlay hidden");
            isFetchingBooks = false; // Fetching is complete
        }).addOnFailureListener(e -> {
            Log.e("FirestoreError", "Error fetching books: " + e.getMessage());
            Toast.makeText(this, "Failed to fetch books", Toast.LENGTH_SHORT).show();
            loadingOverlay.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.GONE);
            errorMessage.setVisibility(View.VISIBLE);
            Log.d("UIDebug", "Loading overlay hidden");
            isFetchingBooks = false; // Fetching is complete
        });
    }

    private void filterBooks() {
        // Don't filter while fetching books
        if (isFetchingBooks) {
            Log.d("FilterDebug", "Skipping filterBooks() while fetching books");
            return;
        }

        List<BookModel> filteredList = new ArrayList<>();
        if (currentQuery.isEmpty()) {
            filteredList.addAll(originalAllBooks); // Show all books if query is empty
        } else {
            String queryLower = currentQuery.toLowerCase();
            for (BookModel book : originalAllBooks) {
                if (book.getTitle().toLowerCase().contains(queryLower) ||
                        book.getAuthor().toLowerCase().contains(queryLower)) {
                    filteredList.add(book);
                }
            }
        }

        // Update allBooks with filtered results for pagination
        allBooks.clear();
        allBooks.addAll(filteredList);

        // Update pagination and load the first page
        totalPages = (int) Math.ceil((double) allBooks.size() / BOOKS_PER_PAGE);
        if (totalPages == 0) totalPages = 1; // Ensure at least 1 page
        loadPage(currentPage);
        updatePaginationBar();

        if (allBooks.isEmpty()) {
            errorMessage.setVisibility(View.VISIBLE);
            Toast.makeText(this, "No books match your search!", Toast.LENGTH_SHORT).show();
        } else {
            errorMessage.setVisibility(View.GONE);
        }
    }

    private void loadPage(int page) {
        bookList.clear();
        int startIndex = (page - 1) * BOOKS_PER_PAGE;
        int endIndex = Math.min(startIndex + BOOKS_PER_PAGE, allBooks.size());

        if (startIndex >= allBooks.size()) {
            Log.d("PaginationDebug", "No books to display on page " + page + ". Start index: " + startIndex + ", Total books: " + allBooks.size());
            adapter.notifyDataSetChanged();
            return;
        }

        bookList.addAll(allBooks.subList(startIndex, endIndex));
        Log.d("PaginationDebug", "Loaded page " + page + ": " + bookList.size() + " books");
        Log.d("PaginationDebug", "bookList size before notify: " + bookList.size());
        adapter.notifyDataSetChanged();
        recyclerView.scheduleLayoutAnimation(); // Trigger animation
        recyclerView.scrollToPosition(0); // Scroll to top of the page
    }

    private void updatePaginationBar() {
        paginationContainer.removeAllViews();
        for (int i = 1; i <= totalPages; i++) {
            TextView pageButton = new TextView(this);
            pageButton.setText(String.valueOf(i));
            pageButton.setPadding(16, 8, 16, 8);
            pageButton.setTextSize(16);
            if (i == currentPage) {
                pageButton.setTextColor(getResources().getColor(android.R.color.white));
                pageButton.setBackgroundResource(R.drawable.button_background);
            } else {
                pageButton.setTextColor(getResources().getColor(android.R.color.black));
                pageButton.setBackgroundResource(android.R.drawable.btn_default);
            }

            final int pageNum = i;
            pageButton.setOnClickListener(v -> {
                currentPage = pageNum;
                loadPage(currentPage);
                updatePaginationBar();
            });

            paginationContainer.addView(pageButton);
        }
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

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
        getLastKnownLocation(); // Try to get location on resume
    }

    @Override
    protected void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                getLastKnownLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Cannot fetch books.", Toast.LENGTH_LONG).show();
            }
        }
    }
}