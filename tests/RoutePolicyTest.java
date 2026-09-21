package local.uway.walletbridge;

/** Run with javac/java; explicit checks work even when JVM assertions are disabled. */
public final class RoutePolicyTest {
    private static final String PKG = RoutePolicy.DEFAULT_PACKAGE;
    private static final String CLS = RoutePolicy.DEFAULT_CLASS;
    private static int checks;

    public static void main(String[] args) {
        candidateRules();
        exactMatching();
        cooldownBehavior();
        clockBoundaries();
        System.out.println("RoutePolicyTest passed: " + checks + " checks");
    }

    private static void candidateRules() {
        yes(RoutePolicy.isCandidate(PKG, CLS), "default OEM component accepted");
        yes(RoutePolicy.isCandidate("com.finshell.wallet", CLS), "finshell allowed");
        yes(RoutePolicy.isCandidate("com.heytap.wallet", CLS), "heytap wallet allowed");
        yes(RoutePolicy.isCandidate(PKG, ".WalletActivity"), "relative class accepted");
        yes(RoutePolicy.isCandidate(PKG, "com.nearme.wallet.Outer$WalletActivity"),
                "nested OEM Activity accepted");
        no(RoutePolicy.isCandidate("com.heytap.tas.evil", CLS), "package suffix rejected");
        no(RoutePolicy.isCandidate("evil.com.heytap.tas", CLS), "package prefix rejected");
        no(RoutePolicy.isCandidate("com.google.android.apps.walletnfcrel", CLS),
                "Google Wallet cannot be a routing source");
        no(RoutePolicy.isCandidate(null, CLS), "null package rejected");
        no(RoutePolicy.isCandidate(PKG, null), "null class rejected");
        no(RoutePolicy.isCandidate("", CLS), "empty package rejected");
        no(RoutePolicy.isCandidate(PKG, ""), "empty class rejected");
        no(RoutePolicy.isCandidate(PKG, "android.widget.FrameLayout"), "widget rejected");
        no(RoutePolicy.isCandidate(PKG, "android.app.Activity"), "generic Activity rejected");
        no(RoutePolicy.isCandidate(PKG, "androidx.activity.ComponentActivity"),
                "generic AndroidX component rejected");
        no(RoutePolicy.isCandidate(PKG, "付款页面"), "pane title rejected");
        no(RoutePolicy.isCandidate(PKG, "WalletActivity"), "unqualified class rejected");
        yes(RoutePolicy.isCandidate(PKG, "com.nearme.wallet.PaymentScreen"),
                "custom Activity names need not have an Activity suffix");
        for (String whitespace : new String[] {" ", "\n", "\r", "\t", "\u00a0"}) {
            no(RoutePolicy.isCandidate(PKG + whitespace, CLS), "package whitespace rejected");
            no(RoutePolicy.isCandidate(PKG, CLS + whitespace), "trailing whitespace rejected");
            no(RoutePolicy.isCandidate(PKG, "com.nearme." + whitespace + "WalletActivity"),
                    "embedded whitespace rejected");
        }
        no(RoutePolicy.isCandidate(PKG, "com.nearme..WalletActivity"), "empty segment rejected");
        no(RoutePolicy.isCandidate(PKG, "com.nearme.wallet/WalletActivity"), "slash rejected");
        equal(PKG + ".WalletActivity", RoutePolicy.normalizeClass(PKG, ".WalletActivity"),
                "normalization expands relative class");
        equal(CLS, RoutePolicy.normalizeClass(PKG, CLS), "foreign OEM namespace retained");
        equal(null, RoutePolicy.normalizeClass(null, ".WalletActivity"),
                "relative class needs a package");
        equal(null, RoutePolicy.normalizeClass(PKG, null), "null normalization safe");
    }

