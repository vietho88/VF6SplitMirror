# VF6 Split Mirror v0.1 (experimental)

Mục tiêu: chọn **2 app trên điện thoại**, mở chúng ở chế độ chia đôi, rồi phản chiếu màn hình đó vào một app Android Auto để dùng trên màn hình VF6.

## Cách hoạt động

1. `MainActivity` liệt kê app launcher trên điện thoại và cho chọn App trái / App phải.
2. `SplitAccessibilityService` gọi thao tác hệ thống `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`, sau đó mở app thứ hai với `FLAG_ACTIVITY_LAUNCH_ADJACENT`.
3. `MirrorCaptureService` dùng MediaProjection để chụp màn hình điện thoại 1280x720, tối đa khoảng 8 fps.
4. `MainCarActivity` dùng AAuto SDK v4.6 (cùng hướng với Widgets for Auto) để hiển thị frame trong Android Auto.
5. Tap/swipe trên màn hình xe được ánh xạ ngược về điện thoại bằng Accessibility `dispatchGesture`.

## Điều kiện

- Android Auto bật Developer mode + Unknown sources.
- Cài APK theo hướng KingInstaller giống Widgets for Auto.
- Bật Accessibility: **VF6 Split Mirror Control**.
- Bật auto-rotate và đặt điện thoại ngang trước khi chia đôi.
- Hai app phải hỗ trợ Android split-screen. Một số app có thể từ chối multi-window.

## Cách dùng

1. Mở **VF6 Split Mirror** trên điện thoại.
2. Chọn App bên trái và App bên phải.
3. Bấm **Bật Accessibility** và bật `VF6 Split Mirror Control`.
4. Bật xoay tự động, xoay điện thoại ngang.
5. Bấm **Chia 2 app + phản chiếu** và chấp nhận hộp thoại ghi màn hình Android.
6. Hai app sẽ được mở ở chế độ split nếu One UI cung cấp global split-screen action cho Accessibility.
7. Kết nối Android Auto và mở **VF6 Split Mirror** trên màn hình xe.

### Nếu không tự chia đôi

Trên một số firmware One UI/Android, action `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` không được expose cho Accessibility. Khi đó:
- vẫn để phản chiếu chạy;
- chia đôi 2 app thủ công trên điện thoại;
- Android Auto sẽ hiển thị đúng màn hình đã chia.

## Build APK trên GitHub Actions

Repo đã có `.github/workflows/build-apk.yml`.

1. Tạo repo GitHub mới và upload toàn bộ thư mục này.
2. Vào **Actions → Build APK → Run workflow**.
3. Khi build xong, tải artifact `VF6SplitMirror-debug-apk`.
4. Giải nén để lấy `app-debug.apk`.
5. Cài APK bằng KingInstaller.

## Lưu ý kỹ thuật

- Đây là bản thử nghiệm để xác nhận đường `KingInstaller + AAuto SDK + MediaProjection + split-screen` trên S24 Ultra/VF6.
- MediaProjection trên Android mới yêu cầu xác nhận ghi màn hình cho mỗi phiên mới.
- Hiệu năng hiện giới hạn ~8 fps để giảm nhiệt/CPU; bản sau có thể chuyển sang surface/OpenGL để 30 fps.
- Touch mapping tốt nhất khi điện thoại đang nằm ngang và ảnh không bị crop.
- Chỉ thao tác giao diện video/web khi xe đang đỗ hoặc do hành khách sử dụng.

## Nguồn / ghi công

Ý tưởng tương thích Android Auto projection app dựa trên cấu trúc của open-source **Widgets for Auto** (GPL-3.0, ns130291), dùng `com.github.martoreto:aauto-sdk:v4.6`. Cơ chế MediaProjection/VirtualDisplay tham khảo kiến trúc các dự án Android Auto mirroring mã nguồn mở hiện có. Source của project này được cung cấp cho mục đích thử nghiệm cá nhân.


## v0.5 compile fix

This revision keeps compileSdk 29 for legacy AAPT1/aauto-sdk compatibility and removes two references that require newer compile SDKs:

- `PendingIntent.FLAG_IMMUTABLE` (API 31) -> `FLAG_UPDATE_CURRENT`
- `AccessibilityService.getSystemActions()` (API 30) -> runtime attempt of `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` with existing fallback


## v0.6
- Replace unsupported `StatusBarController.hideAppHeader()` with documented `showTitle()` from aauto-sdk demo.
- GitHub Actions now stores `build.log` and prints compiler errors explicitly.
