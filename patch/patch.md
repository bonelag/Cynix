# Patch: Multi Layouts cho Gamepad

## Mô tả tính năng

Thêm hệ thống đa bố cục (multi-layout) cho gamepad ảo. Cho phép người dùng tạo, chọn, nhân đôi, đổi tên và xóa nhiều bố cục gamepad khác nhau thông qua menu trong game.

**Trước khi có patch:** Chỉ có 1 bố cục gamepad duy nhất, lưu trong `SharedPreferences("OSC")`.

**Sau khi áp dụng patch:** Người dùng có thể quản lý nhiều bố cục, mỗi bố cục lưu trong `SharedPreferences("gamepad_layout_<UUID>")`, metadata lưu trong `SharedPreferences("gamepad_layouts_meta")`.

---

## Cấu trúc thư mục patch

```
patch/
├── patch.md                    ← File này (tài liệu hướng dẫn)
├── change.patch                ← Git diff cho các file đã sửa
└── file/
    └── GamepadLayoutManager.java   ← File mới hoàn toàn
```

---

## Cách áp dụng patch

### Bước 1: Thêm file mới

Copy file `patch/file/GamepadLayoutManager.java` vào:
```
app/src/main/java/com/limelight/binding/input/virtual_controller/GamepadLayoutManager.java
```

File này là lớp quản lý CRUD layout. Nó **không phụ thuộc** vào bất kỳ thay đổi nào khác, nên có thể thêm trước.

### Bước 2: Áp dụng diff

Chạy lệnh sau từ thư mục gốc của project:
```bash
git apply patch/change.patch
```

Nếu `git apply` thất bại (do upstream đã thay đổi), áp dụng thủ công theo hướng dẫn bên dưới.

---

## Chi tiết thay đổi từng file

### 1. [FILE MỚI] `GamepadLayoutManager.java`

**Đường dẫn:** `app/src/main/java/com/limelight/binding/input/virtual_controller/GamepadLayoutManager.java`

**Nguồn:** `patch/file/GamepadLayoutManager.java`

**Package:** `com.limelight.binding.input.virtual_controller`

**Mục đích:** Quản lý toàn bộ vòng đời của các gamepad layout (tạo, xóa, đổi tên, nhân đôi, chọn active layout).

**Thiết kế lưu trữ:**
- **Metadata** lưu trong `SharedPreferences("gamepad_layouts_meta")`:
  - `layout_ids`: Danh sách UUID cách nhau bởi dấu `,`
  - `layout_name_<id>`: Tên hiển thị của từng layout
  - `active_layout_id`: UUID của layout đang active
- **Dữ liệu layout** lưu trong `SharedPreferences("gamepad_layout_<UUID>")` — cùng format key với `SharedPreferences("OSC")` cũ.

**Hằng số quan trọng:**
- `DEFAULT_LAYOUT_ID = "default"` — ID cố định cho layout mặc định
- `PREFS_NAME = "gamepad_layouts_meta"` — Tên SharedPreferences cho metadata
- `LAYOUT_PREF_PREFIX = "gamepad_layout_"` — Prefix cho tên SharedPreferences của từng layout

**Method tĩnh:**
- `getLayoutPreferenceName(String layoutId)` → Trả về `"gamepad_layout_" + layoutId`. Dùng bởi `VirtualControllerConfigurationLoader` để biết lưu/đọc SharedPreferences nào.

**Method chính:**
- `createLayout(String name)` → Tạo layout mới (UUID ngẫu nhiên), trả về ID
- `duplicateLayout(String sourceId, String name)` → Copy toàn bộ SharedPreferences từ layout nguồn sang layout mới
- `renameLayout(String layoutId, String newName)` → Đổi tên
- `deleteLayout(String layoutId)` → Xóa (không cho xóa default), tự chuyển active về default nếu xóa layout đang active
- `getActiveLayoutId()` / `setActiveLayoutId(String id)` → Getter/setter layout đang active
- `getLayoutIds()` → Danh sách tất cả layout ID
- `getLayoutName(String id)` → Lấy tên hiển thị của layout
- `migrateFromLegacy(Context context)` → **Quan trọng!** Chạy 1 lần, copy dữ liệu từ `SharedPreferences("OSC")` sang layout "default"

