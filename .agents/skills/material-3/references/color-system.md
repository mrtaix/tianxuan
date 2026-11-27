# Hệ thống màu MD3

Tài liệu tham khảo đầy đủ về hệ thống màu của Material Design 3: vai trò, bảng tonal, dynamic color và tạo scheme.

## Vai trò màu

MD3 định nghĩa 29+ vai trò màu, được tổ chức thành các nhóm. **Jetpack Compose:** ánh xạ tới `MaterialTheme.colorScheme` (vd. `primary`, `onPrimary`). **Web:** mỗi vai trò tồn tại dưới dạng CSS custom property `--md-sys-color-{role-name}`.

### Màu nhấn (Accent Colors)

Ba nhóm màu nhấn (primary, secondary, tertiary), mỗi nhóm có 4 vai trò:

| Vai trò | CSS Token | Mục đích |
|------|-----------|---------|
| Primary | `--md-sys-color-primary` | Vùng tô nhấn mạnh cao, văn bản, icon trên surface |
| On Primary | `--md-sys-color-on-primary` | Văn bản và icon trên primary |
| Primary Container | `--md-sys-color-primary-container` | Vùng tô nổi bật cho component quan trọng (FAB, v.v.) |
| On Primary Container | `--md-sys-color-on-primary-container` | Văn bản và icon trên primary-container |
| Secondary | `--md-sys-color-secondary` | Vùng tô, văn bản, icon ít nổi bật hơn |
| On Secondary | `--md-sys-color-on-secondary` | Văn bản và icon trên secondary |
| Secondary Container | `--md-sys-color-secondary-container` | Component lùi lại (tonal button) |
| On Secondary Container | `--md-sys-color-on-secondary-container` | Văn bản và icon trên secondary-container |
| Tertiary | `--md-sys-color-tertiary` | Vùng tô, văn bản, icon bổ trợ |
| On Tertiary | `--md-sys-color-on-tertiary` | Văn bản và icon trên tertiary |
| Tertiary Container | `--md-sys-color-tertiary-container` | Vùng tô container bổ trợ |
| On Tertiary Container | `--md-sys-color-on-tertiary-container` | Văn bản và icon trên tertiary-container |

**Hướng dẫn sử dụng:**
- **Primary**: Component nổi bật nhất — FAB, button nhấn mạnh cao, trạng thái active
- **Secondary**: Component ít nổi bật hơn — filter chip, tonal button, trạng thái selection
- **Tertiary**: Điểm nhấn tương phản cân bằng primary/secondary — input field, badge

### Màu lỗi (Error Colors)

Màu tĩnh, không thay đổi theo dynamic color scheme:

| Vai trò | CSS Token | Mục đích |
|------|-----------|---------|
| Error | `--md-sys-color-error` | Màu thu hút sự chú ý cho phần tử khẩn cấp |
| On Error | `--md-sys-color-on-error` | Văn bản và icon trên error |
| Error Container | `--md-sys-color-error-container` | Vùng tô container lỗi |
| On Error Container | `--md-sys-color-on-error-container` | Văn bản và icon trên error-container |

### Màu bề mặt (Surface Colors)

| Vai trò | CSS Token | Mục đích |
|------|-----------|---------|
| Surface | `--md-sys-color-surface` | Màu nền mặc định |
| On Surface | `--md-sys-color-on-surface` | Văn bản và icon trên mọi surface |
| On Surface Variant | `--md-sys-color-on-surface-variant` | Văn bản/icon nhấn mạnh thấp hơn trên surface |
| Surface Container Lowest | `--md-sys-color-surface-container-lowest` | Container nhấn mạnh thấp nhất |
| Surface Container Low | `--md-sys-color-surface-container-low` | Container nhấn mạnh thấp |
| Surface Container | `--md-sys-color-surface-container` | Container mặc định (khu vực điều hướng) |
| Surface Container High | `--md-sys-color-surface-container-high` | Container nhấn mạnh cao |
| Surface Container Highest | `--md-sys-color-surface-container-highest` | Container nhấn mạnh cao nhất |
| Surface Dim | `--md-sys-color-surface-dim` | Surface tối nhất trong cả hai theme |
| Surface Bright | `--md-sys-color-surface-bright` | Surface sáng nhất trong cả hai theme |

