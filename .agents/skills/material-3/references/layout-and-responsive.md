# Layout và Thiết kế đáp ứng trong MD3

Tài liệu tham khảo về hệ thống layout của Material Design 3: breakpoint, canonical layout và triển khai đáp ứng.

## Jetpack Compose và Android

**Bảng breakpoint** bên dưới là tài liệu tham khảo về **thiết kế**. Trong **Jetpack Compose**, hãy ưu tiên **`calculateWindowSizeClass`** (`androidx.compose.material3:material3-window-size-class`) và/hoặc API **`androidx.compose.material3.adaptive`** (vd. `currentWindowAdaptiveInfo`, scaffold list-detail) thay vì tự viết kiểm tra chiều rộng `BoxWithConstraints` thô ở khắp nơi.

**Edge-to-edge:** Dùng **`enableEdgeToEdge()`** trên `Activity` (AndroidX) khi bạn vẽ phía sau thanh hệ thống. Áp dụng **`WindowInsets`** (`Modifier.statusBarsPadding()`, `navigationBarsPadding()`, **`imePadding()`**, `displayCutoutPadding()`, v.v.) và `contentWindowInsets` của **`Scaffold`** để nội dung và **IME** hoạt động đúng.

**Thiết bị gập:** Dùng **`WindowInfoTracker`**, **`FoldingFeature`**, hoặc API Jetpack WindowManager — xem phần foldable bên dưới; kiểm tra API theo phiên bản dependency của bạn.

---

## Google I/O 2026: Layout Expressive