---

### 2. [SỬA] `VirtualControllerConfigurationLoader.java`

**Đường dẫn:** `app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualControllerConfigurationLoader.java`

**Thay đổi:**

#### a) Method `saveProfile` — Thêm parameter `layoutId`

**Trước:**
```java
public static void saveProfile(final VirtualController controller, final Context context) {
    SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE).edit();
```

**Sau:**
```java
public static void saveProfile(final VirtualController controller, final Context context, final String layoutId) {
    String prefName = GamepadLayoutManager.getLayoutPreferenceName(layoutId);
    SharedPreferences.Editor prefEditor = context.getSharedPreferences(prefName, Activity.MODE_PRIVATE).edit();
```

#### b) Method `loadFromPreferences` — Thêm parameter `layoutId`

**Trước:**
```java
public static void loadFromPreferences(final VirtualController controller, final Context context) {
    SharedPreferences pref = context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE);
```

**Sau:**
```java
public static void loadFromPreferences(final VirtualController controller, final Context context, final String layoutId) {
    String prefName = GamepadLayoutManager.getLayoutPreferenceName(layoutId);
    SharedPreferences pref = context.getSharedPreferences(prefName, Activity.MODE_PRIVATE);
```

> **Lưu ý:** Cả hai method giữ nguyên logic bên trong, chỉ thay đổi nguồn SharedPreferences từ hardcode `"OSC"` sang dynamic `"gamepad_layout_<layoutId>"`.

---

### 3. [SỬA] `VirtualController.java`

**Đường dẫn:** `app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualController.java`

**Thay đổi:**

#### a) Thêm field `currentLayoutId`

Thêm sau dòng `ControllerInputContext inputContext = new ControllerInputContext();`:
```java
private String currentLayoutId = GamepadLayoutManager.DEFAULT_LAYOUT_ID;
```

#### b) Thêm getter/setter

Thêm sau method `removeElements()`:
```java
public void setCurrentLayoutId(String layoutId) {
    this.currentLayoutId = layoutId;
}

public String getCurrentLayoutId() {
    return currentLayoutId;
}
```

#### c) Sửa `refreshLayout()` — dùng `currentLayoutId`

Trong method `refreshLayout()`, thay dòng:
```java
VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
```
Thành:
```java
VirtualControllerConfigurationLoader.loadFromPreferences(this, context, currentLayoutId);
```

#### d) Sửa nút cấu hình — dùng `currentLayoutId` khi save

Trong `onClick` của `buttonConfigure` (khi thoát chế độ cấu hình), thay dòng:
```java
VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
```
Thành:
```java
VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context, currentLayoutId);
```

---

### 4. [SỬA] `GameMenu.java`

**Đường dẫn:** `app/src/main/java/com/limelight/GameMenu.java`

**Thay đổi:**

#### a) Thêm import

```java
import android.widget.EditText;
import com.limelight.binding.input.virtual_controller.GamepadLayoutManager;
```

#### b) Thay menu item "Toggle Gamepad" bằng "Gamepad Layouts"

Trong method `showMenu()`, tìm dòng:
```java
options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true, game::toggleVirtualController));
```
Thay bằng:
```java
options.add(new MenuOption(getString(R.string.game_menu_gamepad_layouts), true, () -> {
    hideMenu();
    showGamepadLayoutMenu();
}));
```

#### c) Thêm các method mới ở cuối class (trước dấu `}` đóng class)

- `showGamepadLayoutMenu()` — Hiển thị sub-menu quản lý layout:
  - Toggle Gamepad visibility
  - Danh sách các layout (layout active có dấu ✓)
  - Tạo mới / Nhân đôi / Đổi tên / Xóa
  - Nút Cancel

- `showLayoutNameInputDialog(title, defaultValue, callback)` — Dialog nhập tên layout

- `showDeleteConfirmDialog(layoutName, onConfirm)` — Dialog xác nhận xóa

