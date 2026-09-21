package local.uway.walletbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

final class State {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("bridge", Context.MODE_PRIVATE); }
    static String pkg(Context c) { return prefs(c).getString("package", RoutePolicy.DEFAULT_PACKAGE); }
    static String cls(Context c) { return prefs(c).getString("class", RoutePolicy.DEFAULT_CLASS); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static boolean capturing(Context c) {
        long remaining = prefs(c).getLong("capture_until", 0) - SystemClock.elapsedRealtime();
        return remaining > 0 && remaining <= 60000;
    }
    static void note(Context c, String message) { prefs(c).edit().putString("status", message).apply(); }
    static void capture(Context c, String pkg, String cls) {
        String key = pkg + "/" + RoutePolicy.normalizeClass(pkg, cls);
        String old = prefs(c).getString("candidates", "");
        String[] rows = old.isEmpty() ? new String[0] : old.split("\n");
        for (String row : rows) if (row.equals(key)) return;
        if (rows.length >= 20) return;
        prefs(c).edit().putString("candidates", old.isEmpty() ? key : old + "\n" + key).apply();
    }
}
