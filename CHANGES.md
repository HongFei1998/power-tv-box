# 本次改动清单

相对基线（`FongMi/TV @ 4573249cd` + `FongMi/media release-1.11.0-fongmi` + 交接包内 6 处 API 补丁）之上，本轮为手机版/车机增加的功能与调整。

## 手机版新增

| 文件 | 说明 |
|---|---|
| `mobile/java/.../setting/AppOrientation.java` | 屏幕方向：默认 / 固定横屏 / 固定竖屏。首次启动按主屏真实宽高自动写入初始值，之后以用户选择为准。 |
| `mobile/java/.../utils/MobileUiScale.java` | 界面缩放：按屏幕短边自动放大 density（自动 / 标准 / 放大），同时校正 `ResUtil` 的 dp、sp 计算，避免高分辨率车机上控件过小。 |
| `mobile/java/.../utils/PlaybackDisplays.java` | 视频展示屏幕：枚举全部可用副屏（排除主屏、私有屏、已关闭屏），选择、持久化、断线识别。 |
| `mobile/java/.../ui/custom/ExternalVideoOutput.java` | 把现有播放视图移入副屏 `Presentation`，主屏保留路由行；副屏失联时暂停等待，不在主屏意外出画。 |
| `mobile/java/.../ui/custom/OriginalOutputBar.java` | 副屏使用与主屏相同的原版控制栏（同一个 `MediaController`），倍速、音轨、字幕选择就在副屏内展开。 |
| `mobile/java/.../ui/custom/OutputSession.java` | 两块屏幕共用的播放控制桥：进度、暂停、跳转、倍速、轨道切换，不创建第二个播放器。 |
| `mobile/java/.../ui/activity/RoutedPlaybackActivity.java` | 点播/直播的路由基类：默认屏幕在绑定播放服务前应用，缺屏时延迟起播并暂停，取消播放与退出统一处理。 |

## 手机版修改

| 文件 | 说明 |
|---|---|
| `mobile/java/.../ui/activity/VideoActivity.java` | 接入外屏；返回按钮仅在控制栏可见时出现；横屏预览窗按屏宽 45% 放大；外屏播放时详情改为整屏单列。 |
| `mobile/java/.../ui/activity/LiveActivity.java` | 接入外屏；恢复单击开频道列表、双击开控制栏；频道列表左上角新增返回箭头；分类列与频道列增加最小宽度。 |
| `mobile/java/.../ui/base/BaseActivity.java` | 应用界面缩放，并在页面创建时初始化/应用屏幕方向。 |
| `mobile/java/.../ui/fragment/SettingFragment.java` | 新增「界面缩放」；新增「显示首页链接按钮」开关。 |
| `mobile/java/.../ui/fragment/VodFragment.java` | 首页链接图标可按设置隐藏。 |
| `mobile/java/.../ui/fragment/Setting{Decode,Preload,Player,Danmaku}Fragment.java` | 设置子页左上角补返回入口。 |
| `mobile/java/.../ui/activity/ScanActivity.java`、`CrashActivity` 等 | 补触摸返回入口，车机无实体返回键时也能退出。 |
| `mobile/res/layout-sw600dp/activity_video.xml`、`mobile/res/layout/activity_live.xml`、`view_external_output.xml`、`fragment_setting.xml` | 对应布局调整。 |
| `mobile/res/values*/home_link.xml`、`ui_scale.xml`、`output_*.xml` | 新增文案（英 / 简中 / 繁中）。 |

## 共用代码（手机版与电视版同时生效）

| 文件 | 说明 |
|---|---|
| `main/java/.../bean/Config.java` | 默认点播源 `http://www.xn--sss604efuw.cc/tv`（`www.饭太硬.cc` 的 IDN 形式），默认直播源 `suxuang/myIPTV` 的 `ipv4.m3u`。仅在用户未配置时使用。 |
| `main/java/.../setting/PlayerSetting.java` | 后台播放默认改为「关」，不再默认进入画中画。 |
| `main/java/.../utils/ResUtil.java` | 增加 `uiScale`，让 dp/sp 计算跟随界面缩放。 |
| `main/java/.../ui/activity/PlaybackActivity.java` | 新增 `hasVisibleExternalPlayback()` 钩子，副屏播放时不因切后台而被暂停。 |

## 清理

- 删除已被原版控制栏取代的 `OutputControls.java`、`view_output_controls.xml` 及其专用字符串，只保留仍在使用的 `output_toggle_controls`。

## 未纳入版本库

- `apk/`（成品包，可由源码重建）
- `src/TV/local.properties`（含本机 SDK 路径与签名口令，模板见 `env/local.properties.template`）
- 各 `build/`、`.gradle/` 产物

## 构建

```powershell
$env:JAVA_HOME='D:\software\jdk21'
cd src\TV
<gradle 9.7.1>\bin\gradle.bat -I ..\..\env\init.gradle -Pchaquopy.buildPython=<python.exe> :app:assembleMobileArm64_v8aRelease
<gradle 9.7.1>\bin\gradle.bat -I ..\..\env\init.gradle -Pchaquopy.buildPython=<python.exe> :app:assembleLeanbackArm64_v8aRelease
```