- `LayoutNameCallback` — Interface callback cho dialog nhập tên

---

### 5. [SỬA] `Game.java`

**Đường dẫn:** `app/src/main/java/com/limelight/Game.java`

**Thay đổi:**

#### a) Thêm import

```java
import com.limelight.binding.input.virtual_controller.GamepadLayoutManager;
```

#### b) Thêm field

Thêm sau dòng `private VirtualController virtualController;`:
```java
private GamepadLayoutManager layoutManager;
```

#### c) Sửa method `initVirtualController()`

**Trước:**
```java
private void initVirtualController(){
    virtualController = new VirtualController(controllerHandler, (FrameLayout)rootView, this);
    virtualController.refreshLayout();
    virtualController.show();
}
```

**Sau:**
```java
private void initVirtualController(){
    if (layoutManager == null) {
        layoutManager = new GamepadLayoutManager(this);
        layoutManager.migrateFromLegacy(this);
    }
    virtualController = new VirtualController(controllerHandler, (FrameLayout)rootView, this);
    virtualController.setCurrentLayoutId(layoutManager.getActiveLayoutId());
    virtualController.refreshLayout();
    virtualController.show();
}
```

#### d) Thêm 2 method mới (sau method `toggleVirtualController`)

```java
public void switchGamepadLayout(String layoutId) {
    if (layoutManager == null) {
        layoutManager = new GamepadLayoutManager(this);
        layoutManager.migrateFromLegacy(this);
    }
    layoutManager.setActiveLayoutId(layoutId);
    if (virtualController != null) {
        virtualController.setCurrentLayoutId(layoutId);
        virtualController.refreshLayout();
        virtualController.show();
    } else {
        initVirtualController();
    }
    prefConfig.onscreenController = true;
}

public GamepadLayoutManager getLayoutManager() {
    if (layoutManager == null) {
        layoutManager = new GamepadLayoutManager(this);
        layoutManager.migrateFromLegacy(this);
    }
    return layoutManager;
}
```

---

### 6. [SỬA] String resources — 4 file ngôn ngữ

Thêm 17 string mới vào **cuối** mỗi file (trước `</resources>`):

#### Danh sách string keys:

| Key | Mô tả |
|-----|--------|
| `game_menu_gamepad_layouts` | Tiêu đề menu Gamepad Layouts |
| `gamepad_layout_create` | Nút tạo mới |
| `gamepad_layout_duplicate` | Nút nhân đôi |
| `gamepad_layout_rename` | Nút đổi tên |
| `gamepad_layout_delete` | Nút xóa |
| `gamepad_layout_name_hint` | Hint cho EditText nhập tên |
| `gamepad_layout_default_name` | Tên mặc định "Default" |
| `gamepad_layout_cannot_delete_default` | Thông báo không xóa được layout default |
| `gamepad_layout_delete_confirm` | Dialog xác nhận xóa (%s = tên layout) |
| `gamepad_layout_switched` | Toast đã chuyển layout (%s = tên) |
| `gamepad_layout_created` | Toast đã tạo layout (%s = tên) |
| `gamepad_layout_deleted` | Toast đã xóa layout (%s = tên) |
| `gamepad_layout_renamed` | Toast đã đổi tên (%s = tên mới) |
| `gamepad_layout_name_empty` | Cảnh báo tên trống |
| `gamepad_layout_toggle_gamepad` | Nút bật/tắt gamepad trong sub-menu |

#### Các file cần sửa:

- `app/src/main/res/values/strings.xml` (English)
- `app/src/main/res/values-vi/strings.xml` (Tiếng Việt)
- `app/src/main/res/values-zh-rCN/strings.xml` (中文简体)
- `app/src/main/res/values-fr/strings.xml` (Français)

> **Lưu ý cho AI:** Nếu upstream thêm ngôn ngữ mới, cần thêm 17 string trên vào file ngôn ngữ đó. Xem nội dung cụ thể trong `change.patch`.

---

## Lưu ý khi áp dụng lên bản cập nhật mới

