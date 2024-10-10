package com.example.langhexx;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {
    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomNavigationView = findViewById(R.id.btnNav);
        addEvents();
        loadFragment();
    }

    public void addEvents(){
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                if (item.getItemId() == R.id.item_btt_nav_home){
                    selectedFragment = new HomeFragment();
                } else if (item.getItemId() == R.id.item_btt_nav_chat) {
                    selectedFragment = new ChatFragment();
                } else if (item.getItemId() == R.id.item_btt_nav_arena) {
                    selectedFragment = new ArenaFragment();
                } else if (item.getItemId() == R.id.item_btt_nav_profile) {
                    selectedFragment = new ProfileFragment();
                }
                if (selectedFragment != null) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.mainFrame, selectedFragment).commit();
                    return true;
                }
                return false;
            }
        });
    }

    private void loadFragment(){
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        Fragment fragment1 = new HomeFragment();
        fragmentTransaction.add(R.id.mainFrame,fragment1);
        fragmentTransaction.commit();
    }

    public void onBackPressed(){
        super.onBackPressed();
        moveTaskToBack(true);
    }
}