**Phân cấp surface container**: Dùng `surface` làm nền, `surface-container` cho điều hướng. 5 cấp container tạo phân cấp thị giác và độ sâu xếp chồng, đặc biệt hữu ích cho layout mở rộng với nhiều pane.

**Surface dim/bright**: Khác với surface thường (chuyển từ sáng sang tối), dim và bright giữ nguyên độ sáng tương đối trong cả hai theme. Dùng bright cho khu vực luôn phải sáng nhất, dim cho khu vực luôn phải tối nhất.

### Màu đảo (Inverse Colors)

Dành cho phần tử tương phản với UI xung quanh (vd. snackbar):

| Vai trò | CSS Token | Mục đích |
|------|-----------|---------|
| Inverse Surface | `--md-sys-color-inverse-surface` | Nền cho phần tử tương phản |
| Inverse On Surface | `--md-sys-color-inverse-on-surface` | Văn bản trên inverse-surface |
| Inverse Primary | `--md-sys-color-inverse-primary` | Văn bản có thể thao tác trên inverse-surface |

### Màu viền (Outline Colors)

| Vai trò | CSS Token | Mục đích |
|------|-----------|---------|
| Outline | `--md-sys-color-outline` | Đường viền quan trọng (viền text field, tương phản 3:1) |
| Outline Variant | `--md-sys-color-outline-variant` | Phần tử trang trí (divider, viền card) |

**Quan trọng**: Đừng dùng `outline` cho divider — hãy dùng `outline-variant`. Đừng dùng `outline-variant` cho đường viền tương tác cần tương phản 3:1 — hãy dùng `outline`.

### Màu nhấn cố định (Fixed Accent Colors — Add-on)

Những màu này giữ nguyên trong cả theme sáng và tối (khác với màu container thường thay đổi tone):

| Vai trò | CSS Token |
|------|-----------|
| Primary Fixed | `--md-sys-color-primary-fixed` |
| Primary Fixed Dim | `--md-sys-color-primary-fixed-dim` |
| On Primary Fixed | `--md-sys-color-on-primary-fixed` |
| On Primary Fixed Variant | `--md-sys-color-on-primary-fixed-variant` |
| Secondary Fixed | `--md-sys-color-secondary-fixed` |
| Secondary Fixed Dim | `--md-sys-color-secondary-fixed-dim` |
| On Secondary Fixed | `--md-sys-color-on-secondary-fixed` |
| On Secondary Fixed Variant | `--md-sys-color-on-secondary-fixed-variant` |
| Tertiary Fixed | `--md-sys-color-tertiary-fixed` |
| Tertiary Fixed Dim | `--md-sys-color-tertiary-fixed-dim` |
| On Tertiary Fixed | `--md-sys-color-on-tertiary-fixed` |
| On Tertiary Fixed Variant | `--md-sys-color-on-tertiary-fixed-variant` |

**Thận trọng**: Màu cố định không thích ứng với theme nên có thể gây vấn đề tương phản. Hãy dùng vai trò màu nhấn thường cho phần tử mà tương phản là yếu tố quan trọng.

## Hệ thống bảng tonal

MD3 tạo màu từ một **màu gốc (seed color)** thông qua hệ thống bảng tonal:

### Cách hoạt động

1. Chọn một **màu gốc** (giá trị hex)
2. Màu gốc tạo ra **5 bảng tonal**: Primary, Secondary, Tertiary, Neutral, Neutral-Variant
3. Mỗi bảng dùng các điểm tonal dọc theo **0–100** (thường 16 điểm chính: 0, 10, 20, 25, 30, 35, 40, 50, 60, 70, 80, 90, 95, 98, 99, 100)
4. Vai trò màu được ánh xạ tới giá trị tonal cụ thể tùy scheme sáng hay tối