1. **Migration tự động:** `migrateFromLegacy()` sẽ tự nhận biết đã migrate chưa (flag `legacy_migrated` trong metadata). Không cần lo duplicate.

2. **Backward compatible:** Nếu không áp dụng patch, app vẫn hoạt động bình thường với layout đơn.

3. **Xung đột có thể xảy ra tại:**
   - `GameMenu.showMenu()` — dòng `game_menu_toggle_virtual_model` bị thay thế
   - `Game.initVirtualController()` — block init được mở rộng
   - `VirtualControllerConfigurationLoader.saveProfile/loadFromPreferences` — signature thay đổi

4. **Nếu cần resolve conflict thủ công:** Ưu tiên giữ logic mới (có `layoutId` parameter) và đảm bảo `GamepadLayoutManager` import đúng.

5. **Thứ tự áp dụng an toàn:**
   1. Copy `GamepadLayoutManager.java` (file mới, không phụ thuộc)
   2. Sửa `VirtualControllerConfigurationLoader.java` (thêm layoutId param)
   3. Sửa `VirtualController.java` (dùng layoutId)
   4. Sửa `Game.java` (tích hợp layout manager)
   5. Sửa `GameMenu.java` (UI menu)
   6. Thêm strings vào tất cả file ngôn ngữ

---
---

# Patch: Multi Layouts cho Keyboard (Phím Tùy Chỉnh)

## Mô tả tính năng

Thêm hệ thống đa bố cục (multi-layout) cho bàn phím phím tùy chỉnh (Special Keys / `KeyBoardController`). Cho phép người dùng tạo, chọn, nhân đôi, đổi tên và xóa nhiều keyboard layout khác nhau thông qua sub-menu trong game.

**Trước khi có patch:** Chỉ có 1 bố cục keyboard duy nhất, lưu trong `SharedPreferences("OSC_Keyboard")`.

**Sau khi áp dụng patch:** Người dùng có thể quản lý nhiều bố cục, mỗi bố cục lưu trong `SharedPreferences("keyboard_layout_<UUID>")`, metadata lưu trong `SharedPreferences("keyboard_layouts_meta")`.

> **Pattern:** Thiết kế hoàn toàn giống `GamepadLayoutManager` (patch trước), áp dụng cho keyboard.

---

## Cấu trúc thư mục patch (cập nhật)

```
patch/
├── patch.md                         ← File này
├── change.patch                     ← Git diff (gamepad + keyboard)
└── file/
    ├── GamepadLayoutManager.java    ← File mới (gamepad - patch trước)
    └── KeyboardLayoutManager.java   ← File mới (keyboard - patch này)
```

---

## Cách áp dụng patch

### Bước 1: Thêm file mới

Copy file `patch/file/KeyboardLayoutManager.java` vào:
```
app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardLayoutManager.java
```

### Bước 2: Áp dụng diff

```bash
git apply patch/change.patch
```

Nếu thất bại, áp dụng thủ công theo hướng dẫn bên dưới.

---

## Chi tiết thay đổi từng file

### 1. [FILE MỚI] `KeyboardLayoutManager.java`

**Đường dẫn:** `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardLayoutManager.java`

**Nguồn:** `patch/file/KeyboardLayoutManager.java`

**Package:** `com.limelight.binding.input.virtual_controller.keyboard`

**Mục đích:** Quản lý CRUD cho keyboard layout (tạo, xóa, đổi tên, nhân đôi, chọn active).

**Thiết kế lưu trữ:**
- **Metadata** lưu trong `SharedPreferences("keyboard_layouts_meta")`:
  - `layout_ids`: JSONArray chứa danh sách UUID
  - `layout_name_<id>`: Tên hiển thị
  - `active_layout_id`: UUID layout đang active
  - `migrated_from_legacy`: Boolean đánh dấu đã migration
- **Dữ liệu layout** lưu trong `SharedPreferences("keyboard_layout_<UUID>")` — cùng format key với `SharedPreferences("OSC_Keyboard")` cũ.

**Hằng số quan trọng:**
- `DEFAULT_LAYOUT_ID = "default"`
- `META_PREFERENCE = "keyboard_layouts_meta"`
- `LAYOUT_PREFERENCE_PREFIX = "keyboard_layout_"`

