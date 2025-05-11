package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.example.langhexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity implements ChatFragment.KeyboardVisibilityListener{

    BottomNavigationView bottomNavigationView;
    FragmentManager fragmentManager;
    HomeFragment homeFragment;
    ChatFragment chatFragment;
    ArenaFragment arenaFragment;
    ProfileFragment profileFragment;
    Fragment activeFragment;
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


    public void onKeyboardVisibilityChanged(boolean isVisible) {
        if (bottomNavigationView != null) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.mainFrame); // Thay ID container
            if (currentFragment instanceof ChatFragment) {
                if (isVisible) {
                    bottomNavigationView.setVisibility(View.GONE);
                } else {
                    bottomNavigationView.setVisibility(View.VISIBLE);
                }
            } else {
                bottomNavigationView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void initializeFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            chatFragment = new ChatFragment();
            arenaFragment = new ArenaFragment();
            profileFragment = new ProfileFragment();
            fragmentManager.beginTransaction()
                    .add(R.id.mainFrame, profileFragment, TAG_PROFILE).hide(profileFragment)
                    .add(R.id.mainFrame, arenaFragment, TAG_ARENA).hide(arenaFragment)
                    .add(R.id.mainFrame, chatFragment, TAG_CHAT).hide(chatFragment)
                    .add(R.id.mainFrame, homeFragment, TAG_HOME) // Add and show Home last
                    .commit();
            activeFragment = homeFragment; // Home is active initially

        } else {
            homeFragment = (HomeFragment) fragmentManager.findFragmentByTag(TAG_HOME);
            chatFragment = (ChatFragment) fragmentManager.findFragmentByTag(TAG_CHAT);
            arenaFragment = (ArenaFragment) fragmentManager.findFragmentByTag(TAG_ARENA);
            profileFragment = (ProfileFragment) fragmentManager.findFragmentByTag(TAG_PROFILE);
            if (homeFragment != null && !homeFragment.isHidden()) {
                activeFragment = homeFragment;
            } else if (chatFragment != null && !chatFragment.isHidden()) {
                activeFragment = chatFragment;
            } else if (arenaFragment != null && !arenaFragment.isHidden()) {
                activeFragment = arenaFragment;
            } else if (profileFragment != null && !profileFragment.isHidden()) {
                activeFragment = profileFragment;
            } else {
                if (homeFragment != null) {
                    activeFragment = homeFragment;
                    fragmentManager.beginTransaction().show(activeFragment).commit();
                }
            }
        }
        if (activeFragment == null && homeFragment != null) {
            activeFragment = homeFragment;
        }
    }


    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                int itemId = item.getItemId(); // Use local variable for readability
                if (itemId == R.id.item_btt_nav_home) {
                    selectedFragment = homeFragment;
                } else if (itemId == R.id.item_btt_nav_chat) {
                    selectedFragment = chatFragment;
                } else if (itemId == R.id.item_btt_nav_arena) {
                    selectedFragment = arenaFragment;
                } else if (itemId == R.id.item_btt_nav_profile) {
                    selectedFragment = profileFragment;
                }

                if (selectedFragment != null && selectedFragment != activeFragment) {
                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    if (activeFragment != null) {
                        transaction.hide(activeFragment);
                    }
                    transaction.show(selectedFragment);
                    transaction.commit();

                    activeFragment = selectedFragment;
                    return true;
                }
                return selectedFragment != null;
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
    }
}