### Ánh xạ giá trị tonal (Scheme sáng)

| Vai trò | Bảng tonal | Tone |
|------|--------------|------|
| Primary | Primary | 40 |
| On Primary | Primary | 100 |
| Primary Container | Primary | 90 |
| On Primary Container | Primary | 10 |
| Surface | Neutral | 98 |
| On Surface | Neutral | 10 |
| Surface Container | Neutral | 94 |
| Surface Container Low | Neutral | 96 |
| Surface Container Lowest | Neutral | 100 |
| Surface Container High | Neutral | 92 |
| Surface Container Highest | Neutral | 90 |
| Outline | Neutral-Variant | 50 |
| Outline Variant | Neutral-Variant | 80 |

### Ánh xạ giá trị tonal (Scheme tối)

| Vai trò | Bảng tonal | Tone |
|------|--------------|------|
| Primary | Primary | 80 |
| On Primary | Primary | 20 |
| Primary Container | Primary | 30 |
| On Primary Container | Primary | 90 |
| Surface | Neutral | 6 |
| On Surface | Neutral | 90 |
| Surface Container | Neutral | 12 |
| Surface Container Low | Neutral | 10 |
| Surface Container Lowest | Neutral | 4 |
| Surface Container High | Neutral | 17 |
| Surface Container Highest | Neutral | 22 |
| Outline | Neutral-Variant | 60 |
| Outline Variant | Neutral-Variant | 30 |

## Quy tắc ghép cặp màu

Màu chỉ được dùng theo đúng cặp đã định để đảm bảo tương phản truy cập được:

| Container/Fill | Màu văn bản/Icon |
|---------------|----------------|
| `primary` | `on-primary` |
| `primary-container` | `on-primary-container` |
| `secondary` | `on-secondary` |
| `secondary-container` | `on-secondary-container` |
| `tertiary` | `on-tertiary` |
| `tertiary-container` | `on-tertiary-container` |
| `error` | `on-error` |
| `error-container` | `on-error-container` |
| `surface` | `on-surface` hoặc `on-surface-variant` |
| `surface-container-*` | `on-surface` hoặc `on-surface-variant` |
| `inverse-surface` | `inverse-on-surface` hoặc `inverse-primary` |

**Không bao giờ ghép màu ngoài cặp đã định** — điều này phá vỡ đảm bảo tương phản, đặc biệt trong dynamic color và chế độ tương phản cao.

## Dynamic Color

Dynamic color tạo scheme màu cá nhân hóa từ nguồn bên ngoài:

### Do người dùng tạo (Wallpaper)
Hệ điều hành trích xuất màu gốc từ hình nền của người dùng và tạo scheme. **Android:** `dynamicLightColorScheme` / `dynamicDarkColorScheme` trên **Android 12+ (API 31+)**. **Web:** **không có** API dynamic-color tương đương từ wallpaper của trình duyệt; bạn có thể suy ra màu gốc từ **nội dung** (vd. hình ảnh) bằng thư viện, nhưng đó là giải pháp riêng của ứng dụng, không phải theming wallpaper của hệ thống.

### Dựa trên nội dung (Content-Based)
Màu gốc được trích xuất từ nội dung trong ứng dụng (ảnh album, bìa sách, v.v.) để tạo scheme theo ngữ cảnh.

### Tạo Scheme bằng JavaScript

```javascript
import {
  argbFromHex,
  themeFromSourceColor,
  applyTheme,
} from '@material/material-color-utilities';

// Generate a theme from a seed color
const theme = themeFromSourceColor(argbFromHex('#6750A4'));

// Apply to the document
applyTheme(theme, { target: document.body, dark: false });
```

### Tạo CSS thủ công từ màu gốc

