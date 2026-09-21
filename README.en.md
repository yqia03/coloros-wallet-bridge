# Wallet Bridge / 钱包快捷桥

[简体中文](README.md) · **English**

Open Google Wallet through the existing double-press power-button shortcut on a Chinese-ROM OPPO phone. Wallet Bridge detects the OPPO Wallet page, closes it, and requests Google Wallet to open. No root or ADB grants are required.

**A user has successfully tested redirection on a Chinese-ROM OPPO Find X9 without root.** The exact ColorOS build and coverage of locked-screen scenarios were not provided. Other devices and firmware versions still need testing.

[Download v0.1.0 APK](https://github.com/yqia03/coloros-wallet-bridge/releases/download/v0.1.0/Wallet-Bridge-0.1.0.apk) · [Releases](https://github.com/yqia03/coloros-wallet-bridge/releases) · [Detailed setup guide, Chinese](docs/使用说明.md)

The app interface and primary documentation are in Simplified Chinese. Android 12 or later is required.

## Quick start

1. Install Google Wallet and confirm that it opens normally. Download and install `Wallet-Bridge-0.1.0.apk` from the release above.
2. Keep OPPO Wallet installed and keep the system's double-press power-button shortcut set to open it.
3. Open **钱包快捷桥**, then tap **1. 测试打开 Google 钱包** to test opening Google Wallet.
4. Tap **2. 开启无障碍服务** and enable **钱包快捷桥** in Android's accessibility settings.
5. Return to the app, wait for **无障碍：已连接** (accessibility connected), then tap **3. 启用自动跳转** (enable redirection).
6. Return to the home screen and double-press the power button. Test while unlocked first; unlock normally if the phone requests it.

If OPPO Wallet still opens, use **开始 60 秒校准** (start calibration), double-press the power button, then return and choose **结束校准并选择页面**. Confirm the captured page and enable redirection again. Calibration pauses redirection, lasts up to 60 seconds, and stores at most 20 package/class pairs locally. A captured pair is used only after confirmation.

To stop redirection, tap **暂停自动跳转**, disable the accessibility service, or uninstall the app.

## Does it need to stay open?

**The accessibility service must remain connected; the app's interface does not need to stay in the foreground.** The Find X9 user reported that clearing Wallet Bridge from the background stopped redirection. Removing it from recent apps or force-stopping it can stop its service under the phone's background-management rules.

Leave Wallet Bridge running in the background. If it stops working, reopen it and check that accessibility is connected and redirection is enabled. Restarting the phone or updating ColorOS may also require checking these settings again.

## Privacy and battery

The service is event-driven: Android notifies it about window-state changes from three known OEM wallet packages. It checks only the package and class names, with exact matching against the configured page.

- No screenshots, screen recording, OCR, screen-node access, event-text reading, or card-content reading.
- No network permission, analytics, or data uploads.
- No root, ADB grants, overlays, `READ_LOGS`, or `WRITE_SECURE_SETTINGS`.
- Calibration and diagnostic information stay on the device unless you choose to copy and share them. Diagnostics include device/system information and page identifiers.

There is no continuous screen-scanning loop. Background operation is not the same as continuously using the processor, but **battery consumption has not been measured on a device**.

## How it works and limitations

`OPPO Wallet page → matching accessibility event → Back → system unlock if needed → Google Wallet`

Wallet Bridge uses its own package, `local.uway.walletbridge`, and opens Google Wallet through its normal launcher intent. It does not replace an OEM package or intercept the physical power button.

- OPPO Wallet may briefly appear before redirection.
- Manually opening the same OPPO Wallet page can also trigger redirection. Pause the bridge when you want to use that page.
- Normal system unlocking still applies. This app does not bypass the lock screen.
- It opens Google Wallet; it does not change the default NFC payment app, select a card, make a payment, or change device, card, or regional eligibility.
- ColorOS background restrictions and changes to wallet page identifiers can affect operation.

## Validation

| Check | Status |
| --- | --- |
| Chinese-ROM OPPO Find X9, no root | User reported successful redirection |
| Clearing the app from the background | User reported that redirection stopped |
| Routing logic | 75 checks passed |
| Android compilation, DEX, APK signature and alignment | Passed |
| Exact ColorOS build, full lock-screen coverage, reboot behavior, battery use | Not established by the user report |
| Other devices and firmware | Not verified |

The v0.1.0 application source remains unchanged from the APK tested by the user.

## Build from source

Requires Python 3, JDK 17+, Android SDK Platform 36, and Build Tools 36.0.0. No Gradle or third-party runtime libraries are used.

```sh
python3 build.py --sdk /path/to/android-sdk --jdk /path/to/jdk
```

The script runs routing tests, builds, signs, and verifies `build/Wallet-Bridge-0.1.0.apk`. It generates a local signing key when needed. **Keep signing keys and password files private and out of Git.** Retain your key for compatible updates; an APK signed with a different key cannot update an existing installation.

See [构建说明.md](docs/构建说明.md) for build details.

## License and attribution

Released under the [MIT License](LICENSE). The default OEM page identifier and Back-before-handoff approach were informed by [Nielk74/coloros-power-button-launcher](https://github.com/Nielk74/coloros-power-button-launcher/tree/2c507ea). Its MIT notice is retained in [UPSTREAM-LICENSE.txt](UPSTREAM-LICENSE.txt). Wallet Bridge is independently implemented.

This is a community project, unaffiliated with OPPO or Google.
