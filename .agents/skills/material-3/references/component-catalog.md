# Danh mục Component MD3

Tài liệu tham khảo đầy đủ về component của Material Design 3. **Ánh xạ chính:** Jetpack Compose (`androidx.compose.material3`) — nơi hầu hết người dùng triển khai UI hiện nay; **web** dùng tên phần tử và import của `@material/web` — [Material Web chỉ ở chế độ maintenance](https://m3.material.io/develop/web).

## Cập nhật Component tại Google I/O 2026

[Bản cập nhật I/O 2026](https://m3.material.io/blog/whats-new-at-io26) của Material nhấn mạnh hướng dẫn expressive được làm mới cho **list**, **menu**, **search** và **search app bar**, với Jetpack Compose là đường triển khai chính. Khi triển khai những cái này trên Android:

- Ưu tiên component `androidx.compose.material3` hiện tại và kiểm tra API expressive với Material3 BOM đang dùng.
- Kỳ vọng biến thể expressive có phong cách thị giác phong phú hơn, chuyển động và cấu hình linh hoạt hơn.
- Coi implementation web là xấp xỉ theo đặc tả trừ khi Material Web cung cấp component tương đương; Material Web vẫn ở chế độ maintenance.

## Actions

### Button

MD3 có 5 loại button, xếp theo mức nhấn mạnh: Filled > Filled Tonal > Elevated > Outlined > Text.

**Thuộc tính chung** (dùng cho mọi loại button):

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `disabled` | boolean | Vô hiệu hóa button |
| `href` | string | Biến button thành liên kết |
| `target` | string | Đích liên kết (`_blank`, v.v.) |
| `trailing-icon` | boolean | Di chuyển icon sang vị trí cuối |
| `type` | string | Kiểu form: `button`, `submit`, `reset` |

#### Filled Button
**Element**: `md-filled-button` | **Import**: `@material/web/button/filled-button.js`
**Dùng khi**: Hành động chính, mức nhấn mạnh cao nhất.

```html
<md-filled-button>Get started</md-filled-button>
<md-filled-button href="/signup">
  <md-icon slot="icon">arrow_forward</md-icon>
  Sign up
</md-filled-button>
```

**Tùy biến**: `--md-filled-button-container-color`, `--md-filled-button-label-text-color`, `--md-filled-button-container-shape`, `--md-filled-button-container-height`

#### Filled Tonal Button
**Element**: `md-filled-tonal-button` | **Import**: `@material/web/button/filled-tonal-button.js`
**Dùng khi**: Nhấn mạnh trung bình, mềm hơn filled. Hành động phụ bên cạnh một filled button.

```html
<md-filled-tonal-button>Save draft</md-filled-tonal-button>
```

**Tùy biến**: `--md-filled-tonal-button-container-color`, `--md-filled-tonal-button-label-text-color`

#### Elevated Button
**Element**: `md-elevated-button` | **Import**: `@material/web/button/elevated-button.js`
**Dùng khi**: Nhấn mạnh trung bình kèm bóng. Dùng trên nền có màu nơi tonal button bị hòa vào nền.

```html
<md-elevated-button>Add to cart</md-elevated-button>
```

#### Outlined Button
**Element**: `md-outlined-button` | **Import**: `@material/web/button/outlined-button.js`
**Dùng khi**: Nhấn mạnh trung bình, trung tính. Phù hợp cho hành động phụ.

```html
<md-outlined-button>Cancel</md-outlined-button>
```

**Tùy biến**: `--md-outlined-button-outline-color`, `--md-outlined-button-outline-width`

#### Text Button
**Element**: `md-text-button` | **Import**: `@material/web/button/text-button.js`
**Dùng khi**: Nhấn mạnh thấp nhất. Hành động inline, hành động trong dialog, tùy chọn ít quan trọng.

```html
<md-text-button>Learn more</md-text-button>
```

#### Kích thước Button (Expressive)
Button hiện hỗ trợ 5 kích thước: extra-small, small (mặc định), medium, large, extra-large. Đặt qua CSS:
```css
md-filled-button { --md-filled-button-container-height: 32px; } /* XS */
md-filled-button { --md-filled-button-container-height: 40px; } /* S (default) */
md-filled-button { --md-filled-button-container-height: 48px; } /* M */
md-filled-button { --md-filled-button-container-height: 56px; } /* L */
md-filled-button { --md-filled-button-container-height: 64px; } /* XL */
```

**A11y**: Button có sẵn role button. Dùng `aria-label` khi dùng button chỉ có icon. Vùng chạm tối thiểu 48x48dp.

### Button Group
**Element**: `md-button-group` | **Import**: `@material/web/button/button-group.js`
**Dùng khi**: Nhóm các hành động liên quan lại với nhau với cách trình bày kết nối.

```html
<md-button-group>
  <md-outlined-button>Day</md-outlined-button>
  <md-outlined-button>Week</md-outlined-button>
  <md-outlined-button>Month</md-outlined-button>
</md-button-group>
```

### FAB (Floating Action Button)
**Element**: `md-fab` | **Import**: `@material/web/fab/fab.js`
**Dùng khi**: Hành động quan trọng nhất trên một màn hình.

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `size` | string | `small`, `medium` (mặc định), `large` |
| `variant` | string | `surface`, `primary`, `secondary`, `tertiary` |
| `label` | string | Nhãn văn bản (cho extended FAB) |

```html
<md-fab aria-label="Create new">
  <md-icon slot="icon">add</md-icon>
</md-fab>

<md-fab size="small" variant="tertiary" aria-label="Edit">
  <md-icon slot="icon">edit</md-icon>
</md-fab>
```

**Tùy biến**: `--md-fab-container-color`, `--md-fab-container-shape`, `--md-fab-icon-color`
**A11y**: Luôn cung cấp `aria-label` vì FAB chỉ có icon.

### Extended FAB
**Element**: `md-extended-fab` | **Import**: `@material/web/fab/extended-fab.js`
**Dùng khi**: Hành động chính kèm văn bản giải thích.

```html
<md-extended-fab label="New message">
  <md-icon slot="icon">edit</md-icon>
</md-extended-fab>
```

### Icon Button
**Element**: `md-icon-button` | **Import**: `@material/web/iconbutton/icon-button.js`

4 biến thể với phần tử riêng:

| Biến thể | Element | Import |
|---------|---------|--------|
| Standard | `md-icon-button` | `@material/web/iconbutton/icon-button.js` |
| Filled | `md-filled-icon-button` | `@material/web/iconbutton/filled-icon-button.js` |
| Filled Tonal | `md-filled-tonal-icon-button` | `@material/web/iconbutton/filled-tonal-icon-button.js` |
| Outlined | `md-outlined-icon-button` | `@material/web/iconbutton/outlined-icon-button.js` |

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `toggle` | boolean | Bật hành vi toggle |
| `selected` | boolean | Trạng thái selected (khi toggle) |

```html
<md-icon-button aria-label="Settings">
  <md-icon>settings</md-icon>
</md-icon-button>

<!-- Toggle icon button (like/unlike) -->
<md-icon-button toggle aria-label="Favorite">
  <md-icon>favorite_border</md-icon>
  <md-icon slot="selected">favorite</md-icon>
</md-icon-button>
```

**A11y**: Luôn cung cấp `aria-label`. Toggle button nên có nhãn mô tả cho cả hai trạng thái.

### Segmented Button
**Chưa có element @material/web.** Triển khai bằng HTML chuẩn + token MD3:

```html
<div class="md3-segmented-buttons" role="group" aria-label="View options">
  <button class="md3-segmented-button md3-segmented-button--selected" aria-pressed="true">
    <md-icon>view_list</md-icon> List
  </button>
  <button class="md3-segmented-button" aria-pressed="false">
    <md-icon>grid_view</md-icon> Grid
  </button>
</div>
```

```css
.md3-segmented-buttons {
  display: flex;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: var(--md-sys-shape-corner-full);
  overflow: hidden;
}
.md3-segmented-button {
  flex: 1;
  padding: 10px 16px;
  border: none;
  background: transparent;
  color: var(--md-sys-color-on-surface);
  cursor: pointer;
  font: var(--md-sys-typescale-label-large);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.md3-segmented-button--selected {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}
```

## Communication

### Badge
**Chưa có element @material/web.** Triển khai bằng CSS:

```html
<div class="md3-badge-container">
  <md-icon>notifications</md-icon>
  <span class="md3-badge md3-badge--large">3</span>
</div>
```

```css
.md3-badge-container { position: relative; display: inline-flex; }

.md3-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: var(--md-sys-color-error);
  color: var(--md-sys-color-on-error);
  border-radius: var(--md-sys-shape-corner-full);
  font: var(--md-sys-typescale-label-small);
}
.md3-badge--small { width: 6px; height: 6px; padding: 0; } /* dot only */
.md3-badge--large { min-width: 16px; height: 16px; padding: 0 4px; text-align: center; }
```

### Progress Indicator
**Element**: `md-linear-progress`, `md-circular-progress`
**Import**: `@material/web/progress/linear-progress.js`, `@material/web/progress/circular-progress.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `value` | number | Tiến trình hiện tại (0–1) |
| `max` | number | Giá trị tối đa (mặc định 1) |
| `indeterminate` | boolean | Hiện hoạt ảnh indeterminate |
| `four-color` | boolean | Biến thể indeterminate bốn màu |

```html
<!-- Determinate -->
<md-linear-progress value="0.6"></md-linear-progress>
<md-circular-progress value="0.75"></md-circular-progress>

<!-- Indeterminate -->
<md-linear-progress indeterminate></md-linear-progress>
<md-circular-progress indeterminate></md-circular-progress>
```

**Tùy biến**: `--md-linear-progress-active-indicator-color`, `--md-circular-progress-active-indicator-color`
**A11y**: Có sẵn role `progressbar`. Thêm `aria-label` để cung cấp ngữ cảnh (vd. "Loading messages").

### Snackbar
**Chưa có element @material/web.** Triển khai bằng HTML chuẩn:

```html
<div class="md3-snackbar" role="status" aria-live="polite">
  <span class="md3-snackbar__text">Message sent</span>
  <md-text-button class="md3-snackbar__action">Undo</md-text-button>
  <md-icon-button class="md3-snackbar__close" aria-label="Dismiss">
    <md-icon>close</md-icon>
  </md-icon-button>
</div>
```

```css
.md3-snackbar {
  background: var(--md-sys-color-inverse-surface);
  color: var(--md-sys-color-inverse-on-surface);
  border-radius: var(--md-sys-shape-corner-extra-small);
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  max-width: 560px;
}
```

### Tooltip
**Chưa có element @material/web.**

Hai loại:
- **Plain**: Nhãn văn bản ngắn, xuất hiện khi hover/focus. Dùng cho icon button và văn bản bị cắt bớt.
- **Rich**: Nhiều dòng với hành động tùy chọn. Dùng cho giải thích phức tạp.

## Containment

### Card
**Chưa có element @material/web.** Ba biến thể:

| Biến thể | Ngoại quan | Elevation |
|---------|-----------|-----------|
| Filled | Tô surface-container-highest, không viền | Cấp 0 |
| Outlined | Tô surface, viền outline-variant | Cấp 0 |
| Elevated | Tô surface-container-low, có bóng | Cấp 1 |

```html
<!-- Outlined card -->
<div class="md3-card md3-card--outlined">
  <div class="md3-card__content">
    <h3 style="font: var(--md-sys-typescale-title-medium)">Title</h3>
    <p style="font: var(--md-sys-typescale-body-medium); color: var(--md-sys-color-on-surface-variant)">
      Supporting text
    </p>
  </div>
  <div class="md3-card__actions">
    <md-filled-tonal-button>Action</md-filled-tonal-button>
  </div>
</div>

<!-- Filled card -->
<div class="md3-card md3-card--filled">...</div>

<!-- Elevated card -->
<div class="md3-card md3-card--elevated">...</div>
```

```css
.md3-card {
  border-radius: var(--md-sys-shape-corner-medium, 12px);
  overflow: hidden;
}
.md3-card--outlined {
  background: var(--md-sys-color-surface);
  border: 1px solid var(--md-sys-color-outline-variant);
}
.md3-card--filled {
  background: var(--md-sys-color-surface-container-highest);
}
.md3-card--elevated {
  background: var(--md-sys-color-surface-container-low);
  box-shadow: 0 1px 2px rgba(0,0,0,0.3), 0 1px 3px 1px rgba(0,0,0,0.15);
}
.md3-card__content { padding: 16px; }
.md3-card__actions { padding: 16px; display: flex; gap: 8px; justify-content: flex-end; }
```

### Dialog
**Element**: `md-dialog` | **Import**: `@material/web/dialog/dialog.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `open` | boolean | Hiện dialog |
| `type` | string | `alert` (mặc định) |

```html
<md-dialog id="confirm-dialog">
  <div slot="headline">Confirm action</div>
  <form slot="content" method="dialog">
    Are you sure you want to proceed?
  </form>
  <div slot="actions">
    <md-text-button form="confirm-dialog" value="cancel">Cancel</md-text-button>
    <md-filled-tonal-button form="confirm-dialog" value="confirm">Confirm</md-filled-tonal-button>
  </div>
</md-dialog>
```

**A11y**: Dialog tự động giữ focus bên trong. Dùng `slot="headline"` cho tiêu đề truy cập được.

### Bottom Sheet
**Chưa có element @material/web.** Hai biến thể: Standard (liên tục, cùng tồn tại với nội dung) và Modal (chặn tương tác, có scrim).

### Side Sheet
**Chưa có element @material/web.** Hai biến thể: Standard (đậu cạnh nội dung) và Modal (phủ lên nội dung kèm scrim).

### Divider
**Element**: `md-divider` | **Import**: `@material/web/divider/divider.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `inset` | boolean | Thêm inset ở cả hai bên |
| `inset-start` | boolean | Thêm inset ở phía đầu |
| `inset-end` | boolean | Thêm inset ở phía cuối |

```html
<md-divider></md-divider>
<md-divider inset></md-divider>
```

### Carousel
**Chưa có element @material/web.** Ba cấu hình:
- **Multi-browse**: Nhiều mục hiển thị, có thể cuộn
- **Uncontained**: Mục vượt ra ngoài cạnh viewport
- **Hero**: Một mục nổi bật lớn kèm các bản xem trước nhỏ hơn

## Input

### Checkbox
**Element**: `md-checkbox` | **Import**: `@material/web/checkbox/checkbox.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `checked` | boolean | Trạng thái checked |
| `indeterminate` | boolean | Trạng thái indeterminate |
| `disabled` | boolean | Trạng thái disabled |
| `required` | boolean | Bắt buộc để xác thực form |

```html
<label>
  <md-checkbox></md-checkbox>
  Accept terms
</label>

<label>
  <md-checkbox checked></md-checkbox>
  Remember me
</label>
```

**A11y**: Bọc trong `<label>` hoặc dùng `aria-label`. Checkbox có sẵn role checkbox.

### Chips
**Element**: `md-chip-set`, `md-assist-chip`, `md-filter-chip`, `md-input-chip`, `md-suggestion-chip`
**Import**: `@material/web/chips/*.js`

| Biến thể | Element | Cách dùng |
|---------|---------|-----|
| Assist | `md-assist-chip` | Gợi ý thông minh, lối tắt |
| Filter | `md-filter-chip` | Lọc nội dung, multi-select |
| Input | `md-input-chip` | Token đầu vào của người dùng (người nhận email) |
| Suggestion | `md-suggestion-chip` | Phản hồi/truy vấn gợi ý |

```html
<md-chip-set>
  <md-filter-chip label="Vegetarian" selected></md-filter-chip>
  <md-filter-chip label="Vegan"></md-filter-chip>
  <md-filter-chip label="Gluten-free"></md-filter-chip>
</md-chip-set>

<md-chip-set>
  <md-input-chip label="user@example.com" removable></md-input-chip>
</md-chip-set>
```

### Menu
**Element**: `md-menu`, `md-menu-item`, `md-sub-menu`
**Import**: `@material/web/menu/menu.js`, `@material/web/menu/menu-item.js`

**Ghi chú I/O 2026:** Menu expressive có hướng dẫn Material cập nhật cho cấu hình linh hoạt và rực rỡ hơn. Trong Jetpack Compose, ưu tiên API menu Material3 hiện tại và biến thể expressive khi có trong BOM. Trên web, dùng `md-menu` / `md-menu-item` cho menu dựa trên token, hoặc xây biến thể tùy chỉnh theo đặc tả khi cần hành vi expressive.

| Thuộc tính (menu) | Kiểu | Mô tả |
|-----------------|------|-------------|
| `anchor` | string | ID của phần tử neo |
| `open` | boolean | Hiện menu |
| `positioning` | string | `absolute`, `fixed`, `popover` |

```html
<span style="position: relative;">
  <md-filled-button id="menu-trigger">Options</md-filled-button>
  <md-menu id="options-menu" anchor="menu-trigger">
    <md-menu-item>
      <div slot="headline">Edit</div>
      <md-icon slot="start">edit</md-icon>
    </md-menu-item>
    <md-menu-item>
      <div slot="headline">Delete</div>
      <md-icon slot="start">delete</md-icon>
    </md-menu-item>
  </md-menu>
</span>

<script>
  document.getElementById('menu-trigger').addEventListener('click', () => {
    document.getElementById('options-menu').open = !document.getElementById('options-menu').open;
  });
</script>
```

### Radio Button
**Element**: `md-radio` | **Import**: `@material/web/radio/radio.js`

```html
<div role="radiogroup" aria-label="Size">
  <label><md-radio name="size" value="s"></md-radio> Small</label>
  <label><md-radio name="size" value="m" checked></md-radio> Medium</label>
  <label><md-radio name="size" value="l"></md-radio> Large</label>
</div>
```

**A11y**: Nhóm radio trong một container có `role="radiogroup"` và `aria-label`.

### Slider
**Element**: `md-slider` | **Import**: `@material/web/slider/slider.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `value` | number | Giá trị hiện tại |
| `min` | number | Giá trị tối thiểu |
| `max` | number | Giá trị tối đa |
| `step` | number | Bước nhảy (tạo dạng discrete) |
| `labeled` | boolean | Hiện nhãn giá trị |
| `range` | boolean | Bật chọn khoảng |
| `value-start` | number | Giá trị đầu (chế độ range) |
| `value-end` | number | Giá trị cuối (chế độ range) |

```html
<!-- Continuous -->
<md-slider value="50" min="0" max="100" aria-label="Volume"></md-slider>

<!-- Discrete with label -->
<md-slider value="3" min="1" max="10" step="1" labeled aria-label="Rating"></md-slider>

<!-- Range -->
<md-slider range value-start="20" value-end="80" min="0" max="100" aria-label="Price range"></md-slider>
```

### Switch
**Element**: `md-switch` | **Import**: `@material/web/switch/switch.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `selected` | boolean | Trạng thái bật |
| `icons` | boolean | Hiện icon bật/tắt |
| `disabled` | boolean | Trạng thái disabled |

```html
<label>
  <md-switch></md-switch>
  Dark mode
</label>

<label>
  <md-switch selected icons></md-switch>
  Notifications
</label>
```

**Tùy biến**: `--md-switch-selected-handle-color`, `--md-switch-selected-track-color`

### Text Field
**Element**: `md-filled-text-field`, `md-outlined-text-field`
**Import**: `@material/web/textfield/filled-text-field.js`, `@material/web/textfield/outlined-text-field.js`

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `label` | string | Văn bản nhãn |
| `value` | string | Giá trị hiện tại |
| `type` | string | Kiểu input (text, email, password, number, textarea, v.v.) |
| `placeholder` | string | Văn bản placeholder |
| `required` | boolean | Xác thực bắt buộc |
| `disabled` | boolean | Trạng thái disabled |
| `error` | boolean | Trạng thái lỗi |
| `error-text` | string | Thông báo lỗi |
| `supporting-text` | string | Văn bản hỗ trợ |
| `prefix-text` | string | Văn bản tiền tố |
| `suffix-text` | string | Văn bản hậu tố |
| `max-length` | number | Giới hạn ký tự (hiện bộ đếm) |
| `rows` | number | Số dòng (cho textarea) |

```html
<!-- Outlined (recommended for most uses) -->
<md-outlined-text-field
  label="Email"
  type="email"
  required
  supporting-text="We'll never share your email">
</md-outlined-text-field>

<!-- Filled -->
<md-filled-text-field
  label="Search"
  type="text"
  placeholder="Type to search...">
  <md-icon slot="leading-icon">search</md-icon>
</md-filled-text-field>

<!-- With error -->
<md-outlined-text-field
  label="Password"
  type="password"
  error
  error-text="Password must be at least 8 characters"
  min-length="8">
</md-outlined-text-field>

<!-- Textarea -->
<md-outlined-text-field
  label="Message"
  type="textarea"
  rows="4"
  max-length="500">
</md-outlined-text-field>
```

**Tùy biến**: `--md-outlined-text-field-container-shape`, `--md-outlined-text-field-focus-outline-color`, `--md-filled-text-field-container-color`

#### Jetpack Compose

Dùng **`OutlinedTextField`** / **`TextField`** từ **`androidx.compose.material3`**. Ưu tiên API **dựa trên state** (`TextFieldState`, `rememberTextFieldState()`) khi nhắm tới bản Material3 hiện tại — xem [tổng quan gói](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary). Ánh xạ label, supporting text và trạng thái lỗi tới vai trò MD3 (`MaterialTheme.colorScheme`, `TextFieldDefaults`).

```kotlin
// Illustrative — API names vary slightly by Material3 version
val state = rememberTextFieldState("")

OutlinedTextField(
    state = state,
    label = { Text("Email") },
    supportingText = { if (isError) Text("Invalid email") },
    isError = isError,
    modifier = Modifier.fillMaxWidth()
)
```

### Date Picker
**Chưa có element @material/web.** Ba cấu hình:
- **Docked**: Lịch inline gắn với input
- **Modal**: Dialog toàn diện để chọn ngày
- **Range**: Chọn một khoảng ngày

### Time Picker
**Chưa có element @material/web.** Hai cấu hình:
- **Docked**: Input giờ inline
- **Modal**: Mặt đồng hồ trong dialog

## Navigation

### App Bar (Top)
**Chưa có element @material/web.** Bốn biến thể:

| Biến thể | Chiều cao | Vị trí tiêu đề | Hành vi cuộn |
|---------|--------|---------------|----------------|
| Center-aligned | 64dp | Giữa | Nâng khi cuộn |
| Small | 64dp | Trái | Nâng khi cuộn |
| Medium | 112dp | Trái, dưới | Thu về small khi cuộn |
| Large | 152dp | Trái, dưới | Thu về small khi cuộn |

**Jetpack Compose:** `TopAppBar`, `CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`, và biến thể expressive (vd. large flexible) có thể cần **`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`** tùy BOM — kiểm tra phiên bản `material3` của bạn.

```html
<header class="md3-top-app-bar md3-top-app-bar--small">
  <md-icon-button aria-label="Menu"><md-icon>menu</md-icon></md-icon-button>
  <span class="md3-top-app-bar__title" style="font: var(--md-sys-typescale-title-large)">
    Page Title
  </span>
  <md-icon-button aria-label="Search"><md-icon>search</md-icon></md-icon-button>
  <md-icon-button aria-label="More"><md-icon>more_vert</md-icon></md-icon-button>
</header>
```

```css
.md3-top-app-bar {
  height: 64px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}
.md3-top-app-bar__title { flex: 1; padding: 0 12px; }
/* Scrolled state */
.md3-top-app-bar--scrolled { background: var(--md-sys-color-surface-container); }
```

### Navigation Bar
**Element**: `md-navigation-bar` | **Import**: `@material/web/navigation/navigation-bar.js`
**Dùng khi**: 3–5 điểm đến chính, màn hình mobile/compact, liên tục.

```html
<md-navigation-bar>
  <md-navigation-tab label="Home" active>
    <md-icon slot="active-icon">home</md-icon>
    <md-icon slot="inactive-icon">home</md-icon>
  </md-navigation-tab>
  <md-navigation-tab label="Explore">
    <md-icon slot="active-icon">explore</md-icon>
    <md-icon slot="inactive-icon">explore</md-icon>
  </md-navigation-tab>
  <md-navigation-tab label="Profile">
    <md-icon slot="active-icon">person</md-icon>
    <md-icon slot="inactive-icon">person</md-icon>
  </md-navigation-tab>
</md-navigation-bar>
```

### Navigation Drawer
**Element**: `md-navigation-drawer` | **Import**: `@material/web/navigation/navigation-drawer.js`
**Dùng khi**: Nhiều điểm đến, màn hình lớn hơn, có thể dạng modal hoặc liên tục.

| Thuộc tính | Kiểu | Mô tả |
|-----------|------|-------------|
| `opened` | boolean | Trạng thái mở |
| `type` | string | `standard` hoặc `modal` |

```html
<md-navigation-drawer opened>
  <div slot="headline">Mail</div>
  <md-list>
    <md-list-item type="button" active>
      <md-icon slot="start">inbox</md-icon>
      Inbox
    </md-list-item>
    <md-list-item type="button">
      <md-icon slot="start">send</md-icon>
      Sent
    </md-list-item>
  </md-list>
</md-navigation-drawer>
```

### Navigation Rail
**Chưa có element @material/web.** Dùng khi: 3–7 điểm đến, màn hình trung bình (600–839dp), điều hướng bên liên tục.

```html
<nav class="md3-nav-rail" aria-label="Main">
  <md-fab size="small" variant="tertiary" aria-label="Compose">
    <md-icon slot="icon">edit</md-icon>
  </md-fab>
  <div class="md3-nav-rail__items">
    <a href="/" class="md3-nav-rail__item md3-nav-rail__item--active" aria-current="page">
      <md-icon>home</md-icon>
      <span>Home</span>
    </a>
    <a href="/search" class="md3-nav-rail__item">
      <md-icon>search</md-icon>
      <span>Search</span>
    </a>
  </div>
</nav>
```

```css
.md3-nav-rail {
  width: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0;
  gap: 12px;
  background: var(--md-sys-color-surface);
}
.md3-nav-rail__items { display: flex; flex-direction: column; gap: 12px; }
.md3-nav-rail__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  text-decoration: none;
  color: var(--md-sys-color-on-surface-variant);
  font: var(--md-sys-typescale-label-medium);
}
.md3-nav-rail__item--active { color: var(--md-sys-color-on-surface); }
```

### Search
**Chưa có element @material/web.** Hai mẫu:
- **Search bar**: Trường tìm kiếm liên tục trong khu vực top app bar
- **Search view**: Lớp phủ tìm kiếm mở rộng kèm gợi ý

**Ghi chú I/O 2026:** Hướng dẫn search và search app bar expressive thêm phong cách thị giác làm mới, chuyển động và hành vi icon cuối linh hoạt hơn. Trong Jetpack Compose, dùng API search Material3 hiện tại và biến thể app bar expressive khi có. Trên web, triển khai search như component tùy chỉnh dùng token shape, color, spacing và motion của MD3.

### Tabs
**Element**: `md-tabs`, `md-primary-tab`, `md-secondary-tab`
**Import**: `@material/web/tabs/tabs.js`, `@material/web/tabs/primary-tab.js`, `@material/web/tabs/secondary-tab.js`

| Biến thể | Element | Cách dùng |
|---------|---------|-----|
| Primary | `md-primary-tab` | Điều hướng cấp cao nhất trong một trang |
| Secondary | `md-secondary-tab` | Phần phụ trong primary tab |

```html
<md-tabs>
  <md-primary-tab active>
    <md-icon slot="icon">flight</md-icon>
    Flights
  </md-primary-tab>
  <md-primary-tab>
    <md-icon slot="icon">hotel</md-icon>
    Hotels
  </md-primary-tab>
  <md-primary-tab>
    <md-icon slot="icon">explore</md-icon>
    Explore
  </md-primary-tab>
</md-tabs>
```

**A11y**: Bộ tab có sẵn role tablist. Kết nối tab với panel qua `aria-controls`.

### Toolbar
**Chưa có element @material/web.** Hiện các hành động thường dùng liên quan đến ngữ cảnh trang hiện tại. Thường đặt bên dưới top app bar hoặc ở vị trí ngữ cảnh.

## Data Display

### List
**Element**: `md-list`, `md-list-item`
**Import**: `@material/web/list/list.js`, `@material/web/list/list-item.js`

**Ghi chú I/O 2026:** List expressive thêm phong cách rực rỡ hơn và cấu hình mục linh hoạt. Trong Compose, ưu tiên mẫu list Material3 và giữ spacing, nội dung đầu/cuối, supporting text và divider đều dựa trên token. Trên web, `md-list` hữu ích cho list chuẩn; có thể cần CSS tùy chỉnh cho cách trình bày list expressive.

| Thuộc tính (list-item) | Kiểu | Mô tả |
|----------------------|------|-------------|
| `type` | string | `text` (mặc định), `button`, `link` |
| `href` | string | URL (khi type="link") |
| `disabled` | boolean | Trạng thái disabled |

```html
<md-list>
  <!-- One-line -->
  <md-list-item>Single line item</md-list-item>

  <!-- Two-line with icon -->
  <md-list-item>
    <md-icon slot="start">person</md-icon>
    <div slot="headline">Jane Smith</div>
    <div slot="supporting-text">Senior Developer</div>
  </md-list-item>

  <!-- Three-line -->
  <md-list-item>
    <md-icon slot="start">mail</md-icon>
    <div slot="headline">Meeting notes</div>
    <div slot="supporting-text">Please review the attached notes from today's standup meeting and provide feedback.</div>
    <div slot="trailing-supporting-text">3 min ago</div>
  </md-list-item>

  <md-divider></md-divider>

  <!-- Clickable item -->
  <md-list-item type="button" onclick="handleClick()">
    <md-icon slot="start">settings</md-icon>
    <div slot="headline">Settings</div>
    <md-icon slot="end">chevron_right</md-icon>
  </md-list-item>
</md-list>
```

**Slot**: `start` (phần tử đầu), `end` (phần tử cuối), `headline` (văn bản chính), `supporting-text` (văn bản phụ), `trailing-supporting-text` (metadata cuối), `overline` (phía trên headline)