**Method chính:**
- `getLayoutIds()` → Danh sách tất cả layout ID
- `getLayoutName(String id)` → Lấy tên hiển thị
- `getActiveLayoutId()` / `setActiveLayoutId(String id)` → Active layout
- `createLayout(String name)` → Tạo layout mới, trả về UUID
- `duplicateLayout(String sourceId, String name)` → Copy layout
- `renameLayout(String layoutId, String newName)` → Đổi tên
- `deleteLayout(String layoutId)` → Xóa (không xóa được default)
- `migrateFromLegacy(Context context)` → Chạy 1 lần, copy từ `SharedPreferences("OSC_Keyboard")` sang layout "default"
- `getLayoutPreferenceName(String layoutId)` → Static, trả về `"keyboard_layout_" + layoutId`

---

### 2. [SỬA] `KeyBoardControllerConfigurationLoader.java`

**Đường dẫn:** `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardControllerConfigurationLoader.java`

**Thay đổi:** Thêm overload `saveProfile` và `loadFromPreferences` nhận `layoutId`.

#### a) Method `saveProfile` — Thêm overload với `layoutId`

```java
// Overload mới:
public static void saveProfile(KeyBoardController controller, Context context, String layoutId) {
    String name = KeyboardLayoutManager.getLayoutPreferenceName(layoutId);
    saveProfileInternal(controller, context, name);
}
// Method cũ giữ nguyên (backward compatible), gọi saveProfileInternal()
```

#### b) Method `loadFromPreferences` — Thêm overload với `layoutId`

```java
// Overload mới:
public static void loadFromPreferences(KeyBoardController controller, Context context, String layoutId) {
    String name = KeyboardLayoutManager.getLayoutPreferenceName(layoutId);
    loadFromPreferencesInternal(controller, context, name);
}
// Method cũ giữ nguyên, gọi loadFromPreferencesInternal()
```

#### c) Method `createDigitalTouchButton` — Ẩn text "TP"

```java
// Nếu text truyền vào là "TP" (TouchPad) thì thay bằng rỗng để không hiển thị chữ lên button
button.setText("TP".equals(text) ? "" : text);
```

> **Lưu ý:** Logic bên trong extract ra `saveProfileInternal()` và `loadFromPreferencesInternal()`, cả method cũ và mới đều gọi chung.

---

### 3. [SỬA] `KeyBoardController.java`

**Đường dẫn:** `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java`

**Thay đổi:**

#### a) Thêm field `currentLayoutId`

```java
private String currentLayoutId = KeyboardLayoutManager.DEFAULT_LAYOUT_ID;
```

#### b) Thêm getter/setter

```java
public String getCurrentLayoutId() { return currentLayoutId; }
public void setCurrentLayoutId(String layoutId) { this.currentLayoutId = layoutId; }
```

#### c) Sửa `refreshLayout()` — dùng `currentLayoutId`

```diff
-KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context);
+KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context, currentLayoutId);
```

#### d) Sửa 3 chỗ save — dùng `currentLayoutId`

```diff
-KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
+KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context, currentLayoutId);
```

Có 3 vị trí save: buttonConfigure onClick, buttonClearAll onClick, và sau addKeys.

---

### 4. [SỬA] `Game.java`

**Đường dẫn:** `app/src/main/java/com/limelight/Game.java`

**Thay đổi:**

#### a) Thêm import

```java
import com.limelight.binding.input.virtual_controller.keyboard.KeyboardLayoutManager;
```

#### b) Thêm field

```java
private KeyboardLayoutManager keyboardLayoutManager;
```

#### c) Sửa `initKeyboardController()`

Thêm init `keyboardLayoutManager` + migration + set active layout cho controller trước khi refresh.

#### d) Thêm 2 method mới

```java
public void switchKeyboardLayout(String layoutId) { ... }
public KeyboardLayoutManager getKeyboardLayoutManager() { ... }
```

---

### 5. [SỬA] `GameMenu.java`

