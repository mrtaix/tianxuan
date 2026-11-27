---
name: material-3
description: >
  Triển khai hệ thống UI Material Design 3 (Material You) của Google. Chính: Jetpack Compose
  Material3 (MaterialTheme, components, adaptive layout). Có cả Flutter và web hạn chế
  (@material/web, chế độ maintenance). Bao gồm tokens, 30+ components, layout, theming,
  M3 Expressive (platform matrix), và accessibility. Dùng khi: "material design", "MD3",
  "material you", "Jetpack Compose", "MaterialTheme", "material component", "md3 button".
user-invokable: true
argument-hint: "[component|theme|layout|scaffold|audit] [mô tả hoặc URL]"
---

# Material Design 3

Skill này hướng dẫn triển khai Material Design 3 (MD3) của Google — một hệ thống thiết kế cá nhân hóa, thích ứng và biểu cảm. MD3 dùng dynamic color, bề mặt tonal, hình bo góc và chuyển động dựa trên lò xo (spring) để tạo ra giao diện cảm giác sống động và mang tính cá nhân.

## Triết lý

MD3 được xây dựng trên ba nguyên tắc:
- **Cá nhân (Personal)**: Dynamic color điều chỉnh UI theo hình nền hoặc nội dung của người dùng. Việc tạo theme mang tính cá thể, không phải giải pháp chung cho tất cả.
- **Thích ứng (Adaptive)**: Layout chuyển đổi qua 5 lớp kích thước cửa sổ (window size classes). Các component thay đổi kích thước, vị trí và hình dạng một cách đáp ứng.
- **Biểu cảm (Expressive)**: Biến đổi hình dạng (shape morphing), vật lý lò xo và typography nhấn mạnh tạo ra những khoảnh khắc thú vị mà không làm giảm khả năng sử dụng.

## Cập nhật hiện tại: Google I/O 2026

