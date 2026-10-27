# Tài liệu học Frontend — App "Kế hoạch"

> **Dành cho:** người mới học Android + Jetpack Compose.
> **Mục tiêu:** sau khi đọc, bạn tự trả lời được: *"Khi mình chạm vào một nút, chuyện gì xảy ra từ ngón tay đến khi hình trên màn hình đổi?"* và *"Mỗi hình trên màn hình được vẽ bởi đoạn code nào?"*
> **Không bàn sâu:** phần dữ liệu (database, repository). Chúng chỉ xuất hiện như một "hộp đen" biết lưu và biết báo khi dữ liệu đổi.

Mọi đường dẫn file trong tài liệu tính từ thư mục gốc code:
`app/src/main/java/com/example/notes/` (viết tắt là **`…/notes/`**).

---

## Mục lục

1. [Bức tranh toàn cảnh](#1-bức-tranh-toàn-cảnh)
2. [Hành trình khởi động: từ chạm icon đến thấy màn hình đầu tiên](#2-hành-trình-khởi-động)
3. [Compose trong 10 phút (bằng ví dụ của chính dự án)](#3-compose-trong-10-phút)
4. [Khung ứng dụng: thanh tab, chuyển màn hình, thông báo](#4-khung-ứng-dụng)
5. [Các luồng thao tác từ A đến Z](#5-các-luồng-thao-tác-từ-a-đến-z) ← **phần quan trọng nhất**
6. [Hình trên màn hình được vẽ như thế nào](#6-hình-trên-màn-hình-được-vẽ-như-thế-nào)
7. [Giao diện không đổi? Cách tự gỡ rối](#7-giao-diện-không-đổi-cách-tự-gỡ-rối)
8. [Bài tập thực hành](#8-bài-tập-thực-hành)
9. [Lộ trình đọc code và bản đồ thư mục](#9-lộ-trình-đọc-code-và-bản-đồ-thư-mục)
10. [Từ điển](#10-từ-điển)

---

## 1. Bức tranh toàn cảnh

### Một câu để nhớ

> **Giao diện = hàm của trạng thái.** Bạn không bao giờ "sửa" một nút hay một dòng chữ trên màn hình.
> Bạn chỉ **đổi dữ liệu**, rồi Compose **tự vẽ lại** phần nào dùng dữ liệu đó.

### Vòng lặp của cả ứng dụng

```
   ┌─────────────────────────────────────────────────────────────────────┐
   │                                                                     │
   │   NGƯỜI DÙNG                                                        │
   │   chạm / gõ / vuốt / kéo                                            │
   │        │                                                            │
   │        ▼  (1) báo "người dùng vừa làm X"                            │
   │   SCREEN  ──────────────────────►  VIEWMODEL                        │
   │   (hàm @Composable,                (giữ trạng thái màn hình,        │
   │    CHỈ vẽ + báo sự kiện)            nhận sự kiện, xử lý)            │
   │        ▲                                │                           │
   │        │ (4) trạng thái mới             │ (2) nhờ ghi / sửa dữ liệu │
   │        │     → tự vẽ lại                ▼                           │
   │        │                         ┌───────────────┐                  │
   │        └──── StateFlow ◄─────────│   HỘP ĐEN     │                  │
   │                                  │ Repository +  │                  │
   │                  (3) dữ liệu đổi │ Room (CSDL)   │                  │
   │                      → tự phát ──┤               │                  │
   │                        giá trị   └───────────────┘                  │
   │                        mới                                          │
   └─────────────────────────────────────────────────────────────────────┘
```

Đọc theo số:

1. **Screen báo sự kiện** lên ViewModel (ví dụ `viewModel.toggleTask(id)`). Screen *không tự sửa dữ liệu*.
2. **ViewModel** nhờ hộp đen lưu thay đổi.
3. Hộp đen lưu xong thì **tự phát ra dữ liệu mới** (cơ chế `Flow`: như một vòi nước luôn chảy dữ liệu mới nhất).
4. ViewModel gói dữ liệu thành **trạng thái** (`StateFlow`). Screen đang "nhìn" trạng thái này nên **tự vẽ lại**.

**Quy tắc vàng:** *Sự kiện đi lên, trạng thái đi xuống.* (Tiếng Anh: "events up, state down".)

### Hai loại trạng thái — phân biệt từ đầu

| | Trạng thái của **ViewModel** | Trạng thái **cục bộ** (`remember`) |
|---|---|---|
| Là gì | Dữ liệu thật của màn hình: danh sách việc, nội dung form | Chuyện thuần giao diện: menu có đang mở không, chữ đang gõ dở |
| Ví dụ trong code | `TasksState.tasks`, `TaskEditorViewModel.form` | `var menuOpen by remember { mutableStateOf(false) }` |
| Khi **xoay màn hình** | **Còn nguyên** | **Mất** (về giá trị ban đầu) |
| Ai được sửa | Chỉ ViewModel | Chính composable đó |

Cách chọn: *"Nếu mất giá trị này khi xoay màn hình thì có sao không?"* Có → để ViewModel. Không → `remember`.

---

## 2. Hành trình khởi động

Chuyện xảy ra từ lúc bạn chạm icon app đến khi thấy màn **Hôm nay**:

```
 (1) Android đọc  AndroidManifest.xml
         │   "app này có lớp Application tên NotesApplication
         │    và Activity mở đầu tên MainActivity"
         ▼
 (2) NotesApplication ra đời            …/notes/NotesApplication.kt
         │   giữ sẵn: các repository (lazy – dùng tới mới tạo)
         │            + UiMessenger (hộp thư thông báo)
         ▼
 (3) MainActivity.onCreate()            …/notes/MainActivity.kt
         │   enableEdgeToEdge()          → cho phép vẽ tràn dưới thanh trạng thái
         │   setContent { … }            → "từ đây giao diện do Compose vẽ"
         ▼
 (4) NotesTheme { … }                   …/notes/ui/theme/Theme.kt
         │   gắn màu + kiểu chữ cho MỌI thứ bên trong
         ▼
 (5) NotesApp(messenger)                …/notes/ui/nav/NotesApp.kt
         │   Scaffold (khung) + thanh tab + snackbar + NavHost
         ▼
 (6) NavHost mở route đầu tiên "today"
         ▼
 (7) TodayScreen(...)                   …/notes/ui/today/TodayScreen.kt
         │   tạo TodayViewModel bằng appViewModel { TodayViewModel(it) }
         │   đọc: val state by viewModel.state.collectAsStateWithLifecycle()
         ▼
 (8) Vẽ màn hình từ `state`
```

### Cây các composable lồng nhau (như búp bê Nga)

```
NotesTheme
└─ NotesApp
   └─ Scaffold ──────────────────────────────────────────────┐
      ├─ bottomBar:    BottomTabs  (5 tab)                    │ khung chung
      ├─ snackbarHost: SnackbarHost (thông báo "Đã xoá…")     │ của cả app
      └─ NavHost                                              ┘
         └─ composable("today") ─ TodayScreen
            ├─ Scaffold riêng (có nút nổi "Thêm nhanh")
            └─ LazyColumn  (danh sách cuộn)
               ├─ HeaderCard           (thẻ chào + vòng tiến độ)
               ├─ suggestionSection    ("Nên làm tiếp")
               ├─ timelineSection      (sáng / chiều / tối)
               ├─ todoSection, notesSection, overdueSection, upcomingSection
```

### Một chi tiết hay: khung hình đầu tiên luôn là "trạng thái ban đầu"

`TodayViewModel.state` được khai báo với giá trị khởi đầu `TodayState()` (chưa có dữ liệu, `overview = null`).
Dữ liệu thật từ CSDL đến sau vài chục mili-giây. Vì vậy:

- Khung hình **đầu tiên** vẽ trạng thái trống: *"Hôm nay chưa có việc nào"*.
- Khi dữ liệu đến → `state` đổi → màn hình **tự vẽ lại** với dữ liệu thật.

Đó là lý do bạn có thể thấy nháy một khung trống rất ngắn khi mở app. Các màn khác xử lý tương tự bằng cờ `loaded` (ví dụ `TasksState.loaded`) để phân biệt *"đang tải"* với *"thật sự trống"*.

---

## 3. Compose trong 10 phút

Phần này giải thích các khái niệm bằng đoạn code **thật** của dự án.

### 3.1. `@Composable` = hàm "vẽ" một mảnh giao diện

Gọi hàm = đặt mảnh đó lên màn hình. Ví dụ nhãn nhỏ bo tròn `Pill` (`…/ui/components/Common.kt`):

```kotlin
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))               // bo tròn
            .background(color.copy(alpha = 0.14f))      // nền = màu chính nhưng mờ
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color)
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
```

Dùng: `Pill("Học tập", Color.Blue)`. Hàm **nhận dữ liệu qua tham số**, **không tự giữ dữ liệu**. Cùng đầu vào thì ra cùng hình.

### 3.2. `Modifier` — danh sách "trang trí", **thứ tự quan trọng**

Đoạn trong `CircleCheck` (nút tick tròn):

```kotlin
Modifier
    .size(26.dp)                  // 1. kích thước 26dp
    .clip(CircleShape)            // 2. cắt thành hình tròn
    .background(fill)             // 3. tô nền (bị cắt tròn theo bước 2)
    .border(2.dp, color, CircleShape)
    .clickable(onClick = onClick) // bấm được (vùng bấm cũng tròn)
```

Đổi chỗ `clip` xuống dưới `background` thì nền vẫn vuông (vì lúc tô chưa bị cắt). Hãy đọc Modifier **từ trên xuống dưới**, mỗi dòng bọc thêm một lớp ra ngoài.

### 3.3. Ba hộp xếp chỗ: `Column`, `Row`, `Box`

```
Column            Row                 Box
┌────────┐       ┌───┬───┬───┐       ┌────────┐
│  A     │       │ A │ B │ C │       │ A      │  ← các con CHỒNG lên nhau
│  B     │       └───┴───┴───┘       │   B    │    (con sau đè lên con trước)
│  C     │       xếp NGANG           └────────┘
└────────┘
xếp DỌC
```

Mẹo `weight(1f)` — "chiếm hết phần còn trống":

```kotlin
Row {
    Column(modifier = Modifier.weight(1f)) { Text("Tiêu đề dài…") }  // giãn ra
    Icon(Icons.Rounded.Flag, null)                                   // bị đẩy sang mép phải
}
```

`TaskCard` dùng đúng mẫu này: phần giữa `weight(1f)`, lá cờ ưu tiên bị đẩy sang phải.

### 3.4. Trạng thái và "vẽ lại" (recomposition)

```kotlin
var menuOpen by remember { mutableStateOf(false) }   // PriorityMenu trong TasksScreen
…
FilterChip(onClick = { menuOpen = true }, …)
DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { … }
```

- `mutableStateOf(false)`: một ô nhớ **mà Compose theo dõi**. Ai *đọc* giá trị này sẽ được vẽ lại khi nó đổi.
- `remember { … }`: giữ ô nhớ qua các lần vẽ lại (nếu không có, mỗi lần vẽ lại nó sẽ về `false`).
- `by`: cách viết gọn để dùng như biến thường (`menuOpen = true` thay vì `menuOpen.value = true`).

Chuỗi sự việc: bấm chip → `menuOpen = true` → Compose thấy ô nhớ đổi → chạy lại **đúng những khối code có đọc `menuOpen`** → `DropdownMenu(expanded = true)` → menu hiện ra.

### 3.5. Danh sách cuộn: `LazyColumn` và `key`

```kotlin
LazyColumn {
    items(tasks, key = { it.task.id }) { item -> SwipeableTask(item, …) }
}
```

- "Lazy" = chỉ vẽ các dòng **đang nhìn thấy** (giống RecyclerView, nhưng không cần adapter).
- `key = { id }` = "định danh" từng dòng. Khi danh sách đổi (xoá, đổi thứ tự), Compose dựa vào key để biết **dòng nào là dòng nào**, giữ đúng trạng thái (ví dụ đang vuốt dở) và chạy hiệu ứng mượt. Thiếu key thì dòng có thể "nhảy" hoặc nhận nhầm trạng thái của dòng khác.

### 3.6. Nâng trạng thái lên (state hoisting)

`CircleCheck` **không biết** việc đã xong hay chưa, cũng **không tự đổi** nó:

```kotlin
fun CircleCheck(checked: Boolean, color: Color, onClick: () -> Unit, …)
//              ▲ nhận giá trị      ▲ báo "bị bấm" lên trên
```

Ai dùng `CircleCheck` quyết định `checked` là gì và làm gì khi `onClick`. Nhờ vậy cùng một nút tick dùng được ở màn Hôm nay, màn Công việc, timeline… Đây là mẫu xuyên suốt dự án: **mảnh nhỏ thì "ngốc" (chỉ vẽ), logic nằm ở trên**.

### 3.7. Một khung hình được tạo ra qua 3 giai đoạn

```
 ① COMPOSITION         ② LAYOUT                ③ DRAW
 Chạy các hàm          Đo kích thước và        Vẽ pixel lên màn hình
 @Composable →         đặt vị trí từng         (nền, chữ, đường kẻ,
 ra "cây giao diện"    mảnh                    Canvas …)
```

Khi một `State` đổi, Compose **chỉ làm lại ① cho những khối có đọc State đó**, rồi ②, ③ cho phần bị ảnh hưởng. Đó là lý do app vẫn nhanh dù "vẽ lại" liên tục.

---

## 4. Khung ứng dụng

File: `…/ui/nav/NotesApp.kt`. Đây là "bộ khung" mà mọi màn hình nằm trong.

```
┌──────────────────────────────────┐
│ (vùng thanh trạng thái hệ thống) │  ← statusBarsPadding() chừa chỗ
├──────────────────────────────────┤
│                                  │
│                                  │
│      NavHost                     │  ← MÀN HÌNH HIỆN TẠI nằm ở đây
│      (Today / Lịch / Công việc / │    đổi theo "đường dẫn" (route)
│       Ghi chú / Thống kê)        │
│                                  │
│  ┌────────────────────────────┐  │
│  │ thông báo (Snackbar) ...   │  │  ← hiện đè lên, tự ẩn
│  └────────────────────────────┘  │
├──────────────────────────────────┤
│ ☀ Hôm nay │ Lịch │ Việc │ Ghi chú│  ← BottomTabs (chỉ hiện ở 5 màn chính)
└──────────────────────────────────┘
```

### 4.1. Route = "địa chỉ" của màn hình

```kotlin
private enum class Tab(val route: String, …) {
    TODAY("today", "Hôm nay", …), CALENDAR("calendar", …), TASKS("tasks", …),
    NOTES("notes", …), INSIGHTS("insights", …)
}
private object Routes {
    const val TASK_EDITOR = "task/{id}"                          // id = 0 nghĩa là TẠO MỚI
    const val NOTE_EDITOR = "note/{id}?taskId={taskId}&date={date}"
}
```

- `navController.navigate("task/5")` → mở form sửa công việc số 5. `"task/0"` → tạo mới.
- Phần `{id}` là **tham số**; trong `composable(...)` lấy ra bằng `entry.arguments?.getLong("id")`.
- Ghi chú có thêm tham số tuỳ chọn (`taskId`, `date`) để khi tạo ghi chú *từ một công việc*, nó tự gắn liên kết.

### 4.2. Hai loại màn hình

| | 5 màn chính (tab) | Form soạn thảo |
|---|---|---|
| Thanh tab dưới | **Có** | **Không** (`showBottomBar` = false) |
| Hiệu ứng vào | Hiện nhanh (mờ 120ms) | Trượt nhẹ từ phải + mờ dần |
| Nút Back | Về màn Hôm nay; bấm nữa thì thoát app | `popBackStack()` về màn trước |

`showBottomBar` được tính từ route hiện tại:
```kotlin
val currentRoute = backStackEntry?.destination?.route
val showBottomBar = Tab.entries.any { it.route == currentRoute }
```

### 4.3. Đổi tab chạy như thế nào (`openTab`)

```kotlin
navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }  // không chất chồng màn hình
    launchSingleTop = true                                          // bấm lại tab hiện tại: không mở bản sao
    restoreState = true                                             // quay lại tab thì nhớ vị trí cuộn
}
```

### 4.4. Hộp thư thông báo (`UiMessenger` → Snackbar)

ViewModel **không biết gì về giao diện**, nhưng vẫn cần báo "Đã xoá…". Giải pháp: một "hộp thư" dùng chung.

```
Bất kỳ ViewModel            UiMessenger              NotesApp (ShowMessages)
────────────────           ─────────────            ───────────────────────
messenger.post(…)  ──►   SharedFlow (luồng   ──►   LaunchedEffect: collectLatest
                          sự kiện một lần)          → snackbarHostState.showSnackbar(…)
                                                    → nếu bấm nút "Hoàn tác":
                                                       gọi message.onAction()
```

### 4.5. Vùng an toàn (inset)

App vẽ tràn full màn hình (`enableEdgeToEdge`). Vì vậy:
- 5 màn chính bọc trong `Box(Modifier.statusBarsPadding())` → chừa chỗ cho giờ/pin ở đỉnh.
- Các form soạn thảo bọc `navigationBarsPadding()` → chừa chỗ cho thanh điều hướng ở đáy.

---

## 5. Các luồng thao tác từ A đến Z

Mỗi luồng theo cùng một khuôn:
**Bạn làm gì → Chuỗi code chạy (từng bước) → Giao diện đổi ra sao → Điều đáng học.**

### 5.1. Bấm một tab ở thanh dưới (ví dụ: Công việc)

| Bước | Điều xảy ra | Ở đâu |
|---|---|---|
| 1 | Chạm `NavigationBarItem` | `BottomTabs` (NotesApp.kt) |
| 2 | `onClick` → `onTabSelected(tab.route)` | NotesApp.kt |
| 3 | `navController.openTab("tasks")` | `openTab` (NotesApp.kt) |
| 4 | NavHost đổi sang `composable("tasks")`, màn Today biến mất ngay, màn Công việc **mờ hiện 120ms** | `NavHost(enterTransition = fadeIn(tween(120)), exitTransition = None …)` |
| 5 | `TasksScreen` chạy → `appViewModel { TasksViewModel(it) }` lấy ViewModel (tạo mới nếu chưa có, **dùng lại nếu đã có**) | TasksScreen.kt, AppViewModels.kt |
| 6 | `collectAsStateWithLifecycle()` đọc danh sách → `LazyColumn` vẽ | TasksScreen.kt |
| 7 | Mục tab dưới được tô sáng vì `selected = (currentRoute == tab.route)` | BottomTabs |

**Điều đáng học:** *không ai "báo" cho thanh tab biết phải sáng cái nào* — nó tự suy ra từ route hiện tại. Đó là "UI = f(state)".

---

### 5.2. Tick hoàn thành một công việc (màn Hôm nay)

```
 Ngón tay chạm vào vòng tròn
   │
   ▼
 CircleCheck  .clickable(onClick = onClick)             …/ui/components/TaskCard.kt
   │  onClick = onToggleDone
   ▼
 TaskCard(onToggleDone = { onToggle(item.task.id) })    …/ui/today/TodayScreen.kt  (todoSection)
   │  onToggle = viewModel::toggleTask
   ▼
 TodayViewModel.toggleTask(taskId)                      …/ui/today/TodayViewModel.kt
   │  viewModelScope.launch { taskRepository.toggleDone(taskId) }
   ▼
 ┌────────────── HỘP ĐEN ──────────────┐
 │ đổi isDone = !isDone trong CSDL      │
 │ Room thấy bảng "tasks" đổi           │
 │ → các Flow đang lắng nghe phát       │
 │   danh sách MỚI                      │
 └──────────────────┬───────────────────┘
                    ▼
 TodayViewModel.state  (combine 3 nguồn → TodayState mới)
                    ▼
 TodayScreen:  val state by viewModel.state.collectAsStateWithLifecycle()
                    ▼
 Compose VẼ LẠI những chỗ đọc `state`:
```

**Những gì thay đổi trên màn hình, KHÔNG dòng code nào phải ra lệnh:**

| Chỗ trên màn hình | Vì sao tự đổi | Hiệu ứng |
|---|---|---|
| Vòng tròn tick của thẻ | `checked = task.isDone` đổi | `animateColorAsState` làm nền chuyển mượt từ trong suốt sang đặc |
| Tiêu đề thẻ | `isDone = true` | Gạch ngang + nhạt màu |
| Cờ ưu tiên | Điều kiện `!task.isDone` | Biến mất |
| Vòng tiến độ ở đầu trang (`25%` → `50%`) | `overview.progress` tính lại | `animateFloatAsState` trượt 700ms |
| Chữ "Đã xong 1/4 việc" | `doneCount` đổi | Đổi chữ |
| Mục "Nên làm tiếp" | `suggestions` tính lại | Việc đã xong bị loại khỏi gợi ý |

**Điều đáng học:**
- Một lần ghi dữ liệu → **nhiều nơi cập nhật cùng lúc**, vì chúng cùng nhìn một nguồn. Không cần code "cập nhật vòng tiến độ".
- Cùng `CircleCheck` + cùng hàm `toggleTask` được dùng ở: thẻ công việc, thẻ gợi ý, dòng timeline.

---

### 5.3. Gõ vào ô tìm kiếm / bấm chip lọc (màn Công việc)

```
 Gõ chữ "báo"
   ▼
 OutlinedTextField(value = state.search, onValueChange = onChange)   TasksScreen → SearchField
   ▼  onChange = viewModel::setSearch
 TasksViewModel.setSearch("báo")  →  search.value = "báo"            (MutableStateFlow)
   │
   ├───────────────► (A) NHÁNH HIỂN THỊ CHỮ: đi thẳng vào state
   │                     → ô tìm kiếm hiện chữ NGAY, không trễ
   │
   └───────────────► (B) NHÁNH TRUY VẤN: search.debounce(200)
                         "đợi người dùng ngừng gõ 0,2 giây"
                         → flatMapLatest { observeTasks(...) } → Room trả danh sách đã lọc
                         → state.tasks đổi → LazyColumn vẽ lại
```

**Điều đáng học:**
- **Hai nhánh**: chữ trong ô gõ phải hiện tức thì (nếu chờ truy vấn xong mới hiện, bàn phím sẽ cảm giác giật), còn truy vấn thì nên "nghỉ 200ms" để khỏi chạy mỗi phím bấm.
- `flatMapLatest`: có yêu cầu mới thì **bỏ truy vấn cũ** đang chạy dở.
- Bấm chip lọc (`FilterChip`) đi theo đường tương tự: `viewModel.setFilter(...)` → ô nhớ đổi → danh sách tải lại. Bấm lại chip danh mục đang chọn thì bỏ chọn (`if (isSelected) null else option`).
- Danh sách trống: nếu đang lọc → "Không có công việc phù hợp"; nếu chưa lọc → "Chưa có công việc nào" (hàm `isFiltering`).

---

### 5.4. Vuốt một công việc: hoàn thành hoặc xoá (có Hoàn tác)

Mỗi dòng của danh sách là `SwipeableTask` (`TasksScreen.kt`), bọc `SwipeToDismissBox`.

```
 Vuốt PHẢI đủ xa                         Vuốt TRÁI đủ xa
   ▼                                        ▼
 confirmValueChange(StartToEnd)          confirmValueChange(EndToStart)
   ▼                                        ▼
 latestToggle()  → viewModel.toggleDone  latestDelete() → viewModel.delete(item)
 return false  → dòng TRƯỢT VỀ chỗ cũ    return true   → dòng BIẾN MẤT
 (vì việc vẫn còn trong danh sách)       (danh sách sẽ bớt 1 dòng)
```

Khi vuốt, phần **nền** lộ ra phía sau là `SwipeBackground`: xanh + dấu tick (vuốt phải), đỏ + thùng rác (vuốt trái).
Hướng vuốt được đọc từ `dismissState.dismissDirection` → nên màu nền đổi theo hướng ngón tay.

**Luồng Xoá + Hoàn tác:**

```
 TasksViewModel.delete(item):
   1. taskRepository.deleteTask(id)            → CSDL xoá → danh sách tự cập nhật (dòng biến mất)
   2. messenger.post(UiMessage(
         text        = "Đã xoá “Tên việc”",
         actionLabel = "Hoàn tác",
         onAction    = { restoreTask(item) }))   ← giữ sẵn bản sao để khôi phục
                │
                ▼  (qua UiMessenger, mục 4.4)
 NotesApp hiện Snackbar có nút "Hoàn tác"
                │ bấm nút
                ▼
 onAction() → taskRepository.restoreTask(item) → CSDL thêm lại → dòng hiện lại
```

**Điều đáng học:**
- `rememberUpdatedState(onToggleDone)`: hàm `confirmValueChange` được tạo **một lần** nên nếu không bọc, nó sẽ giữ bản cũ của lambda. Bọc lại để luôn gọi bản **mới nhất**.
- "Hoàn tác" hoạt động vì ViewModel giữ lại bản sao đối tượng đã xoá trong lambda.

---

### 5.5. Nút "Thêm nhanh" (tờ giấy trượt từ dưới lên)

File: `…/ui/today/QuickAddSheet.kt`.

```
 Bấm nút nổi "Thêm nhanh"
   ▼
 TodayScreen:  showQuickAdd = true                (trạng thái CỤC BỘ: remember)
   ▼
 `if (showQuickAdd) { QuickAddSheet(...) }`  → tờ giấy ModalBottomSheet hiện lên
   │
   │  Trong tờ giấy: các ô là trạng thái CỤC BỘ, sống chừng nào tờ giấy còn mở
   │      var isTask   by remember { mutableStateOf(true) }
   │      var text     by remember { mutableStateOf("") }
   │      var category by remember { mutableStateOf(Category.PERSONAL) }
   │      var priority …  var dueToday …
   │  (Gõ vào ô → `text = it` → vẽ lại ô với chữ mới)
   ▼
 Bấm "Thêm" (hoặc Enter trên bàn phím)
   ▼
 submit():  onAddTask(text, category, priority, dueToday)   ← gửi cả gói lên ViewModel
            onDismiss()                                     ← showQuickAdd = false → tờ giấy đóng
   ▼
 TodayViewModel.quickAddTask(...) → lưu → messenger.post("Đã thêm công việc")
   ▼
 Danh sách tự có thêm việc mới (vì vẫn là vòng lặp ở mục 1)
```

**Điều đáng học:**
- Dữ liệu dở dang trong form **chỉ nằm ở trạng thái cục bộ** và chỉ được gửi lên ViewModel **một lần** khi bấm "Thêm". ViewModel không phải biết người dùng đang gõ gì.
- `FocusRequester` + `LaunchedEffect(isTask)` tự đặt con trỏ vào ô nhập khi mở tờ giấy.
- Nút Enter: `ImeAction.Done` + `KeyboardActions(onDone = { submit() })`.

---

### 5.6. Mở form công việc, sửa, rồi bấm Lưu

**Mở form:**

```
 Bấm vào một thẻ công việc
   ▼
 onClick → onOpen(id) → onOpenTask(id)  (truyền từ NotesApp xuống)
   ▼
 navController.navigate("task/5")
   ▼
 NavHost mở composable("task/{id}")  (trượt từ phải vào, mục 4.2)
   ▼
 TaskEditorScreen(taskId = 5)
   │  viewModel = appViewModel(key = "task-editor-5") { TaskEditorViewModel(it, 5) }
   │      ↑ key riêng cho từng công việc, tránh hai form dùng chung một ViewModel
   ▼
 TaskEditorViewModel.init: đọc công việc từ CSDL → đổ vào `form`
   │  trong lúc đó `loaded = false` → màn hình hiện khung trống
   ▼
 `loaded = true` → form hiện đủ các ô
```

**Sửa một ô (ví dụ gõ tên):**

```kotlin
OutlinedTextField(
    value = form.title,
    onValueChange = { newTitle -> viewModel.update { it.copy(title = newTitle) } }
)
```

- `form` là `TaskForm` (một `data class` **bất biến**). `update { it.copy(title = newTitle) }` tạo **bản sao** với một trường đã đổi rồi gán lại → `form` đổi → màn hình vẽ lại với chữ mới.
- `form` khai báo `var form by mutableStateOf(TaskForm()); private set` → giao diện **đọc được nhưng không tự ghi được**, chỉ ViewModel được ghi.
- Chú ý: các thao tác **chưa ghi vào CSDL**. Người dùng bấm Quay lại thì mọi thứ bị bỏ.

**Bấm "Lưu":**

```
 EditorTopBar: onSave → viewModel.save(onSaved = onBack)
   ▼
 save():
   • tên trống?            → messenger.post("Hãy nhập tên công việc") → DỪNG, không đóng form
   • xếp lịch mà giờ kết thúc ≤ bắt đầu? → thông báo → DỪNG
   • đổi "ngày + số phút" của form thành mili-giây (TimeUtils.at(...))
   • taskRepository.saveTask(entity)          ← ghi CSDL
   • việc con tạm (công việc mới) → ghi sau khi có id
   • nếu trùng giờ với lịch khác → messenger.post("Trùng giờ với: …")  (vẫn cho lưu)
   • onSaved()  → navController.popBackStack() → form đóng, quay về danh sách
   ▼
 Danh sách phía sau tự có dữ liệu mới (vòng lặp mục 1)
```

**Điều đáng học:**
- Việc con của công việc **đang sửa** ghi thẳng CSDL ngay khi thêm/tick (có id rồi); của công việc **mới** thì giữ tạm trong bộ nhớ (`temporarySubtasks`, id âm) đến lúc Lưu.
- Ô chọn ngày/giờ (`PickerField`, `DateField`, `TimeField` trong `components/Common.kt`): giao diện thuần, bấm vào thì mở hộp chọn của Material.

---

### 5.7. Soạn ghi chú — không có nút Lưu, tự lưu nháp

```
 Gõ một chữ vào tiêu đề / nội dung
   ▼
 viewModel.update { it.copy(...) }              (NoteEditorViewModel)
   │   form = ...        → màn hình vẽ lại chữ mới ngay
   │   scheduleAutosave()
   ▼
 scheduleAutosave():
   saved = false                     → nhãn "Đã lưu nháp" biến mất
   autosaveJob?.cancel()             → HUỶ hẹn giờ trước (người dùng vẫn đang gõ)
   autosaveJob = launch {
       delay(700)                    → "im lặng 0,7 giây rồi mới lưu"
       persist()                     → ghi CSDL (ghi chú mới lưu với cờ isDraft = true)
       saved = true                  → hiện lại "Đã lưu nháp"
   }
```

**Khi thoát:** cả nút "Xong", nút Back trên thanh trên và Back của hệ thống (`BackHandler`) đều gọi `viewModel.finish(onBack)`:

- Form **trống** → xoá bản nháp (không để lại ghi chú rỗng).
- Có nội dung → bỏ cờ nháp (`isDraft = false`) + lưu → ghi chú **xuất hiện trong danh sách**.

**Nếu app bị tắt giữa chừng:** bản nháp vẫn nằm trong CSDL (ẩn khỏi danh sách). Lần sau mở màn Ghi chú sẽ có banner (`DraftBanner`) hỏi "Tiếp tục / Bỏ".

**Chuyển văn bản ⇄ checklist** (`toggleChecklist`): mỗi dòng văn bản thành một mục tick; ngược lại ghép các mục thành các dòng. Dòng mới thêm nhận con trỏ nhờ `focusKey` + `LaunchedEffect(viewModel.focusKey)` + `FocusRequester` riêng mỗi dòng.

**Màu nền:** chọn màu ở `ColorPicker` → `form.color` (số nguyên ARGB, 0 = không màu) → nền cả màn hình đổi theo. Chế độ tối: `noteContainerColor` pha màu pastel với màu tối (`lerp`) để khỏi chói.

---

### 5.8. Nhấn giữ rồi kéo một khối trên Lịch (kéo thả)

File: `…/ui/calendar/TimeGrid.kt`, hàm `EventBlock`. Đây là luồng "khó" nhất của giao diện.

```
 ① NHẤN GIỮ khối
    detectDragGesturesAfterLongPress → onDragStart: isDragging = true
    → khối NỔI LÊN: zIndex = 10, có bóng (shadow), nền đậm hơn (alpha 0.95), chữ trắng

 ② KÉO ngón tay
    onDrag(change, amount): dragOffset += amount        ← cộng dồn quãng đường (pixel)
    Modifier.offset { IntOffset(dragOffset.x, dragOffset.y) }
    → khối ĐI THEO ngón tay trong lúc kéo
      (CHƯA đụng tới CSDL: chỉ là dịch hình)

 ③ THẢ TAY (onDragEnd)
    pixel → phút:  rawMinutes   = dragOffset.y ÷ (pixel mỗi giờ) × 60
                   deltaMinutes = làm tròn về BỘI CỦA 15 phút       ← "hít" vào mốc
    pixel → ngày:  deltaDays    = dragOffset.x ÷ (bề rộng một cột), làm tròn
    ­• quá ít (0 phút, 0 ngày)  → trả về chỗ cũ, coi như không dời
    ­• còn lại: isWaitingForSave = true
                onMove(block, deltaDays, deltaMinutes)

 ④ ViewModel ghi:  CalendarViewModel.moveBlock → calendarRepository.shiftBlock
                   → báo "Đã dời “…”" hoặc "…, trùng giờ với: …"

 ⑤ CSDL cập nhật → khối được vẽ lại ở giờ MỚI
    LaunchedEffect(block.start, block.end) → dragOffset = 0   ← bỏ phần dịch tạm
    (phòng khi lỗi: sau 0,9 giây tự trả khối về chỗ cũ)
```

**Điều đáng học:**
- Trong lúc kéo là **trạng thái tạm cục bộ** (`dragOffset`), chỉ khi thả tay mới "gửi lên" ViewModel. Nhờ vậy kéo mượt, không ghi CSDL mỗi pixel.
- Tại sao dùng `rememberUpdatedState(block/onMove)`: cử chỉ (`pointerInput`) được tạo một lần, nếu không bọc nó sẽ giữ bản cũ của `block`.
- Chế độ **Ngày** chỉ có 1 cột nên `canMoveAcrossDays = false` (không kéo ngang được).
- Lịch **lặp hằng tuần** chỉ đổi được *giờ trong ngày*, không đổi được thứ (xem `shiftBlock`).

---

### 5.9. Tick thói quen ở màn Thống kê

```
 Bấm vòng tròn một ngày trong dải 7 ngày  (hoặc nút tick lớn của HÔM NAY)
   ▼
 onToggle(day) → InsightsViewModel.toggleHabit(habitId, day)
   ▼
 habitRepository.toggle(...)  → thêm/xoá lượt check-in (hộp đen)
   ▼
 Flow phát dữ liệu mới → InsightsState mới
   ▼
 Tự vẽ lại: dải 7 ngày (vòng tròn đặc/nhạt/xám), chữ "🔥 12 ngày" (streak),
            ô thống kê "Chuỗi ngày dài nhất", heatmap "Hoạt động"
```

Màu mỗi vòng tròn trong `LastSevenDays` quyết định bởi 3 trạng thái:
đã làm → màu đặc · có lịch nhưng chưa làm → màu nhạt · ngày không nằm trong lịch thói quen → xám.

---

## 6. Hình trên màn hình được vẽ như thế nào

### 6.1. Bảng "hình bạn thấy → hàm vẽ ra nó"

| Hình bạn thấy | Composable | File |
|---|---|---|
| Thẻ gradient tím có lời chào + vòng tiến độ | `HeaderCard` | `ui/today/TodayScreen.kt` |
| Vòng tròn tiến độ | `ProgressRing` | `ui/components/Common.kt` |
| Nhãn bo tròn ("Học tập", "Quá hạn") | `Pill` | `ui/components/Common.kt` |
| Tiêu đề mục + nút bên phải | `SectionHeader` | `ui/components/Common.kt` |
| Khối "chưa có gì" (icon tròn + chữ) | `EmptyState` | `ui/components/Common.kt` |
| Nút tick tròn | `CircleCheck` | `ui/components/TaskCard.kt` |
| Thẻ một công việc | `TaskCard` | `ui/components/TaskCard.kt` |
| Dòng công việc vuốt được | `SwipeableTask` | `ui/tasks/TasksScreen.kt` |
| Dòng timeline có đường dọc + chấm | `TimelineRow` | `ui/today/TodayScreen.kt` |
| Tờ giấy "Thêm nhanh" | `QuickAddSheet` | `ui/today/QuickAddSheet.kt` |
| Lưới giờ của lịch ngày/tuần | `TimeGrid`, `DayColumn`, `EventBlock` | `ui/calendar/TimeGrid.kt` |
| Lịch tháng | `MonthView` | `ui/calendar/CalendarScreen.kt` |
| Tờ giấy thêm/sửa lịch | `EventEditorSheet` | `ui/calendar/EventEditorSheet.kt` |
| Lưới ghi chú so le | `NotesGrid`, `NoteCard` | `ui/notes/NotesScreen.kt` |
| Form ghi chú không viền | `NoteEditorScreen`, `BorderlessField` | `ui/notes/NoteEditorScreen.kt` |
| Ô số liệu to ở đầu Thống kê | `StatTile` | `ui/insights/InsightsScreen.kt` |
| Thẻ thói quen | `HabitCard`, `LastSevenDays` | `ui/insights/InsightsScreen.kt` |
| Biểu đồ cột / tròn / heatmap / thanh | `WeeklyBarChart`, `DonutChart`, `ActivityHeatmap`, `PlanActualBars` | `ui/components/Charts.kt` |

### 6.2. Giải phẫu một thẻ công việc (`TaskCard`)

```
┌─┬────────────────────────────────────────────────────────┐   Card (bo góc 18dp, nền surface)
│ │  ◯   Làm slide thuyết trình đồ án                  🚩  │
│ │      [💼 Công việc]  🕒 Hôm nay 18:00   ⏱ 1g           │
│█│      ▓▓▓▓▓░░░░░  1/3                                   │
└─┴────────────────────────────────────────────────────────┘
 │   │      │                 │                 │          │
 │   │      │                 │                 │          └ cờ ưu tiên (chỉ khi Vừa/Cao & chưa xong)
 │   │      │                 │                 └ SubtaskProgress (LinearProgressIndicator + "1/3")
 │   │      │                 └ TaskInfoRow: Pill danh mục + MetaText hạn chót + MetaText thời lượng
 │   │      └ TaskTitle (tối đa 2 dòng, gạch ngang khi xong)
 │   └ CircleCheck
 └ CategoryStripe: vạch màu 5dp theo danh mục (mờ đi khi xong)
```

Cấu trúc code: `Card → Row(height = IntrinsicSize.Min) { CategoryStripe; Row { CircleCheck; Column(weight 1f){…}; cờ } }`.
`IntrinsicSize.Min` giúp vạch màu bên trái **cao bằng cả thẻ** dù nội dung cao thấp khác nhau.

Hạn chót hiển thị bằng `Fmt.deadline(...)` (`ui/util/Formatters.kt`): "Hôm nay 18:00", "Ngày mai 09:00", "Quá hạn 2 ngày". Quá hạn thì chữ đỏ (`DangerRed`).

### 6.3. Màn **Hôm nay** — từng khối

```
┌───────────────────────────────────┐
│ ░░ HeaderCard (gradient) ░░░░░░░░ │  Brush.linearGradient(primary → secondary)
│  Chào buổi tối        ◜◝ 25%     │  Fmt.greeting(giờ hiện tại)
│  Chủ Nhật, 25 tháng 10 ◟◞        │  ProgressRing (Canvas) ở bên phải
│  Đã xong 1/4 việc                 │
│  [⚠ 1 việc quá hạn]               │  Pill, chỉ hiện khi overdueCount > 0
├───────────────────────────────────┤
│ Nên làm tiếp                      │  suggestionSection: việc #1 to, #2 #3 nhỏ
│ ┌───────────────────────────────┐ │  (do TaskPrioritizer chấm điểm → ở tầng dữ liệu)
│ │ ✨ Nộp báo cáo…  ◯            │ │
│ └───────────────────────────────┘ │
├───────────────────────────────────┤
│ Lịch trình hôm nay        Mở lịch │  timelineSection
│ ☀ Buổi sáng                       │  nhóm theo DayPart.ofHour(giờ bắt đầu)
│ 06:00 ●─ [Chạy bộ 5km      ✔]     │  TimelineRow: giờ │ đường dọc+chấm │ thẻ
│ 07:00 │                           │
│ 🌇 Buổi chiều                     │
│ 15:00 ●─ [Họp nhóm  ⚠ Trùng giờ]  │  nhãn đỏ khi block.key ∈ conflictKeys
└───────────────────────────────────┘
```

Chi tiết đáng chú ý:
- **Đường dọc + chấm tròn** của timeline **không phải ảnh**, mà tự vẽ bằng `drawBehind { drawLine(...) }` phía sau một `Box` rỗng.
- "Đang diễn ra": `isHappeningNow = now in block.start until block.end`.
- `HeaderCard` dùng `remember { TimeUtils.hourOf(now) }` để chọn lời chào.
- Mỗi mục (`suggestionSection`, `timelineSection`…) là một **hàm mở rộng của `LazyListScope`** thêm các `item { }` vào cùng một `LazyColumn`. Cách viết này tách màn hình dài thành các hàm nhỏ mà vẫn là **một danh sách cuộn duy nhất**.

### 6.4. Lưới giờ của **Lịch** — toán học đằng sau

Hình dung một cái bảng 24 hàng (giờ) × N cột (ngày):

```
          T2     T3     T4     T5  …            DayHeader (tên thứ + số ngày)
         ┌──────┬──────┬──────┬──────┐
 07:00   │      │      │      │      │
         │ ┌──┐ │      │ ┌──┐ │      │          Mỗi khối = một hộp chữ nhật
 08:00   │ │A │ │      │ │A │ │      │          đặt bằng Modifier.offset + height
         │ └──┘ │      │ └──┘ │      │
 09:00   ├──────┼──────┼──────┼──────┤          ← đường kẻ mỗi giờ: drawBehind + drawLine
```

Hai công thức cốt lõi (`HourHeight = 56.dp`):

```
 vị trí dọc  (y)      = HourHeight × (số phút kể từ nửa đêm ÷ 60)
 chiều cao khối       = HourHeight × (thời lượng phút ÷ 60)        (tối thiểu 24dp để còn bấm được)
```

- Chia cột: `Row { for (day in days) DayColumn(modifier = Modifier.weight(1f)) }` → mỗi ngày một cột bằng nhau.
- **Hai khối trùng giờ không đè nhau** nhờ `assignLanes`: nhóm các khối chồng thời gian, mỗi khối vào "làn" đầu tiên còn trống; rộng mỗi khối = `columnWidth / số làn`.
- **Đường đỏ "bây giờ"** (`NowLine`): chỉ vẽ ở cột hôm nay, đặt theo cùng công thức vị trí dọc.
- Mở lịch tự cuộn tới 7 giờ sáng: `rememberScrollState(initial = HourHeight * 7)`.
- **Lịch lặp hằng tuần** được "mở rộng" thành từng lần xuất hiện ở tầng dữ liệu (`Scheduling.buildBlocks`), nên giao diện chỉ việc vẽ một danh sách `TimeBlock` phẳng, không cần biết lặp hay không.

### 6.5. Màn **Ghi chú** — lưới so le

```
LazyVerticalStaggeredGrid(columns = Fixed(2))   ←  Fixed(1) khi chuyển sang dạng danh sách

 ┌─────────┐ ┌─────────┐
 │  Ghi chú │ │ Ghi chú │       Các thẻ cao thấp khác nhau, xếp khít như Google Keep
 │  dài     │ └─────────┘
 │          │ ┌─────────┐
 └─────────┘ │ …        │
 ┌─────────┐ └─────────┘
```

- "Đã ghim" / "Khác" là các `item(span = StaggeredGridItemSpan.FullLine)` — **chiếm trọn một hàng**, không bị chia cột.
- `NoteCard`: `combinedClickable(onClick = mở, onLongClick = mở menu)` → phân biệt **bấm** và **nhấn giữ**. Menu nhấn giữ có Ghim / Lưu trữ / Xoá.
- Màu thẻ: `noteContainerColor(note.color, dark, default)`; ghi chú không có màu thì thêm viền mảnh để tách khỏi nền.
- Nội dung thẻ: văn bản (tối đa 7 dòng ở dạng lưới, 3 dòng ở dạng danh sách) **hoặc** `ChecklistPreview` (tối đa 5 mục, tick được ngay trên thẻ).

### 6.6. Màn **Thống kê** — dùng `Canvas` để tự vẽ biểu đồ

`Canvas` cho phép vẽ bằng lệnh (`drawArc`, `drawRoundRect`, `drawLine`…) trên một vùng có kích thước cho trước.

**Vòng tiến độ** (`ProgressRing`):

```kotlin
Canvas(Modifier.size(size)) {
    drawArc(trackColor, 0f,   360f,            false, …, style = stroke)   // vòng nền: cung 360°
    drawArc(color,     -90f,  360f * animated, false, …, style = stroke)   // vòng tiến độ: bắt đầu ở đỉnh (-90°)
}
```
`animated` đến từ `animateFloatAsState(progress, tween(700))`, nên khi `progress` đổi vòng **trượt dần** thay vì nhảy.

**Heatmap hoạt động** (`ActivityHeatmap`): 16 cột (tuần) × 7 hàng (thứ). Mỗi ô là một `drawRoundRect`.

```
 màu ô = nếu 0 hoạt động:  xám (surfaceVariant)
         ngược lại:        màu chính, độ đục = 0.3 + 0.7 × (số hoạt động ÷ số lớn nhất)
 ngày trong tương lai:     không vẽ
```

**Các thanh so sánh** (`PlanActualBars`): mỗi thanh là hai `Box` lồng nhau — ngoài là nền xám, trong dài `fillMaxWidth(fraction)`. `fraction` được `animateFloatAsState` làm mượt, nên thanh **dài ra dần** khi đổi tuần. Biểu đồ cột tuần (`WeeklyBarChart`) cũng làm mượt chiều cao cột theo cách này.

**Ô số liệu** (`StatTile`): `Card` nền = màu chủ đề với độ đục 12% (`color.copy(alpha = 0.12f)`), icon + số lớn + nhãn nhỏ.

### 6.7. Màu, chữ, chế độ tối (`ui/theme/`)

| File | Vai trò |
|---|---|
| `Color.kt` | Khai báo màu cố định (màu thương hiệu, màu danh mục, màu ưu tiên, bảng màu ghi chú) |
| `Theme.kt` | Gom thành `LightScheme` / `DarkScheme`; `NotesTheme` chọn bộ theo **cài đặt của điện thoại** (`isSystemInDarkTheme()`) |
| `Type.kt` | `AppTypography`: các cỡ chữ (headline > title > body > label) |
| `CategoryStyle.kt` | Thuộc tính mở rộng: `category.color / .label / .icon`, `priority.color / .label`… |

Cách dùng trong code, **luôn lấy từ theme** thay vì gõ cứng màu:

```kotlin
color = MaterialTheme.colorScheme.primary          // màu chính (tự đổi theo sáng/tối)
style = MaterialTheme.typography.titleMedium       // cỡ chữ chuẩn
Category.STUDY.color   Category.STUDY.label        // "Học tập" + màu của nó
```

Nhờ vậy đổi một dòng ở `Color.kt`/`Theme.kt` là **cả app đổi màu**, và chế độ tối tự chạy đúng. (Ngoại lệ: màu `DangerRed`, `WarningAmber`, `SuccessGreen` dùng cố định cho trạng thái lỗi / cảnh báo / thành công.)

`Fmt` (`ui/util/Formatters.kt`) đổi dữ liệu thô thành chữ tiếng Việt: `Fmt.time(ms)` → "14:30", `Fmt.dayTitle(day)` → "Chủ Nhật, 25 tháng 10", `Fmt.duration(ms)` → "1g 30p"…

---

## 7. Giao diện không đổi? Cách tự gỡ rối

| Triệu chứng | Nguyên nhân hay gặp | Cách kiểm tra |
|---|---|---|
| Sửa dữ liệu nhưng màn hình **không đổi** | Dữ liệu được lưu vào biến thường, không phải `State` | Tìm xem biến có phải `mutableStateOf` / `StateFlow` không |
| Màn hình đổi nhưng **mất khi xoay máy** | Dùng `remember` cho thứ quan trọng | Chuyển thứ đó lên ViewModel |
| Danh sách **nhảy lung tung / sai trạng thái** | Thiếu `key` trong `items(...)` | Thêm `key = { id }` |
| **Chữ gõ** chậm hoặc nhảy con trỏ | Ô nhập đọc giá trị qua truy vấn chậm | Dùng 2 nhánh như mục 5.3 |
| **Vùng bấm** nhỏ hơn mong muốn | `padding` đặt **trước** `clickable` nên phần đệm không bấm được | Đọc Modifier từ trên xuống (mục 3.2) |
| Hộp/hình bị **cắt vuông** | `clip` đặt sau `background` | Đặt `clip` trước |
| Nội dung bị **thanh hệ thống che** | Thiếu `statusBarsPadding()` / `navigationBarsPadding()` / `imePadding()` | Xem mục 4.5 |
| Màn hình **giật khi cuộn nhanh** | Đang chạy bản **debug** (chậm hơn nhiều so với release) | Chạy bản release để so sánh |

**Mẹo xem nhanh:** Android Studio → **Layout Inspector** để xem cây composable thật; **`@Preview`** để xem một mảnh nhỏ mà không cần chạy cả app.

---

## 8. Bài tập thực hành

Làm từ dễ đến khó. Mỗi bài chỉ cần sửa ở **tầng giao diện**.

**Mức 1 — chỉ đổi giao diện**
1. Đổi màu chính của app: sửa `Indigo600` trong `ui/theme/Color.kt`. Quan sát cả app đổi màu (thanh tab, nút nổi, thẻ Hôm nay…).
2. Đổi lời chào "Chào buổi tối" thành khung giờ khác: sửa `Fmt.greeting` trong `ui/util/Formatters.kt`.
3. Làm vạch màu của `TaskCard` dày hơn: sửa `.width(5.dp)` trong `CategoryStripe`.
4. Đổi chiều cao một giờ trên lịch: sửa `HourHeight` (56.dp → 72.dp) trong `TimeGrid.kt`. Khối lịch và đường kẻ tự co giãn theo (vì dùng cùng công thức).

**Mức 2 — thêm một mảnh nhỏ**
5. Thêm nhãn "Quan trọng" vào `TaskInfoRow` khi `priority == HIGH`, dùng `Pill`.
6. Ở `HeaderCard`, đổi dòng "Đã xong x/y việc" thành thêm phần trăm.
7. Thêm một ô thống kê vào `StatTiles` (`InsightsScreen.kt`), ví dụ "Số thói quen đang theo dõi" = `state.habits.size`.

**Mức 3 — thêm một ô vào form (theo đúng khuôn của dự án)**
8. Thêm ô "Địa điểm" vào form công việc:
   1. `data/Entities.kt`: thêm cột `location` vào `TaskEntity` và viết migration mới trong `NotesDatabase.kt` (nhờ người hướng dẫn nếu chưa quen phần dữ liệu).
   2. `ui/tasks/TaskEditorViewModel.kt`: thêm `location` vào `TaskForm`, đọc trong `toForm()`, ghi trong `save()`.
   3. `ui/tasks/TaskEditorScreen.kt`: thêm một `OutlinedTextField(value = form.location, onValueChange = { v -> viewModel.update { it.copy(location = v) } })`.

**Mức 4 — thêm màn hình mới**
9. Tạo `ui/xyz/XyzViewModel.kt` + `XyzScreen.kt` theo mẫu `TasksViewModel`/`TasksScreen`; thêm một `Tab` và một `composable("xyz") { … }` trong `NotesApp.kt`.

---

## 9. Lộ trình đọc code và bản đồ thư mục

### Thứ tự nên đọc (từ dễ đến khó)

1. `ui/theme/` — xem màu, chữ khai báo ở đâu.
2. `ui/util/Formatters.kt` — các hàm đổi dữ liệu thành chữ.
3. `ui/components/Common.kt`, `ui/components/TaskCard.kt` — các mảnh nhỏ dễ hiểu (mục 3 & 6.2).
4. `ui/tasks/TasksViewModel.kt` → `ui/tasks/TasksScreen.kt` — mẫu **ViewModel + Screen** đầy đủ nhất (mục 5.3, 5.4).
5. `ui/today/` — ghép nhiều nguồn dữ liệu, timeline, tờ giấy thêm nhanh (mục 5.2, 5.5, 6.3).
6. `ui/tasks/TaskEditorScreen.kt` + `…ViewModel.kt` — form có nhiều ô (mục 5.6).
7. `ui/notes/` — lưới so le, tự lưu nháp (mục 5.7, 6.5).
8. `ui/insights/` + `ui/components/Charts.kt` — vẽ bằng `Canvas` (mục 6.6).
9. `ui/calendar/` — phần khó nhất: lưới giờ, kéo thả (mục 5.8, 6.4).
10. `ui/nav/NotesApp.kt` — xem mọi thứ được nối với nhau (mục 4). *(Có thể đọc sớm hơn để nắm khung.)*

### Bản đồ thư mục `ui/`

```
ui/
├─ nav/NotesApp.kt            Khung app: tab dưới, NavHost, snackbar
├─ AppViewModels.kt           appViewModel { … }: tạo/lấy ViewModel
├─ UiMessenger.kt             Hộp thư thông báo toàn app
├─ theme/                     Color, Theme (sáng/tối), Type, CategoryStyle
├─ util/Formatters.kt         Fmt: ngày/giờ/thời lượng → chữ tiếng Việt
├─ components/                Mảnh dùng chung: Common, TaskCard, Charts
├─ today/                     Màn Hôm nay (+ QuickAddSheet)
├─ calendar/                  Màn Lịch: CalendarScreen, TimeGrid, EventEditorSheet
├─ tasks/                     Màn Công việc + form soạn công việc
├─ notes/                     Màn Ghi chú + form soạn ghi chú
└─ insights/                  Màn Thống kê: thói quen, biểu đồ, heatmap
```

Mỗi màn hình dùng **cùng một mẫu**: `XxxViewModel.kt` (trạng thái + hành động) và `XxxScreen.kt` (chỉ vẽ + báo sự kiện).

---

## 10. Từ điển

| Thuật ngữ | Hiểu đơn giản |
|---|---|
| `@Composable` | Hàm vẽ một mảnh giao diện. Gọi hàm = đặt mảnh đó lên màn hình. |
| Recomposition ("vẽ lại") | Compose chạy lại các khối code đọc một `State` vừa đổi, để hình khớp với dữ liệu mới. |
| `State` / `mutableStateOf` | Ô nhớ mà Compose theo dõi. Đổi giá trị → các nơi đọc nó được vẽ lại. |
| `remember` | Giữ một giá trị qua các lần vẽ lại (mất khi xoay màn hình). |
| `StateFlow` | `State` nằm trong ViewModel. Screen đọc bằng `collectAsStateWithLifecycle()`. |
| `Flow` | "Vòng nước" phát dữ liệu mới mỗi khi nguồn đổi. Hộp đen dùng nó để báo dữ liệu thay đổi. |
| `combine(a, b, c)` | Gộp nhiều `Flow` thành một: bất kỳ nguồn nào đổi thì tính lại kết quả. |
| `flatMapLatest` | Khi đầu vào đổi, bỏ luồng cũ và chuyển sang luồng mới. |
| `debounce(ms)` | Đợi nguồn "im lặng" `ms` mili-giây rồi mới chuyển tiếp (tránh xử lý mỗi phím gõ). |
| ViewModel | Giữ trạng thái màn hình; sống qua việc xoay máy; nhận sự kiện từ Screen. |
| `viewModelScope.launch { }` | Chạy việc nền trong ViewModel; tự huỷ khi ViewModel bị bỏ. |
| `LaunchedEffect(key)` | Chạy một đoạn code (được phép chờ) khi composable xuất hiện hoặc `key` đổi. |
| `Modifier` | Chuỗi "trang trí" cho một mảnh giao diện: kích thước, lề, nền, bo góc, bấm được… (thứ tự quan trọng). |
| `Scaffold` | Khung chuẩn của Material: có chỗ cho thanh trên/dưới, nút nổi, snackbar. |
| `LazyColumn` | Danh sách cuộn, chỉ vẽ các dòng đang thấy. |
| `Canvas` | Vùng để tự vẽ hình bằng lệnh (`drawArc`, `drawLine`…). |
| `NavHost` / `route` | Vùng chứa màn hình hiện tại / "địa chỉ" của từng màn. |
| State hoisting | Đẩy trạng thái lên cấp trên; mảnh nhỏ chỉ nhận giá trị + báo sự kiện. |
| `epochDay` / `epochMillis` | Cách app lưu ngày (số ngày từ 1/1/1970) / thời điểm (mili-giây từ 1/1/1970). Xem `domain/TimeUtils.kt`. |
| Snackbar | Thông báo ngắn ở đáy màn hình (có thể kèm nút như "Hoàn tác"). |
| Bottom sheet | "Tờ giấy" trượt từ đáy lên (`QuickAddSheet`, `EventEditorSheet`). |

> **Quy ước của dự án:** chữ hiển thị cho người dùng luôn là **tiếng Việt**; tên hàm, tên biến là **tiếng Anh**.
