# Hướng dẫn tạo bộ plugin Android đầy đủ hoạt động ngoại tuyến

Tài liệu này hướng dẫn cách đóng gói toàn bộ Android SDK, Build-Tools, AAPT2 ARM64, NDK, JDK, Gradle, ADB và Flutter SDK phiên bản chỉ dành cho Android vào một tệp `.txplugin`, sau đó nhập và cài đặt trong môi trường không có mạng. Plugin này không chứa tài nguyên biên dịch cho Web, iOS, Windows, macOS hay máy tính Linux; iOS cũng không thể biên dịch trong PRoot Linux ARM64.

## 0. Hiểu rõ triển khai hiện tại trước đã

`android-suite` trong kho mã nguồn hiện là plugin tích hợp sẵn (`BuiltinPluginBundles`), được điều khiển bởi `ToolManager.batchInstallComponents()`; tập lệnh mặc định của nó sẽ tải xuống tài nguyên Android/Gradle/NDK/Flutter.

Trong khi đó, `.txplugin` cục bộ đi theo luồng `ToolRegistry -> ToolManager -> GenericRecipeInstaller`. Vì vậy, để làm phiên bản hoàn toàn ngoại tuyến, nên gộp toàn bộ bộ công cụ Android thành một plugin cục bộ duy nhất, ví dụ `android-suite-offline`, với một tập lệnh cài đặt ngoại tuyến triển khai mọi tài nguyên trong một lần. Đừng sao chép nguyên xi tập lệnh có dùng mạng hiện tại vào gói.

Các triển khai liên quan:

- [ToolManifest.kt](../core/model/src/main/java/top/wkbin/tianxuan/core/model/ToolManifest.kt)
- [ToolRegistry.kt](../tools/src/main/java/top/wkbin/tianxuan/core/tools/ToolRegistry.kt)
- [GenericRecipeInstaller.kt](../tools/src/main/java/top/wkbin/tianxuan/runtime/tools/GenericRecipeInstaller.kt)
- [RuntimeAssetSynchronizer.kt](../runtime/src/main/java/top/wkbin/tianxuan/runtime/scripts/RuntimeAssetSynchronizer.kt)

## 1. Cấu trúc thư mục đề xuất

```text
android-suite-offline/
├── manifest.json
└── payload/
    ├── archives/
    │   ├── jdk-17-linux-arm64.tar.xz
    │   ├── gradle-8.14.2-bin.zip
    │   ├── android-platform-34.zip
    │   ├── android-build-tools-35.0.0.zip
    │   ├── android-sdk-tools-static-aarch64.zip
    │   ├── android-ndk-r29-arm64.tar.xz
    │   ├── cmake-linux-arm64.tar.xz
    │   ├── ninja-linux-arm64.zip
    │   ├── adb-linux-arm64
    │   └── flutter-linux-arm64-android-only-slim.tar.gz
    ├── checksums/
    │   └── SHA256SUMS
    ├── licenses/
    │   └── android-sdk-license
    ├── scripts/
    │   ├── install-android-suite.sh
    │   └── uninstall-android-suite.sh
    └── config/
        ├── gradle.properties
        └── tianxuan-android-ndk.gradle
```

### Tài nguyên nào là bắt buộc

| Tài nguyên | Android lõi | Flutter | Ghi chú |
|---|---:|---:|---|
| JDK 17 Linux ARM64 | Bắt buộc | Bắt buộc | Không dùng JDK x86_64. |
| Android Platform 34 | Bắt buộc | Bắt buộc | Ít nhất phải có `android.jar`. |
| Build-Tools 35 | Bắt buộc | Bắt buộc | Ít nhất phải có `d8.jar`, `aapt2`, `zipalign`, `apksigner`. |
| Bộ công cụ ARM64 AAPT2/SDK | Bắt buộc | Bắt buộc | Các chương trình gốc trong Build-Tools thông thường của Google thường là x86_64; phải dùng phiên bản ARM64 để ghi đè. |
| NDK ARM64 | Bắt buộc | Bắt buộc khi biên dịch mã gốc | Phải chứa `toolchains/llvm/.../clang`, `llvm-strip`. |
| Gradle 8.14.2 | Bắt buộc | Bắt buộc | Nên dùng một phiên bản cố định. |
| ADB ARM64 | Nên có | Cần để gỡ lỗi Android | Nếu RootFS đã có thì không cần đóng gói lại. |
| CMake/Ninja | Cần để biên dịch bằng NDK | Thường dùng cho plugin mã gốc Flutter | Nên cung cấp kèm. |
| Flutter ARM64 SDK | Tùy chọn | Bắt buộc | Dùng gói Flutter Linux ARM64 chỉ dành cho Android đã được tinh gọn; không chứa tài nguyên Web/iOS/máy tính. |

