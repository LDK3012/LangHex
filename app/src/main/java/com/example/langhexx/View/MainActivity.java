//package com.example.langhexx.View;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.fragment.app.Fragment;
//import androidx.fragment.app.FragmentManager;
//import androidx.fragment.app.FragmentTransaction;
//
//import android.os.Bundle;
//import android.view.MenuItem;
//
//import com.example.langhexx.R;
//import com.google.android.material.bottomnavigation.BottomNavigationView;
//import com.google.android.material.navigation.NavigationBarView;
//
//public class MainActivity extends AppCompatActivity {
//    BottomNavigationView bottomNavigationView;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        bottomNavigationView = findViewById(R.id.btnNav);
//        addEvents();
//        loadFragment();
//    }
//
//    public void addEvents(){
//        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
//            @Override
//            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
//                Fragment selectedFragment = null;
//                if (item.getItemId() == R.id.item_btt_nav_home){
//                    selectedFragment = new HomeFragment();
//                } else if (item.getItemId() == R.id.item_btt_nav_chat) {
//                    selectedFragment = new ChatFragment();
//                } else if (item.getItemId() == R.id.item_btt_nav_arena) {
//                    selectedFragment = new ArenaFragment();
//                } else if (item.getItemId() == R.id.item_btt_nav_profile) {
//                    selectedFragment = new ProfileFragment();
//                }
//                if (selectedFragment != null) {
//                    getSupportFragmentManager().beginTransaction().replace(R.id.mainFrame, selectedFragment).commit();
//                    return true;
//                }
//                return false;
//            }
//        });
//    }
//
//    private void loadFragment(){
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
//        Fragment fragment1 = new HomeFragment();
//        fragmentTransaction.add(R.id.mainFrame,fragment1);
//        fragmentTransaction.commit();
//
//    }
//
//    public void onBackPressed(){
//        super.onBackPressed();
//        moveTaskToBack(true);
//    }
//}

package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;

import com.example.langhexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;
    FragmentManager fragmentManager;

    // Declare fragments as member variables
    HomeFragment homeFragment;
    ChatFragment chatFragment;
    ArenaFragment arenaFragment;
    ProfileFragment profileFragment;
    Fragment activeFragment; // To keep track of the currently visible fragment

    // Tags for restoring fragments on configuration change or process death
    private static final String TAG_HOME = "HOME_FRAGMENT";
    private static final String TAG_CHAT = "CHAT_FRAGMENT";
    private static final String TAG_ARENA = "ARENA_FRAGMENT";
    private static final String TAG_PROFILE = "PROFILE_FRAGMENT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.btnNav);
        fragmentManager = getSupportFragmentManager();

        initializeFragments(savedInstanceState);
        setupBottomNavigation();

        // Set the initial selected item in the BottomNavigationView
        // This ensures the visual state matches the actual fragment shown
        if (activeFragment == homeFragment) {
            bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_home);
        } else if (activeFragment == chatFragment) {
            bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_chat);
        } else if (activeFragment == arenaFragment) {
            bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_arena);
        } else if (activeFragment == profileFragment) {
            bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_profile);
        }
    }

    private void initializeFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            // First time creation: create new instances
            homeFragment = new HomeFragment();
            chatFragment = new ChatFragment();
            arenaFragment = new ArenaFragment();
            profileFragment = new ProfileFragment();

            // Add all fragments to the manager, hide all except the default (Home)
            // Use tags to be able to find them later
            fragmentManager.beginTransaction()
                    .add(R.id.mainFrame, profileFragment, TAG_PROFILE).hide(profileFragment)
                    .add(R.id.mainFrame, arenaFragment, TAG_ARENA).hide(arenaFragment)
                    .add(R.id.mainFrame, chatFragment, TAG_CHAT).hide(chatFragment)
                    .add(R.id.mainFrame, homeFragment, TAG_HOME) // Add and show Home last
                    .commit();
            activeFragment = homeFragment; // Home is active initially

        } else {
            // Activity is being recreated: retrieve existing fragments by tag
            homeFragment = (HomeFragment) fragmentManager.findFragmentByTag(TAG_HOME);
            chatFragment = (ChatFragment) fragmentManager.findFragmentByTag(TAG_CHAT);
            arenaFragment = (ArenaFragment) fragmentManager.findFragmentByTag(TAG_ARENA);
            profileFragment = (ProfileFragment) fragmentManager.findFragmentByTag(TAG_PROFILE);

            // Find the fragment that was active before recreation
            // FragmentManager usually restores the visibility state (show/hide)
            if (homeFragment != null && !homeFragment.isHidden()) {
                activeFragment = homeFragment;
            } else if (chatFragment != null && !chatFragment.isHidden()) {
                activeFragment = chatFragment;
            } else if (arenaFragment != null && !arenaFragment.isHidden()) {
                activeFragment = arenaFragment;
            } else if (profileFragment != null && !profileFragment.isHidden()) {
                activeFragment = profileFragment;
            } else {
                // Fallback: If no fragment is found as visible (shouldn't normally happen),
                // default to homeFragment if it exists. Ensure it's shown.
                if (homeFragment != null) {
                    activeFragment = homeFragment;
                    fragmentManager.beginTransaction().show(activeFragment).commit();
                }
                // Handle the case where even homeFragment is null if necessary
            }
        }
        // Ensure we always have a reference to an active fragment if possible
        if (activeFragment == null && homeFragment != null) {
            activeFragment = homeFragment; // Default fallback after recreation
        }
    }


    // Renamed from addEvents for clarity
    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                int itemId = item.getItemId(); // Use local variable for readability

                // Get the reference to the target fragment based on the menu item ID
                if (itemId == R.id.item_btt_nav_home) {
                    selectedFragment = homeFragment;
                } else if (itemId == R.id.item_btt_nav_chat) {
                    selectedFragment = chatFragment;
                } else if (itemId == R.id.item_btt_nav_arena) {
                    selectedFragment = arenaFragment;
                } else if (itemId == R.id.item_btt_nav_profile) {
                    selectedFragment = profileFragment;
                }

                // Check if the selected fragment is valid and different from the current active one
                if (selectedFragment != null && selectedFragment != activeFragment) {
                    // Perform the transaction: hide the current active fragment, show the selected one
                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    if (activeFragment != null) {
                        transaction.hide(activeFragment);
                    }
                    transaction.show(selectedFragment);
                    transaction.commit(); // Commit the transaction

                    activeFragment = selectedFragment; // Update the active fragment reference
                    return true; // Indicate the selection was handled
                }
                // If the selected fragment is the same as the active one, or null, do nothing
                // Return false prevents the item from being shown as selected (if default behavior is desired)
                // Return true allows reselection visually without fragment transaction
                return selectedFragment != null; // Return true if a valid fragment was targeted
            }
        });
    }

    // No need for the old loadFragment() method
    // private void loadFragment(){ ... } // REMOVED

    @Override
    public void onBackPressed() {
        // Current behavior: Move the task to the background instead of finishing the activity.
        // Call super first is generally good practice, though order might not matter here.
        super.onBackPressed();
        moveTaskToBack(true);
        // If you wanted the default behavior (finish activity), you would just call:
        // super.onBackPressed();
    }
}