# 环境清单（Environment manifest）

本包所有构建都在 Windows 10/11 x64 上验证通过。下面每一项都给出**版本、落地路径、获取方式**，可完全复现。

---

## 1. 汇总表

| # | 组件 | 版本 | 本机路径 | 用途 |
|---|---|---|---|---|
| 1 | Temurin JDK (Adoptium) | 21.0.13.1+2 LTS | `D:\ai\env\jdk21` | 运行 Gradle / AGP / javac（AGP 9 必须 21） |
| 2 | Android cmdline-tools | 19.0 | `D:\ai\env\android-sdk\cmdline-tools\latest` | sdkmanager / avdmanager |
| 3 | Android platform-tools | 37.0.3 | `D:\ai\env\android-sdk\platform-tools` | adb |
| 4 | Android SDK Platform | `android-37.0` | `D:\ai\env\android-sdk\platforms\android-37.0` | TV: compileSdk 37 |
| 5 | Android SDK Platform | `android-36` | `D:\ai\env\android-sdk\platforms\android-36` | media3: compileSdk 36 |
| 6 | Android Build-Tools | `37.0.0` | `D:\ai\env\android-sdk\build-tools\37.0.0` | TV 打包/签名（aapt2、apksigner、d8） |
| 7 | Android Build-Tools | `36.0.0` | `D:\ai\env\android-sdk\build-tools\36.0.0` | media3 打包 |
| 8 | Android NDK | `28.2.13676358` | `D:\ai\env\android-sdk\ndk\28.2.13676358` | 编 `libffmpegJNI.so` / mpvplayer jni |
| 9 | CMake | `3.22.1` | `D:\ai\env\android-sdk\cmake\3.22.1` | decoder_ffmpeg 的 externalNativeBuild |
| 10 | Gradle | 9.7.1 | `D:\ai\env\gradle-9.7.1` | 编 TV |
| 11 | Gradle | 9.1.0 | `D:\ai\env\gradle-9.1.0` | 编 media3 aar（9.7.1 会报 annotations 版本冲突） |
| 12 | CPython | 3.10.11（含 pip 23.0.1） | `D:\ai\env\Python310` | Chaquopy 的 `buildPython` |
| 13 | Gradle 镜像配置 | — | `%USERPROFILE%\.gradle\init.gradle` | 阿里云 maven 镜像（google/central/gradle-plugin） |
| 14 | 签名库 | JKS, RSA2048 | `env\fongmi.jks` | alias `fongmi`，口令 `fongmi123` |

> 不需要 Node.js / .NET / Visual Studio。`lib-decoder-ffmpeg` 的 JNI 用 NDK 自带的 clang+CMake 编，不需要外部工具链。

---

## 2. 手工安装命令（PowerShell）

如果不用 `setup-env.ps1`，按下面逐段执行即可。

### 2.1 JDK 21

```powershell
$ProgressPreference='SilentlyContinue'
$j = Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
$pkg = $j[0].binary.package
Invoke-WebRequest $pkg.link -OutFile "$env:TEMP\jdk21.zip"
Expand-Archive "$env:TEMP\jdk21.zip" -DestinationPath "D:\ai\env\jdk21x" -Force
Move-Item (Get-ChildItem "D:\ai\env\jdk21x" -Directory | Select-Object -First 1).FullName "D:\ai\env\jdk21"
D:\ai\env\jdk21\bin\java.exe -version
```

### 2.2 Android cmdline-tools + SDK 组件

```powershell
$sdk="D:\ai\env\android-sdk"
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip" -OutFile "$env:TEMP\ct.zip"
Expand-Archive "$env:TEMP\ct.zip" -DestinationPath "$env:TEMP\ct" -Force
New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
Move-Item "$env:TEMP\ct\cmdline-tools" "$sdk\cmdline-tools\latest"

$env:JAVA_HOME="D:\ai\env\jdk21"; $env:ANDROID_HOME=$sdk; $env:ANDROID_SDK_ROOT=$sdk
("y`n"*30) | & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses --sdk_root=$sdk
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$sdk `
    "platform-tools" "platforms;android-36" "platforms;android-37.0" `
    "build-tools;36.0.0" "build-tools;37.0.0" "cmake;3.22.1" "ndk;28.2.13676358"
