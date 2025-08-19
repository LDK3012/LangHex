package vn.enghexteam.enghex.Model;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class TokenLimiter {
    private static final String PREF_NAME = "chat_token_limit";
    private static final String KEY_TIMESTAMPS = "timestamps";
    private static final int LIMIT = 10;
    private static final long ONE_HOUR = 60 * 60 * 1000L;

    public static boolean canSend(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String raw = prefs.getString(KEY_TIMESTAMPS, "");
        List<Long> timestamps = new ArrayList<>();

        for (String ts : raw.split(",")) {
            if (!ts.isEmpty()) {
                try {
                    long time = Long.parseLong(ts);
                    if (now - time < ONE_HOUR) {
                        timestamps.add(time);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (timestamps.size() >= LIMIT) {
            return false;
        }

        timestamps.add(now);
        StringBuilder sb = new StringBuilder();
        for (Long t : timestamps) {
            sb.append(t).append(",");
        }
        prefs.edit().putString(KEY_TIMESTAMPS, sb.toString()).apply();
        return true;
    }
}