Nếu yêu cầu "có thể cài đặt ngay cả khi mất mạng và RootFS hoàn toàn mới", bạn cũng phải cung cấp luôn các công cụ chạy như `adb`, CMake, Ninja, `sha256sum`, `tar`, `xz`, hoặc xác nhận chúng đã có sẵn trong RootFS. Đừng chạy `apt-get install` trong tập lệnh cài đặt ngoại tuyến. Việc giải nén ZIP ưu tiên dùng `unzip` của hệ thống; khi không có `unzip` thì tự động chuyển sang `jar xf` của JDK đã triển khai, nên không cần cài thêm gói giải nén từ mạng.

## 2. Nguyên tắc chuẩn bị tài nguyên

### 2.1 Mọi tài nguyên phải là phiên bản chạy được trên Linux ARM64

Ứng dụng chủ là Android ARM64, nhưng tài nguyên plugin lại chạy bên trong PRoot Linux. Tài nguyên cần đáp ứng:

```sh
file jdk/bin/java
readelf -h adb | grep -E 'Machine|AArch64'
```

Kết quả phải hiện `ARM aarch64` hoặc `AArch64`. Không được đưa vào gói các tệp nhị phân x86_64 trích từ Android APK, tệp `.exe` của Windows hay tệp Mach-O của macOS.

### 2.2 Đừng để tệp nén lồng thêm một tầng thư mục plugin bên trong gói

Đúng:

```text
payload/archives/flutter-linux-arm64.tar.xz
```

Không nên:

```text
payload/archives/flutter-linux-arm64/flutter/bin/flutter
```

Nên lưu nguyên tệp nén chính thức, để tập lệnh cài đặt đảm nhiệm việc kiểm tra, giải nén và thay thế nguyên tử.

### 2.3 Ghi lại SHA-256

PowerShell:

```powershell
Get-ChildItem .\payload\archives -File | Get-FileHash -Algorithm SHA256 |
  ForEach-Object { "{0}  {1}" -f $_.Hash.ToLower(), $_.Path.Substring((Get-Location).Path.Length + 1) } |
  Set-Content .\payload\checksums\SHA256SUMS
```

Linux:

```sh
sha256sum payload/archives/* > payload/checksums/SHA256SUMS
```

## 3. Ví dụ `manifest.json`

```json
{
  "schemaVersion": 1,
  "id": "android-suite-offline",
  "name": "Android 全栈开发套件（离线版）",
  "description": "内置 JDK、Android SDK、Build-Tools、ARM64 AAPT2、NDK、Gradle、ADB、CMake、Ninja 和 Flutter 的离线开发环境",
  "version": "1.0.0",
  "latestVersion": "1.0.0",
  "enabled": true,
  "publisher": "Your Name",
  "category": "DEVELOPER",
  "launchType": "command",
  "architectures": ["ARM64"],
  "permissions": ["WORKSPACE_READ", "WORKSPACE_WRITE"],
  "updateStrategy": "REINSTALL",
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE",
  "installSteps": [
    "/bin/sh \"$TIANXUAN_PLUGIN_PAYLOAD/scripts/install-android-suite.sh\""
  ],
  "uninstallSteps": [
    "/bin/sh \"$TIANXUAN_PLUGIN_PAYLOAD/scripts/uninstall-android-suite.sh\""
  ],
  "launchCommand": "gradle --version",
  "verifyCommand": "test -x \"$TIANXUAN_TOOL_DIR/bin/java\" && java -version && gradle --version && test -f /opt/android-sdk/platforms/android-34/android.jar && test -f /opt/gradle-8.14.2/lib/gradle-launcher-8.14.2.jar",
  "commandLinks": ["java", "javac", "gradle", "adb", "flutter", "dart"],
  "environment": {
    "ANDROID_HOME": "/opt/android-sdk",
    "ANDROID_SDK_ROOT": "/opt/android-sdk",
    "GRADLE_HOME": "/opt/gradle-8.14.2",
    "JAVA_HOME": "/opt/tianxuan/toolchains/android/jdk",
    "ANDROID_NDK_HOME": "/opt/tianxuan/toolchains/android/ndk"
  }
}
```

Trình nhập sẽ ép plugin cục bộ thành `source=LOCAL`, `offlineOnly=true` và `installMethod=LOCAL_PACKAGE`. Với plugin ngoại tuyến, `dependencies` phải để trống, không được phụ thuộc vào việc tải môi trường thực thi từ mạng.