```

> `sdkmanager` 版本不同，包名可能显示成 `platforms;android-37.0`（37 的次版本命名）。若报找不到，先 `sdkmanager --list | Select-String "platforms;android-37"` 看实际名字。

### 2.3 Gradle（走腾讯镜像，比 services.gradle.org 稳）

```powershell
foreach($v in @("9.7.1","9.1.0")){
  $zip="D:\ai\env\gradle-$v-bin.zip"
  curl.exe -L -o $zip "https://mirrors.cloud.tencent.com/gradle/gradle-$v-bin.zip"
  Expand-Archive $zip -DestinationPath "D:\ai\env" -Force
}
$env:JAVA_HOME="D:\ai\env\jdk21"; D:\ai\env\gradle-9.7.1\bin\gradle.bat --version
```

> 项目自带的 `gradlew` 会去 `services.gradle.org` 拉分发包，国内经常卡在 0 字节，所以本包统一用上面装好的 `gradle.bat`，不用 wrapper。

### 2.4 Python 3.10（Chaquopy 必需）

Chaquopy 只认 **3.10**，且需要一个带 pip 的真实 CPython。官方 exe 静默安装有时会挂，用 NuGet 的绿色包最稳：

```powershell
$nupkg="D:\ai\env\python310.zip"
curl.exe -L -o $nupkg "https://www.nuget.org/api/v2/package/python/3.10.11"
Expand-Archive $nupkg -DestinationPath "D:\ai\env\py310x" -Force
Move-Item "D:\ai\env\py310x\tools" "D:\ai\env\Python310"
D:\ai\env\Python310\python.exe -V          # Python 3.10.11
D:\ai\env\Python310\python.exe -m pip -V
```

编译时通过 Gradle 属性指定：

```
-Pchaquopy.buildPython=D:\ai\env\Python310\python.exe
```

### 2.5 Gradle 镜像（强烈建议）

把 `env\init.gradle` 复制到：

```
%USERPROFILE%\.gradle\init.gradle
```

内容就是给 `pluginManagement` / `dependencyResolutionManagement` 前置阿里云仓库（`repository/gradle-plugin`、`repository/google`、`repository/public`），官方仓库保留兜底。

**注意**：这个文件必须是 **UTF-8 无 BOM**，带 BOM 会让 Gradle 直接报 `Unexpected character: '\ufeff' @ line 1, column 1`。

---

## 3. 编译时必备的环境变量

```powershell
$env:JAVA_HOME="D:\ai\env\jdk21"
$env:ANDROID_HOME="D:\ai\env\android-sdk"
$env:ANDROID_SDK_ROOT="D:\ai\env\android-sdk"
$env:PATH="D:\ai\env\Python310;D:\ai\env\Python310\Scripts;$env:PATH"
```

`src\TV\local.properties`（从 `env\local.properties.template` 复制）必须有：

```properties
sdk.dir=D\:\\ai\\env\\android-sdk
storeFile=../fongmi.jks
keyAlias=fongmi
storePassword=fongmi123
```

`app/build.gradle` 里 `rootProject.file('local.properties').withInputStream { ... }` 是**硬读**的，缺文件直接构建失败；`storeFile` 相对 **`app/`** 目录解析，所以写 `../fongmi.jks`。

---

## 4. 依赖来源（会不会被墙）

| 来源 | 用途 | 备注 |
|---|---|---|
| `maven.aliyun.com` | AGP / androidx / kotlin / 第三方库 | 镜像优先，快 |
| `dl.google.com` | Google Maven 兜底 | 可直连 |
| `repo1.maven.org` | Maven Central 兜底 | 可直连，慢 |
| `jitpack.io` | `com.github.*` 依赖（NewPipeExtractor、AndroidAutoSize、TextDrawable、LibRtmp 等） | 直连有时抽风，重试即可 |
| `chaquo.com/pypi-13.1` | Python android wheel（lxml/ujson/pycryptodome 等） | **很慢（10~25 kB/s）**，首次编译约 10~20 分钟，无法镜像，只能等 |
| `services.gradle.org` / `mirrors.cloud.tencent.com` | Gradle 分发包 | 走腾讯镜像 |
| `dl.google.com/android/repository` | SDK 组件 | 可直连 |

---

## 5. 磁盘与时间预算

- 环境（JDK+SDK+Gradle×2+Python）：约 **4~5 GB**
- 源码 + aar：约 **250 MB**（本包 `src/` + `libs/`）
- 构建中间产物：约 **3~4 GB**（`TV/app/build`、`media/libraries/*/buildout`、`~/.gradle/caches`）
- 首次全量编译：**30~50 分钟**（其中 Chaquopy wheel 下载占大头）
- 只改 TV 代码 + 复用 `app/libs` 里的 aar：**约 9 分钟**
