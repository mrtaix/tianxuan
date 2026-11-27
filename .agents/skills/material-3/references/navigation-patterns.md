# Mẫu điều hướng MD3

Hướng dẫn lựa chọn và triển khai component điều hướng của Material Design 3.

## Jetpack Compose (chính)

Dùng **`androidx.compose.material3`**: `NavigationBar`, `NavigationRail`, `NavigationDrawerItem`, `ModalNavigationDrawer`, `DismissibleNavigationDrawer`, `PermanentNavigationDrawer`, `NavigationBarItem`, `NavigationRailItem`, top app bar (`TopAppBar`, `CenterAlignedTopAppBar`, `LargeTopAppBar`, biến thể expressive theo BOM), và **`Scaffold`** (`bottomBar`, `floatingActionButton`, `snackbarHost`).

Kết nối các đích đến với **Navigation Compose** (`NavHost`, `composable`, `rememberNavController`). Với UI **adaptive**, dùng **`calculateWindowSizeClass`**, **`androidx.compose.material3.adaptive`**, hoặc **`currentWindowAdaptiveInfo`** / **`NavigableListDetailPaneScaffold`** (tên và package tùy BOM của bạn — kiểm tra [Android Developers](https://developer.android.com/jetpack/androidx/releases/compose-material3)).

[Bản cập nhật I/O 2026](https://m3.material.io/blog/whats-new-at-io26) của Material nhấn mạnh expressive/adaptive:

- Ưu tiên scaffold expressive/adaptive cho mobile, desktop, thiết bị gập, đồng hồ và XR thay vì phóng to một mô hình điều hướng phone duy nhất.
- Search và search app bar expressive có phong cách thị giác, chuyển động làm mới và hành vi icon cuối linh hoạt hơn. Dùng API Compose Material3 hiện tại khi có; chỉ dùng xấp xỉ web/CSS khi nhắm tới web.
- Giữ khoảng cách điều hướng trên hệ thống 8dp để khoảng cách rail/drawer/app-bar có thể thích ứng theo loại thiết bị và mật độ.

```kotlin
// Conceptual — adapt routes and selection to your app
Scaffold(
    bottomBar = {
        NavigationBar {
            destinations.forEach { dest ->
                NavigationBarItem(
                    selected = currentRoute == dest.route,
                    onClick = { navController.navigate(dest.route) },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) }
                )
            }
        }
    }
) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.padding(innerPadding)
    ) { /* composable routes */ }
}
```

**Web (hạn chế):** Các phần HTML/`@material/web` bên dưới vẫn hữu ích cho site dựa trên token; [Material Web chỉ ở chế độ maintenance](https://m3.material.io/develop/web).

---

## Lựa chọn component điều hướng

### Cây quyết định

```
How many primary destinations?
├── 2 destinations → Tabs (primary)
├── 3–5 destinations
│   ├── Compact screen (<600dp) → Navigation Bar (bottom)
│   ├── Medium screen (600–839dp) → Navigation Rail (side)
│   └── Expanded+ screen (840dp+) → Navigation Drawer (side) or Rail
├── 6+ destinations
│   ├── Compact → Navigation Drawer (modal)
│   ├── Medium → Navigation Drawer (standard) or Rail + overflow menu
│   └── Expanded+ → Navigation Drawer (standard)
└── Hierarchical (nested sections)
    └── Navigation Drawer with sections
```

### Tham chiếu nhanh

| Component | Số đích đến | Kích thước màn hình | Tính liên tục | Vị trí |
|-----------|-------------|-------------|-------------|----------|
| Navigation Bar | 3–5 | Compact | Liên tục | Dưới cùng |
| Navigation Rail | 3–7 | Medium | Liên tục | Bên (đầu) |
| Navigation Drawer | Không giới hạn | Expanded+ | Standard hoặc Modal | Bên (đầu) |
| Tabs | 2+ view liên quan | Mọi kích thước | Liên tục | Trên (dưới app bar) |
| Bottom App Bar | — (hành động ngữ cảnh) | Compact | Liên tục | Dưới cùng |

## Navigation Bar

**Dùng khi**: 3–5 đích đến chính trên màn hình compact (mobile).
**Vị trí**: Đáy màn hình, luôn hiển thị.

### Cấu trúc
- Cố định ở đáy, full width
- 3–5 mục điều hướng với icon + nhãn
- Mục active hiện icon filled + thanh indicator dạng pill
- Chiều cao: 80dp

### Triển khai

```html
<md-navigation-bar active-index="0">
  <md-navigation-tab label="Home" active-icon="home" inactive-icon="home">
    <md-icon slot="active-icon">home</md-icon>
    <md-icon slot="inactive-icon">home</md-icon>
  </md-navigation-tab>
  <md-navigation-tab label="Search">
    <md-icon slot="active-icon">search</md-icon>
    <md-icon slot="inactive-icon">search</md-icon>
  </md-navigation-tab>
  <md-navigation-tab label="Notifications">
    <md-icon slot="active-icon">notifications</md-icon>
    <md-icon slot="inactive-icon">notifications</md-icon>
  </md-navigation-tab>
  <md-navigation-tab label="Profile">
    <md-icon slot="active-icon">person</md-icon>
    <md-icon slot="inactive-icon">person</md-icon>
  </md-navigation-tab>
</md-navigation-bar>
```

### Tạo kiểu

```css
md-navigation-bar {
  --md-navigation-bar-container-color: var(--md-sys-color-surface-container);
}
```

### Nguyên tắc
- Luôn hiện nhãn (đừng dùng chỉ icon)
- Dùng icon filled cho trạng thái active, outlined cho inactive
- Không dùng cho ít hơn 3 hoặc nhiều hơn 5 đích đến
- Ẩn khi cuộn xuống trên màn hình nhiều nội dung (tùy chọn)
- Elevation cấp 2 (3dp)

## Navigation Rail

**Dùng khi**: 3–7 đích đến chính trên màn hình trung bình (tablet).
**Vị trí**: Cạnh đầu (trái trong LTR), luôn hiển thị.

### Cấu trúc
- Chiều rộng: 80dp
- FAB tùy chọn ở trên cùng
- Mục điều hướng xếp dọc
- Mục active hiện thanh indicator dạng pill

### Triển khai

```html
<nav class="md3-nav-rail" aria-label="Main navigation">
  <!-- Optional FAB -->
  <md-fab size="small" variant="tertiary" aria-label="Compose">
    <md-icon slot="icon">edit</md-icon>
  </md-fab>

  <div class="md3-nav-rail__items" role="tablist">
    <a href="/" class="md3-nav-rail__item" role="tab" aria-selected="true" aria-current="page">
      <div class="md3-nav-rail__indicator">
        <md-icon>home</md-icon>
      </div>
      <span class="md3-nav-rail__label">Home</span>
    </a>
    <a href="/search" class="md3-nav-rail__item" role="tab" aria-selected="false">
      <div class="md3-nav-rail__indicator">
        <md-icon>search</md-icon>
      </div>
      <span class="md3-nav-rail__label">Search</span>
    </a>
    <a href="/settings" class="md3-nav-rail__item" role="tab" aria-selected="false">
      <div class="md3-nav-rail__indicator">
        <md-icon>settings</md-icon>
      </div>
      <span class="md3-nav-rail__label">Settings</span>
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
  border-right: 1px solid var(--md-sys-color-outline-variant);
}

.md3-nav-rail__items {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.md3-nav-rail__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: var(--md-sys-color-on-surface-variant);
  font: var(--md-sys-typescale-label-medium);
  cursor: pointer;
  width: 56px;
}

.md3-nav-rail__indicator {
  width: 56px;
  height: 32px;
  display: flex;

  align-items: center;
  justify-content: center;
  border-radius: var(--md-sys-shape-corner-full);
}

.md3-nav-rail__item[aria-selected="true"] .md3-nav-rail__indicator {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.md3-nav-rail__item[aria-selected="true"] {
  color: var(--md-sys-color-on-surface);
}
```

### Nguyên tắc
- Căn mục lên trên (beneath optional FAB)
- Luôn hiện nhãn (có thể ẩn, nhưng nên hiện)
- FAB ở trên cùng là tùy chọn nhưng thường gặp
- Elevation cấp 0

## Navigation Drawer

**Dùng khi**: Nhiều đích đến, màn hình mở rộng, hoặc phân cấp sâu.
**Vị trí**: Cạnh đầu, standard (liên tục) hoặc modal (lớp phủ).

### Standard Drawer (liên tục)

Luôn hiển thị cùng nội dung. Chiều rộng: 360dp.

```html
<div class="md3-layout">
  <md-navigation-drawer opened>
    <div slot="headline">App Name</div>
    <md-list>
      <md-list-item type="button" active>
        <md-icon slot="start">inbox</md-icon>
        <div slot="headline">Inbox</div>
        <div slot="trailing-supporting-text">24</div>
      </md-list-item>
      <md-list-item type="button">
        <md-icon slot="start">send</md-icon>
        <div slot="headline">Sent</div>
      </md-list-item>
      <md-divider></md-divider>
      <md-list-item type="button">
        <md-icon slot="start">drafts</md-icon>
        <div slot="headline">Drafts</div>
      </md-list-item>
    </md-list>
  </md-navigation-drawer>
  <main class="md3-content">
    <!-- Page content -->
  </main>
</div>
```

### Modal Drawer (lớp phủ)

Phủ lên nội dung kèm scrim. Dùng trên màn hình nhỏ hơn hoặc khi không gian nội dung hạn chế.

```html
<md-navigation-drawer type="modal" id="nav-drawer">
  <!-- Same content as standard -->
</md-navigation-drawer>

<script>
  // Toggle drawer
  document.getElementById('menu-btn').addEventListener('click', () => {
    const drawer = document.getElementById('nav-drawer');
    drawer.opened = !drawer.opened;
  });
</script>
```

### Nguyên tắc
- Standard drawer dùng nền `surface-container`
- Modal drawer có elevation cấp 1 và lớp scrim
- Nhóm đích đến bằng divider và tiêu đề phần
- Mục active dùng nền `secondary-container`
- Shape: `large` ở góc cuối (cạnh phải trong LTR)

## Top App Bar

**Dùng khi**: Mọi màn hình cần tiêu đề và hành động tùy chọn.

**Ghi chú I/O 2026:** Search app bar là một phần của hướng dẫn search expressive hiện tại. Trong Compose, kiểm tra BOM Material3 của bạn để xem có API app bar và search expressive không trước khi tự xây. Với web, kết hợp top app bar dựa trên token với trường/view tìm kiếm tùy chỉnh vì Material Web không cung cấp parity search expressive đầy đủ.

### Biến thể

| Biến thể | Tiêu đề | Chiều cao | Hành vi cuộn |
|---------|-------|--------|----------------|
| Center-aligned | Giữa | 64dp | Nâng lên cấp 2 khi cuộn |
| Small | Căn đầu | 64dp | Nâng lên cấp 2 khi cuộn |
| Medium | Dưới, căn đầu | 112dp | Thu về 64dp khi cuộn |
| Large | Dưới, căn đầu | 152dp | Thu về 64dp khi cuộn |

### Triển khai

```html
<!-- Small app bar -->
<header class="md3-top-app-bar">
  <md-icon-button aria-label="Open menu">
    <md-icon>menu</md-icon>
  </md-icon-button>
  <h1 class="md3-top-app-bar__title">Page Title</h1>
  <md-icon-button aria-label="Search">
    <md-icon>search</md-icon>
  </md-icon-button>
  <md-icon-button aria-label="More options">
    <md-icon>more_vert</md-icon>
  </md-icon-button>
</header>

<!-- Medium app bar (collapsed state shown; expand on scroll-to-top) -->
<header class="md3-top-app-bar md3-top-app-bar--medium">
  <div class="md3-top-app-bar__row">
    <md-icon-button aria-label="Back"><md-icon>arrow_back</md-icon></md-icon-button>
    <span class="md3-top-app-bar__title-collapsed"></span>
    <md-icon-button aria-label="More"><md-icon>more_vert</md-icon></md-icon-button>
  </div>
  <div class="md3-top-app-bar__expanded-title">
    <h1>Page Title</h1>
  </div>
</header>
```

```css
.md3-top-app-bar {
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 4px;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}

.md3-top-app-bar__title {
  flex: 1;
  padding: 0 12px;
  font: var(--md-sys-typescale-title-large);
  margin: 0;
}

/* Scrolled state: elevate */
.md3-top-app-bar--scrolled {
  background: var(--md-sys-color-surface-container);
}
```

### Hành vi cuộn

```javascript
// Elevate app bar on scroll
const appBar = document.querySelector('.md3-top-app-bar');
window.addEventListener('scroll', () => {
  appBar.classList.toggle('md3-top-app-bar--scrolled', window.scrollY > 0);
});
```

## Tabs

**Dùng khi**: Chuyển đổi giữa nội dung liên quan cùng cấp phân cấp.

### Primary vs Secondary

- **Primary tab**: Chuyển nội dung cấp cao nhất (Flights / Hotels / Explore)
- **Secondary tab**: Phần phụ trong nội dung primary

```html
<!-- Primary tabs -->
<md-tabs>
  <md-primary-tab active>
    <md-icon slot="icon">flight</md-icon>
    Flights
  </md-primary-tab>
  <md-primary-tab>Hotels</md-primary-tab>
  <md-primary-tab>Car Rental</md-primary-tab>
</md-tabs>

<!-- Secondary tabs (nested under primary) -->
<md-tabs>
  <md-secondary-tab active>Overview</md-secondary-tab>
  <md-secondary-tab>Reviews</md-secondary-tab>
  <md-secondary-tab>Photos</md-secondary-tab>
</md-tabs>
```

### Kết nối Tab + Panel

```html
<md-tabs id="my-tabs">
  <md-primary-tab id="tab-1" aria-controls="panel-1" active>Tab 1</md-primary-tab>
  <md-primary-tab id="tab-2" aria-controls="panel-2">Tab 2</md-primary-tab>
</md-tabs>

<div id="panel-1" role="tabpanel" aria-labelledby="tab-1">
  Panel 1 content
</div>
<div id="panel-2" role="tabpanel" aria-labelledby="tab-2" hidden>

  Panel 2 content
</div>

<script>
  document.getElementById('my-tabs').addEventListener('change', (e) => {
    // Hide all panels
    document.querySelectorAll('[role="tabpanel"]').forEach(p => p.hidden = true);
    // Show selected panel
    const activeTab = e.target.querySelector('[active]');
    const panelId = activeTab.getAttribute('aria-controls');
    document.getElementById(panelId).hidden = false;
  });
</script>
```

## Mẫu điều hướng đáp ứng (Responsive)

Mẫu MD3 then chốt: component điều hướng biến đổi qua các breakpoint.

### Mobile → Tablet → Desktop

```
Compact (<600dp):   Navigation Bar (bottom)
Medium (600–839dp): Navigation Rail (side)
Expanded (840dp+):  Navigation Drawer (side, standard)
```

### Triển khai CSS

```css
/* Hide all nav variants by default */
.md3-nav-bar,
.md3-nav-rail,
.md3-nav-drawer { display: none; }

/* Compact: show bottom navigation bar */
@media (max-width: 599px) {
  .md3-nav-bar { display: flex; }
  .md3-app { flex-direction: column; }
}

/* Medium: show navigation rail */
@media (min-width: 600px) and (max-width: 839px) {
  .md3-nav-rail { display: flex; }
  .md3-app { flex-direction: row; }
}

/* Expanded+: show navigation drawer */
@media (min-width: 840px) {
  .md3-nav-drawer { display: flex; }
  .md3-app { flex-direction: row; }
}
```

### Shell đáp ứng hoàn chỉnh

```html
<div class="md3-app">
  <!-- Navigation drawer (expanded+) -->
  <aside class="md3-nav-drawer">
    <md-navigation-drawer opened>
      <div slot="headline">My App</div>
      <md-list>
        <md-list-item type="button" active>
          <md-icon slot="start">home</md-icon>Home
        </md-list-item>
        <md-list-item type="button">
          <md-icon slot="start">search</md-icon>Search
        </md-list-item>
        <md-list-item type="button">
          <md-icon slot="start">settings</md-icon>Settings
        </md-list-item>
      </md-list>
    </md-navigation-drawer>
  </aside>

  <!-- Navigation rail (medium) -->
  <nav class="md3-nav-rail" aria-label="Main">
    <md-fab size="small" aria-label="New"><md-icon slot="icon">add</md-icon></md-fab>
    <a class="md3-nav-rail__item active"><md-icon>home</md-icon><span>Home</span></a>
    <a class="md3-nav-rail__item"><md-icon>search</md-icon><span>Search</span></a>
    <a class="md3-nav-rail__item"><md-icon>settings</md-icon><span>Settings</span></a>
  </nav>

  <!-- Main content -->
  <main class="md3-main">
    <header class="md3-top-app-bar">
      <md-icon-button class="md3-menu-btn" aria-label="Menu"><md-icon>menu</md-icon></md-icon-button>
      <h1 class="md3-top-app-bar__title">Home</h1>
    </header>
    <div class="md3-body">
      <!-- Page content -->
    </div>
  </main>

  <!-- Navigation bar (compact) -->
  <md-navigation-bar class="md3-nav-bar">
    <md-navigation-tab label="Home" active>
      <md-icon slot="active-icon">home</md-icon>
      <md-icon slot="inactive-icon">home</md-icon>
    </md-navigation-tab>
    <md-navigation-tab label="Search">
      <md-icon slot="active-icon">search</md-icon>
      <md-icon slot="inactive-icon">search</md-icon>
    </md-navigation-tab>
    <md-navigation-tab label="Settings">
      <md-icon slot="active-icon">settings</md-icon>
      <md-icon slot="inactive-icon">settings</md-icon>
    </md-navigation-tab>
  </md-navigation-bar>
</div>
```

```css
.md3-app {
  display: flex;
  min-height: 100vh;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}

.md3-main { flex: 1; display: flex; flex-direction: column; }
.md3-body { flex: 1; padding: 16px; }

/* Compact */
@media (max-width: 599px) {
  .md3-app { flex-direction: column; }
  .md3-nav-rail, .md3-nav-drawer { display: none; }
  .md3-nav-bar { display: flex; order: 1; }
  .md3-menu-btn { display: none; }
}

/* Medium */
@media (min-width: 600px) and (max-width: 839px) {
  .md3-nav-bar, .md3-nav-drawer { display: none; }
  .md3-nav-rail { display: flex; }
  .md3-menu-btn { display: none; }
}

/* Expanded+ */
@media (min-width: 840px) {
  .md3-nav-bar, .md3-nav-rail { display: none; }
  .md3-nav-drawer { display: flex; }
  .md3-menu-btn { display: none; }
}
```


