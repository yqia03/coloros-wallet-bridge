# 更新记录 / Changelog

## v0.1.0 — 2026-09-21

首次公开发布，保留此前已获得用户实机成功反馈的应用构建。

- 国行 OPPO 钱包指定页面自动转到 Google 钱包，无需 root 或 ADB 授权。
- 中文设置界面、手动测试、暂停开关、60 秒页面校准与诊断信息。
- 使用系统解锁，重复事件过滤，关闭屏幕时取消待处理操作。
- 只处理指定钱包应用的窗口标识，不读取页面文字、不联网。
- 中文为主的 README、英文介绍、使用与构建说明。

已知限制：无障碍服务需保持连接，清理应用后台可能使跳转停止；可能短暂出现原厂钱包页面。

Initial public release of the existing APK reported working on an unrooted Chinese-ROM OPPO Find X9. Includes scoped wallet-page redirection, Chinese setup UI, calibration, normal system unlock, duplicate filtering, and Chinese-first documentation with an English README. Clearing the app from the background may stop the service; the OEM wallet may briefly appear.
