package local.uway.walletbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView status;
    private Button toggle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { renderStatus(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        content.addView(text(this, "钱包快捷桥", 29));
        content.addView(text(this, "OPPO 钱包 → Google 钱包\n国行 ColorOS 实验版 · 0.1.0", 16));
        content.addView(text(this, "保持手机原有的“双击电源键打开钱包”设置。快捷桥识别钱包页面后自动转到 Google 钱包，可能短暂闪过原厂页面。锁屏时仍须正常解锁。", 16));
        status = text(this, "", 15);
        status.setTextIsSelectable(true);
        content.addView(status);

        add(content, "1. 测试打开 Google 钱包", () -> {
            if (!WalletLauncher.open(this)) toast(State.prefs(this).getString("status", "打开失败"));
        });
        add(content, "2. 开启无障碍服务", () -> new AlertDialog.Builder(this)
            .setTitle("允许快捷桥处理钱包页面")
            .setMessage("服务只读取指定 OPPO 钱包应用发出的窗口包名与类名，用于识别页面并执行返回、打开 Google 钱包。校准时最多在本机保存 20 个页面名称。\n\n不读取窗口文字、卡号、密码，不联网，不申请屏幕内容、按键过滤或手势权限。\n\n接下来在系统设置中找到“钱包快捷桥”并开启。")
            .setPositiveButton("前往系统设置", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (RuntimeException e) { toast("请手动在设置中搜索“无障碍”，开启钱包快捷桥。"); }
            }).setNegativeButton("取消", null).show());
        toggle = add(content, "3. 启用自动跳转", this::toggle);

        content.addView(text(this, "如果双击后仍停在 OPPO 钱包", 20));
        content.addView(text(this, "先校准：开始记录 → 双击电源键一次 → 手动回到本应用 → 选择刚记录的页面。仅记录页面名称，不记录页面内容。", 15));
        add(content, "开始 60 秒校准（暂停跳转）", () -> {
            if (!WalletService.connected()) { toast("请先开启并连接无障碍服务。"); return; }
            State.prefs(this).edit().putBoolean("enabled", false).putString("candidates", "")
                .putLong("capture_until", SystemClock.elapsedRealtime() + 60000).apply();
            State.note(this, "正在校准：请双击一次电源键，再手动回到这里选择记录。");
            renderStatus();
        });
        add(content, "结束校准并选择页面", this::chooseCandidate);
        add(content, "恢复预设识别页面", () -> {
            State.prefs(this).edit().putBoolean("enabled", false).remove("capture_until")
                .remove("package").remove("class").remove("candidates").apply();
            State.note(this, "已恢复预设；请重新启用跳转。"); renderStatus();
        });

        content.addView(text(this, "使用与停用", 20));
        content.addView(text(this, "• 这是页面转发，不能直接接管电源键。手动打开同一 OPPO 钱包页面也可能跳转。需要用原厂钱包时，请先暂停。\n• 普通 Find X9 尚未经实机验证。重启或系统升级后，如失效，请检查无障碍服务；部分 ColorOS 会关闭它。\n• 如系统询问是否允许打开 Google 钱包，可在该应用跳转提示中允许。后台受限时，在应用电池设置中允许后台活动。\n• 只负责打开钱包，不改变默认 NFC 支付应用，也不改变 Google 钱包原有的支付条件。\n• 关闭无障碍服务或卸载本应用即可停用。", 15));
        add(content, "复制诊断信息", () -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("钱包快捷桥诊断", diagnostics()));
            toast("已复制机型、系统版本、页面名称与服务状态，不含卡片内容。");
        });
        setContentView(scroll);
    }

    private void toggle() {
        if (State.enabled(this)) {
            State.prefs(this).edit().putBoolean("enabled", false).remove("capture_until").apply();
            State.note(this, "已暂停自动跳转。");
        } else {
            if (!WalletService.connected()) { toast("请先开启并连接无障碍服务。"); return; }
            if (!WalletLauncher.available(this)) { toast("请先安装 Google 钱包，并完成测试打开。"); return; }
            State.prefs(this).edit().putBoolean("enabled", true).remove("capture_until").apply();
            State.note(this, "自动跳转已开启，请双击电源键测试。");
        }
        renderStatus();
    }

    private void chooseCandidate() {
        State.prefs(this).edit().remove("capture_until").apply();
        String raw = State.prefs(this).getString("candidates", "");
        if (raw.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("没有记录到钱包页面")
                .setMessage("确认无障碍服务已连接，然后重新校准。如果依然为空，请复制诊断信息发回；这版系统可能使用了不同入口。")
                .setPositiveButton("知道了", null).show(); return;
        }
        String[] rows = raw.split("\n");
        new AlertDialog.Builder(this).setTitle("选择双击时出现的页面")
            .setItems(rows, (dialog, which) -> {
                String[] route = rows[which].split("/", 2);
                if (route.length != 2 || !RoutePolicy.isCandidate(route[0], route[1])) return;
                new AlertDialog.Builder(this).setTitle("使用这个页面触发跳转？")
                    .setMessage(rows[which] + "\n\n这无法区分页面来自双击还是手动打开。手动打开同一页面时也会跳转；可随时暂停或恢复预设。保存后需重新启用自动跳转。")
                    .setPositiveButton("保存页面", (d, w) -> {
                        State.prefs(this).edit().putString("package", route[0]).putString("class", route[1])
                            .putBoolean("enabled", false).apply();
                        State.note(this, "已保存校准页面，请启用跳转后测试。"); renderStatus();
                    }).setNegativeButton("取消", null).show();
            }).setNegativeButton("取消", null).show();
    }

    private void renderStatus() {
        if (status == null) return;
        String mode = State.capturing(this) ? "校准中（跳转已暂停）" : State.enabled(this) ? "自动跳转已开启" : "自动跳转已暂停";
        status.setText("状态：" + mode + "\n无障碍：" + (WalletService.connected() ? "已连接" : "未连接")
            + "\nGoogle 钱包：" + (WalletLauncher.available(this) ? "可打开" : "未找到")
            + "\n\n" + State.prefs(this).getString("status", "请先测试 Google 钱包，再开启服务和自动跳转。")
            + "\n\n识别页面：\n" + State.pkg(this) + "/" + State.cls(this));
        toggle.setText(State.enabled(this) ? "暂停自动跳转" : "3. 启用自动跳转");
    }

    private String diagnostics() {
        return "钱包快捷桥 0.1.0\n设备：" + Build.MANUFACTURER + " " + Build.MODEL
            + "\nAndroid：" + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT
            + "\n系统版本：" + Build.DISPLAY + "\n服务连接：" + WalletService.connected()
            + "\n跳转启用：" + State.enabled(this) + "\nGoogle 钱包可打开：" + WalletLauncher.available(this)
            + "\n识别页面：" + State.pkg(this) + "/" + State.cls(this)
            + "\n状态：" + State.prefs(this).getString("status", "")
            + "\n校准记录：\n" + State.prefs(this).getString("candidates", "");
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private Button add(LinearLayout parent, String label, Runnable action) {
        Button b = button(this, label); b.setOnClickListener(v -> action.run()); parent.addView(b); return b;
    }
    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Activity a) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(a, 22);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets edge = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(padding + edge.left, padding + edge.top, padding + edge.right, padding + edge.bottom);
            return insets;
        });
        return layout;
    }
    static TextView text(Context c, String value, int size) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(30, 47, 40));
        t.setPadding(0, dp(c, 8), 0, dp(c, 12)); t.setLineSpacing(dp(c, 3), 1); return t;
    }
    static Button button(Context c, String label) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(c, 54)); return b;
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