**Đường dẫn:** `app/src/main/java/com/limelight/GameMenu.java`

**Thay đổi:**

#### a) Thêm import

```java
import com.limelight.binding.input.virtual_controller.keyboard.KeyboardLayoutManager;
```

#### b) Thay menu item toggle keyboard bằng sub-menu

```diff
-options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true, game::toggleKeyboardController));
+options.add(new MenuOption(getString(R.string.game_menu_keyboard_layouts), true, () -> {
+    hideMenu();
+    showKeyboardLayoutMenu();
+}));
```

#### c) Thêm method `showKeyboardLayoutMenu()`

Sub-menu gồm:
- Toggle Keyboard visibility
- Danh sách layout (layout active có dấu ✓)
- Tạo mới / Nhân đôi / Đổi tên / Xóa (không xóa Default)
- Cancel

Tái sử dụng `showLayoutNameInputDialog()` và `showDeleteConfirmDialog()` từ Gamepad.

---

### 6. [SỬA] String resources — 4 file ngôn ngữ

Thêm 14 string mới vào **cuối** mỗi file (trước `</resources>`):

| Key | EN | VI |
|-----|----|----|
| `game_menu_keyboard_layouts` | Keyboard Layouts | Bố cục Keyboard |
| `keyboard_layout_create` | Create New Layout | Tạo bố cục mới |
| `keyboard_layout_duplicate` | Duplicate Current Layout | Nhân đôi bố cục hiện tại |
| `keyboard_layout_rename` | Rename Current Layout | Đổi tên bố cục hiện tại |
| `keyboard_layout_delete` | Delete Current Layout | Xóa bố cục hiện tại |
| `keyboard_layout_default_name` | Default | Mặc định |
| `keyboard_layout_delete_confirm` | Delete layout "%s"? | Xóa bố cục "%s"? |
| `keyboard_layout_switched` | Switched to layout: %s | Đã chuyển sang bố cục: %s |
| `keyboard_layout_created` | Created layout: %s | Đã tạo bố cục: %s |
| `keyboard_layout_deleted` | Deleted layout: %s | Đã xóa bố cục: %s |
| `keyboard_layout_renamed` | Renamed to: %s | Đã đổi tên thành: %s |
| `keyboard_layout_name_empty` | Layout name cannot be empty | Tên bố cục không được để trống |
| `keyboard_layout_toggle_keyboard` | Toggle Keyboard | Bật/Tắt Keyboard |

#### Các file cần sửa:
- `app/src/main/res/values/strings.xml` (English)
- `app/src/main/res/values-vi/strings.xml` (Tiếng Việt)
- `app/src/main/res/values-zh-rCN/strings.xml` (中文简体)
- `app/src/main/res/values-fr/strings.xml` (Français)

---

## Lưu ý khi áp dụng lên bản cập nhật mới

1. **Migration tự động:** `migrateFromLegacy()` nhận biết đã migrate chưa (flag `migrated_from_legacy`). Không lo duplicate.

2. **Phụ thuộc patch Gamepad:** Patch này dùng chung `showLayoutNameInputDialog()` và `showDeleteConfirmDialog()` từ `GameMenu.java` đã được thêm bởi patch Gamepad. **Phải áp dụng patch Gamepad trước.**

3. **Xung đột có thể xảy ra tại:**
   - `GameMenu.showMenu()` — dòng `game_menu_toggle_keyboard_model` bị thay thế
   - `Game.initKeyboardController()` — block init được mở rộng
   - `KeyBoardControllerConfigurationLoader.saveProfile/loadFromPreferences` — extract thành internal methods

4. **Thứ tự áp dụng an toàn:**
   1. Copy `KeyboardLayoutManager.java` (file mới, không phụ thuộc)
   2. Sửa `KeyBoardControllerConfigurationLoader.java` (thêm overload)
   3. Sửa `KeyBoardController.java` (dùng layoutId)
   4. Sửa `Game.java` (tích hợp layout manager)
   5. Sửa `GameMenu.java` (UI sub-menu)
   6. Thêm strings vào 4 file ngôn ngữ
