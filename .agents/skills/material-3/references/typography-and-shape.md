# Typography, Shape, Elevation và Motion trong MD3

Tài liệu tham khảo về các hệ thống token thị giác của Material Design 3 ngoài màu sắc.

## Typography

### Thang kiểu chữ (Type Scale)

MD3 dùng 15 kiểu cơ sở + 15 kiểu nhấn mạnh (emphasized), tổ chức thành 5 danh mục (Display, Headline, Title, Body, Label), mỗi danh mục 3 kích thước (Large, Medium, Small).

#### Thang kiểu chữ cơ sở (giá trị mặc định)

| Kiểu | Font | Weight | Kích thước (sp) | Kích thước (rem) | Chiều cao dòng | Tracking |
|-------|------|--------|-----------|------------|-------------|----------|
| Display Large | Roboto | 400 | 57 | 3.5625 | 64sp / 4rem | -0.25px |
| Display Medium | Roboto | 400 | 45 | 2.8125 | 52sp / 3.25rem | 0 |
| Display Small | Roboto | 400 | 36 | 2.25 | 44sp / 2.75rem | 0 |
| Headline Large | Roboto | 400 | 32 | 2 | 40sp / 2.5rem | 0 |
| Headline Medium | Roboto | 400 | 28 | 1.75 | 36sp / 2.25rem | 0 |
| Headline Small | Roboto | 400 | 24 | 1.5 | 32sp / 2rem | 0 |
| Title Large | Roboto | 400 | 22 | 1.375 | 28sp / 1.75rem | 0 |
| Title Medium | Roboto | 500 | 16 | 1 | 24sp / 1.5rem | 0.15px |
| Title Small | Roboto | 500 | 14 | 0.875 | 20sp / 1.25rem | 0.1px |
| Body Large | Roboto | 400 | 16 | 1 | 24sp / 1.5rem | 0.5px |
| Body Medium | Roboto | 400 | 14 | 0.875 | 20sp / 1.25rem | 0.25px |
| Body Small | Roboto | 400 | 12 | 0.75 | 16sp / 1rem | 0.4px |
| Label Large | Roboto | 500 | 14 | 0.875 | 20sp / 1.25rem | 0.1px |
| Label Medium | Roboto | 500 | 12 | 0.75 | 16sp / 1rem | 0.5px |
| Label Small | Roboto | 500 | 11 | 0.6875 | 16sp / 1rem | 0.5px |

#### Kiểu chữ nhấn mạnh (Emphasized — cập nhật Expressive)

15 kiểu nhấn mạnh phản chiếu thang cơ sở nhưng có **weight cao hơn** và vài điều chỉnh nhỏ. Dùng cho:
- Trạng thái selected/active trong component
- Button hành động chính
- Headline cần nhấn mạnh
- Nội dung chưa đọc/quan trọng

Cách dùng: thay token cơ sở bằng phiên bản emphasized:
- Cơ sở: `md.sys.typescale.display-large`
- Nhấn mạnh: `md.sys.typescale.emphasized.display-large`

### CSS Custom Properties

Mỗi kiểu chữ ánh xạ tới các token trục riêng lẻ:

```css
/* Example: Body Large */
--md-sys-typescale-body-large-font: 'Roboto', sans-serif;
--md-sys-typescale-body-large-weight: 400;
--md-sys-typescale-body-large-size: 1rem;        /* 16sp */
--md-sys-typescale-body-large-line-height: 1.5rem; /* 24sp */
--md-sys-typescale-body-large-tracking: 0.03125rem; /* 0.5px */

/* Example: Title Medium */
--md-sys-typescale-title-medium-font: 'Roboto', sans-serif;
--md-sys-typescale-title-medium-weight: 500;
--md-sys-typescale-title-medium-size: 1rem;
--md-sys-typescale-title-medium-line-height: 1.5rem;
--md-sys-typescale-title-medium-tracking: 0.009375rem;

/* Example: Label Large (used for buttons) */
--md-sys-typescale-label-large-font: 'Roboto', sans-serif;
--md-sys-typescale-label-large-weight: 500;
--md-sys-typescale-label-large-size: 0.875rem;
--md-sys-typescale-label-large-line-height: 1.25rem;
--md-sys-typescale-label-large-tracking: 0.00625rem;
```

