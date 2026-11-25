# power-tv-box

基于 [FongMi/TV](https://github.com/FongMi/TV) **5.5.6**（提交 `4573249cd`）与 [FongMi/media](https://github.com/FongMi/media) `release-1.11.0-fongmi` 的 Android 播放器构建包，面向**手机 / 车机多屏**场景做了定制，并附带全部播放器依赖 aar，克隆后无需重编 media3 即可出包。

---

## 特性

### 车机多屏播放

- **视频展示屏幕**：`设置 → 播放器 → 默认播放屏幕`，可选择主屏或任意可用副屏（HDMI、后排屏等）。切换后视频画面与字幕只输出到该屏，主应用留在原屏。
- **双屏同源控制**：副屏挂载与主屏相同的原版控制栏，共用同一个 `MediaController`，不创建第二个播放器，不会出现双路音频。
- **主屏常驻控制**：即使副屏不支持触摸，也能在主屏暂停、拖动进度、调倍速、切音轨与字幕。
- **切屏不重启**：播放中「播放到其他屏幕」直接迁移同一播放会话，不重新起播。
- **后台继续播放**：副屏播放时主屏回到桌面或打开导航，播放不中断。
- **断屏保护**：副屏被拔掉或不可用时暂停等待，不会把画面意外搬回主屏。

### 界面与交互

- **屏幕方向**：默认 / 固定横屏 / 固定竖屏，首次启动按主屏真实宽高自动取初值。
- **界面缩放**：自动 / 标准 / 放大。高分辨率低密度车机（例如 2560×1600 @160dpi）会把 48dp 按钮放大到约 96px，避免控件过小。
- **触摸返回入口**：无实体返回键的车机上，设置子页、扫码页、崩溃页、频道列表均有可见返回按钮；播放页返回按钮随控制栏显隐。
- **首页链接按钮可隐藏**：`设置 → 显示首页链接按钮`。

### 播放

- 保留 Exo / mpv 双引擎、FFmpeg 软解、TS/HDMV、弹幕、投屏等原版能力。
- **默认片源**（仅在未配置时生效）：
  - 点播：`http://www.xn--sss604efuw.cc/tv`
  - 直播：`https://raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u`
- **后台播放默认关闭**，不再默认进入画中画。

改动明细见 [CHANGES.md](CHANGES.md)。

---

## 目录结构

```text
power-tv-box/
├── README.md                 本文件
├── CHANGES.md                相对基线的改动清单
├── ENV.md                    环境清单与手工安装命令
├── README-HANDOFF.md         交接包原始说明
├── MULTI-DISPLAY-TEST.md     车机多屏验收清单
├── CHECKSUMS.sha256          原始物料校验和
├── src/
│   ├── TV/                   TV 应用源码（已打 6 处 media3 API 适配补丁）
│   │   ├── app/libs/         26 个 aar（media3 剪裁版 + mpvplayer/decoder_ffmpeg 等）
│   │   ├── catvod/ quickjs/ chaquo/
│   │   └── gradle/libs.versions.toml
│   └── media/                FongMi/media 源码（含 libraries/mpvplayer、ui_danmaku）
├── libs/                     30 个 aar 的独立备份
├── patches/                  API 适配补丁与基线提交信息
└── env/
    ├── fongmi.jks            签名库（alias fongmi / 口令 fongmi123）
    ├── local.properties.template
    ├── init.gradle           阿里云 Maven 镜像
    ├── setup-env.ps1         一键装环境
    └── rebuild.ps1           一键重建（编 aar → 编 APK）
```

---

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | Temurin **21** | AGP 9 强制要求，JDK 17/11 无法编译 |
| Android SDK Platform | `android-37.0`（TV）/ `android-36`（media3） | 两者都要 |
| Build-Tools | `37.0.0` / `36.0.0` | 与 Platform 对应 |
| Gradle | **9.7.1** | TV；media3 需 9.1.0（9.7.1 会撞 annotations 版本） |
| CPython | **3.10** 且带 pip | Chaquopy `buildPython`，只认 3.10 |
| NDK / CMake | `28.2.13676358` / `3.22.1` | 仅在重编 `libffmpegJNI.so` 时需要 |

快速装环境：

```powershell
cd env
powershell -ExecutionPolicy Bypass -File .\setup-env.ps1
```

手工安装、镜像与依赖来源见 [ENV.md](ENV.md)。国内建议把 `env/init.gradle` 复制到 `%USERPROFILE%\.gradle\init.gradle`。

---

## 配置本机路径

```powershell
Copy-Item env\local.properties.template src\TV\local.properties
```

按实际路径修改 `src\TV\local.properties`（`storeFile` 相对 `app/` 目录解析）：

```properties
sdk.dir=C:/Users/<你>/AppData/Local/Android/Sdk
storeFile=../fongmi.jks
keyAlias=fongmi
storePassword=fongmi123
```

该文件含本机路径与口令，**已被 .gitignore 排除**，不要提交。

---

## 构建

### 一键脚本

```powershell
cd env
powershell -ExecutionPolicy Bypass -File .\rebuild.ps1              # 手机版 + 电视版 arm64 release
powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -Variants all
powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -SkipAar     # 只改应用代码时，跳过重编 media3
```

`-SkipAar` 最常用：aar 已在 `src/TV/app/libs`，改 UI 逻辑时直接复用，省十几分钟。

### 手动构建

```powershell
$env:JAVA_HOME = "D:\software\jdk21"          # 改成你的 JDK 21
$env:Path      = "<python310>;<python310>\Scripts;$env:Path"

cd src\TV
<gradle-9.7.1>\bin\gradle.bat -I ..\..\env\init.gradle `
  -Pchaquopy.buildPython=<python310>\python.exe `
  :app:assembleMobileArm64_v8aRelease

# 电视版
<gradle-9.7.1>\bin\gradle.bat -I ..\..\env\init.gradle `
  -Pchaquopy.buildPython=<python310>\python.exe `
  :app:assembleLeanbackArm64_v8aRelease
```

加 `--offline` 可跳过依赖检查，本地缓存齐全时更快。

### 产物

```text
src/TV/app/build/outputs/apk/mobileArm64_v8a/release/mobile-arm64_v8a.apk      手机 / 车机
src/TV/app/build/outputs/apk/leanbackArm64_v8a/release/leanback-arm64_v8a.apk  电视 / 盒子
src/TV/app/build/outputs/apk/mobileArmeabi_v7a/release/...                     32 位（把变体名换成 Armeabi_v7a）
```

两个变体包名同为 `com.fongmi.android.tv`，**互相覆盖安装**；与官方版签名不同，覆盖前先卸载官方版。

### 安装

```powershell
adb install -r src\TV\app\build\outputs\apk\mobileArm64_v8a\release\mobile-arm64_v8a.apk
```

部分车机 GMS/ROM 屏蔽了 shell 安装（报 `cannot run :install,the cmd is limited`），此时把 APK 拷到设备上用文件管理器安装即可。

---

## 关于 APK 体积

成品约 **90 MiB**，绝大部分是播放器原生库，与本次定制无关：

| 组成 | 占比 |
|---|---|
| `lib/arm64-v8a/*`（libmpv、libavcodec、thunder、mpv 等） | 约 65 MiB |
| assets（Chaquopy Python 依赖等） | 约 15 MiB |
| dex + 资源 | 约 8 MiB |

想显著瘦身只能裁播放引擎或砍视频源兼容性，删界面代码收益极小。

---

## 已知限制

- **显示 ID 不是永久标识**：副屏选择按「开机计数 + 名称 + 分辨率 + ID」核对，车机重启后需要重新选择，避免两块同型号 HDMI 屏被认错。
- **副屏能否输出取决于车机**：只要系统允许普通应用在该屏创建 `Presentation` 窗口即可用；仅支持镜像或依赖厂商私有接口的屏不在列表内。
- **视频位置与音频路由是两件事**，不要用喇叭位置判断画面输出目标。
- 电视版沿用原 leanback 界面，不包含手机版的多屏、方向锁定与触屏返回改动。
- 未做长期压力与多车型实机验证，验收参考 [MULTI-DISPLAY-TEST.md](MULTI-DISPLAY-TEST.md)。

---

## 签名

`env/fongmi.jks`，alias `fongmi`，store/key 口令均为 `fongmi123`，RSA2048 / 36500 天。用于自用分发，正式发布请换成自己的密钥。

## 致谢

上游项目：[FongMi/TV](https://github.com/FongMi/TV)、[FongMi/media](https://github.com/FongMi/media)、[androidx/media3](https://github.com/androidx/media)、[catvod](https://github.com/FongMi/CatVodSpider)。
