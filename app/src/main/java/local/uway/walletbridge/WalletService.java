package local.uway.walletbridge;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public final class WalletService extends AccessibilityService {
    private static volatile boolean connected;
    private static long suppressUntil;
    private boolean receiverRegistered;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { handler.removeCallbacksAndMessages(null); }
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RoutePolicy policy = new RoutePolicy();
    static boolean connected() { return connected; }
    static void bridgeClosed() { suppressUntil = SystemClock.elapsedRealtime() + 1500; }

    @Override protected void onServiceConnected() {
        connected = true;
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOff, filter);
            receiverRegistered = true;
        }
        // A process/service restart must not resume an old calibration session.
        State.prefs(this).edit().remove("capture_until").apply();
        State.note(this, "无障碍服务已连接。");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        // Never read event text, descriptions, source nodes or window contents.
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (!RoutePolicy.isCandidate(pkg, cls)) return;
        if (State.capturing(this)) { State.capture(this, pkg, cls); return; }
        if (!State.enabled(this) || UnlockActivity.active || SystemClock.elapsedRealtime() < suppressUntil) return;
        if (!RoutePolicy.matches(State.pkg(this), State.cls(this), pkg, cls)) return;
        if (!WalletLauncher.available(this)) {
            State.note(this, "未找到 Google 钱包，已保留 OPPO 钱包页面。");
            return;
        }
        if (!policy.claim(State.pkg(this), State.cls(this), pkg, cls, SystemClock.elapsedRealtime())) return;
        // Close only the just-matched OEM wallet screen to reduce resume loops.
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> {
            if (!State.enabled(this) || State.capturing(this) || UnlockActivity.active) return;
            try {
                startActivity(new Intent(this, UnlockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                State.note(this, "已匹配 OPPO 钱包页面，正在打开快捷桥。");
            } catch (SecurityException e) {
                State.note(this, "ColorOS 阻止了后台打开。请检查允许后台活动与跨应用跳转设置。");
            }
        }, 100);
    }

    @Override public void onInterrupt() { handler.removeCallbacksAndMessages(null); }
    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        connected = false;
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) { unregisterReceiver(screenOff); receiverRegistered = false; }
        super.onDestroy();
    }
}