    private static void exactMatching() {
        yes(RoutePolicy.matches(PKG, CLS, PKG, CLS), "exact route matches");
        yes(RoutePolicy.matches(PKG, ".WalletActivity", PKG, PKG + ".WalletActivity"),
                "relative expected component matches canonical event");
        yes(RoutePolicy.matches(PKG, PKG + ".WalletActivity", PKG, ".WalletActivity"),
                "relative actual component matches canonical route");
        no(RoutePolicy.matches(PKG, CLS, "com.finshell.wallet", CLS),
                "different allowlisted package does not match");
        no(RoutePolicy.matches(PKG, CLS, PKG, "com.nearme.wallet.OtherActivity"),
                "different Activity does not match");
        no(RoutePolicy.matches(PKG, CLS, PKG, CLS + "Activity"), "class substring rejected");
        no(RoutePolicy.matches(PKG, CLS, PKG, "prefix." + CLS), "class prefix rejected");
        no(RoutePolicy.matches(null, null, null, null), "null pairs cannot match");
        no(RoutePolicy.matches(PKG, "android.app.Activity", PKG, "android.app.Activity"),
                "equal invalid classes cannot match");
        no(RoutePolicy.matches(PKG, CLS, PKG, "android.widget.FrameLayout"),
                "generic window event cannot match");
    }

    private static void cooldownBehavior() {
        RoutePolicy policy = new RoutePolicy();
        yes(claim(policy, 0L), "first event at zero is allowed");
        no(claim(policy, 0L), "same event timestamp suppressed");
        no(claim(policy, 1L), "immediate duplicate suppressed");
        no(claim(policy, 1499L), "event just before cooldown suppressed");
        yes(claim(policy, 1500L), "second press at cooldown boundary allowed");
        no(claim(policy, 1500L), "second press duplicate suppressed");
        yes(claim(policy, 3000L), "later independent press allowed");
        yes(claim(new RoutePolicy(), 3000L), "independent instance has independent cooldown");

        RoutePolicy wrongEventPolicy = new RoutePolicy();
        no(wrongEventPolicy.claim(PKG, CLS, PKG, "com.nearme.OtherActivity", 1000L),
                "nonmatch does not create cooldown");
        yes(claim(wrongEventPolicy, 1000L), "match follows same-time nonmatch");
        no(wrongEventPolicy.claim(PKG, CLS, "wrong.package", CLS, 2400L),
                "nonmatch during cooldown rejected");
        yes(claim(wrongEventPolicy, 2500L), "nonmatch does not extend cooldown");
    }

    private static void clockBoundaries() {
        RoutePolicy rollbackPolicy = new RoutePolicy();
        yes(claim(rollbackPolicy, 10000L), "initial claim before rollback");
        no(rollbackPolicy.claim(PKG, CLS, "wrong.package", CLS, 1L),
                "nonmatching rollback ignored");
        no(claim(rollbackPolicy, 10001L), "nonmatch rollback cannot clear cooldown");
        yes(claim(rollbackPolicy, 5L), "matching clock rollback rebases cooldown");
        no(claim(rollbackPolicy, 5L), "duplicate after rollback suppressed");
        no(claim(rollbackPolicy, 1504L), "rebased cooldown remains enforced");
        yes(claim(rollbackPolicy, 1505L), "rebased cooldown ends at boundary");

        RoutePolicy extremePolicy = new RoutePolicy();
        yes(claim(extremePolicy, Long.MIN_VALUE), "minimum timestamp allowed once");
        no(claim(extremePolicy, Long.MIN_VALUE), "minimum timestamp duplicate suppressed");
        no(claim(extremePolicy, Long.MIN_VALUE + 1499L), "minimum boundary safe");
        yes(claim(extremePolicy, Long.MIN_VALUE + 1500L), "minimum cooldown ends");
        yes(claim(extremePolicy, Long.MAX_VALUE - 1L), "huge forward interval cannot overflow");
        no(claim(extremePolicy, Long.MAX_VALUE), "maximum timestamp adjacent event suppressed");
        yes(claim(extremePolicy, 0L), "wrap or rollback resets safely");
    }

    private static boolean claim(RoutePolicy policy, long now) {
        return policy.claim(PKG, CLS, PKG, CLS, now);
    }

    private static void yes(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void no(boolean value, String message) {
        yes(!value, message);
    }

    private static void equal(String expected, String actual, String message) {
        yes(expected == null ? actual == null : expected.equals(actual), message);
    }
}