[Bản cập nhật I/O 2026](https://m3.material.io/blog/whats-new-at-io26) của Material giới thiệu hướng dẫn layout expressive/adaptive rộng hơn:

- **Expressive layout scaffold**: Thiết kế màn hình để thích ứng trên mobile, desktop, thiết bị gập, đồng hồ, XR và các dạng hình không gian khác. Trong Compose, ưu tiên adaptive scaffold và window size class của Material3 thay vì nhánh phone/tablet hard-code.
- **Hệ thống khoảng cách 8dp**: Coi khoảng cách như token. Dùng thang 8dp cho margin, padding, gap và khoảng cách component để thay đổi mật độ và loại thiết bị được áp dụng nhất quán.
- **Hướng dẫn đồng hồ**: Dùng chuyển động dựa trên vật lý, kiểu văn bản cong và container sát cạnh. Tránh thu nhỏ layout phone lên màn hình đeo hình tròn hoặc compact.
- **Hướng dẫn XR**: Dùng panel không gian và elevation theo chiều sâu. Đừng coi XR chỉ là canvas desktop lớn hơn; hãy tính đến chiều sâu, sự thoải mái và vị trí panel.

### Mẫu Spacing Token

Định nghĩa khoảng cách một lần, rồi ánh xạ theo ngữ cảnh:

```kotlin
object MdSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}
```

Dùng spacing token cho:
- Margin màn hình
- Khoảng cách giữa pane
- Khoảng cách mục list
- Padding nội bộ card
- Nhóm component và toolbar

Đừng rải literal `Dp` thô khắp UI dùng lại. Chỉ giữ giá trị một-off ở phạm vi local khi thực sự đặc thù component.

---

## Window Size Class

MD3 định nghĩa 5 lớp breakpoint:

| Lớp | Phạm vi chiều rộng | Thiết bị điển hình | Cột |
|-------|-----------|----------------|---------|
| Compact | < 600dp | Phone portrait | 4 |
| Medium | 600–839dp | Tablet portrait, thiết bị gập | 8 |
| Expanded | 840–1199dp | Tablet landscape, desktop nhỏ | 12 |
| Large | 1200–1599dp | Desktop | 12 |
| Extra-large | 1600dp+ | Ultra-wide, desktop lớn | 12 |

### CSS Media Query (web)

Dùng những cái này cho **layout CSS**. Ứng dụng **Compose** nên dùng window size class / API adaptive thay vì chỉ nhân đôi logic này trong CSS.

```css
/* Compact (default — mobile-first) */
/* No media query needed, this is the base */

/* Medium */
@media (min-width: 600px) { }

/* Expanded */
@media (min-width: 840px) { }

/* Large */
@media (min-width: 1200px) { }

/* Extra-large */
@media (min-width: 1600px) { }
```

### Chuyển đổi dp sang px
Trên web, 1dp ≈ 1px ở mật độ chuẩn. Giá trị breakpoint chuyển trực tiếp sang CSS pixel.

## Cấu trúc Layout

### Thuật ngữ chính

- **Window**: Khu vực hiển thị của ứng dụng
- **Pane**: Container layout bên trong window. Pane có thể cố định, linh hoạt, nổi hoặc bán vĩnh viễn
- **Column**: Khối nội dung dọc bên trong pane
- **Margin**: Khoảng cách giữa cạnh màn hình và nội dung
- **Gutter**: Khoảng cách giữa các cột
- **Spacer**: Khoảng cách giữa các pane (trong layout đa pane)

### Giá trị Margin và Gutter

| Kích thước Window | Margin | Gutter |
|-------------|---------|---------|
| Compact | 16dp | 8dp |
| Medium | 24dp | 16dp |
| Expanded | 24dp | 16dp |
| Large | 24dp | 24dp |
| Extra-large | 24dp | 24dp |

## Canonical Layout

MD3 định nghĩa 3 canonical layout làm điểm khởi đầu. Luôn bắt đầu từ một trong số đó thay vì từ grid thô.

### Feed Layout

**Dùng khi**: Hiển thị tập hợp lớn các mục có thể duyệt (feed mạng xã hội, tin tức, lưới sản phẩm).

```
Compact:    Single column of cards
Medium:     2-column grid
Expanded:   3-column grid
Large:      4-column grid + optional side panel
```

```html
<div class="md3-feed">
  <div class="md3-feed__item">
    <!-- Card content -->
  </div>
  <div class="md3-feed__item">
    <!-- Card content -->
  </div>
  <!-- More items -->
</div>
```

```css
.md3-feed {
  display: grid;
  gap: 8px;
  padding: 16px;
  grid-template-columns: 1fr; /* Compact: 1 column */
}

@media (min-width: 600px) {
  .md3-feed {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
    padding: 24px;
  }
}

@media (min-width: 840px) {
  .md3-feed {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 1200px) {
  .md3-feed {
    grid-template-columns: repeat(4, 1fr);
    gap: 24px;
  }
}
```

### List-Detail Layout

**Dùng khi**: Duyệt danh sách mục trong đó mỗi mục có nội dung chi tiết (email, trình duyệt tệp, danh bạ).

```
Compact:    List view OR detail view (navigate between them)
Medium:     Side-by-side list (1/3) + detail (2/3)
Expanded:   Side-by-side with wider detail pane
```

```html
<div class="md3-list-detail">
  <aside class="md3-list-detail__list">
    <md-list>
      <md-list-item type="button" class="active">
        <div slot="headline">Item 1</div>
        <div slot="supporting-text">Description</div>
      </md-list-item>
      <md-list-item type="button">
        <div slot="headline">Item 2</div>
        <div slot="supporting-text">Description</div>
      </md-list-item>
    </md-list>
  </aside>
  <main class="md3-list-detail__detail">
    <h2>Item 1 Detail</h2>
    <p>Full content here...</p>
  </main>
</div>
```

```css
.md3-list-detail {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.md3-list-detail__list {
  background: var(--md-sys-color-surface-container);
  border-radius: var(--md-sys-shape-corner-large);
  overflow: auto;
}

.md3-list-detail__detail {
  flex: 1;
  padding: 24px;
}

/* Compact: show one at a time */
@media (max-width: 599px) {
  .md3-list-detail__detail { display: none; }
  .md3-list-detail--detail-active .md3-list-detail__list { display: none; }
  .md3-list-detail--detail-active .md3-list-detail__detail { display: block; }
}

/* Medium+: side by side */
@media (min-width: 600px) {
  .md3-list-detail {
    flex-direction: row;
    gap: 24px;
    padding: 24px;
  }
  .md3-list-detail__list {
    width: 360px;
    flex-shrink: 0;
  }
}

/* Expanded: wider detail */
@media (min-width: 840px) {
  .md3-list-detail__list {
    width: 400px;
  }
}
```

### Tay cầm kéo để đổi kích thước Pane

Trong layout list-detail và supporting pane, người dùng có thể đổi kích thước pane bằng tay cầm kéo:

```html
<div class="md3-list-detail">
  <aside class="md3-list-detail__list">...</aside>
  <div class="md3-drag-handle" role="separator" aria-orientation="vertical" tabindex="0"></div>
  <main class="md3-list-detail__detail">...</main>
</div>
```

```css
.md3-drag-handle {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  position: relative;
}
.md3-drag-handle::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 4px;
  height: 24px;
  background: var(--md-sys-color-outline);
  border-radius: 2px;
}
```

### Supporting Pane Layout

**Dùng khi**: Nội dung chính cần thông tin bổ trợ (tài liệu + bảng thuộc tính, video + bình luận).

```
Compact:    Stacked — primary on top, supporting below (or bottom sheet)
Medium:     Side-by-side (2/3 primary + 1/3 supporting)
Expanded:   Same but with more space
```

```html
<div class="md3-supporting-pane">
  <main class="md3-supporting-pane__primary">
    <!-- Primary content (2/3) -->
  </main>
  <aside class="md3-supporting-pane__secondary">
    <!-- Supporting content (1/3) -->
  </aside>
</div>
```

```css
.md3-supporting-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

/* Medium+: side by side */
@media (min-width: 600px) {
  .md3-supporting-pane {
    flex-direction: row;
    gap: 24px;
    padding: 24px;
  }
  .md3-supporting-pane__primary { flex: 2; }
  .md3-supporting-pane__secondary { flex: 1; }
}
```

## CSS Container Query

Với hành vi đáp ứng ở cấp component (độc lập với viewport), dùng container query:

```css
/* Define a container */
.md3-card-container {
  container-type: inline-size;
  container-name: card;
}

/* Respond to container width */
@container card (min-width: 400px) {
  .md3-card {
    flex-direction: row; /* Horizontal layout when container is wide */
  }
}

@container card (max-width: 399px) {
  .md3-card {
    flex-direction: column; /* Vertical layout when narrow */
  }
}
```

## Hành vi Component thích ứng

Component biến đổi qua các breakpoint:

| Component | Compact | Medium (gồm thiết bị gập mở) | Expanded+ / màn hình lớn |
|-----------|---------|--------|-----------|
| Điều hướng | Bottom bar | Side rail | Side drawer |
| App bar | Small (64dp) | Small (64dp) | Small hoặc Medium (112dp) |
| Dialog | Toàn màn hình | Dialog căn giữa | Dialog căn giữa (tối đa 560dp) |
| Bottom sheet | Chiều cao đầy | Chiều cao một phần | Side sheet |
| Search | Search view toàn màn hình | Search bar liên tục | Search bar liên tục |
| Card | Một cột full-width | Lưới nhiều cột | Lưới nhiều cột (tối đa 4 cột) |
| Pane nội dung | Một pane | Pane thứ hai tùy chọn | Hai hoặc ba pane |
| Phương thức nhập | Chỉ cảm ứng | Cảm ứng + bút | Cảm ứng + chuột/trackpad + bàn phím |

## Ví dụ Layout ứng dụng hoàn chỉnh

```html
<div class="md3-app-layout">
  <!-- Navigation (responsive — see navigation-patterns.md) -->
  <nav class="md3-nav" aria-label="Main navigation">
    <!-- Nav content varies by breakpoint -->
  </nav>

  <!-- Main area -->
  <div class="md3-main-area">
    <!-- Top app bar -->
    <header class="md3-top-app-bar">
      <h1 class="md3-top-app-bar__title">Dashboard</h1>
    </header>

    <!-- Content area with canonical layout -->
    <main class="md3-content-area">
      <!-- Use feed, list-detail, or supporting pane here -->
    </main>
  </div>
</div>
```

```css
.md3-app-layout {
  display: flex;
  min-height: 100vh;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}

.md3-main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0; /* Prevent flex overflow */
}

.md3-content-area {
  flex: 1;
  overflow-y: auto;
}

/* Compact: stack vertically */
@media (max-width: 599px) {
  .md3-app-layout { flex-direction: column; }
  .md3-content-area { padding: 16px; }
}

/* Medium */
@media (min-width: 600px) and (max-width: 839px) {
  .md3-content-area { padding: 24px; }
}

/* Expanded+ */
@media (min-width: 840px) {
  .md3-content-area { padding: 24px; }
}

/* Large+: constrain max content width */
@media (min-width: 1200px) {
  .md3-content-area {
    max-width: 1040px;
    margin: 0 auto;
    padding: 24px;
  }
}
```

## Thiết bị gập và màn hình lớn

MD3 cung cấp hướng dẫn cụ thể cho thiết bị gập, tablet và dạng hình màn hình lớn. Đây là mục tiêu hạng nhất trong Material Design 3 — không phải thứ thêm vào sau.

### Tư thế thiết bị gập

Thiết bị gập mang đến các tư thế không tồn tại trên phone truyền thống:

| Tư thế | Mô tả | Hành vi layout |
|---------|-------------|----------------|
| **Flat (unfolded)** | Thiết bị mở hoàn toàn, một màn hình lớn duy nhất | Coi là lớp window Medium hoặc Expanded tùy chiều rộng |
| **Half-opened (tabletop)** | Gập ~90° theo chiều ngang, nửa dưới đặt trên bàn | Chia nội dung tại đường gập — video/hình ảnh nửa trên, điều khiển/thông tin nửa dưới |
| **Half-opened (book)** | Gập ~90° theo chiều dọc, cầm như sách | Chia nội dung tại đường gập — list một bên, detail bên kia |
| **Folded** | Thiết bị đóng, màn hình ngoài/cover | Coi là Compact — chỉ hiện nội dung thiết yếu |

### Layout nhận biết đường gập (Hinge-Aware)

Đường gập/hinge là một ranh giới vật lý. Không bao giờ đặt nội dung tương tác hoặc thông tin quan trọng qua khu vực đường gập.

**Web — CSS Viewport Segments API:**

```css
/* Detect a dual-screen / foldable device with two horizontal segments */
@media (horizontal-viewport-segments: 2) {
  .md3-list-detail {
    flex-direction: row;
  }
  .md3-list-detail__list {
    /* Span the left segment */
    width: env(viewport-segment-width 0 0);
    margin-right: env(viewport-segment-left 1 0, 0px) - env(viewport-segment-right 0 0, 0px);
  }
  .md3-list-detail__detail {
    flex: 1;
  }
}

/* Detect tabletop posture (two vertical segments) */
@media (vertical-viewport-segments: 2) {
  .md3-media-player {
    display: flex;
    flex-direction: column;
  }
  .md3-media-player__video {
    height: env(viewport-segment-height 0 0);
  }
  .md3-media-player__controls {
    flex: 1;
  }
}
```

**Flutter — `MediaQuery` và display feature:**

```dart
Widget build(BuildContext context) {
  final displayFeatures = MediaQuery.of(context).displayFeatures;
  final hinge = displayFeatures.whereType<DisplayFeature>().where(
    (f) => f.type == DisplayFeatureType.hinge || f.type == DisplayFeatureType.fold,
  ).firstOrNull;

  if (hinge != null) {
    // Foldable device — split at the hinge
    return TwoPane(
      startPane: ListPane(),
      endPane: DetailPane(),
      paneProportion: 0.5,
      panePriority: isPortrait ? TwoPanePriority.start : TwoPanePriority.both,
    );
  }

  // Single screen — use window size class
  final width = MediaQuery.sizeOf(context).width;
  if (width < 600) return CompactLayout();
  if (width < 840) return MediumLayout();
  return ExpandedLayout();
}
```

**Jetpack Compose — `WindowInfoTracker` và `FoldingFeature`:**

```kotlin
@Composable
fun AdaptiveLayout() {
    val windowInfo = WindowInfoTracker.getOrCreate(LocalContext.current)
        .windowLayoutInfo(LocalContext.current as Activity)
        .collectAsState(initial = WindowLayoutInfo(emptyList()))

    val foldingFeature = windowInfo.value.displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull()

    when {
        foldingFeature != null && foldingFeature.state == FoldingFeature.State.HALF_OPENED -> {
            // Tabletop or book posture
            if (foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                TabletopLayout(foldingFeature)  // top: content, bottom: controls
            } else {
                BookLayout(foldingFeature)  // left: list, right: detail
            }
        }
        else -> {
            // Standard adaptive layout based on window size class
            val windowSizeClass = calculateWindowSizeClass(LocalContext.current as Activity)
            StandardAdaptiveLayout(windowSizeClass)
        }
    }
}
```

### Mẫu tư thế Tabletop

Khi thiết bị ở tư thế tabletop (gập ngang, nửa dưới tựa trên bề mặt), nội dung tự nhiên chia thành hai nửa:

```
┌─────────────────────┐
│                     │  ← Nửa trên: nội dung trực quan
│   Video / Image /   │     (camera viewfinder, video player,
│   Primary content   │      image gallery, map)
│                     │
├─ ─ ─ hinge ─ ─ ─ ─ ┤
│                     │  ← Nửa dưới: điều khiển & thông tin
│  Controls / Text /  │     (playback controls, chat input,
│  Supporting info    │      product details, toolbar)
│                     │
└─────────────────────┘
```

### Mẫu tư thế Book

Khi thiết bị ở tư thế book (gập dọc, cầm như sách), nó ánh xạ tự nhiên sang list-detail:

```
┌──────────┬──────────┐
│          │          │
│  List /  │  Detail  │
│  Nav /   │  Content │
│  Browse  │  / Edit  │
│          │          │
└──────────┴──────────┘
         hinge
```

### Hướng dẫn layout màn hình lớn

Với tablet, Chromebook, desktop và thiết bị gập lớn (Expanded, Large, Extra-large):

**Ràng buộc chiều rộng nội dung:**
- Đừng kéo nội dung căng đầy màn hình siêu rộng — dòng đọc dài hơn ~80 ký tự trở nên khó theo dõi
- Giới hạn nội dung thân ở chiều rộng tối đa (thường 840–1040dp) và căn giữa
- Dùng không gian thừa cho layout đa pane, không phải cột đơn rộng hơn

```css
/* Constrain content on large screens */
@media (min-width: 1200px) {
  .md3-content-area {
    max-width: 1040px;
    margin-inline: auto;
  }
}
```

**Chiến lược đa pane theo lớp window:**

| Lớp window | Cột | Layout khuyến nghị |
|-------------|---------|-------------------|
| Compact (<600dp) | 4 | Một pane. Điều hướng toàn màn hình giữa các view. |
| Medium (600–839dp) | 8 | Pane thứ hai tùy chọn. List-detail với list hẹp. Điều hướng rail. |
| Expanded (840–1199dp) | 12 | Hai pane là chuẩn. List-detail hoặc supporting pane. Điều hướng drawer. |
| Large (1200–1599dp) | 12 | Hai hoặc ba pane. Feed kèm panel bên. Supporting pane liên tục. |
| Extra-large (1600dp+) | 12 | Ba pane hoặc hai pane bị ràng buộc với margin rộng rãi. |

**Khác biệt về nhập và tương tác:**
- Màn hình lớn thường có chuột/trackpad — trạng thái hover và menu chuột phải quan trọng
- Vùng chạm vẫn tối thiểu 48dp nhưng có thể bổ sung tooltip khi hover
- Phím tắt bàn phím trở thành kỳ vọng trên thiết bị hạng desktop
- Kéo-thả tự nhiên hơn trên màn hình lớn

```css
/* Add hover states for pointer devices */
@media (hover: hover) {
  .md3-card:hover {
    background: color-mix(
      in srgb,
      var(--md-sys-color-on-surface) 8%,
      var(--md-sys-color-surface)
    );
  }

  .md3-list-item:hover {
    background: color-mix(
      in srgb,
      var(--md-sys-color-on-surface) 8%,
      transparent
    );
  }
}

/* Ensure pointer-specific affordances */
@media (pointer: fine) {
  /* Scrollbars, resize handles, tighter spacing are acceptable */
  .md3-drag-handle { cursor: col-resize; }
}
```

**Flutter — nhập thích ứng:**

```dart
Widget build(BuildContext context) {
  final width = MediaQuery.sizeOf(context).width;
  final isLargeScreen = width >= 840;

  return Scaffold(
    body: Row(
      children: [
        // Navigation adapts
        if (isLargeScreen)
          NavigationRail(
            destinations: destinations,
            selectedIndex: selectedIndex,
            onDestinationSelected: onSelected,
            labelType: NavigationRailLabelType.all,
            leading: FloatingActionButton(
              onPressed: onCompose,
              child: const Icon(Icons.edit),
            ),
          ),
        // Content fills remaining space
        Expanded(
          child: isLargeScreen
              ? Row(
                  children: [
                    SizedBox(width: 360, child: ListPane()),
                    const VerticalDivider(width: 1),
                    Expanded(child: DetailPane()),
                  ],
                )
              : selectedItem == null
                  ? ListPane()
                  : DetailPane(),
        ),
      ],
    ),
    bottomNavigationBar: isLargeScreen
        ? null
        : NavigationBar(
            destinations: destinations.map((d) =>
              NavigationDestination(icon: d.icon, label: d.label)).toList(),
            selectedIndex: selectedIndex,
            onDestinationSelected: onSelected,
          ),
  );
}
```

### Canonical Layout nhận biết thiết bị gập

Ba canonical layout thích ứng tự nhiên với thiết bị gập:

| Layout | Hành vi trên thiết bị gập |
|--------|------------------|
| **Feed** | Mở: lưới nhiều cột lấp đầy cả hai nửa. Tabletop: lưới trên, xem trước mục đã chọn ở dưới. |
| **List-detail** | Tư thế book: list nửa trái, detail nửa phải — khớp hoàn hảo tự nhiên. Tabletop: list trên, detail dưới. |
| **Supporting pane** | Tư thế book: primary bên trái, supporting bên phải. Tabletop: primary trên, điều khiển supporting dưới. |

### Kiểm tra màn hình lớn và thiết bị gập

**Web:**
- Dùng chế độ responsive của Chrome DevTools để kiểm tra ở breakpoint 600, 840, 1200 và 1600px
- Kiểm tra với media query pointer: coarse (cảm ứng) và pointer: fine (chuột)
- Xác minh nội dung không căng vượt quá độ dài dòng dễ đọc ở 1600px+

**Flutter:**
- Dùng gói `DevicePreview` để mô phỏng thiết bị gập và tablet
- Kiểm tra với override `MediaQuery` cho `displayFeatures`
- Chạy trên emulator Android: Pixel Fold, foldable 7.6", tablet 10", Chromebook

**Compose:**
- Dùng emulator thiết bị gập của Android Studio (Pixel Fold, 7.6" Foldable)
- Kiểm tra thay đổi tư thế: flat → half-opened → folded
- Dùng `WindowInfoTracker` để xác minh chuyển đổi layout nhận biết gập

### Danh sách kiểm tra khi audit hỗ trợ thiết bị gập/màn hình lớn

Khi kiểm tra, hãy xem các mục cụ thể sau:

- [ ] Ứng dụng dùng `MediaQuery.sizeOf(context).width` hoặc tương đương để xác định lớp window size
- [ ] Layout chuyển từ một pane sang đa pane ở 600dp
- [ ] Điều hướng biến đổi: bottom bar → rail → drawer qua các breakpoint
- [ ] Nội dung có ràng buộc max-width trên màn hình lớn (không căng đầy)
- [ ] Không có nội dung quan trọng hoặc phần tử tương tác đặt qua đường gập/hinge
- [ ] Xử lý tư thế thiết bị gập (nếu nhắm tới thiết bị gập): chế độ tabletop và book
- [ ] Có trạng thái hover cho thiết bị con trỏ (`@media (hover: hover)`)
- [ ] Vùng chạm vẫn tối thiểu 48dp ngay cả trên màn hình lớn
- [ ] Dialog được căn giữa (không toàn màn hình) trên màn hình medium+
- [ ] Bottom sheet chuyển thành side sheet trên màn hình expanded+

## Hệ thống khoảng cách

MD3 dùng grid cơ sở 4dp cho khoảng cách:

| Cách dùng | Giá trị |
|-----|--------|
| Padding nội bộ component | 4, 8, 12, 16, 24dp |
| Giữa các component | 8, 12, 16, 24dp |
| Khoảng cách phần | 24, 32, 48dp |
| Margin layout | 16dp (compact), 24dp (medium+) |
| Gutter grid | 8dp (compact), 16dp (medium), 24dp (large+) |

Luôn dùng bội của 4dp để có nhịp không gian nhất quán.


