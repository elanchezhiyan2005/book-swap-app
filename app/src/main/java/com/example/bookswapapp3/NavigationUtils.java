package com.example.bookswapapp3;

import android.app.Activity;
import android.content.Intent;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class NavigationUtils {

    public static void setupBottomNavigation(Activity activity, BottomNavigationView bottomNavigationView, int selectedItemId) {
        // Set the selected item
        bottomNavigationView.setSelectedItemId(selectedItemId);

        // Set listener for navigation
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                Class<?> targetActivity = null;

                if (itemId == R.id.nav_home) {
                    targetActivity = HomeActivity.class;
                } else if (itemId == R.id.nav_browse) {
                    targetActivity = ViewBooksActivity.class; // Browse maps to ViewBooksActivity
                } else if (itemId == R.id.nav_add) {
                    targetActivity = AddBookActivity.class;
                } else if (itemId == R.id.nav_chat) {
                    targetActivity = MessagesActivity.class; // Chat maps to MessagesActivity
                } else if (itemId == R.id.nav_profile) {
                    targetActivity = ProfileActivity.class;
                }

                // If the target activity is not the current activity, navigate to it
                if (targetActivity != null && !activity.getClass().equals(targetActivity)) {
                    Intent intent = new Intent(activity, targetActivity);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                    activity.finish();
                    return true;
                }
                return false;
            }
        });

        // Customize icon for Add item when active
        bottomNavigationView.getMenu().findItem(R.id.nav_add).setIcon(
                selectedItemId == R.id.nav_add ? R.drawable.ic_plus : R.drawable.ic_plus
        );
    }
}