## 4. Cấu trúc tập lệnh cài đặt ngoại tuyến

Đừng dùng trực tiếp `app/src/main/assets/scripts/setup_android_core.sh` và `setup_flutter.sh` hiện tại, vì chúng chứa logic tải từ mạng. Hãy sao chép rồi tạo mới:

```text
payload/scripts/install-android-suite.sh
```

Cấu trúc tập lệnh đề xuất:

```sh
#!/bin/sh
set -eu

PAYLOAD="${TIANXUAN_PLUGIN_PAYLOAD:?missing TIANXUAN_PLUGIN_PAYLOAD}"
ARCHIVES="$PAYLOAD/archives"
CHECKSUMS="$PAYLOAD/checksums/SHA256SUMS"
TOOL_DIR="${TIANXUAN_TOOL_DIR:?missing TIANXUAN_TOOL_DIR}"
ANDROID_HOME="/opt/android-sdk"
TOOLCHAIN_ROOT="/opt/tianxuan/toolchains/android"
JDK_HOME="$TOOLCHAIN_ROOT/jdk"
NDK_HOME="$TOOLCHAIN_ROOT/ndk"
GRADLE_VERSION="8.14.2"

mkdir -p "$TOOL_DIR/bin" "$ANDROID_HOME" "$TOOLCHAIN_ROOT" /opt/tianxuan/bin

# 1. 校验所有内置归档，不联网、不调用 apt。
if [ -f "$CHECKSUMS" ]; then
    (cd "$PAYLOAD" && sha256sum -c checksums/SHA256SUMS)
fi

# 2. JDK：先解压到临时目录，验证后原子替换。
rm -rf "$JDK_HOME.staging"
mkdir -p "$JDK_HOME.staging"
tar -xJf "$ARCHIVES/jdk-17-linux-arm64.tar.xz" -C "$JDK_HOME.staging"
JDK_SOURCE=$(find "$JDK_HOME.staging" -type f -path '*/bin/java' -print -quit)
test -n "$JDK_SOURCE"
JDK_SOURCE=$(dirname "$(dirname "$JDK_SOURCE")")
rm -rf "$JDK_HOME"
mv "$JDK_SOURCE" "$JDK_HOME"
rm -rf "$JDK_HOME.staging"

# 3. Gradle。
rm -rf "/opt/gradle-$GRADLE_VERSION.staging"
mkdir -p "/opt/gradle-$GRADLE_VERSION.staging"
unzip -q "$ARCHIVES/gradle-$GRADLE_VERSION-bin.zip" -d "/opt/gradle-$GRADLE_VERSION.staging"
test -f "/opt/gradle-$GRADLE_VERSION.staging/gradle-$GRADLE_VERSION/lib/gradle-launcher-$GRADLE_VERSION.jar"
rm -rf "/opt/gradle-$GRADLE_VERSION"
mv "/opt/gradle-$GRADLE_VERSION.staging/gradle-$GRADLE_VERSION" "/opt/gradle-$GRADLE_VERSION"
rm -rf "/opt/gradle-$GRADLE_VERSION.staging"

# 4. Android Platform。
rm -rf /tmp/android-platform-staging
mkdir -p /tmp/android-platform-staging
unzip -q "$ARCHIVES/android-platform-34.zip" -d /tmp/android-platform-staging
PLATFORM_SOURCE=$(find /tmp/android-platform-staging -name android.jar -print -quit | xargs -r dirname)
test -n "$PLATFORM_SOURCE"
rm -rf "$ANDROID_HOME/platforms/android-34"
mkdir -p "$ANDROID_HOME/platforms"
mv "$PLATFORM_SOURCE" "$ANDROID_HOME/platforms/android-34"
rm -rf /tmp/android-platform-staging

# 5. Build-Tools + ARM64 原生工具。
rm -rf /tmp/android-build-tools-staging
mkdir -p /tmp/android-build-tools-staging
unzip -q "$ARCHIVES/android-build-tools-35.0.0.zip" -d /tmp/android-build-tools-staging
BUILD_SOURCE=$(find /tmp/android-build-tools-staging -name source.properties -print -quit | xargs -r dirname)
test -f "$BUILD_SOURCE/lib/d8.jar"
rm -rf "$ANDROID_HOME/build-tools/35.0.0"
mkdir -p "$ANDROID_HOME/build-tools"
mv "$BUILD_SOURCE" "$ANDROID_HOME/build-tools/35.0.0"
rm -rf /tmp/android-build-tools-staging

rm -rf /tmp/android-arm64-tools-staging
mkdir -p /tmp/android-arm64-tools-staging
unzip -q "$ARCHIVES/android-sdk-tools-static-aarch64.zip" -d /tmp/android-arm64-tools-staging
test -x /tmp/android-arm64-tools-staging/build-tools/aapt2
cp -a /tmp/android-arm64-tools-staging/build-tools/. "$ANDROID_HOME/build-tools/35.0.0/"
rm -rf /tmp/android-arm64-tools-staging

# 6. NDK / CMake / Ninja / ADB。根据实际归档结构调整目录名。
rm -rf "$NDK_HOME.staging"
mkdir -p "$NDK_HOME.staging"
tar -xJf "$ARCHIVES/android-ndk-r29-arm64.tar.xz" -C "$NDK_HOME.staging"
NDK_SOURCE=$(find "$NDK_HOME.staging" -name source.properties -print -quit | xargs -r dirname)
test -x "$NDK_SOURCE/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
rm -rf "$NDK_HOME"
mv "$NDK_SOURCE" "$NDK_HOME"
rm -rf "$NDK_HOME.staging"

mkdir -p "$TOOL_DIR/bin"
ln -sfn "$JDK_HOME/bin/java" "$TOOL_DIR/bin/java"
ln -sfn "$JDK_HOME/bin/javac" "$TOOL_DIR/bin/javac"
ln -sfn "/opt/gradle-$GRADLE_VERSION/bin/gradle" "$TOOL_DIR/bin/gradle"

if [ -f "$ARCHIVES/adb-linux-arm64" ]; then
    cp "$ARCHIVES/adb-linux-arm64" "$TOOL_DIR/bin/adb"
    chmod +x "$TOOL_DIR/bin/adb"
fi

# 7. 可选 Flutter。只有包内确实包含归档时才执行。
if [ -s "$ARCHIVES/flutter-linux-arm64.tar.xz" ]; then
    rm -rf /tmp/flutter-staging
    mkdir -p /tmp/flutter-staging
    tar -xJf "$ARCHIVES/flutter-linux-arm64.tar.xz" -C /tmp/flutter-staging
    FLUTTER_SOURCE=$(find /tmp/flutter-staging -type f -path '*/bin/flutter' -print -quit | xargs -r dirname | xargs -r dirname)
    test -x "$FLUTTER_SOURCE/bin/flutter"
    rm -rf /opt/flutter
    mv "$FLUTTER_SOURCE" /opt/flutter
    rm -rf /tmp/flutter-staging
    ln -sfn /opt/flutter/bin/flutter "$TOOL_DIR/bin/flutter"
    ln -sfn /opt/flutter/bin/dart "$TOOL_DIR/bin/dart"
fi

ln -sfn "$TOOL_DIR/bin/java" /opt/tianxuan/bin/java
ln -sfn "$TOOL_DIR/bin/javac" /opt/tianxuan/bin/javac
ln -sfn "$TOOL_DIR/bin/gradle" /opt/tianxuan/bin/gradle
[ -e "$TOOL_DIR/bin/adb" ] && ln -sfn "$TOOL_DIR/bin/adb" /opt/tianxuan/bin/adb || true
[ -e "$TOOL_DIR/bin/flutter" ] && ln -sfn "$TOOL_DIR/bin/flutter" /opt/tianxuan/bin/flutter || true
[ -e "$TOOL_DIR/bin/dart" ] && ln -sfn "$TOOL_DIR/bin/dart" /opt/tianxuan/bin/dart || true

mkdir -p /root/.gradle
cp "$PAYLOAD/config/gradle.properties" /root/.gradle/gradle.properties
mkdir -p /root/.gradle/init.d
cp "$PAYLOAD/config/tianxuan-android-ndk.gradle" /root/.gradle/init.d/tianxuan-android-ndk.gradle
printf '%s\n' 'android.builder.sdkDownload=false' >> /root/.gradle/gradle.properties

test -x "$TOOL_DIR/bin/java"
test -f "$ANDROID_HOME/platforms/android-34/android.jar"
test -f "/opt/gradle-$GRADLE_VERSION/lib/gradle-launcher-$GRADLE_VERSION.jar"
test -x "$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
```