[Bản cập nhật Google I/O 2026](https://m3.material.io/blog/whats-new-at-io26) của Material củng cố hướng đi **Compose-first** trên Android và mở rộng hướng dẫn expressive/adaptive:

- **Material Android là Compose-first**: Với các công việc Android mới, hãy ưu tiên Jetpack Compose Material3 để có các component mới nhất, API expressive, adaptive scaffold và tích hợp Styles API. Android Views có thể vẫn cần thiết trong các ứng dụng hiện có, nhưng không nên xem chúng là đường mặc định cho các triển khai Material 3 mới.
- **Hệ thống layout expressive**: Dùng expressive layout scaffold để thích ứng màn hình trên mobile, desktop, thiết bị gập, đồng hồ, XR và các dạng hình không gian khác. Bắt đầu từ adaptive scaffold/window size classes thay vì layout cố định theo kiểu phone-first.
- **Hệ thống khoảng cách 8dp**: Áp dụng spacing token cho margin, padding và gap để layout và component có thể thích ứng theo lập trình với loại thiết bị và mật độ màn hình.
- **Component expressive mới/cập nhật**: Lists, menus, search và search app bar có hướng dẫn expressive được làm mới, với Jetpack Compose là mục tiêu triển khai chính.
- **Đồng hồ và XR**: Đồng hồ nhấn mạnh chuyển động dựa trên vật lý, văn bản cong (arc text) và container sát cạnh. XR nhấn mạnh panel không gian và độ cao (elevation) theo chiều sâu.

**Khác biệt chính so với MD2:**
- Bề mặt tonal thay thế bóng đổ elevation làm tín hiệu độ sâu chính
- Dynamic color tạo ra toàn bộ bảng màu từ một màu gốc duy nhất
- Bo góc hoàn toàn theo mặc định (không chỉ bo nhẹ)
- Vật lý chuyển động lò xo thay thế các đường cong easing cố định cho component
- 3 mức tương phản do người dùng kiểm soát (standard/medium/high)

**Quan hệ với skill frontend-design:**
Khi cả hai skill cùng hoạt động, MD3 cung cấp hệ thống thiết kế (tokens, components, quy tắc layout) còn frontend-design cung cấp định hướng sáng tạo trong những ràng buộc đó. Quy tắc MD3 được ưu tiên cho cấu trúc component và cách dùng token. Lưu ý: Roboto/Roboto Flex CHÍNH LÀ typeface mặc định đúng trong MD3 — hướng dẫn của frontend-design là tránh Roboto không áp dụng khi triển khai MD3.

## Cây quyết định

**Bạn đang xây dựng cái gì?**
```
Full app scaffold        → See "Common Patterns: App Shell" + references/layout-and-responsive.md
Single component         → See "Component Quick Reference" table → references/component-catalog.md
Custom theme             → See references/theming-and-dynamic-color.md
Form / input layout      → See references/component-catalog.md § Input Components
Navigation structure     → See references/navigation-patterns.md
Data display             → See references/component-catalog.md § Data Display
```

**Nền tảng nào?**
```
Jetpack Compose          → Primary: androidx.compose.material3, MaterialTheme, references/*
Flutter                  → useMaterial3: true in ThemeData, ColorScheme.fromSeed()
Web (vanilla JS)         → @material/web (limited; maintenance mode) + CSS custom properties
Web (React/Vue/Svelte)   → CSS custom properties + wrapper components (no official React lib)
Web (CSS-only)           → MD3 token values as CSS custom properties (no <md-*> elements)
```

## Hệ thống Design Token

Tất cả token MD3 dùng namespace `md.sys`. **Jetpack Compose** ánh xạ các vai trò tới `MaterialTheme.colorScheme`, `MaterialTheme.typography` và `MaterialTheme.shapes` (cùng vai trò ngữ nghĩa với đặc tả). **Trên web**, chúng ánh xạ tới CSS custom properties (`--md-sys-*`):

### Color Tokens (`--md-sys-color-*`)
| Token | Mục đích |
|-------|---------|
| `primary` | Vùng tô nhấn mạnh cao, văn bản, icon trên surface |
| `on-primary` | Văn bản/icon trên primary |
| `primary-container` | Vùng tô nổi bật cho component quan trọng (FAB, v.v.) |
| `on-primary-container` | Văn bản/icon trên primary-container |
| `secondary` / `on-secondary` | Điểm nhấn ít nổi bật hơn |
| `secondary-container` / `on-secondary-container` | Component lùi lại (tonal button) |
| `tertiary` / `on-tertiary` | Điểm nhấn tương phản |
| `tertiary-container` / `on-tertiary-container` | Container bổ trợ |
| `error` / `on-error` | Trạng thái lỗi (tĩnh — không đổi theo dynamic color) |
| `error-container` / `on-error-container` | Vùng tô container lỗi |
| `surface` | Nền mặc định |
| `on-surface` | Văn bản/icon trên mọi surface |
| `on-surface-variant` | Văn bản/icon nhấn mạnh thấp hơn trên surface |
| `surface-container-lowest` | Container nhấn mạnh thấp nhất |
| `surface-container-low` | Container nhấn mạnh thấp |
| `surface-container` | Container mặc định (khu vực điều hướng) |
| `surface-container-high` | Container nhấn mạnh cao |
| `surface-container-highest` | Container nhấn mạnh cao nhất |
| `surface-dim` / `surface-bright` | Duy trì độ sáng tương đối giữa sáng/tối |
| `inverse-surface` / `inverse-on-surface` / `inverse-primary` | Phần tử tương phản (snackbar) |
| `outline` | Đường viền quan trọng (viền text field) |
| `outline-variant` | Phần tử trang trí (divider) |

Chi tiết đầy đủ: `references/color-system.md`

### Typography Tokens (`--md-sys-typescale-*`)
| Thang | Kích thước | Cách dùng |
|-------|-------|-----|
| Display | L / M / S | Văn bản tiêu điểm, số lớn |
| Headline | L / M / S | Tiêu đề phần |
| Title | L / M / S | Tiêu đề nhỏ hơn, tiêu đề card |
| Body | L / M / S | Văn bản đoạn, mô tả |
| Label | L / M / S | Button, chip, chú thích |

Mỗi kiểu có token cho: `-font`, `-weight`, `-size`, `-line-height`, `-tracking`
Cộng thêm 15 biến thể **emphasized** (weight cao hơn) qua `--md-sys-typescale-emphasized-*`

Chi tiết đầy đủ: `references/typography-and-shape.md`

### Shape Tokens (`--md-sys-shape-corner-*`)
| Token | Giá trị | Component ví dụ |
|-------|-------|-------------------|
| `none` | 0dp | — |
| `extra-small` | 4dp | Chip, snackbar |
| `small` | 8dp | Text field, menu |
| `medium` | 12dp | Card |
| `large` | 16dp | FAB, navigation drawer |
| `large-increased` | 20dp | (Expressive) |
| `extra-large` | 28dp | Dialog, bottom sheet |
| `extra-large-increased` | 32dp | (Expressive) |
| `extra-extra-large` | 48dp | (Expressive) |
| `full` | 9999px | Button, chip, badge |

### Cấp độ Elevation
| Cấp | DP | Tonal offset | Cách dùng |
|-------|-----|-------------|-----|
| 0 | 0dp | None | Bề mặt phẳng, hầu hết component ở trạng thái nghỉ |
| 1 | 1dp | +5% primary | Card nâng, sheet dạng modal |
| 2 | 3dp | +8% primary | Menu, nav bar, app bar khi cuộn |
| 3 | 6dp | +11% primary | FAB, dialog, search, date/time picker |
| 4 | 8dp | +12% primary | (chỉ tăng khi hover/focus) |
| 5 | 12dp | +14% primary | (chỉ tăng khi hover/focus) |

Elevation trong MD3 được truyền đạt qua **màu bề mặt tonal**, không phải bóng đổ. Bóng chỉ dùng khi cần bảo vệ thêm khỏi nền quá nhiều chi tiết.

### Chuyển động (Motion)
MD3 Expressive (tháng 5/2025) giới thiệu **vật lý chuyển động dựa trên lò xo** cho component. Hệ thống easing/duration cũ vẫn được dùng cho **transition** (vào/ra/shared-axis):

| Easing | Thời lượng | Loại transition |
|--------|----------|-----------------|
| Emphasized | 500ms | Bắt đầu và kết thúc trên màn hình |
| Emphasized decelerate | 400ms | Vào màn hình |
| Emphasized accelerate | 200ms | Rời màn hình |
| Standard | 300ms | Bắt đầu và kết thúc trên màn hình (utility) |
| Standard decelerate | 250ms | Vào màn hình (utility) |
| Standard accelerate | 200ms | Rời màn hình (utility) |

Giá trị easing CSS:
- Emphasized: `cubic-bezier(0.2, 0, 0, 1)`
- Emphasized decelerate: `cubic-bezier(0.05, 0.7, 0.1, 1)`
- Emphasized accelerate: `cubic-bezier(0.3, 0, 0.8, 0.15)`
- Standard: `cubic-bezier(0.2, 0, 0, 1)`
- Standard decelerate: `cubic-bezier(0, 0, 0, 1)`
- Standard accelerate: `cubic-bezier(0.3, 0, 1, 1)`

## Tham chiếu nhanh Component

| Component | Web Element | Biến thể chính | Danh mục |
|-----------|------------|--------------|----------|
| Button | `md-filled-button`, `md-outlined-button`, `md-text-button`, `md-elevated-button`, `md-filled-tonal-button` | Filled, Outlined, Text, Elevated, Tonal; 5 kích thước (XS–XL); toggle | Actions |
| Button group | `md-button-group` | Standard, connected | Actions |
| Extended FAB | `md-extended-fab` | Surface, Primary, Secondary, Tertiary | Actions |
| FAB | `md-fab` | Small, Medium, Large | Actions |
| FAB menu | — | — | Actions |
| Icon button | `md-icon-button`, `md-filled-icon-button`, `md-filled-tonal-icon-button`, `md-outlined-icon-button` | Standard, Filled, Filled Tonal, Outlined | Actions |
| Segmented button | — | Single-select, Multi-select | Actions |
| Split button | — | — | Actions |
| Badge | — | Small (dot), Large (count) | Communication |
| Loading indicator | — | Linear, Circular | Communication |
| Progress indicator | `md-linear-progress`, `md-circular-progress` | Linear, Circular; determinate/indeterminate | Communication |
| Snackbar | — | Single-line, Two-line, Action | Communication |
| Tooltip | — | Plain, Rich | Communication |
| Card | — | Filled, Outlined, Elevated | Containment |
| Carousel | — | Multi-browse, Uncontained, Hero | Containment |
| Dialog | `md-dialog` | Basic, Full-screen | Containment |
| Bottom sheet | — | Standard, Modal | Sheets |
| Side sheet | — | Standard, Modal | Sheets |
| Divider | `md-divider` | Full-width, Inset | Containment |
| Checkbox | `md-checkbox` | — | Input |
| Chips | `md-chip-set`, `md-assist-chip`, `md-filter-chip`, `md-input-chip`, `md-suggestion-chip` | Assist, Filter, Input, Suggestion | Input |
| Date picker | — | Docked, Modal, Range | Input |
| Menu | `md-menu`, `md-menu-item` | — | Input |
| Radio button | `md-radio` | — | Input |
| Slider | `md-slider` | Continuous, Discrete, Range | Input |
| Switch | `md-switch` | Có/không icon | Input |
| Text field | `md-filled-text-field`, `md-outlined-text-field` | Filled, Outlined | Input |
| Time picker | — | Docked, Modal | Input |
| App bar (top) | — | Center-aligned, Small, Medium, Large | Navigation |
| Navigation bar | `md-navigation-bar` | — | Navigation |
| Navigation drawer | `md-navigation-drawer` | Standard, Modal | Navigation |
| Navigation rail | — | — | Navigation |
| Search | — | Search bar, Search view | Navigation |
| Tabs | `md-tabs`, `md-primary-tab`, `md-secondary-tab` | Primary, Secondary | Navigation |
| Toolbar | — | — | Navigation |
| List | `md-list`, `md-list-item` | One-line, Two-line, Three-line | Data Display |

**Lưu ý:** Component được đánh dấu `—` ở cột web element chưa có implementation `@material/web`. Hãy dùng CSS custom properties với HTML chuẩn cho những component này. Ánh xạ và ví dụ **Compose** nằm trong `references/component-catalog.md`.

Chi tiết đầy đủ component kèm ví dụ code: `references/component-catalog.md`

## Jetpack Compose (chính)

Dùng **`androidx.compose.material3`** với `MaterialTheme` và các composable Material 3 (`Scaffold`, `Button`, `NavigationBar`, top app bar, v.v.).

- **Theming**: `MaterialTheme(colorScheme = …, typography = …, shapes = …)`. Ưu tiên `dynamicLightColorScheme` / `dynamicDarkColorScheme` trên **Android 12+ (API 31+)** khi muốn dynamic color; nếu không thì dùng `lightColorScheme` / `darkColorScheme` hoặc mã theme được tạo bởi Material Theme Builder.
- **UI thích ứng (Adaptive)**: Window size class, layout list-detail và supporting-pane, thiết bị gập — xem `references/layout-and-responsive.md` và `references/navigation-patterns.md`.
- **Edge-to-edge & insets**: Bố trí nội dung với `WindowInsets` / padding của scaffold để thanh và IME hoạt động đúng — xem `references/layout-and-responsive.md`.
- **API thử nghiệm**: Một số API Material 3 cần `@OptIn(ExperimentalMaterial3Api::class)` hoặc opt-in expressive; hãy khớp với BOM và compiler của bạn.

```kotlin
MaterialTheme(
    colorScheme = colorScheme, // from dynamicLightColorScheme / lightColorScheme / etc.
    typography = Typography(),
    shapes = Shapes(),
) {
    // M3 content — prefer references for Scaffold, navigation, text fields
}
```

## Web (hạn chế): @material/web

**Quan trọng:** Theo [Material Design 3 for Web](https://m3.material.io/develop/web), **Material Web Components đang ở chế độ maintenance** và **M3 Expressive chưa được triển khai trên Web**. Dùng `@material/web` cho UI web dựa trên token khi phù hợp, nhưng đừng coi nó tương đương Compose cho các tính năng Expressive hiện tại.

### Thiết lập

```bash
npm install @material/web
```

### Import từng Component

Luôn chỉ import những component bạn dùng — import toàn bộ gói sẽ làm phình bundle:

```javascript
// Good — individual imports
import '@material/web/button/filled-button.js';
import '@material/web/button/outlined-button.js';
import '@material/web/textfield/outlined-text-field.js';
import '@material/web/icon/icon.js';

// Bad — never do this
import '@material/web'; // imports everything
```

### Cách dùng cơ bản

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link href="https://fonts.googleapis.com/css2?family=Roboto+Flex:wght@400;500;700&display=swap" rel="stylesheet">
  <link href="https://fonts.googleapis.com/icon?family=Material+Symbols+Outlined" rel="stylesheet">
</head>
<body>
  <md-filled-button>Get started</md-filled-button>
  <md-outlined-text-field label="Email" type="email"></md-outlined-text-field>

  <script type="module">
    import '@material/web/button/filled-button.js';
    import '@material/web/textfield/outlined-text-field.js';
  </script>
</body>
</html>
```

### Tạo theme với CSS Custom Properties

Áp dụng theme tùy chỉnh bằng cách đặt CSS custom properties trên `:root` hoặc bất kỳ phần tử tổ tiên nào:

```css
:root {
  /* Color scheme (generate with @material/material-color-utilities) */
  --md-sys-color-primary: #6750A4;
  --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #EADDFF;
  --md-sys-color-on-primary-container: #21005D;
  --md-sys-color-secondary: #625B71;
  --md-sys-color-on-secondary: #FFFFFF;
  --md-sys-color-secondary-container: #E8DEF8;
  --md-sys-color-on-secondary-container: #1D192B;
  --md-sys-color-surface: #FEF7FF;
  --md-sys-color-on-surface: #1D1B20;
  --md-sys-color-surface-container: #F3EDF7;
  --md-sys-color-outline: #79747E;
  --md-sys-color-outline-variant: #CAC4D0;

  /* Typography */
  --md-sys-typescale-body-large-font: 'Roboto Flex', sans-serif;
  --md-sys-typescale-body-large-size: 1rem;
  --md-sys-typescale-body-large-weight: 400;
  --md-sys-typescale-body-large-line-height: 1.5rem;

  /* Shape */
  --md-sys-shape-corner-full: 9999px;
  --md-sys-shape-corner-medium: 12px;
}
```

### Ghi đè ở cấp Component

Ghi đè token của từng component để tùy biến cụ thể:

```css
md-filled-button {
  --md-filled-button-container-color: var(--md-sys-color-primary);
  --md-filled-button-label-text-color: var(--md-sys-color-on-primary);
  --md-filled-button-container-shape: var(--md-sys-shape-corner-full);
  --md-filled-button-container-height: 40px;
}

md-outlined-text-field {
  --md-outlined-text-field-container-shape: var(--md-sys-shape-corner-small);
  --md-outlined-text-field-focus-outline-color: var(--md-sys-color-primary);
}
```

### Theme tối (Dark Theme)

Áp dụng theme tối bằng cách ghi đè color token trên một class hoặc media query:

```css
@media (prefers-color-scheme: dark) {
  :root {
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-on-primary: #381E72;
    --md-sys-color-primary-container: #4F378B;
    --md-sys-color-on-primary-container: #EADDFF;
    --md-sys-color-surface: #141218;
    --md-sys-color-on-surface: #E6E0E9;
    --md-sys-color-surface-container: #211F26;
    --md-sys-color-outline: #938F99;
    --md-sys-color-outline-variant: #49454F;
  }
}
```

Hướng dẫn theming đầy đủ: `references/theming-and-dynamic-color.md`

## Các mẫu thường dùng

### App Shell

App MD3 chuẩn với điều hướng đáp ứng + top app bar + khu vực nội dung:

```html
<div class="md3-app">
  <nav class="md3-nav-rail" aria-label="Main navigation">
    <!-- Navigation rail for medium+ screens -->
    <md-fab size="small" aria-label="Compose">
      <md-icon slot="icon">edit</md-icon>
    </md-fab>
    <md-navigation-bar>
      <md-navigation-tab label="Home">
        <md-icon slot="active-icon">home</md-icon>
        <md-icon slot="inactive-icon">home</md-icon>
      </md-navigation-tab>
      <md-navigation-tab label="Search">
        <md-icon slot="active-icon">search</md-icon>
        <md-icon slot="inactive-icon">search</md-icon>
      </md-navigation-tab>
    </md-navigation-bar>
  </nav>
  <main class="md3-content">
    <header class="md3-top-app-bar">
      <h1 class="md3-top-app-bar__title" style="font: var(--md-sys-typescale-title-large)">
        Page Title
      </h1>
    </header>
    <div class="md3-body">
      <!-- Content here -->
    </div>
  </main>
</div>
```

```css
.md3-app {
  display: flex;
  min-height: 100vh;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}

.md3-nav-rail {
  width: 80px;
  background: var(--md-sys-color-surface);
  border-right: 1px solid var(--md-sys-color-outline-variant);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 12px;
  gap: 12px;
}

.md3-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.md3-top-app-bar {
  height: 64px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  background: var(--md-sys-color-surface);
}

.md3-body {
  padding: 24px;
  flex: 1;
}

/* Responsive: switch to bottom nav on compact */
@media (max-width: 599px) {
  .md3-app { flex-direction: column; }
  .md3-nav-rail {
    order: 1;
    width: 100%;
    flex-direction: row;
    justify-content: center;
    border-right: none;
    border-top: 1px solid var(--md-sys-color-outline-variant);
    padding: 0;
  }
}
```

### Lưới Card

```html
<div class="md3-card-grid">
  <div class="md3-card md3-card--outlined">
    <img src="image.jpg" alt="Description" class="md3-card__media">
    <div class="md3-card__content">
      <h3 style="font: var(--md-sys-typescale-title-medium)">Card Title</h3>
      <p style="font: var(--md-sys-typescale-body-medium); color: var(--md-sys-color-on-surface-variant)">
        Supporting text for this card.
      </p>
    </div>
    <div class="md3-card__actions">
      <md-text-button>Learn more</md-text-button>
      <md-filled-tonal-button>Action</md-filled-tonal-button>
    </div>
  </div>
</div>
```

```css
.md3-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.md3-card--outlined {
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: var(--md-sys-shape-corner-medium, 12px);
  background: var(--md-sys-color-surface);
  overflow: hidden;
}

.md3-card__content { padding: 16px; }
.md3-card__actions { padding: 8px 16px 16px; display: flex; gap: 8px; justify-content: flex-end; }
.md3-card__media { width: 100%; aspect-ratio: 16/9; object-fit: cover; }
```

### Bố trí Form

```html
<form class="md3-form">
  <md-outlined-text-field label="Full name" required></md-outlined-text-field>
  <md-outlined-text-field label="Email" type="email" required></md-outlined-text-field>
  <md-outlined-text-field label="Message" type="textarea" rows="4"></md-outlined-text-field>
  <div class="md3-form__actions">
    <md-text-button type="reset">Cancel</md-text-button>
    <md-filled-button type="submit">Submit</md-filled-button>
  </div>
</form>
```

```css
.md3-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 560px;
}

.md3-form__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}
```

Thêm mẫu: `references/navigation-patterns.md`, `references/layout-and-responsive.md`

## Anti-Pattern (những việc không nên làm)

**Không bao giờ làm những điều này khi triển khai MD3:**

- **Trộn thư viện MD2 và MD3**: Đừng dùng `@material/mdc-*` (MD2) cùng với `@material/web` (MD3). Chúng có API và kiểu dáng không tương thích.
- **Hardcode màu**: Luôn dùng token `var(--md-sys-color-*)`, không bao giờ dùng giá trị hex/rgb thô. Màu hardcode phá vỡ dynamic theming, dark mode và điều chỉnh tương phản.
- **Bỏ qua ghép cặp tonal**: Chỉ kết hợp màu theo cặp đã định (ví dụ `primary` + `on-primary`, `surface-container` + `on-surface`). Ghép tùy tiện phá vỡ tương phản trong dynamic color và chế độ tương phản cao.
- **Dùng `outline` cho divider**: Hãy dùng `outline-variant` cho divider. `outline` dành cho đường viền quan trọng như viền text field.
- **Import toàn bộ @material/web**: Luôn import từng module component. Barrel import bao gồm mọi component và phá hủy kích thước bundle.
- **Dùng `border-radius` trực tiếp**: Hãy dùng shape token (`var(--md-sys-shape-corner-medium)`) để hình dạng luôn nhất quán với theming.
- **Mặc định dùng bóng đổ cho elevation**: MD3 truyền đạt elevation qua màu bề mặt tonal, không phải bóng đổ. Chỉ thêm bóng khi phần tử cần tách biệt thêm khỏi nền nhiều chi tiết.
- **Áp dụng quy tắc "tránh Roboto" của frontend-design**: Trên **Android**, **Roboto** là typeface Material mặc định; **web** thường dùng Roboto hoặc Roboto Flex với token MD3. Chỉ thay thế khi bạn chủ định tùy biến thang typography.
- **Giả định tương thích SSR**: `@material/web` dùng Web Components (custom element) cần JavaScript để render. Chúng sẽ không tạo ra HTML có ý nghĩa trong SSR nếu không có chiến lược hydration bổ sung.
- **Bỏ qua thiết bị gập và màn hình lớn**: MD3 được thiết kế cho mọi kích thước màn hình. Đừng chỉ gửi layout phone-only — hãy dùng canonical layout, multi-pane ở 600dp+ và kiểm tra trên emulator foldable/tablet. Không đặt nội dung tương tác qua đường gập/hinge.
- **Kéo nội dung căng đầy màn hình rộng**: Trên cửa sổ Large (1200dp+) và Extra-large (1600dp+), giới hạn nội dung ở chiều rộng tối đa (840–1040dp). Dòng văn bản dài vô hạn rất khó đọc.

## Ghi chú nền tảng

### Flutter
```dart
MaterialApp(
  theme: ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
  ),
);
```

### Jetpack Compose
Xem phần **[Jetpack Compose (chính)](#jetpack-compose-primary)** ở trên. Dùng `LocalContext.current` với `dynamicLightColorScheme` / `dynamicDarkColorScheme` chỉ khi `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` và dynamic color được bật; nếu không thì cung cấp scheme sáng/tối tĩnh.

### Ánh xạ tên Component
| Khái niệm | Web | Flutter | Compose |
|---------|-----|---------|---------|
| Filled button | `md-filled-button` | `FilledButton` | `Button` |
| Outlined text field | `md-outlined-text-field` | `OutlinedTextField` | `OutlinedTextField` |
| FAB | `md-fab` | `FloatingActionButton` | `FloatingActionButton` |
| Navigation bar | `md-navigation-bar` | `NavigationBar` | `NavigationBar` |
| Switch | `md-switch` | `Switch` | `Switch` |

## M3 Expressive (tháng 5/2025)

Bản cập nhật Expressive thêm sự phong phú thị giác trong khi vẫn giữ khả năng sử dụng. **Tính sẵn có khác nhau theo nền tảng** — đừng giả định một stack triển khai được mọi thứ.

| Khả năng | Jetpack Compose | Flutter | Web (`@material/web`) |
|------------|-----------------|---------|------------------------|
| Expressive layout scaffold / adaptive layout | Compose-first qua API adaptive của Material3 và window size class | Dùng primitive adaptive/layout của Flutter | CSS/container query/layout thủ công; không có parity với Material Web |
| Hệ thống khoảng cách 8dp | Dùng design token / hằng số khoảng cách `Dp`; giữ margin, padding, gap thích ứng | Dùng hằng số spacing của theme | CSS custom property / design token |
| Expressive list, menu, search, search app bar | Mục tiêu chính theo hướng dẫn Material hiện tại; kiểm tra BOM và opt-in | Kiểm tra tài liệu Flutter Material hiện tại | Tự triển khai theo đặc tả; `@material/web` chỉ maintenance |
| Vật lý lò xo / chuyển động | Được hỗ trợ trong Material 3 (xem `MotionScheme`, API expressive theo BOM) | Tùy phiên bản Flutter Material | **Không có** trong Material Web; dùng easing/duration hoặc chuyển động tùy chỉnh |
| Typography nhấn mạnh | Qua theme / type scale | Qua theme | Chỉ token/CSS; không có bộ component Expressive đầy đủ |
| Biến đổi hình dạng (shape morphing) | Compose-first trong đợt triển khai expressive của Google | Kiểm tra tài liệu Flutter hiện tại | **Không có** trong `@material/web` |
| Kích thước button mới (XS–XL), toggle | Theo component Compose Material3 | Theo Flutter MD3 | Chỉ xấp xỉ bằng chiều cao/CSS |
| Token góc bổ sung (vd. large-increased) | `MaterialTheme.shapes` / token | Theme shapes | CSS `--md-sys-shape-*` |
| 3 mức tương phản | Scheme builder / hệ thống | Plugin / thủ công | Tham số tương phản `SchemeContent` trong utility JS |
| Dạng đồng hồ / XR | Dùng hướng dẫn riêng Compose/Wear/XR khi có | Theo từng nền tảng | Tự triển khai UI web/không gian |

**Web:** [Material Web chỉ maintenance; M3 Expressive không có trên Web](https://m3.material.io/develop/web). Dùng token easing/duration CSS làm phương án dự phòng cho chuyển động, không phải parity lò xo.

**Easing/duration cũ** vẫn hợp lệ cho **transition** (vào/ra/shared-axis) khi đặc tả còn tham chiếu đến chúng; xem bảng Motion bên dưới.

## Kiểm tra tuân thủ MD3

Khi được gọi với đối số `audit` (ví dụ `/material-3 audit`), hoặc khi được yêu cầu kiểm tra/đánh giá mức tuân thủ MD3, hãy phân tích ứng dụng hoặc trang mục tiêu và tạo báo cáo tuân thủ.

### Quy trình kiểm tra

1. **Xác định mục tiêu**: Người dùng cung cấp URL (dùng công cụ trình duyệt để kiểm tra), đường dẫn tệp (đọc mã nguồn), hoặc một ứng dụng đang chạy.
2. **Kiểm tra các danh mục sau** và chấm điểm từng cái 0–10:

| Danh mục | Cần kiểm tra |
|----------|--------------|
| **Color token** | **Web:** `--md-sys-color-*` / CSS được tạo. **Compose:** vai trò `MaterialTheme.colorScheme` (không dùng `Color(...)` tùy tiện cho surface khi không có lý do). Ghép cặp tonal đúng (`onX` trên `X`). Theme tối. **Flutter:** vai trò `ColorScheme`. |
| **Typography** | Thang kiểu chữ MD3: **Compose** `MaterialTheme.typography`; **web** token typescale; đúng vai trò (Display, Headline, Title, Body, Label). |
| **Shape** | **Compose** `MaterialTheme.shapes` / `Shape` của component; **web** `var(--md-sys-shape-*)`. Button: full; card: medium; tránh magic number. |
| **Elevation** | Elevation tonal (`Surface` tonal/shadow tùy trường hợp). **Web:** hover/focus khi phù hợp. |
| **Component** | **Compose:** composable Material3 (`Button`, `Scaffold`, v.v.). **Web:** `@material/web` hoặc HTML/CSS theo đặc tả. Đúng biến thể. |
| **Layout** | Canonical layout; **Compose** window size class / API adaptive; chiều rộng tối đa dễ đọc ở màn hình rộng; tránh đường gập thiết bị gập. |
| **Điều hướng** | Bar / rail / drawer / drawers + mẫu `NavHost` của **Compose** theo từng size class; predictive back khi áp dụng. |
| **Motion** | **Compose** `MotionScheme` / API expressive khi dùng; transition vẫn có thể dùng easing/duration. **Web:** token chuyển động CSS dự phòng. |
| **Khả năng truy cập** | Vai trò MD3 giúp ích, nhưng **phải kiểm tra tương phản**: component UI thường cần **3:1** cho văn bản/viền lớn và **4.5:1** cho văn bản thường (WCAG 2.x). TalkBack/semantics (Compose), thứ tự focus, vùng chạm (~48dp). **Web:** ARIA, bàn phím. |
| **Theming** | **Compose:** `MaterialTheme` + sáng/tối/dynamic như thiết kế. **Web:** CSS custom property trên `:root` hoặc cây con. **Flutter:** `ThemeData` + `ColorScheme`. |

3. **Tạo báo cáo**:

```
# MD3 Compliance Audit Report

