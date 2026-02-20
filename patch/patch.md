# Hướng dẫn Áp dụng Hệ thống Multi-Layout cho Gamepad và Keyboard

Tài liệu này là cẩm nang toàn diện mô tả hệ thống đa bố cục (multi-layout) cho cả Gamepad và Keyboard ảo trong ứng dụng Cynix, cho phép người dùng tạo, chọn, đổi tên, sao chép, và xóa nhiều cấu hình điều khiển khác nhau thông qua in-game menu.

---

## 1. Cấu trúc thư mục Patch

Bộ patch bao gồm các file sau:
```
patch/
├── patch.md                     ← File này (tài liệu hướng dẫn)
├── change.patch                 ← Git diff chứa TẤT CẢ thay đổi logic và giao diện cho cả Gamepad & Keyboard
└── file/
    ├── GamepadLayoutManager.java   ← Class quản lý CRUD bố cục cho Gamepad
    ├── KeyboardLayoutManager.java  ← Class quản lý CRUD bố cục cho Keyboard
    └── (Các file khác nếu có)
```

**YÊU CẦU DÀNH CHO AI / LẬP TRÌNH VIÊN TƯƠNG LAI:**
**KHÔNG CẦN CODE LẠI TỪ ĐẦU!** Hãy thực hiện theo thứ tự dưới đây để áp dụng toàn bộ tính năng này một cách nhanh chóng:

1. Copy trực tiếp các file manager từ `patch/file/` vào source code.
2. Đọc file `patch/change.patch` để hiểu luồng thay đổi và dùng nó để sửa đổi các lớp Controller/Loader/Game hiện tại. (Bạn có thể dùng lệnh `git apply patch/change.patch` hoặc áp dụng thủ công nếu code base đã thay đổi gây xung đột).

---

## 2. Chi tiết hệ thống lưu trữ (Storage Design)

Cả hai hệ thống Gamepad và Keyboard chia sẻ chung một pattern thiết kế:
- **Metadata** (Tên layout, UUID, cờ Active, trạng thái Migrate) sẽ được lưu chung vào một file `SharedPreferences` duy nhất (`gamepad_layouts_meta` hoặc `keyboard_layouts_meta`).
- **Dữ liệu Mapping thực tế** lưu trong từng file `SharedPreferences` riêng biệt, được hậu tố bởi UUID của layout đó (Ví dụ: `gamepad_layout_<UUID>` hoặc `keyboard_layout_<UUID>`).
- Khi Game khởi tạo, nó sẽ đọc Metadata để biết Layout nào đang Active, sau đó truyền `<UUID>` đó cho Configuration Loader để Loader biết cần chép và ghi vào đúng file Preference nào.

---

## 3. Cách áp dụng - Từng bước (Step x Step)

### Bước 1: Thêm 2 File Layout Manager mới
Bạn lấy thẳng các file này trong thư mục `patch/file/` để copy sang source, hoàn toàn độc lập và không lo xung đột:

1. **Copy:** `patch/file/GamepadLayoutManager.java`
   **Đến:** `app/src/main/java/com/limelight/binding/input/virtual_controller/GamepadLayoutManager.java`

2. **Copy:** `patch/file/KeyboardLayoutManager.java`
   **Đến:** `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardLayoutManager.java`

Các class này đảm nhận toàn bộ các thao tác: Create, Rename, Duplicate, Delete, set/getActive, và migrate dữ liệu Legacy cũ.

### Bước 2: Sửa đổi các Loader (`VirtualControllerConfigurationLoader.java` & `KeyBoardControllerConfigurationLoader.java`)
- Thêm Overload cho 2 hàm `saveProfile` và `loadFromPreferences`. Các hàm mới này nhận thêm tham số thứ 3 là `String layoutId`.
- Loader sẽ fetch đúng Preference File dựa trên tham số `layoutId` thay vì dùng file mặc định. 
- (Đặc biệt ở KeyboardLoader: Để làm đẹp UI, thay vì hiển thị chữ "TP" che khuất Touchpad, set button label thành text rỗng `""` nhưng vẫn lưu "TP" vào JSON cấu hình).

### Bước 3: Cập nhật các Controller (`VirtualController.java` & `KeyBoardController.java`)
- Thêm thuộc tính `private String currentLayoutId` cùng các hàm get/set.
- Bất cứ khi nào gọi hàm Save Configuration/Refresh layout trong Controller, tham số `currentLayoutId` sẽ được nạp xuống Configuration Loader tương ứng mà ta vừa xử lý ở Bước 2.

### Bước 4: Khai báo Management trong `Game.java`
- Khởi tạo 2 biến quản lý `layoutManager` (Gamepad) và `keyboardLayoutManager` (Keyboard).
- Sửa hàm `initVirtualController` và `initKeyboardController` để nó: 1. Khởi tạo Manager. 2. Migrate data cũ (nếu có). 3. Gán cờ Active Layout vào Controller.
- Tạo các hàm switch/transition như `switchGamepadLayout(String id)` hay `switchKeyboardLayout(String id)` để kích hoạt thay đổi cấu hình nóng ngay trong in-game.

### Bước 5: Cập nhật Sub-Menu In-Game (`GameMenu.java`)
- Thay thế các Button Menu "Toggle Gamepad" và "Toggle Keyboard" nguyên thuỷ thành 2 Menu Category (Sub-Menu) riêng.
- Trong sub-menu sẽ render Dynamic List các layout đang có (đọc từ Manager). Bên cạnh là nút để toggle hiển thị on-screen, thêm nút (+) để Tạo mới, và các Options Copy/Rename/Trashing cho mỗi layout đã chọn.

### Bước 6: String Resources (Đa Ngôn Ngữ)
- Trong `change.patch`, có hỗ trợ các string resource cho English, Vietnamese, Chinese, French, v.v.
- Các String dùng cho Label/Alerts/Toast như "Create New Layout", "Rename Layout", v.v... cần được bổ sung vào `app/src/main/res/values/strings.xml` và các locale tương ứng.

---

> **Làm thế nào để áp dụng nếu gặp lỗi? (Conflict Resolution)**
> Nếu bạn chạy `git apply patch/change.patch` mà báo lỗi `hunk failed`, điều đó tức là source upstream/cũ của Cynix đã có những dòng thay thế không khớp so với thời điểm lập patch. Hãy mở file `.patch` bằng trình editor (Notepad/Vscode), sau đó do-dò các khối `@@ ... @@` và copy/paste thêm dấu `+` vào code một cách thủ công đối với các file có lỗi.
