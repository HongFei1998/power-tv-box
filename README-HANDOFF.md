# FongMi TV 编译交接包（handoff kit）

这个包里是**一份可以直接在另一台 Windows 机器上复现 FongMi TV APK 的完整物料**：源码、需要手工造的 20 个 media3 aar、编译好的成品 APK、签名密钥、补丁、环境安装脚本。

整套东西能跑通的结论先写在前面：

- `FongMi/TV` **HEAD（5.8.9）用公开源码编不出来**，`app/build.gradle` 依赖的 20 个 `lib-*.aar` 是作者本地私有产物（仓库 `.gitignore` 里有 `lib-*.aar`），而且 HEAD 用到公开 `FongMi/media` 分支里不存在的 API。
- 所以本包锁定 **TV 提交 `4573249cd`（2026-06-30，versionName 5.5.6）** + **`FongMi/media` 分支 `release-1.11.0-fongmi`**（media3 剪裁版，含作者自写的 `libraries/mpvplayer`、`libraries/ui_danmaku`），打了 6 处 API 适配补丁后编译通过，真机（OnePlus PJD110 / Android 16 / arm64-v8a）安装并启动验证无崩溃。

---

## 一、目录结构

```
FongMiTV-build-kit/
├── README-HANDOFF.md              ← 本文件
├── ENV.md                         ← 环境清单（版本/组件/安装命令）
├── CHECKSUMS.sha256               ← 全部文件校验和
├── apk/                           ← 成品（4 个，已签名）
│   ├── FongMiTV-5.5.6-leanback-arm64-v8a-release.apk   电视/盒子版，fongmi.jks 签名
│   ├── FongMiTV-5.5.6-mobile-arm64-v8a-release.apk     手机版，fongmi.jks 签名
│   ├── FongMiTV-5.5.6-leanback-arm64-v8a-debug.apk     debug 版
│   └── FongMiTV-5.5.6-mobile-arm64-v8a-debug.apk       debug 版
├── src/
│   ├── TV/                        ← 已打补丁的 TV 源码（= 上游 4573249cd + 6 处补丁，无 .git）
│   │   └── app/libs/              ← 25 个 aar（20 个 media3 + forcetech/hook/jianpian/thunder/tvbus）
│   └── media/                     ← FongMi/media 分支 release-1.11.0-fongmi 源码（无 .git，去掉了 495MB 测试资源）
├── libs/                          ← 25 个 aar 的单独备份（和 src/TV/app/libs 内容一致）
├── patches/
│   ├── 0001-tv-media3-api-compat.patch   ← 6 处补丁的 git diff
│   └── BASE-COMMIT.txt                   ← 基线提交哈希 + 改动文件清单
└── env/
    ├── fongmi.jks                 ← 签名库（alias: fongmi / 口令: fongmi123）
    ├── local.properties.template  ← sdk.dir + 签名字段模板
    ├── init.gradle                ← Gradle 阿里云镜像（放 ~/.gradle/init.gradle）
    ├── setup-env.ps1              ← 一键装 JDK21 / Android SDK 组件 / Gradle / Python3.10
    └── rebuild.ps1                ← 一键重建（编 aar → 拷 aar → 编 APK）
```

---

## 二、新电脑上怎么用

### 1）装环境（一次性，约 15~30 分钟，看网速）

```powershell
cd <解压目录>\FongMiTV-build-kit\env
powershell -ExecutionPolicy Bypass -File .\setup-env.ps1
```

脚本会做（全部落在 `D:\ai\env`，路径可在脚本头部改）：

| 组件 | 版本 | 落地路径 |
|---|---|---|
| Temurin JDK | 21（Adoptium 直链） | `D:\ai\env\jdk21` |
| Android cmdline-tools | 19.0 | `D:\ai\env\android-sdk\cmdline-tools\latest` |
| Android SDK 组件 | `platform-tools`、`platforms;android-36`、`platforms;android-37.0`、`build-tools;36.0.0`、`build-tools;37.0.0`、`cmake;3.22.1`、`ndk;28.2.13676358` | `D:\ai\env\android-sdk` |
| Gradle（编 TV） | 9.7.1（腾讯镜像下载） | `D:\ai\env\gradle-9.7.1` |
| Gradle（编 media3） | 9.1.0（腾讯镜像下载） | `D:\ai\env\gradle-9.1.0` |
| CPython（Chaquopy buildPython） | 3.10.11（NuGet 包，自带 pip） | `D:\ai\env\Python310` |

装完记得把 `env\init.gradle` 复制到 `%USERPROFILE%\.gradle\init.gradle`（脚本会自动做），这是阿里云镜像配置，没有它下依赖会很慢甚至超时。

> 想手工装也完全可以，命令见 [ENV.md](ENV.md)。

### 2）只想要 APK，不重编

直接 `adb install -r apk\FongMiTV-5.5.6-mobile-arm64-v8a-release.apk`（手机）或
`adb install -r apk\FongMiTV-5.5.6-leanback-arm64-v8a-release.apk`（TV/盒子）。

注意：包名都是 `com.fongmi.android.tv`，两个变体互装会互相覆盖；和官方版签名不同，装之前先卸载官方版。

### 3）要重新编译

```powershell
cd <解压目录>\FongMiTV-build-kit\env
powershell -ExecutionPolicy Bypass -File .\rebuild.ps1                 # 默认：leanback + mobile 的 arm64 release
powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -Variants all   # 再加 debug
powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -SkipAar        # 跳过重编 media3 aar（app/libs 里已有现成的）
```

