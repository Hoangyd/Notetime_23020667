package com.example.notes.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.data.NoteItemEntity
import com.example.notes.domain.Note
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.EmptyState
import com.example.notes.ui.components.Pill
import com.example.notes.ui.theme.LocalDarkTheme
import com.example.notes.ui.theme.noteContainerColor
import com.example.notes.ui.util.Fmt

// ===============================================================================================
// MÀN HÌNH "GHI CHÚ"
// Từ trên xuống: tiêu đề + các nút (dạng lưới/danh sách, lưu trữ, menu) → ô tìm kiếm → chip lọc →
// banner bản nháp (nếu có) → lưới ghi chú (đã ghim trước, rồi các ghi chú khác).
// ===============================================================================================

@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    viewModel: NotesViewModel = appViewModel { NotesViewModel(it) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Hai "bộ chọn tệp" của hệ thống: một để chọn nơi lưu tệp xuất, một để chọn tệp cần nhập.
    // Khi người dùng chọn xong, hệ thống trả về `uri` (địa chỉ tệp) cho khối lệnh bên dưới.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportToUri(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromUri(context.contentResolver, uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewNote,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Rounded.Add, contentDescription = "Ghi chú mới") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            NotesHeader(
                state = state,
                onSearchChange = viewModel::setSearch,
                onToggleGrid = viewModel::toggleGrid,
                onToggleArchived = viewModel::toggleArchivedView,
                onExport = { exportLauncher.launch("ghi-chu-${System.currentTimeMillis()}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
            )

            if (state.filters.isNotEmpty()) {
                FilterChipsRow(state = state, onToggle = viewModel::toggleFilter)
            }

            // Banner "Bản nháp chưa lưu" nếu lần trước thoát app khi đang soạn dở.
            state.draft?.let { draft ->
                DraftBanner(
                    draft = draft,
                    onContinue = { onOpenNote(draft.id) },
                    onDiscard = { viewModel.discardDraft(draft) }
                )
            }

            if (state.isEmpty && state.loaded) {
                EmptyNotes(state)
            } else {
                NotesGrid(state = state, viewModel = viewModel, onOpenNote = onOpenNote)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Đầu trang: tiêu đề, nút, ô tìm kiếm
// ---------------------------------------------------------------------------------------------

@Composable
private fun NotesHeader(
    state: NotesState,
    onSearchChange: (String) -> Unit,
    onToggleGrid: () -> Unit,
    onToggleArchived: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    // Menu ba chấm đang mở hay không – trạng thái cục bộ.
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.showArchived) "Lưu trữ" else "Ghi chú",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            // Đổi giữa dạng lưới và dạng danh sách
            IconButton(onClick = onToggleGrid) {
                Icon(
                    imageVector = if (state.grid) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewModule,
                    contentDescription = if (state.grid) "Dạng danh sách" else "Dạng lưới"
                )
            }
            // Xem ghi chú đã lưu trữ (tô màu chính khi đang bật)
            IconButton(onClick = onToggleArchived) {
                Icon(
                    imageVector = Icons.Rounded.Archive,
                    contentDescription = "Xem lưu trữ",
                    tint = if (state.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Menu ba chấm: xuất / nhập
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Thêm")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Xuất ra tệp JSON") },
                        leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                        onClick = { menuOpen = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Nhập từ tệp JSON") },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = { menuOpen = false; onImport() }
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm theo tiêu đề, nội dung, nhãn…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Xoá tìm kiếm")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun FilterChipsRow(state: NotesState, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (filter in state.filters) {
            FilterChip(
                selected = state.selectedFilter == filter.key,
                onClick = { onToggle(filter.key) },
                // Nhãn có tiền tố # cho dễ phân biệt với danh mục.
                label = { Text(if (filter.isLabel) "#${filter.text}" else filter.text) }
            )
        }
    }
}

@Composable
private fun EmptyNotes(state: NotesState) {
    val isSearching = state.search.isNotBlank() || state.selectedFilter != null
    EmptyState(
        icon = Icons.AutoMirrored.Rounded.StickyNote2,
        title = when {
            state.showArchived -> "Chưa có ghi chú lưu trữ"
            isSearching -> "Không tìm thấy ghi chú"
            else -> "Chưa có ghi chú nào"
        },
        subtitle = "Bấm dấu + để viết ghi chú hoặc tạo checklist mua sắm."
    )
}

@Composable
private fun DraftBanner(draft: Note, onContinue: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Bản nháp chưa lưu",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = draft.title.ifBlank { draft.content.take(40).ifBlank { "Ghi chú không tên" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onContinue) { Text("Mở tiếp") }
            IconButton(onClick = onDiscard) { Icon(Icons.Rounded.Delete, contentDescription = "Bỏ bản nháp") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lưới ghi chú
// ---------------------------------------------------------------------------------------------

/**
 * Lưới "so le" (staggered): các thẻ có chiều cao khác nhau xếp khít vào nhau như Google Keep.
 * `StaggeredGridCells.Fixed(2)` = 2 cột; `Fixed(1)` = 1 cột (dạng danh sách).
 */
@Composable
private fun NotesGrid(state: NotesState, viewModel: NotesViewModel, onOpenNote: (Long) -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (state.grid) 2 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mục "Đã ghim" – tiêu đề chiếm trọn một hàng (FullLine) nên không bị chia cột.
        if (state.pinned.isNotEmpty()) {
            item(key = "pinned-header", span = StaggeredGridItemSpan.FullLine) {
                SectionLabel("Đã ghim")
            }
            items(state.pinned, key = { "note-${it.id}" }) { note ->
                NoteCard(note, state.items[note.id].orEmpty(), state.grid, viewModel, onOpenNote)
            }
            // Chỉ cần tiêu đề "Khác" khi có cả hai nhóm.
            if (state.others.isNotEmpty()) {
                item(key = "others-header", span = StaggeredGridItemSpan.FullLine) {
                    SectionLabel("Khác")
                }
            }
        }

        items(state.others, key = { "note-${it.id}" }) { note ->
            NoteCard(note, state.items[note.id].orEmpty(), state.grid, viewModel, onOpenNote)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Thẻ một ghi chú. Bấm = mở; nhấn giữ = mở menu (ghim / lưu trữ / xoá).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    items: List<NoteItemEntity>,
    grid: Boolean,
    viewModel: NotesViewModel,
    onOpen: (Long) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    val dark = LocalDarkTheme.current
    val containerColor = noteContainerColor(note.color, dark, MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(20.dp)

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                // combinedClickable cho phép phân biệt "bấm" và "nhấn giữ".
                .combinedClickable(onClick = { onOpen(note.id) }, onLongClick = { menuOpen = true })
                // Ghi chú không có màu riêng thì thêm viền mảnh để tách khỏi nền.
                .then(
                    if (note.color == 0) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier
                ),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteTitleRow(note)

                if (note.isChecklist && items.isNotEmpty()) {
                    ChecklistPreview(items = items, onToggleItem = viewModel::toggleItem)
                } else if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (grid) 7 else 3, // dạng lưới cho hiện nhiều dòng hơn
                        overflow = TextOverflow.Ellipsis
                    )
                }

                NoteMetaRow(note)
            }
        }

        NoteContextMenu(
            expanded = menuOpen,
            note = note,
            onDismiss = { menuOpen = false },
            onTogglePinned = { viewModel.togglePinned(note.id) },
            onToggleArchived = { viewModel.toggleArchived(note) },
            onDelete = { viewModel.delete(note, items) }
        )
    }
}

/** Tiêu đề ghi chú, kèm biểu tượng ghim nếu đã ghim. */
@Composable
private fun NoteTitleRow(note: Note) {
    Row(verticalAlignment = Alignment.Top) {
        if (note.title.isNotBlank()) {
            Text(
                note.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Box(Modifier.weight(1f)) // giữ chỗ để biểu tượng ghim vẫn nằm bên phải
        }
        if (note.isPinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = "Đã ghim",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Xem trước tối đa 5 mục của checklist, tick được ngay trên thẻ. */
@Composable
private fun ChecklistPreview(items: List<NoteItemEntity>, onToggleItem: (NoteItemEntity) -> Unit) {
    val maxShown = 5
    for (item in items.take(maxShown)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggleItem(item) },
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (items.size > maxShown) {
        Text(
            "+${items.size - maxShown} mục nữa",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Hàng nhãn nhỏ cuối thẻ: danh mục, tối đa 2 nhãn, liên kết công việc / ngày. */
@Composable
private fun NoteMetaRow(note: Note) {
    val hasAnything = note.category.isNotBlank() || note.labels.isNotEmpty() ||
        note.linkedTaskId != null || note.linkedDate != null
    if (!hasAnything) return

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (note.category.isNotBlank()) {
            Pill(note.category, MaterialTheme.colorScheme.primary)
        }
        for (label in note.labels.take(2)) {
            Pill("#$label", MaterialTheme.colorScheme.secondary)
        }
        if (note.linkedTaskId != null) {
            Pill("Công việc", MaterialTheme.colorScheme.tertiary, icon = Icons.Rounded.TaskAlt)
        }
        note.linkedDate?.let { day ->
            Pill(Fmt.shortDay(day), MaterialTheme.colorScheme.tertiary, icon = Icons.Rounded.CalendarMonth)
        }
    }
}

/** Menu hiện khi nhấn giữ thẻ ghi chú. */
@Composable
private fun NoteContextMenu(
    expanded: Boolean,
    note: Note,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (note.isPinned) "Bỏ ghim" else "Ghim") },
            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
            onClick = { onDismiss(); onTogglePinned() }
        )
        DropdownMenuItem(
            text = { Text(if (note.isArchived) "Khôi phục" else "Lưu trữ") },
            leadingIcon = {
                Icon(if (note.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, contentDescription = null)
            },
            onClick = { onDismiss(); onToggleArchived() }
        )
        DropdownMenuItem(
            text = { Text("Xoá") },
            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}