Đây mới chỉ là khung của quy trình cài đặt; cấu trúc thư mục bên trong tệp nén cần được điều chỉnh theo đúng tệp JDK, NDK và Flutter mà bạn thực sự tải về. Nguyên tắc then chốt là: mọi `curl`, `wget`, `git clone`, `apt-get` đều phải bị xóa khỏi tập lệnh ngoại tuyến.

## 5. Tập lệnh gỡ cài đặt ngoại tuyến

`payload/scripts/uninstall-android-suite.sh`:

```sh
#!/bin/sh
set -eu

TOOL_DIR="${TIANXUAN_TOOL_DIR:?missing TIANXUAN_TOOL_DIR}"
rm -f /opt/tianxuan/bin/java /opt/tianxuan/bin/javac /opt/tianxuan/bin/gradle
rm -f /opt/tianxuan/bin/adb /opt/tianxuan/bin/flutter /opt/tianxuan/bin/dart
rm -rf "$TOOL_DIR"
rm -rf /opt/android-sdk
rm -rf /opt/gradle-8.14.2
rm -rf /opt/tianxuan/toolchains/android
rm -rf /opt/flutter
rm -f /root/.gradle/init.d/tianxuan-android-ndk.gradle
```

Nếu muốn giữ lại dự án của người dùng và bộ nhớ đệm Gradle, đừng xóa `/root/.gradle/caches`, cũng đừng xóa `$TIANXUAN_TOOL_DATA`.