### Dùng kiểu chữ trong CSS

Áp dụng bằng các thuộc tính riêng lẻ:

```css
.headline {
  font-family: var(--md-sys-typescale-headline-large-font);
  font-weight: var(--md-sys-typescale-headline-large-weight);
  font-size: var(--md-sys-typescale-headline-large-size);
  line-height: var(--md-sys-typescale-headline-large-line-height);
  letter-spacing: var(--md-sys-typescale-headline-large-tracking);
}
```

Hoặc dùng thuộc tính viết tắt `font` cho tiện (lưu ý: cần định nghĩa token dạng viết tắt):

```css
.headline {
  font: var(--md-sys-typescale-headline-large-weight)
        var(--md-sys-typescale-headline-large-size) /
        var(--md-sys-typescale-headline-large-line-height)
        var(--md-sys-typescale-headline-large-font);
  letter-spacing: var(--md-sys-typescale-headline-large-tracking);
}
```

### Tùy biến typeface

MD3 dùng hai vai trò typeface:
- **Brand**: Dùng cho kiểu Display và Headline (tập trung vào biểu cảm)
- **Plain**: Dùng cho kiểu Title, Body và Label (tập trung vào khả năng đọc)

Cả hai mặc định là Roboto. Để tùy biến:

```css
:root {
  /* Brand typeface for display/headline */
  --md-ref-typeface-brand: 'Your Display Font', sans-serif;
  /* Plain typeface for body/label/title */
  --md-ref-typeface-plain: 'Your Body Font', sans-serif;
}
```

### Roboto Flex

Roboto Flex là font biến đổi hỗ trợ nhiều trục:
- **Weight** (wght): 100–1000
- **Width** (wdth): 25–151
- **Optical size** (opsz): 8–144

```css
@font-face {
  font-family: 'Roboto Flex';
  src: url('RobotoFlex-VariableFont.woff2') format('woff2');
  font-weight: 100 1000;
  font-stretch: 25% 151%;
}
```

### Đơn vị kích thước font

| Nền tảng | Đơn vị | Chuyển đổi |
|----------|------|------------|
| Android | sp | 1:1 |
| Web | rem | sp / 16 = rem (giả sử root 16px) |

Ví dụ: 10sp = 0.625rem, 12sp = 0.75rem, 14sp = 0.875rem, 16sp = 1rem, 24sp = 1.5rem

### Cách dùng kiểu chữ theo Component

| Component | Kiểu chữ |
|-----------|-----------|
| Button label | Label Large |
| Card title | Title Medium |
| Card body | Body Medium |
| Top app bar title | Title Large |
| Navigation label | Label Medium |
| Dialog headline | Headline Small |
| Dialog body | Body Medium |
| Chip label | Label Large |
| Text field input | Body Large |
| Text field label | Body Small (floating) / Body Large (resting) |
| List headline | Body Large |
| List supporting text | Body Medium |
| Snackbar text | Body Medium |
| Tooltip text | Body Small |
| Tab label | Title Small |
| Badge count | Label Small |

## Shape

### Thang bán kính góc

| Token | Giá trị (dp) | Giá trị (px/CSS) | Component mặc định |
|-------|-----------|----------------|-------------------|
| `none` | 0 | 0px | — |
| `extra-small` | 4 | 4px | Snackbar |
| `small` | 8 | 8px | Text field, menu, chip |
| `medium` | 12 | 12px | Card |
| `large` | 16 | 16px | FAB, extended FAB, nav drawer |
| `large-increased` | 20 | 20px | (cập nhật Expressive) |
| `extra-large` | 28 | 28px | Dialog, bottom sheet, side sheet |
| `extra-large-increased` | 32 | 32px | (cập nhật Expressive) |
| `extra-extra-large` | 48 | 48px | (cập nhật Expressive) |
| `full` | — | 9999px | Button, badge, pill, slider |