```javascript
import {
  argbFromHex,

  hexFromArgb,
  SchemeContent,
  Hct,
} from '@material/material-color-utilities';

function generateScheme(seedHex, isDark = false) {
  const hct = Hct.fromInt(argbFromHex(seedHex));
  const scheme = new SchemeContent(hct, isDark, 0.0); // 0.0 = standard contrast

  return {
    '--md-sys-color-primary': hexFromArgb(scheme.primary),
    '--md-sys-color-on-primary': hexFromArgb(scheme.onPrimary),
    '--md-sys-color-primary-container': hexFromArgb(scheme.primaryContainer),
    '--md-sys-color-on-primary-container': hexFromArgb(scheme.onPrimaryContainer),
    '--md-sys-color-secondary': hexFromArgb(scheme.secondary),
    '--md-sys-color-on-secondary': hexFromArgb(scheme.onSecondary),
    '--md-sys-color-secondary-container': hexFromArgb(scheme.secondaryContainer),
    '--md-sys-color-on-secondary-container': hexFromArgb(scheme.onSecondaryContainer),
    '--md-sys-color-tertiary': hexFromArgb(scheme.tertiary),
    '--md-sys-color-on-tertiary': hexFromArgb(scheme.onTertiary),
    '--md-sys-color-tertiary-container': hexFromArgb(scheme.tertiaryContainer),
    '--md-sys-color-on-tertiary-container': hexFromArgb(scheme.onTertiaryContainer),
    '--md-sys-color-error': hexFromArgb(scheme.error),
    '--md-sys-color-on-error': hexFromArgb(scheme.onError),
    '--md-sys-color-error-container': hexFromArgb(scheme.errorContainer),
    '--md-sys-color-on-error-container': hexFromArgb(scheme.onErrorContainer),
    '--md-sys-color-surface': hexFromArgb(scheme.surface),
    '--md-sys-color-on-surface': hexFromArgb(scheme.onSurface),
    '--md-sys-color-on-surface-variant': hexFromArgb(scheme.onSurfaceVariant),
    '--md-sys-color-surface-container': hexFromArgb(scheme.surfaceContainer),
    '--md-sys-color-surface-container-low': hexFromArgb(scheme.surfaceContainerLow),
    '--md-sys-color-surface-container-lowest': hexFromArgb(scheme.surfaceContainerLowest),
    '--md-sys-color-surface-container-high': hexFromArgb(scheme.surfaceContainerHigh),
    '--md-sys-color-surface-container-highest': hexFromArgb(scheme.surfaceContainerHighest),
    '--md-sys-color-outline': hexFromArgb(scheme.outline),
    '--md-sys-color-outline-variant': hexFromArgb(scheme.outlineVariant),
    '--md-sys-color-inverse-surface': hexFromArgb(scheme.inverseSurface),
    '--md-sys-color-inverse-on-surface': hexFromArgb(scheme.inverseOnSurface),
    '--md-sys-color-inverse-primary': hexFromArgb(scheme.inversePrimary),
  };
}

// Apply to document
function applyScheme(seedHex, isDark = false) {
  const tokens = generateScheme(seedHex, isDark);
  const root = document.documentElement;
  for (const [property, value] of Object.entries(tokens)) {
    root.style.setProperty(property, value);
  }
}
```

## Hòa hợp màu (Color Harmonization)

Khi tích hợp màu thương hiệu tùy biến không đến từ màu gốc, hãy dùng hòa hợp để pha trộn chúng vào hệ thống tonal:

```javascript
import { Blend } from '@material/material-color-utilities';

// Harmonize a custom color with the primary color
const harmonized = Blend.harmonize(customColorArgb, primaryColorArgb);
```

Thao tác này dịch nhẹ sắc thái của màu tùy biến về phía màu primary của scheme, giúp nó hòa hợp mà không mất bản sắc.

## Tương phản do người dùng kiểm soát (tháng 5/2025)

MD3 hiện hỗ trợ 3 mức tương phản:
- **Standard** (0.0): Tương phản mặc định
- **Medium** (0.5): Tăng khoảng cách tonal giữa các vai trò
- **High** (1.0): Khoảng cách tonal tối đa để hỗ trợ thị giác