Target: [URL or file path]
Date: [date]
Overall Score: [X/100]

## Scores by Category
| Category       | Score | Status |
|----------------|-------|--------|
| Color tokens   | X/10  | [pass/warn/fail] |
| Typography     | X/10  | [pass/warn/fail] |
| Shape          | X/10  | [pass/warn/fail] |
| Elevation      | X/10  | [pass/warn/fail] |
| Components     | X/10  | [pass/warn/fail] |
| Layout         | X/10  | [pass/warn/fail] |
| Navigation     | X/10  | [pass/warn/fail] |
| Motion         | X/10  | [pass/warn/fail] |
| Accessibility  | X/10  | [pass/warn/fail] |
| Theming        | X/10  | [pass/warn/fail] |

## Critical Issues
[List items scoring 0-3 with specific file:line references and fixes]

## Warnings
[List items scoring 4-6 with recommendations]

## Passing
[List items scoring 7-10 with notes on what's done well]

## Recommended Fixes (Priority Order)
1. [Most impactful fix first]
2. ...
```

### Phương pháp kiểm tra

**Với URL trực tiếp** (trình duyệt hoặc devtools):
- Kiểm tra style đã tính toán và biến CSS (`--md-sys-*`)
- Thay đổi kích thước viewport hoặc dùng chế độ responsive cho breakpoint
- Chụp ảnh màn hình ở các chiều rộng quan trọng nếu hữu ích

**Với mã nguồn** (được cung cấp đường dẫn tệp):
- **Compose/Kotlin:** tệp `.kt` — `MaterialTheme`, composable, lạm dụng `Color(0x…)`, `Dp` hard-code, thiếu `Modifier.semantics` khi cần
- **Flutter:** `.dart` — `ThemeData`, `ColorScheme`
- **Web:** HTML/JSX/Vue/Svelte; CSS/SCSS cho token
- Kiểm tra import **web** xem dùng `@material/web` hay `@material/mdc-*` (MD2)

**Kiểm tra nhanh** (điều chỉnh đường dẫn theo stack của bạn):
```
# Web: hardcoded colors
grep -rn '#[0-9a-fA-F]\{3,8\}' --include='*.css' --include='*.scss'

# Compose: raw Color(...) audits (sample — tune for your codebase)
grep -rn 'Color(0x' --include='*.kt'

# MD2 on web
grep -rn '@material/mdc-' --include='*.js' --include='*.ts'
```

**Tự động hóa trình duyệt** (nếu môi trường của bạn có công cụ MCP browser): điều hướng, chụp DOM/biến CSS, đổi kích thước cho breakpoint — tùy chọn, không bắt buộc.

### Hướng dẫn chấm điểm

- **9-10**: Tuân thủ MD3 hoàn toàn, dùng đúng token và mẫu
- **7-8**: Phần lớn tuân thủ, lỗi nhỏ (vd. vài giá trị hardcode)
- **4-6**: Tuân thủ một phần, có vài mẫu MD3 nhưng còn khoảng trống đáng kể
- **1-3**: Vi phạm lớn, chủ yếu là mẫu phi MD3 hoặc MD2
- **0**: Không áp dụng hoặc hoàn toàn vắng mặt

Ngưỡng trạng thái: **pass** (7+), **warn** (4-6), **fail** (0-3)

## Tài liệu tham khảo

- `references/color-system.md` — Vai trò màu, bảng tonal, dynamic color, ánh xạ Compose + CSS
- `references/typography-and-shape.md` — Thang kiểu chữ, góc bo, elevation, chuyển động, ghi chú Expressive
- `references/component-catalog.md` — Component: Compose + `@material/web` khi áp dụng
- `references/navigation-patterns.md` — Lựa chọn điều hướng, mẫu adaptive Compose-first
- `references/layout-and-responsive.md` — Breakpoint, canonical layout, inset, thiết bị gập
- `references/theming-and-dynamic-color.md` — Theming: Compose trước, sau đó Flutter và web


