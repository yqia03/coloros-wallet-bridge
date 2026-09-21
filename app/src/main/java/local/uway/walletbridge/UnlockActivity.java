package local.uway.walletbridge;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

public final class UnlockActivity extends Activity {
    static volatile boolean active;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private KeyguardManager keyguard;
    private TextView message;
    private boolean prompted, launched, resumed;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        active = true;
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        keyguard = getSystemService(KeyguardManager.class);
        LinearLayout content = MainActivity.column(this);
        message = MainActivity.text(this, "解锁后打开 Google 钱包", 23);
        content.addView(message);
        content.addView(MainActivity.text(this, "请使用手机系统的指纹、面容或锁屏密码验证。", 16));
        Button retry = MainActivity.button(this, "解锁并继续");
        retry.setOnClickListener(v -> { prompted = false; proceed(); });
        content.addView(retry);
        Button cancel = MainActivity.button(this, "取消");
        cancel.setOnClickListener(v -> finish());
        content.addView(cancel);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, filter);
        handler.postDelayed(() -> { State.note(this, "等待解锁超时，请重新双击。"); finish(); }, 60000);
    }

    @Override protected void onResume() { super.onResume(); resumed = true; handler.post(this::proceed); }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) handler.post(this::proceed);
    }

    private void proceed() {
        if (!resumed || isFinishing() || launched) return;
        if (!State.enabled(this) || !WalletService.connected()) { finish(); return; }
        if (keyguard == null) { message.setText("无法确认锁屏状态，请取消后手动打开 Google 钱包。"); return; }
        if (!keyguard.isKeyguardLocked() && !keyguard.isDeviceLocked()) {
            launched = true;
            boolean opened = WalletLauncher.open(this);
            if (opened) finish();
            else { launched = false; message.setText(State.prefs(this).getString("status", "打开失败")); }
            return;
        }
        if (prompted || !hasWindowFocus()) return;
        prompted = true;
        keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() {
                // Some OEMs finish keyguard transitions after the callback. Retry briefly while resumed.
                handler.postDelayed(UnlockActivity.this::afterUnlock, 150);
            }
            @Override public void onDismissCancelled() { State.note(UnlockActivity.this, "已取消解锁。"); finish(); }
            @Override public void onDismissError() { message.setText("系统没有弹出解锁界面，请点“解锁并继续”重试。"); }
        });
    }

    private int unlockChecks;
    private void afterUnlock() {
        if (isFinishing() || launched) return;
        proceed();
        if (!launched && ++unlockChecks < 10) handler.postDelayed(this::afterUnlock, 150);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(screenOff);
        WalletService.bridgeClosed();
        active = false;
        super.onDestroy();
    }
}