### CSS Custom Properties

```css
:root {
  --md-sys-shape-corner-none: 0px;
  --md-sys-shape-corner-extra-small: 4px;
  --md-sys-shape-corner-small: 8px;
  --md-sys-shape-corner-medium: 12px;
  --md-sys-shape-corner-large: 16px;
  --md-sys-shape-corner-large-increased: 20px;
  --md-sys-shape-corner-extra-large: 28px;
  --md-sys-shape-corner-extra-large-increased: 32px;
  --md-sys-shape-corner-extra-extra-large: 48px;
  --md-sys-shape-corner-full: 9999px;
}
```

### Ánh xạ Shape theo Component

| Component | Shape token mặc định |
|-----------|-------------------|
| Button (mọi loại) | `full` |
| FAB | `large` |
| Extended FAB | `large` |
| Icon button | `full` |
| Chip | `small` |
| Card | `medium` |
| Dialog | `extra-large` |
| Text field | `small` (góc trên) |
| Menu | `small` |
| Navigation drawer | `large` (góc cuối) |
| Bottom sheet | `extra-large` (góc trên) |
| Snackbar | `extra-small` |
| Badge | `full` |
| Slider (handle) | `full` |

| Switch (track) | `full` |
| Tabs (indicator) | `full` (góc trên) |
| Search bar | `full` |

### Biến đổi hình dạng (Shape Morphing — Expressive)

Trong bản cập nhật M3 Expressive, component có thể biến đổi hình dạng khi tương tác:
- Hình dạng button thay đổi khi được nhấn
- Trạng thái selected có thể đổi hình dạng
- Loading indicator dùng shape morphing để thể hiện tiến trình