```javascript
// Generate high contrast scheme
const scheme = new SchemeContent(hct, isDark, 1.0); // 1.0 = high contrast
```

Tham số tương phản điều chỉnh khoảng cách tonal giữa các vai trò ghép cặp, tăng khả năng đọc mà không làm thay đổi cảm nhận màu tổng thể.

## Scheme màu cơ sở (giá trị mặc định)

Scheme cơ sở tĩnh dành cho sản phẩm không dùng dynamic color:

### Theme sáng
```css
:root {
  --md-sys-color-primary: #6750A4;
  --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #EADDFF;
  --md-sys-color-on-primary-container: #21005D;
  --md-sys-color-secondary: #625B71;
  --md-sys-color-on-secondary: #FFFFFF;
  --md-sys-color-secondary-container: #E8DEF8;
  --md-sys-color-on-secondary-container: #1D192B;
  --md-sys-color-tertiary: #7D5260;
  --md-sys-color-on-tertiary: #FFFFFF;
  --md-sys-color-tertiary-container: #FFD8E4;
  --md-sys-color-on-tertiary-container: #31111D;
  --md-sys-color-error: #B3261E;
  --md-sys-color-on-error: #FFFFFF;
  --md-sys-color-error-container: #F9DEDC;
  --md-sys-color-on-error-container: #410E0B;
  --md-sys-color-surface: #FEF7FF;
  --md-sys-color-on-surface: #1D1B20;
  --md-sys-color-on-surface-variant: #49454F;
  --md-sys-color-surface-container-lowest: #FFFFFF;
  --md-sys-color-surface-container-low: #F7F2FA;
  --md-sys-color-surface-container: #F3EDF7;
  --md-sys-color-surface-container-high: #ECE6F0;
  --md-sys-color-surface-container-highest: #E6E0E9;
  --md-sys-color-surface-dim: #DED8E1;
  --md-sys-color-surface-bright: #FEF7FF;
  --md-sys-color-outline: #79747E;
  --md-sys-color-outline-variant: #CAC4D0;
  --md-sys-color-inverse-surface: #322F35;
  --md-sys-color-inverse-on-surface: #F5EFF7;
  --md-sys-color-inverse-primary: #D0BCFF;
}
```

### Theme tối
```css
@media (prefers-color-scheme: dark) {
  :root {
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-on-primary: #381E72;
    --md-sys-color-primary-container: #4F378B;
    --md-sys-color-on-primary-container: #EADDFF;
    --md-sys-color-secondary: #CCC2DC;
    --md-sys-color-on-secondary: #332D41;
    --md-sys-color-secondary-container: #4A4458;
    --md-sys-color-on-secondary-container: #E8DEF8;
    --md-sys-color-tertiary: #EFB8C8;
    --md-sys-color-on-tertiary: #492532;
    --md-sys-color-tertiary-container: #633B48;
    --md-sys-color-on-tertiary-container: #FFD8E4;
    --md-sys-color-error: #F2B8B5;
    --md-sys-color-on-error: #601410;
    --md-sys-color-error-container: #8C1D18;
    --md-sys-color-on-error-container: #F9DEDC;
    --md-sys-color-surface: #141218;
    --md-sys-color-on-surface: #E6E0E9;
    --md-sys-color-on-surface-variant: #CAC4D0;
    --md-sys-color-surface-container-lowest: #0F0D13;
    --md-sys-color-surface-container-low: #1D1B20;
    --md-sys-color-surface-container: #211F26;
    --md-sys-color-surface-container-high: #2B2930;
    --md-sys-color-surface-container-highest: #36343B;
    --md-sys-color-surface-dim: #141218;
    --md-sys-color-surface-bright: #3B383E;
    --md-sys-color-outline: #938F99;
    --md-sys-color-outline-variant: #49454F;
    --md-sys-color-inverse-surface: #E6E0E9;
    --md-sys-color-inverse-on-surface: #322F35;
    --md-sys-color-inverse-primary: #6750A4;
  }
}
```