`-SkipAar` 是常用的：aar 已经在 `src/TV/app/libs`，只改 TV 代码时用它，能省 10 分钟。

产物在 `src\TV\app\build\outputs\apk\`：

```
leanbackArm64_v8a/release/leanback-arm64_v8a.apk
mobileArm64_v8a/release/mobile-arm64_v8a.apk
leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

> 想编 armeabi-v7a（32 位）就把 `assembleLeanbackArm64_v8aRelease` 里的 `Arm64_v8a` 换成 `Armeabi_v7a`。

---

## 三、6 处补丁都改了什么

基线：`FongMi/TV` 提交 `4573249cd`（2026-06-30）。完整 diff 见 `patches/0001-tv-media3-api-compat.patch`（191 行）。

| 文件 | 原因 | 改法 |
|---|---|---|
| `player/mpv/MpvUtil.java` | 公开 media 分支的 mpvplayer builder 与 TV 预期 API 不同 | 改用 `MpvAndroidOptions.Builder`（`setGpuNextEnabled/setVulkanEnabled/setAudioPassthroughEnabled/setShaderCacheDirectory`）+ `addAndroidDefaults(options)`；`addPostInitStringOption` → `addAppOwnedStringOption`；`addDiskCacheOptions(file,seconds)` 两参；`addAndroidSubtitleOptions(ctx, MpvSubtitleOptions)`；`setSubtitleOptions(MpvSubtitleOptions)`；去掉 `setHlsHttpPersistent` 和 `MpvPlayerConfig.VIDEO_OUTPUT_GPU_NEXT`（该分支用 `gpu-next` 布尔开关） |
| `player/exo/ExoUtil.java` | 该分支 `DefaultRenderersFactory` 没有 ffmpeg 偏好开关 | 去掉 `setFfmpegAudioPrefer/setFfmpegVideoPrefer/setEnableDv7HevcFallback`，保留 `setEnableDecoderFallback(true).setExtensionRendererMode(mode)` |
| `player/exo/MediaSourceFactory.java` | 该分支 `DefaultTsPayloadReaderFactory` 无 `FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS` | 去掉该 flag，保留 `setTsExtractorTimestampSearchBytes(DEFAULT_TIMESTAMP_SEARCH_BYTES*10)` |
| `player/media/MediaItemFactory.java` | 该分支 `MediaItem.Builder` 无 `setDecode(int)` | 去掉，`from(spec, decode)` 退化为 `from(spec)` |
| `ui/dialog/SubtitleDialog.java` | 该分支 `SubtitleView` 无 `addPosition/subPosition/getPosition/addTextSize/subTextSize/getTextSize/reset` | 字幕位置/字号直接读写 `PlayerSetting`（`putSubtitlePosition/getSubtitlePosition`、`putSubtitleTextSize/getSubtitleTextSize`），再 `player.setSubtitleStyle()` |
| `ui/activity/PlaybackActivity.java` | `PlayerView` 无 `hideDebugView()` | 用 `if (isDebugViewVisible()) toggleDebugView()` 等效实现 |

**这些补丁只影响编译期 API 适配，不改播放/解析逻辑**；跑起来的效果是：Exo 不再强制优先 ffmpeg 渲染器（默认顺序）、TS 流不再强制打开 HDMV DTS 音轨、字幕对话框的微调值改为写设置项。mpv 的 GPU-next/Vulkan/直通/字幕位置仍然按原意传给 `libmpv`。

---

## 四、已知限制 / 下一步可做的

1. **版本落后**：成品是 5.5.6（2026-06-30），不是 HEAD 的 5.8.9。要上 HEAD 只有一个办法——拿到作者本地那批 aar（`lib-mpvplayer-release.aar`、`lib-decoder-ffmpeg-release.aar` 及另外 18 个），丢进 `src/TV/app/libs` 就能直接编 HEAD；本包的环境、签名、构建脚本、media3 基线都是现成的。
2. 未做真机播放链路实测（`libmpv.so` / `libffmpegJNI.so` 运行时加载、音轨切换、字幕）。验证方法：
   ```powershell
   adb logcat -c; adb logcat | Select-String "mpv|ffmpeg|UnsatisfiedLink|FATAL"
   ```
   然后在 app 里切「设置 → 播放器 → mpv / Exo」放一个源。
3. Chaquopy 每次编新 variant 都会从 `chaquo.com/pypi-13.1` 重新拉 android wheel（很慢，十几 kB/s），首次编译耐心等；wheel 有本地缓存，同 variant 二次编译很快。
4. 32 位（armeabi-v7a）变体没编，命令见上。

---

## 五、关键版本速查

| 项 | 值 |
|---|---|
| TV 基线提交 | `4573249cd`（2026-06-30，"Fix epg title parse"） |
| media3 源码分支 | `FongMi/media` → `release-1.11.0-fongmi`（tip `3c2cbe8`，2026-07-27） |
| AGP / compileSdk / buildTools | 9.2.1 / 37 / 37.0.0（TV）；media3 用 compileSdk 36 + build-tools 36 |
| Gradle | TV: 9.7.1；media3: 9.1.0（9.7.1 会撞 `org.jetbrains:annotations` 版本钉死） |
| JDK | Temurin 21.0.13.1 |
| NDK / CMake | 28.2.13676358 / 3.22.1（`libffmpegJNI.so` 是构建时用 NDK 现编的，`libraries/decoder_ffmpeg/src/main/ffmpeg/include` 里有头文件） |
| Python | 3.10.11（Chaquopy 只认 3.10） |
| 签名 | `env/fongmi.jks`，alias `fongmi`，store/key 口令 `fongmi123`，RSA2048/36500 天 |