## 6. Đóng gói

Windows PowerShell:

```powershell
$root = "D:\plugins\android-suite-offline"
Compress-Archive `
  -Path "$root\manifest.json", "$root\payload" `
  -DestinationPath "D:\plugins\android-suite-offline.txplugin" `
  -Force
```

Linux/macOS:

```sh
cd android-suite-offline
zip -r ../android-suite-offline.txplugin manifest.json payload
```

Kiểm tra nội dung gói:

```sh
unzip -l android-suite-offline.txplugin
```

Thư mục gốc phải là:

```text
manifest.json
payload/archives/...
payload/checksums/SHA256SUMS
payload/scripts/...
```

Không được có thêm một tầng thư mục `android-suite-offline/` bên ngoài.

## 7. Nhập và cài đặt

1. Mở "Cài đặt -> Trung tâm plugin và công cụ".
2. Nhấn nút nhập ở góc trên bên phải, chọn `android-suite-offline.txplugin`.
3. Sau khi nhập thành công, toàn bộ bộ công cụ Android sẽ hiện ra như một plugin cục bộ.
4. Nhấn Cài đặt.
5. Tập lệnh cài đặt trước tiên kiểm tra tài nguyên đóng gói, rồi triển khai JDK, SDK, Build-Tools, NDK, Gradle, ADB và Flutter.
6. Sau khi `verifyCommand` báo đạt, dùng thiết bị đầu cuối kiểm tra lại `java`, `gradle`, `adb`, `flutter`.

Quá trình cài đặt không được truy cập mạng. Nếu nhật ký xuất hiện `curl`, `wget`, `apt-get update` hay `git clone`, chứng tỏ tập lệnh vẫn còn nhánh dùng mạng và không thể coi là plugin hoàn toàn ngoại tuyến.

## 8. Giới hạn kích thước và khuyến nghị thực tế

Trình nhập cục bộ hiện giới hạn tổng kích thước sau khi giải nén ZIP là 8 GiB. Toàn bộ Android + NDK + Flutter vẫn có thể tiệm cận ngưỡng này, vì vậy phải thống kê kích thước tài nguyên trước khi đóng gói:

```powershell
Get-ChildItem .\payload -Recurse -File | Measure-Object Length -Sum
```

Nếu vượt 8 GiB, triển khai hiện tại không thể nhập bằng một `.txplugin` duy nhất; về sau cần bổ sung khả năng chia plugin thành nhiều phần hoặc gói tài nguyên dùng chung. Phải đảm bảo ứng dụng có đủ dung lượng lưu trữ nội bộ; không được dựa vào `/sdcard` để thực thi trực tiếp.

## 9. Kiểm tra cuối cùng

```text
[ ] Mọi chương trình gốc JDK/NDK/Flutter/ADB/SDK đều là Linux ARM64
[ ] payload/archives chứa toàn bộ archive cần thiết cho offline
[ ] payload/checksums/SHA256SUMS khớp với tệp thực tế
[ ] Script cài đặt không chứa curl, wget, git clone, apt-get hay URL Maven/Google trực tuyến
[ ] Android Platform, Build-Tools, AAPT2, NDK, Gradle đều có kiểm tra sau khi cài
[ ] Có thể hoàn tất cài đặt mới khi mất mạng
[ ] Cài đặt lặp lại không làm hỏng môi trường hiện có
[ ] Gỡ cài đặt không xóa project người dùng và cache Gradle (trừ khi người dùng chủ chọn xóa dữ liệu)
[ ] Tổng kích thước sau khi giải nén `.txplugin` nhỏ hơn 8 GiB
```