**Ghi chú nền tảng**: Shape morphing **không có** trong `@material/web` ([Material Web chỉ maintenance; Expressive không có trên Web](https://m3.material.io/develop/web)). **Jetpack Compose** là nơi Google ghi lại hành vi shape/motion expressive cho Android. **Flutter:** kiểm tra tài liệu Material 3 / expressive hiện tại cho SDK của bạn. **Web:** xấp xỉ bằng CSS transition trên `border-radius`.

## Elevation

### Cấp độ Elevation

| Cấp | Độ cao DP | Cách dùng |
|-------|-----------|-----|
| 0 | 0dp | Hầu hết component ở trạng thái nghỉ |
| 1 | 1dp | Biến thể nâng (card, sheet) |
| 2 | 3dp | Menu, nav bar, app bar khi cuộn |
| 3 | 6dp | FAB, dialog, search, date/time picker |
| 4 | 8dp | Chỉ tăng khi hover/focus |
| 5 | 12dp | Chỉ tăng khi hover/focus |

### Elevation tonal (không phải bóng đổ)

MD3 dùng **màu bề mặt tonal** để truyền đạt elevation, không phải bóng đổ. Elevation càng cao = tone bề mặt càng sáng (trong theme sáng) hoặc tone bề mặt càng sáng (trong theme tối).

Các vai trò surface container ánh xạ tới khái niệm này:
- Cấp 0: `surface` (phẳng nhất)
- Cấp 1: `surface-container-low`
- Cấp 2: `surface-container`
- Cấp 3: `surface-container-high`
- Cấp 4-5: `surface-container-highest`

### Khi nào dùng bóng đổ

Bóng chỉ phù hợp khi:
- Component nổi trên nội dung có thể nhiều chi tiết thị giác (vd. FAB trên hình ảnh)
- Cần thêm tín hiệu độ sâu ngoài màu sắc (vd. phần tử chồng lên nhau)
- Quy ước nền tảng mong đợi bóng đổ (một số component Android)

### Giá trị bóng CSS

Khi cần bóng:

```css
/* Level 1 */
box-shadow: 0 1px 2px rgba(0,0,0,0.3), 0 1px 3px 1px rgba(0,0,0,0.15);

/* Level 2 */
box-shadow: 0 1px 2px rgba(0,0,0,0.3), 0 2px 6px 2px rgba(0,0,0,0.15);

/* Level 3 */
box-shadow: 0 4px 8px 3px rgba(0,0,0,0.15), 0 1px 3px rgba(0,0,0,0.3);

/* Level 4 */
box-shadow: 0 6px 10px 4px rgba(0,0,0,0.15), 0 2px 3px rgba(0,0,0,0.3);

/* Level 5 */
box-shadow: 0 8px 12px 6px rgba(0,0,0,0.15), 0 4px 4px rgba(0,0,0,0.3);
```

### Ánh xạ Elevation theo Component

| Cấp | Component ở trạng thái nghỉ |
|-------|--------------------|
| 0 | App bar (phẳng), button filled/tonal/outlined, button group, card filled/outlined, carousel, chip, dialog toàn màn hình, icon button, list, nav rail, segmented button, slider, split button, tab |
| 1 | Banner, modal bottom sheet, elevated button, elevated card, elevated chip, modal nav drawer, modal side sheet |
| 2 | App bar (khi cuộn), menu, nav bar, rich tooltip, toolbar |
| 3 | Date picker, dialog dạng modal, extended FAB, FAB, nút đóng FAB menu, search, time picker |

**Hover/focus**: Hầu hết component tương tác tăng thêm 1 cấp khi hover/focus (vd. FAB từ cấp 3 lên cấp 4).

## Motion

### Chuyển động dựa trên lò xo (Spring — cập nhật Expressive)

MD3 Expressive (tháng 5/2025) giới thiệu vật lý chuyển động dựa trên lò xo cho hoạt ảnh component. Lò xo tạo chuyển động tự nhiên và đáp ứng hơn:

- Lò xo không có thời lượng cố định — chúng phản ứng động với đầu vào
- Hai scheme: **standard** (thực dụng) và **expressive** (nảy)
- **Jetpack Compose** cung cấp motion scheme / API hướng lò xo trong Material3 hiện tại (xem `MotionScheme` và BOM của bạn). **MDC-Android** có thể khác theo phiên bản. **Web:** Material Web không triển khai vật lý chuyển động Expressive — dùng easing/duration hoặc CSS/JS tùy chỉnh. **Flutter:** kiểm tra phiên bản Flutter/Material của bạn để biết mức tương đương.

### Easing và Duration (Transition)

Hệ thống easing/duration được dùng cho **transition** (vào, ra, shared-axis) và làm phương án dự phòng trên web:

#### Bộ Easing

**Emphasized** (khuyến nghị cho hầu hết transition — bắt đúng phong cách MD3):
| Kiểu | CSS Cubic-bezier | Cách dùng |
|------|-----------------|-----|
| Emphasized | `cubic-bezier(0.2, 0, 0, 1)` | Bắt đầu và kết thúc trên màn hình |
| Emphasized Decelerate | `cubic-bezier(0.05, 0.7, 0.1, 1)` | Vào màn hình |
| Emphasized Accelerate | `cubic-bezier(0.3, 0, 0.8, 0.15)` | Rời màn hình |

**Standard** (cho transition tiện ích, dự phòng web):
| Kiểu | CSS Cubic-bezier | Cách dùng |
|------|-----------------|-----|
| Standard | `cubic-bezier(0.2, 0, 0, 1)` | Bắt đầu và kết thúc trên màn hình |
| Standard Decelerate | `cubic-bezier(0, 0, 0, 1)` | Vào màn hình |
| Standard Accelerate | `cubic-bezier(0.3, 0, 1, 1)` | Rời màn hình |

#### Thang Duration

| Token | Giá trị | Cách dùng |
|-------|-------|-----|
| Short 1 | 50ms | Tương tác vi mô |
| Short 2 | 100ms | Transition nhỏ |
| Short 3 | 150ms | Transition nhỏ |
| Short 4 | 200ms | Transition rời |
| Medium 1 | 250ms | Transition trung bình |
| Medium 2 | 300ms | Transition chuẩn |
| Medium 3 | 350ms | Transition trung bình |
| Medium 4 | 400ms | Transition vào |
| Long 1 | 450ms | Transition lớn |
| Long 2 | 500ms | Transition lớn, nhấn mạnh |
| Long 3 | 550ms | Transition phức tạp |
| Long 4 | 600ms | Transition phức tạp |
| Extra Long 1 | 700ms | Transition trang |
| Extra Long 2 | 800ms | Transition trang |
| Extra Long 3 | 900ms | Transition trang phức tạp |
| Extra Long 4 | 1000ms | Transition trang phức tạp |

#### Kết hợp gợi ý

| Transition | Easing | Duration |
|-----------|--------|----------|
| Phần tử ở lại trên màn hình | Emphasized | 500ms |
| Phần tử vào màn hình | Emphasized Decelerate | 400ms |
| Phần tử rời hẳn | Emphasized Accelerate | 200ms |
| Phần tử rời tạm thời | Emphasized | 300ms |
| Transition tiện ích nhỏ | Standard | 300ms |

### Triển khai CSS

```css
:root {
  /* Easing */
  --md-sys-motion-easing-emphasized: cubic-bezier(0.2, 0, 0, 1);
  --md-sys-motion-easing-emphasized-decelerate: cubic-bezier(0.05, 0.7, 0.1, 1);
  --md-sys-motion-easing-emphasized-accelerate: cubic-bezier(0.3, 0, 0.8, 0.15);
  --md-sys-motion-easing-standard: cubic-bezier(0.2, 0, 0, 1);
  --md-sys-motion-easing-standard-decelerate: cubic-bezier(0, 0, 0, 1);
  --md-sys-motion-easing-standard-accelerate: cubic-bezier(0.3, 0, 1, 1);

  /* Duration */
  --md-sys-motion-duration-short1: 50ms;
  --md-sys-motion-duration-short2: 100ms;
  --md-sys-motion-duration-short3: 150ms;
  --md-sys-motion-duration-short4: 200ms;
  --md-sys-motion-duration-medium1: 250ms;
  --md-sys-motion-duration-medium2: 300ms;
  --md-sys-motion-duration-medium3: 350ms;
  --md-sys-motion-duration-medium4: 400ms;
  --md-sys-motion-duration-long1: 450ms;
  --md-sys-motion-duration-long2: 500ms;
  --md-sys-motion-duration-long3: 550ms;
  --md-sys-motion-duration-long4: 600ms;
  --md-sys-motion-duration-extra-long1: 700ms;
  --md-sys-motion-duration-extra-long2: 800ms;
  --md-sys-motion-duration-extra-long3: 900ms;
  --md-sys-motion-duration-extra-long4: 1000ms;
}

/* Example: dialog enter */
.md3-dialog-enter {
  animation: dialog-enter var(--md-sys-motion-duration-medium4)
             var(--md-sys-motion-easing-emphasized-decelerate);
}

@keyframes dialog-enter {
  from { opacity: 0; transform: scale(0.8); }
  to { opacity: 1; transform: scale(1); }
}

/* Example: fade out */
.md3-fade-out {
  animation: fade-out var(--md-sys-motion-duration-short4)
             var(--md-sys-motion-easing-emphasized-accelerate);
}

@keyframes fade-out {
  from { opacity: 1; }
  to { opacity: 0; }
}
```

