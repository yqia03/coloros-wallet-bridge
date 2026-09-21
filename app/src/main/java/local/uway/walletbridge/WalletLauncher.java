package local.uway.walletbridge;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

final class WalletLauncher {
    static final String GOOGLE = "com.google.android.apps.walletnfcrel";
    static Intent intent(Context c) { return c.getPackageManager().getLaunchIntentForPackage(GOOGLE); }
    static boolean available(Context c) { return intent(c) != null; }
    static boolean open(Activity activity) {
        Intent target = intent(activity);
        if (target == null) { State.note(activity, "没有找到可打开的 Google 钱包，请先安装并手动打开一次。"); return false; }
        try {
            activity.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            State.note(activity, "已请求打开 Google 钱包；是否实际显示请以手机为准。");
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            State.note(activity, "系统未允许打开 Google 钱包，请检查跨应用跳转提示。");
            return false;
        }
    }
}
