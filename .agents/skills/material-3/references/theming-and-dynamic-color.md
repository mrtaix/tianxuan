# Theming và Dynamic Color trong MD3

Hướng dẫn đầy đủ để tạo, áp dụng và quản lý theme Material Design 3.

## Kiến trúc Theme

Cùng một **vai trò ngữ nghĩa** (primary, onSurface, surface container, v.v.) xuất hiện trên mọi nền tảng:

| Nền tảng | Bề mặt theme |
|----------|----------------|
| **Jetpack Compose** | `MaterialTheme(colorScheme, typography, shapes, …)` |
| **Flutter** | `ThemeData` + `ColorScheme`, `useMaterial3: true` |
| **Web** | CSS custom property `--md-sys-*` trên `:root` hoặc một cây con |

**Web:** [Material Web chỉ maintenance; M3 Expressive chưa được triển khai trên Web](https://m3.material.io/develop/web). Dùng `@material/web` + CSS với hiểu biết rằng stack này có hạn chế.

---

## Theming trong Jetpack Compose

Dùng `androidx.compose.material3.MaterialTheme`. Ưu tiên **dynamic color** trên **Android 12+ (API 31+)** khi được bật; nếu không thì dùng `lightColorScheme` / `darkColorScheme` hoặc Kotlin được tạo bởi [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/).

```kotlin
import androidx.compose.material3.*
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            // … add remaining roles or use generated Theme.kt
        )
        else -> lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            // … add remaining roles or use generated Theme.kt
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
```

---

## Theming trong Flutter

```dart
import 'package:flutter/material.dart';

// Basic MD3 theme from seed
MaterialApp(
  theme: ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: Colors.deepPurple,
      brightness: Brightness.light,
    ),
  ),
  darkTheme: ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: Colors.deepPurple,
      brightness: Brightness.dark,
    ),
  ),
);

// Dynamic color (Android 12+)
// Requires package:dynamic_color
DynamicColorBuilder(
  builder: (lightDynamic, darkDynamic) {
    return MaterialApp(
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: lightDynamic ?? ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        colorScheme: darkDynamic ?? ColorScheme.fromSeed(
          seedColor: Colors.deepPurple,
          brightness: Brightness.dark,
        ),
      ),
    );
  },
);
```

---

## Web: Theme Builder và CSS

### 1. Chọn màu gốc (Seed Color)

Bắt đầu với một màu hex duy nhất đại diện cho thương hiệu của bạn. Toàn bộ scheme được tạo từ màu gốc này.

### 2. Tạo Scheme

Dùng `@material/material-color-utilities` để tạo scheme sáng và tối:

```bash
npm install @material/material-color-utilities
```

```javascript
import {
  argbFromHex,
  hexFromArgb,
  SchemeContent,
  Hct,
} from '@material/material-color-utilities';

function generateTheme(seedHex, isDark = false, contrast = 0.0) {
  const hct = Hct.fromInt(argbFromHex(seedHex));
  const scheme = new SchemeContent(hct, isDark, contrast);

  return {
    // Primary
    '--md-sys-color-primary': hexFromArgb(scheme.primary),
    '--md-sys-color-on-primary': hexFromArgb(scheme.onPrimary),
    '--md-sys-color-primary-container': hexFromArgb(scheme.primaryContainer),
    '--md-sys-color-on-primary-container': hexFromArgb(scheme.onPrimaryContainer),
    // Secondary
    '--md-sys-color-secondary': hexFromArgb(scheme.secondary),
    '--md-sys-color-on-secondary': hexFromArgb(scheme.onSecondary),
    '--md-sys-color-secondary-container': hexFromArgb(scheme.secondaryContainer),
    '--md-sys-color-on-secondary-container': hexFromArgb(scheme.onSecondaryContainer),
    // Tertiary
    '--md-sys-color-tertiary': hexFromArgb(scheme.tertiary),
    '--md-sys-color-on-tertiary': hexFromArgb(scheme.onTertiary),
    '--md-sys-color-tertiary-container': hexFromArgb(scheme.tertiaryContainer),
    '--md-sys-color-on-tertiary-container': hexFromArgb(scheme.onTertiaryContainer),
    // Error
    '--md-sys-color-error': hexFromArgb(scheme.error),
    '--md-sys-color-on-error': hexFromArgb(scheme.onError),
    '--md-sys-color-error-container': hexFromArgb(scheme.errorContainer),
    '--md-sys-color-on-error-container': hexFromArgb(scheme.onErrorContainer),
    // Surface
    '--md-sys-color-surface': hexFromArgb(scheme.surface),
    '--md-sys-color-on-surface': hexFromArgb(scheme.onSurface),
    '--md-sys-color-on-surface-variant': hexFromArgb(scheme.onSurfaceVariant),
    '--md-sys-color-surface-container-lowest': hexFromArgb(scheme.surfaceContainerLowest),
    '--md-sys-color-surface-container-low': hexFromArgb(scheme.surfaceContainerLow),
    '--md-sys-color-surface-container': hexFromArgb(scheme.surfaceContainer),
    '--md-sys-color-surface-container-high': hexFromArgb(scheme.surfaceContainerHigh),
    '--md-sys-color-surface-container-highest': hexFromArgb(scheme.surfaceContainerHighest),
    '--md-sys-color-surface-dim': hexFromArgb(scheme.surfaceDim),
    '--md-sys-color-surface-bright': hexFromArgb(scheme.surfaceBright),
    // Outline
    '--md-sys-color-outline': hexFromArgb(scheme.outline),
    '--md-sys-color-outline-variant': hexFromArgb(scheme.outlineVariant),
    // Inverse
    '--md-sys-color-inverse-surface': hexFromArgb(scheme.inverseSurface),
    '--md-sys-color-inverse-on-surface': hexFromArgb(scheme.inverseOnSurface),
    '--md-sys-color-inverse-primary': hexFromArgb(scheme.inversePrimary),
  };
}
```

### 3. Áp dụng Theme

```javascript
function applyTheme(seedHex, isDark = false, contrast = 0.0) {
  const tokens = generateTheme(seedHex, isDark, contrast);
  const root = document.documentElement;
  for (const [prop, value] of Object.entries(tokens)) {
    root.style.setProperty(prop, value);
  }
}

// Apply light theme
applyTheme('#1A73E8');

// Apply dark theme
applyTheme('#1A73E8', true);

// Apply high contrast
applyTheme('#1A73E8', false, 1.0);
```

### 4. Xuất ra CSS

```javascript
function exportAsCSS(seedHex) {
  const light = generateTheme(seedHex, false);
  const dark = generateTheme(seedHex, true);

  let css = ':root {\n';
  for (const [prop, value] of Object.entries(light)) {
    css += `  ${prop}: ${value};\n`;
  }
  css += '}\n\n';
  css += '@media (prefers-color-scheme: dark) {\n  :root {\n';
  for (const [prop, value] of Object.entries(dark)) {
    css += `    ${prop}: ${value};\n`;
  }
  css += '  }\n}\n';
  return css;
}
```

## Tích hợp màu thương hiệu

### Ánh xạ màu thương hiệu hiện có

Nếu bạn đã có màu thương hiệu, hãy ánh xạ chúng vào vai trò MD3:

| Khái niệm thương hiệu | Vai trò MD3 |
|--------------|----------|
| Màu thương hiệu chính | Dùng làm seed cho bảng `primary` |
| Màu thương hiệu phụ | Ghi đè `secondary` hoặc dùng làm màu tùy biến |
| Màu nhấn (accent) | Ánh xạ tới `tertiary` |
| Màu cảnh báo/nguy hiểm | Ghi đè `error` (hoặc giữ mặc định MD3) |
| Nền | Được tạo từ seed (đừng hardcode) |

### Dùng màu thương hiệu làm Seed

Cách đơn giản nhất: dùng màu thương hiệu chính làm seed. Thuật toán sẽ tự động tạo màu secondary và tertiary hài hòa.

```javascript
// Your brand's primary blue
applyTheme('#1A73E8');
```

### Hòa hợp màu cho các màu thương hiệu bổ sung

Nếu cần tích hợp một màu thương hiệu cụ thể không được tạo từ seed:

```javascript
import { Blend, argbFromHex, hexFromArgb } from '@material/material-color-utilities';

// Your brand's orange accent color
const brandOrange = argbFromHex('#FF6D00');
// The generated primary color
const schemePrimary = argbFromHex('#1A73E8');

// Harmonize: shifts the hue slightly toward primary
const harmonizedOrange = hexFromArgb(Blend.harmonize(brandOrange, schemePrimary));
// Use harmonizedOrange as a custom color in your theme
```

## Theme tối (Dark Theme)

### Tạo tự động

Theme tối được tạo tự động từ cùng một màu gốc. Ánh xạ tonal chỉ dịch chuyển:
- Theme sáng dùng tone sáng hơn (80–100) cho surface, tone tối hơn (10–40) cho điểm nhấn
- Theme tối đảo ngược: tone tối hơn (4–22) cho surface, tone sáng hơn (80–90) cho điểm nhấn

### Triển khai CSS

```css
/* Light theme (default) */
:root {
  --md-sys-color-primary: #6750A4;
  --md-sys-color-surface: #FEF7FF;
  /* ... all light tokens ... */
}

/* Dark theme via media query */
@media (prefers-color-scheme: dark) {
  :root {
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-surface: #141218;
    /* ... all dark tokens ... */
  }
}

/* Dark theme via class (for manual toggle) */
.dark-theme {
  --md-sys-color-primary: #D0BCFF;
  --md-sys-color-surface: #141218;
  /* ... all dark tokens ... */
}
```

### Chuyển đổi theme lúc chạy

```javascript
class ThemeManager {
  constructor(seedHex) {
    this.seedHex = seedHex;
    this.isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    this.contrast = 0.0; // standard

    // Listen for system theme changes
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      this.isDark = e.matches;
      this.apply();
    });
  }

  apply() {
    const tokens = generateTheme(this.seedHex, this.isDark, this.contrast);
    const root = document.documentElement;
    for (const [prop, value] of Object.entries(tokens)) {
      root.style.setProperty(prop, value);
    }
    root.setAttribute('data-theme', this.isDark ? 'dark' : 'light');
  }

  toggleDark() {
    this.isDark = !this.isDark;
    this.apply();
  }

  setContrast(level) {
    // 0.0 = standard, 0.5 = medium, 1.0 = high
    this.contrast = level;
    this.apply();
  }

  setSeed(hex) {
    this.seedHex = hex;
    this.apply();
  }
}

// Usage
const theme = new ThemeManager('#6750A4');
theme.apply();

// Toggle dark mode
document.getElementById('theme-toggle').addEventListener('click', () => theme.toggleDark());
```

## Theme tương phản cao

MD3 hỗ trợ 3 mức tương phản, điều chỉnh qua tham số `contrast`:

| Mức | Giá trị | Hiệu ứng |
|-------|-------|--------|
| Standard | 0.0 | Khoảng cách tonal mặc định |
| Medium | 0.5 | Tăng khoảng cách tonal, dễ đọc hơn |
| High | 1.0 | Khoảng cách tonal tối đa, khả năng đọc cao nhất |

```javascript
// Standard contrast
const standard = new SchemeContent(hct, isDark, 0.0);

// Medium contrast
const medium = new SchemeContent(hct, isDark, 0.5);

// High contrast
const high = new SchemeContent(hct, isDark, 1.0);
```

Tương phản cao hơn tăng khoảng cách tonal giữa các vai trò màu ghép cặp (vd. `primary` và `on-primary`), giúp văn bản dễ đọc hơn mà không làm thay đổi căn bản cảm nhận màu sắc.

## Ghi đè ở cấp Component (web)

Ghi đè ngoại quan của từng component **web** bằng CSS custom property riêng của component. **Compose** dùng `TextFieldDefaults`, `ButtonDefaults`, `MaterialTheme`, v.v.

```css
/* Override specific button colors */
md-filled-button {
  --md-filled-button-container-color: var(--md-sys-color-tertiary);
  --md-filled-button-label-text-color: var(--md-sys-color-on-tertiary);
}

/* Override text field shape */
md-outlined-text-field {
  --md-outlined-text-field-container-shape: var(--md-sys-shape-corner-medium);
}

/* Override FAB */
md-fab {
  --md-fab-container-color: var(--md-sys-color-tertiary-container);
  --md-fab-icon-color: var(--md-sys-color-on-tertiary-container);
}

/* Override switch colors */
md-switch {
  --md-switch-selected-track-color: var(--md-sys-color-primary);
  --md-switch-selected-handle-color: var(--md-sys-color-on-primary);
}
```

### Quy tắc đặt tên token Component

Token component có dạng: `--md-{component}-{element}-{property}`

Ví dụ:
- `--md-filled-button-container-color`
- `--md-filled-button-container-shape`
- `--md-filled-button-label-text-color`
- `--md-outlined-text-field-outline-color`
- `--md-outlined-text-field-focus-outline-color`
- `--md-fab-container-color`
- `--md-fab-container-shape`
- `--md-switch-selected-track-color`

## Theme theo khu vực (Scoped Theming)

CSS custom property có tính kế thừa (cascade), nên phần tử con tự động kế thừa token của tổ tiên gần nhất. Điều này cho phép tạo nhiều theme trong cùng một trang:

```html
<!-- Default theme -->
<div class="app-content">
  <md-filled-button>Standard button</md-filled-button>
</div>

<!-- Scoped theme: different seed for a specific section -->
<div class="themed-section" id="brand-section">
  <h2>Partner Portal</h2>
  <md-filled-button>Partner button</md-filled-button>
</div>
```

```css
.themed-section {
  /* Override just the primary palette for this section */
  --md-sys-color-primary: #0066CC;
  --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #D1E4FF;
  --md-sys-color-on-primary-container: #001D35;

  padding: 24px;
  border-radius: 12px;
  background: var(--md-sys-color-surface-container);
}
```

```javascript
// Generate full scoped theme programmatically
function applyScopedTheme(elementId, seedHex, isDark = false) {
  const element = document.getElementById(elementId);
  const tokens = generateTheme(seedHex, isDark);
  for (const [prop, value] of Object.entries(tokens)) {
    element.style.setProperty(prop, value);
  }
}

applyScopedTheme('brand-section', '#0066CC');
```

## Kiểm tra và xác thực theme

### Danh sách kiểm tra theme

- [ ] Tất cả màu dùng dạng token `var(--md-sys-color-*)` (không hardcode)
- [ ] Mọi cặp văn bản/nền đều dùng đúng cặp vai trò (`on-*` trên `*`)
- [ ] Theme tối được tạo từ cùng seed và kiểm tra trên thiết bị thật
- [ ] Cân nhắc 3 mức tương phản (standard, medium, high)
- [ ] Thương hiệu được hòa hợp với scheme (dùng `Blend.harmonize` cho màu tùy biến)
- [ ] Không dùng màu cố định (fixed) cho phần tử yêu cầu tương phản
- [ ] Theme tối và sáng chia sẻ cùng shape/typography token
- [ ] Trên **Compose**: dùng `MaterialTheme`; trên **web**: token CSS nằm trên `:root` hoặc tổ tiên đúng; trên **Flutter**: `ThemeData` + `ColorScheme`

### Kiểm tra tương phản

```javascript
// Calculate contrast ratio between two colors
function getContrastRatio(color1, color2) {
  const getLuminance = (color) => {
    const rgb = color.match(/\w\w/g).map(x => parseInt(x, 16) / 255);
    const [r, g, b] = rgb.map(c => c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const l1 = getLuminance(color1);
  const l2 = getLuminance(color2);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

// WCAG AA: 4.5:1 for normal text, 3:1 for large text/UI components
const ratio = getContrastRatio('6750A4', 'FFFFFF');
console.log('Contrast ratio:', ratio.toFixed(2));
```

### Các lỗi thường gặp

| Lỗi | Vấn đề | Cách sửa |
|------|--------|---------|
| Hardcode màu | Theme thay đổi không lan truyền | Dùng `var(--md-sys-color-*)` / vai trò `MaterialTheme.colorScheme` |
| Ghép cặp sai | Tương phản kém | Luôn dùng `on-X` trên `X` |
| Dùng `outline` cho divider | Quá nổi bật | Dùng `outline-variant` |
| Dùng `outline-variant` cho viền input | Tương phản kém | Dùng `outline` |
| Bỏ qua theme tối | Trải nghiệm kém cho người dùng đêm | Tạo và kiểm tra cả hai theme |
| Màu thương hiệu không hòa hợp | Trông lệch trong hệ thống | Dùng `Blend.harmonize` |
| Không hỗ trợ tương phản cao | Không hỗ trợ người dùng khiếm thị | Dùng tham số contrast `0.5` hoặc `1.0` |
| Dùng fixed color cho văn bản | Tương phản không ổn định | Dùng màu động (dynamic) cho phần tử tương tác |
| Ghi đè token trên `:root` nhưng component ở shadow DOM | Không kế thừa | Truyền custom property qua chính phần tử component |
| Nhầm lẫn `@material/web` với `@material/mdc-*` | Trộn MD2 và MD3 | Chỉ dùng `@material/web` cho MD3 |

## Tài nguyên

- [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/) — Tạo theme từ màu gốc, xuất sang Compose/Kotlin, Flutter/Dart, web/CSS
- [Material Color Utilities](https://github.com/material-foundation/material-color-utilities) — Thư viện JavaScript/Java/Dart tạo scheme
- [Material 3 Theming](https://m3.material.io/styles/theme/overview) — Hướng dẫn chính thức
- [Material 3 Dynamic Color](https://m3.material.io/styles/color/dynamic-color/overview) — Chi tiết dynamic color
- [Material Web](https://m3.material.io/develop/web) — Trạng thái maintenance và giới hạn của web


