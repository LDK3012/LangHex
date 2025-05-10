package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent; // Quan trọng
import android.os.Bundle;
import android.util.Log; // Giữ lại Log để debug nếu cần
import android.view.MenuItem;
import android.view.View; // Cần cho setVisibility

import com.example.langhexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity { // Bỏ KeyboardVisibilityListener nếu không dùng chung ở đây

    private static final String TAG = "MainActivity"; // Thêm TAG cho MainActivity

    BottomNavigationView bottomNavigationView;
    FragmentManager fragmentManager;

    // Khai báo các Fragment
    HomeFragment homeFragment;
    ChatFragment chatFragment;
    ArenaFragment arenaFragment;
    ProfileFragment profileFragment;
    Fragment activeFragment; // Theo dõi Fragment đang hiển thị

    // TAGs để khôi phục Fragment
    private static final String TAG_HOME = "HOME_FRAGMENT";
    private static final String TAG_CHAT = "CHAT_FRAGMENT";
    private static final String TAG_ARENA = "ARENA_FRAGMENT";
    private static final String TAG_PROFILE = "PROFILE_FRAGMENT";

    // Key cho extra trong Intent để điều hướng
    public static final String TARGET_FRAGMENT_EXTRA = "TARGET_FRAGMENT"; // Public để Activity khác có thể dùng

    // --- ADDED --- Hằng số mới cho action điều hướng về Home
    public static final String ACTION_NAVIGATE_TO_HOME = "ACTION_NAVIGATE_TO_HOME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate called");

        bottomNavigationView = findViewById(R.id.btnNav);
        fragmentManager = getSupportFragmentManager();

        initializeFragments(savedInstanceState);
        setupBottomNavigation();

        // Xử lý Intent khi Activity được tạo mới (ví dụ từ InternalReadingTopic)
        processNavigationIntent(getIntent());

        // Cập nhật lựa chọn trên BottomNavigationView dựa trên activeFragment
        updateBottomNavSelection();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        setIntent(intent); // Cập nhật Intent hiện tại của Activity
        // Xử lý Intent khi Activity đã chạy và nhận được Intent mới
        processNavigationIntent(intent);
        updateBottomNavSelection();
    }

    private void processNavigationIntent(Intent intent) {
        if (intent != null && intent.hasExtra(TARGET_FRAGMENT_EXTRA)) {
            String targetActionOrTag = intent.getStringExtra(TARGET_FRAGMENT_EXTRA);
            Log.d(TAG, "processNavigationIntent: Received target: " + targetActionOrTag);
            boolean navigationHandledByAddedBlock = false; // --- ADDED --- Cờ để theo dõi

            // --- START OF ADDED CODE BLOCK ---
            // Kiểm tra action điều hướng về Home mới được thêm
            if (ACTION_NAVIGATE_TO_HOME.equals(targetActionOrTag)) {
                Log.d(TAG, "ACTION_NAVIGATE_TO_HOME detected by new block.");
                if (homeFragment != null) { // Đảm bảo homeFragment đã được khởi tạo
                    if (activeFragment != homeFragment) {
                        Log.d(TAG, "New block: Switching to HomeFragment via ACTION_NAVIGATE_TO_HOME.");
                        FragmentTransaction ft = fragmentManager.beginTransaction();
                        if (activeFragment != null) { // Chỉ hide nếu activeFragment không null
                            ft.hide(activeFragment);
                        }
                        ft.show(homeFragment).commit(); // Hiển thị homeFragment
                        activeFragment = homeFragment; // Cập nhật activeFragment
                    } else {
                        Log.d(TAG, "New block: Already on HomeFragment. ACTION_NAVIGATE_TO_HOME received.");
                    }
                    navigationHandledByAddedBlock = true; // --- ADDED --- Đánh dấu đã xử lý
                } else {
                    Log.e(TAG, "New block: HomeFragment is null, cannot navigate via ACTION_NAVIGATE_TO_HOME.");
                }
            }
            // --- END OF ADDED CODE BLOCK ---

            // Logic gốc của bạn, sẽ chỉ chạy nếu khối code mới không xử lý
            if (!navigationHandledByAddedBlock) { // --- ADDED --- Điều kiện dựa trên cờ
                if (TAG_HOME.equals(targetActionOrTag)) {
                    if (homeFragment != null && activeFragment != homeFragment) {
                        Log.d(TAG, "Original logic: Switching to HomeFragment from intent (using TAG_HOME).");
                        FragmentTransaction ft = fragmentManager.beginTransaction();
                        if (activeFragment != null) {
                            ft.hide(activeFragment);
                        }
                        ft.show(homeFragment).commit();
                        activeFragment = homeFragment;
                    }
                }
                // Thêm else if cho các fragment khác nếu cần điều hướng tương tự theo logic gốc
                // ví dụ: else if (TAG_CHAT.equals(targetActionOrTag)) { ... }
            }

            // Xóa extra để không xử lý lại khi xoay màn hình hoặc Activity resume
            // Dòng này là của bạn và được giữ nguyên vị trí.
            intent.removeExtra(TARGET_FRAGMENT_EXTRA);
        }
    }

    private void initializeFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            Log.d(TAG, "Initializing new fragments.");
            homeFragment = new HomeFragment();
            chatFragment = new ChatFragment();
            arenaFragment = new ArenaFragment();
            profileFragment = new ProfileFragment();

            fragmentManager.beginTransaction()
                    .add(R.id.mainFrame, profileFragment, TAG_PROFILE).hide(profileFragment)
                    .add(R.id.mainFrame, arenaFragment, TAG_ARENA).hide(arenaFragment)
                    .add(R.id.mainFrame, chatFragment, TAG_CHAT).hide(chatFragment)
                    .add(R.id.mainFrame, homeFragment, TAG_HOME) // HomeFragment được show mặc định
                    .commit();
            activeFragment = homeFragment;
        } else {
            Log.d(TAG, "Restoring existing fragments by tag.");
            homeFragment = (HomeFragment) fragmentManager.findFragmentByTag(TAG_HOME);
            chatFragment = (ChatFragment) fragmentManager.findFragmentByTag(TAG_CHAT);
            arenaFragment = (ArenaFragment) fragmentManager.findFragmentByTag(TAG_ARENA);
            profileFragment = (ProfileFragment) fragmentManager.findFragmentByTag(TAG_PROFILE);

            if (homeFragment != null && !homeFragment.isHidden()) activeFragment = homeFragment;
            else if (chatFragment != null && !chatFragment.isHidden()) activeFragment = chatFragment;
            else if (arenaFragment != null && !arenaFragment.isHidden()) activeFragment = arenaFragment;
            else if (profileFragment != null && !profileFragment.isHidden()) activeFragment = profileFragment;
            else {
                if (homeFragment != null) {
                    activeFragment = homeFragment;
                    if (activeFragment.isHidden()) {
                        fragmentManager.beginTransaction().show(activeFragment).commit();
                    }
                }
            }
        }
        if (activeFragment == null && homeFragment != null) {
            activeFragment = homeFragment;
        }
        Log.d(TAG, "Active fragment after initialization: " + (activeFragment != null ? activeFragment.getClass().getSimpleName() : "null"));
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                int itemId = item.getItemId();

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
                    Log.d(TAG, "BottomNav: Switching from " + (activeFragment != null ? activeFragment.getClass().getSimpleName() : "null") + " to " + selectedFragment.getClass().getSimpleName());
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

    private void updateBottomNavSelection() {
        Log.d(TAG, "updateBottomNavSelection called. Current activeFragment: " + (activeFragment != null ? activeFragment.getClass().getSimpleName() : "null"));
        if (activeFragment == null) return;

        if (activeFragment == homeFragment) {
            if (bottomNavigationView.getSelectedItemId() != R.id.item_btt_nav_home) {
                bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_home);
                Log.d(TAG, "BottomNav selection updated to: Home");
            }
        } else if (activeFragment == chatFragment) {
            if (bottomNavigationView.getSelectedItemId() != R.id.item_btt_nav_chat) {
                bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_chat);
                Log.d(TAG, "BottomNav selection updated to: Chat");
            }
        } else if (activeFragment == arenaFragment) {
            if (bottomNavigationView.getSelectedItemId() != R.id.item_btt_nav_arena) {
                bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_arena);
                Log.d(TAG, "BottomNav selection updated to: Arena");
            }
        } else if (activeFragment == profileFragment) {
            if (bottomNavigationView.getSelectedItemId() != R.id.item_btt_nav_profile) {
                bottomNavigationView.setSelectedItemId(R.id.item_btt_nav_profile);
                Log.d(TAG, "BottomNav selection updated to: Profile");
            }
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        Log.d(TAG, "onBackPressed: Moving task to back.");
    }
}
