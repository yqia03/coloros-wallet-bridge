package local.uway.walletbridge;

/** Pure routing rules; contains no Android state or dependencies. */
public final class RoutePolicy {
    public static final String DEFAULT_PACKAGE = "com.heytap.tas";
    public static final String DEFAULT_CLASS = "com.nearme.wallet.nfc.ui.NfcConsumeActivity";
    public static final String[] PACKAGES = {
        DEFAULT_PACKAGE, "com.finshell.wallet", "com.heytap.wallet"
    };
    public static final long COOLDOWN_MS = 1500L;

    private long lastClaimElapsed = Long.MIN_VALUE;
    private boolean hasClaim;

    /** Expands Android's relative component notation without trimming user input. */
    public static String normalizeClass(String pkg, String cls) {
        if (cls == null) return null;
        if (cls.startsWith(".")) {
            return pkg == null || pkg.isEmpty() ? null : pkg + cls;
        }
        return cls;
    }

    /**
     * Accepts only known OEM wallet packages and plausible Activity class names.
     * This is a syntax heuristic, not proof that a class extends Android Activity.
     */
    public static boolean isCandidate(String pkg, String cls) {
        // Do not use the exposed array for authorization: callers can mutate arrays.
        if (!DEFAULT_PACKAGE.equals(pkg)
                && !"com.finshell.wallet".equals(pkg)
                && !"com.heytap.wallet".equals(pkg)) {
            return false;
        }
        String canonical = normalizeClass(pkg, cls);
        if (canonical == null || canonical.startsWith("android.")
                || canonical.startsWith("androidx.")) {
            return false;
        }
        // ASCII identifier syntax excludes whitespace, newlines, slashes and pane
        // titles. '$' permits binary names for nested Activity classes. Actual
        // Activity subclasses are not required to have an "Activity" name suffix.
        return canonical.matches("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+"
                + "[A-Za-z_$][A-Za-z0-9_$]*");
    }

    /** Both package and canonical class must match exactly. */
    public static boolean matches(String wantedPkg, String wantedClass,
            String actualPkg, String actualClass) {
        return isCandidate(wantedPkg, wantedClass)
                && isCandidate(actualPkg, actualClass)
                && wantedPkg.equals(actualPkg)
                && normalizeClass(wantedPkg, wantedClass)
                        .equals(normalizeClass(actualPkg, actualClass));
    }

    /**
     * Claims a matching event, then suppresses matching duplicates for 1500 ms.
     * Nonmatches never affect the cooldown; a clock rollback starts a new window.
     */
    public boolean claim(String expectedPkg, String expectedClass,
            String actualPkg, String actualClass, long nowElapsed) {
        if (!matches(expectedPkg, expectedClass, actualPkg, actualClass)) return false;
        if (hasClaim && nowElapsed >= lastClaimElapsed) {
            long elapsed = nowElapsed - lastClaimElapsed;
            // If subtraction overflows, the forward interval exceeds Long.MAX_VALUE
            // and is necessarily longer than the cooldown.
            if (elapsed >= 0L && elapsed < COOLDOWN_MS) return false;
        }
        lastClaimElapsed = nowElapsed;
        hasClaim = true;
        return true;
    }
}
