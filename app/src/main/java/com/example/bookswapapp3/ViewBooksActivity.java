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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SearchView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
    private Button selectDistanceButton;
    private SearchView searchView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable fetchRunnable;
    private long lastFetchTime = 0;
    private static final long DEBOUNCE_DELAY = 2000; // 2 seconds

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) return;
            for (Location location : locationResult.getLocations()) {
                userLat = location.getLatitude();
                userLon = location.getLongitude();
                Log.d("LocationUpdate", "User Location: " + userLat + ", " + userLon);
                fetchBooksDebounced();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_books);

        recyclerView = findViewById(R.id.recyclerViewBooks);
        selectDistanceButton = findViewById(R.id.btn_apply_distance);
        searchView = findViewById(R.id.search_books);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViewBookAdapter(this, bookList);
        recyclerView.setAdapter(adapter);

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

        checkGPSEnabled();

        selectDistanceButton.setOnClickListener(v -> showDistanceDialog());

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterBooks(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterBooks(newText);
                return false;
            }
        });
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

    private void showDistanceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_select_distance, null);
        builder.setView(view);

        RadioButton btn2km = view.findViewById(R.id.radio2km);
        RadioButton btn5km = view.findViewById(R.id.radio5km);
        RadioButton btn10km = view.findViewById(R.id.radio10km);
        Button applyButton = view.findViewById(R.id.apply_distance);

        AlertDialog dialog = builder.create();
        dialog.show();

        applyButton.setOnClickListener(v -> {
            if (btn2km.isChecked()) selectedDistance = 2;
            else if (btn5km.isChecked()) selectedDistance = 5;
            else if (btn10km.isChecked()) selectedDistance = 10;
            fetchBooksDebounced();
            dialog.dismiss();
        });
    }

    private void fetchBooksDebounced() {
        if (!searchView.isIconified() && !searchView.getQuery().toString().isEmpty()) {
            return; // Skip fetch during active search
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime >= DEBOUNCE_DELAY) {
            fetchBooksFromFirestore();
            lastFetchTime = currentTime;
        } else {
            if (fetchRunnable != null) handler.removeCallbacks(fetchRunnable);
            fetchRunnable = () -> {
                fetchBooksFromFirestore();
                lastFetchTime = System.currentTimeMillis();
            };
            handler.postDelayed(fetchRunnable, DEBOUNCE_DELAY);
        }
    }

    private void fetchBooksFromFirestore() {
        bookList.clear();
        Set<String> bookIds = new HashSet<>();
        String currentUserPhone = getSharedPreferences("BookSwapPrefs", MODE_PRIVATE).getString("phoneNumber", null);
        Log.d("FirestoreDebug", "Current User Phone: " + currentUserPhone);
        if (currentUserPhone == null) {
            Log.e("AuthError", "No user phone number found!");
            Toast.makeText(this, "Please log in first!", Toast.LENGTH_LONG).show();
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
                            if (distance <= selectedDistance * 1000) { // Test with 1000km: distance <= 1000000
                                book.setDistance(distance);
                                book.setFormattedDistance(formatDistance(distance));
                                tempList.add(book);
                            }
                        }
                    }
                    bookList.clear();
                    if (!tempList.isEmpty()) {
                        bookList.addAll(tempList);
                        bookListFull = new ArrayList<>(tempList); // For search
                        adapter.updateList(bookList);
                    } else {
                        Toast.makeText(this, "No books from other users found within " + selectedDistance + "km!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FirestoreError", "Error fetching books: " + e.getMessage());
                    Toast.makeText(this, "Failed to fetch books", Toast.LENGTH_SHORT).show();
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

    private String formatDistance(double distanceInMeters) {
        if (distanceInMeters < 1000) {
            return String.format("%.0f meter%s", distanceInMeters, distanceInMeters == 1 ? "" : "s");
        } else {
            return String.format("%.2f km", distanceInMeters / 1000);
        }
    }

    private void filterBooks(String query) {
        adapter.getFilter().filter(query); // Use adapter's filter
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