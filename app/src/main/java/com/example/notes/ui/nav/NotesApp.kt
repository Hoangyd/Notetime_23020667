package com.example.notes.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notes.ui.UiMessenger
import com.example.notes.ui.calendar.CalendarScreen
import com.example.notes.ui.insights.InsightsScreen
import com.example.notes.ui.notes.NoteEditorScreen
import com.example.notes.ui.notes.NotesScreen
import com.example.notes.ui.tasks.TaskEditorScreen
import com.example.notes.ui.tasks.TasksScreen
import com.example.notes.ui.today.TodayScreen
import kotlinx.coroutines.flow.collectLatest

// ---------------------------------------------------------------------------------------------
// Khai báo "đường dẫn" (route) của các màn hình
// ---------------------------------------------------------------------------------------------

/**
 * 5 tab ở thanh dưới. Mỗi tab có: đường dẫn (route), tên hiển thị và biểu tượng.
 * Thêm một tab mới = thêm một dòng ở đây + một khối `composable(...)` bên dưới.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Hôm nay", Icons.Rounded.WbSunny),
    CALENDAR("calendar", "Lịch", Icons.Rounded.CalendarMonth),
    TASKS("tasks", "Công việc", Icons.Rounded.TaskAlt),
    NOTES("notes", "Ghi chú", Icons.AutoMirrored.Rounded.StickyNote2),
    INSIGHTS("insights", "Thống kê", Icons.Rounded.Insights)
}

/**
 * Các màn hình không nằm trên thanh tab (form soạn thảo).
 * Đường dẫn có "tham số" trong dấu ngoặc nhọn, ví dụ "task/{id}" -> "task/5" mở công việc số 5.
 * id = 0 nghĩa là TẠO MỚI.
 */
private object Routes {
    const val TASK_EDITOR = "task/{id}"
    const val NOTE_EDITOR = "note/{id}?taskId={taskId}&date={date}"

    const val ARG_ID = "id"
    const val ARG_TASK = "taskId"
    const val ARG_DATE = "date"

    // Giá trị "không có" cho các tham số tuỳ chọn của ghi chú.
    const val NO_TASK = -1L
    const val NO_DATE = Long.MIN_VALUE

    fun task(id: Long) = "task/$id"

    /** Mở ghi chú; có thể kèm sẵn công việc / ngày muốn liên kết khi tạo mới. */
    fun note(id: Long, taskId: Long = NO_TASK, date: Long = NO_DATE) = "note/$id?taskId=$taskId&date=$date"
}

// Hiệu ứng khi mở/đóng form soạn thảo: trượt nhẹ từ phải sang + mờ dần.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.editorEnter(): EnterTransition =
    slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.editorPopExit(): ExitTransition =
    slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()

// ---------------------------------------------------------------------------------------------
// Khung ứng dụng
// ---------------------------------------------------------------------------------------------

/**
 * Khung của cả app, gồm 3 phần:
 *  1. Thanh tab dưới cùng (chỉ hiện ở 5 màn chính).
 *  2. Thanh thông báo (snackbar) nhận tin từ [messenger].
 *  3. NavHost: vùng chứa màn hình hiện tại, đổi theo đường dẫn.
 */
@Composable
fun NotesApp(messenger: UiMessenger) {
    // navController: "người điều hướng", nhớ ngăn xếp các màn hình đã mở.
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Màn hình đang hiện là gì? Dùng để biết có nên hiện thanh tab không.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = Tab.entries.any { it.route == currentRoute }

    ShowMessages(messenger, snackbarHostState)

    // Scaffold = khung chuẩn của Material: có chỗ cho thanh dưới, snackbar, nội dung.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // từng màn hình tự xử lý vùng thanh hệ thống
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                BottomTabs(currentRoute = currentRoute, onTabSelected = { navController.openTab(it) })
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.TODAY.route, // màn hình mở đầu tiên
            modifier = Modifier.padding(innerPadding), // chừa chỗ cho thanh tab
            // Mặc định NavHost mờ dần 700ms và vẽ cùng lúc cả hai màn → giật khi đổi tab.
            // Màn cũ biến mất ngay, màn mới hiện nhanh (120ms).
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { ExitTransition.None }
        ) {
            // ----- 5 màn hình chính -----
            // `statusBarsPadding()` chừa chỗ cho thanh trạng thái (giờ, pin) ở đỉnh màn hình.

            composable(Tab.TODAY.route) {
                Box(Modifier.statusBarsPadding()) {
                    TodayScreen(
                        onOpenTask = { navController.navigate(Routes.task(it)) },
                        onNewTask = { navController.navigate(Routes.task(0)) },
                        onNewNote = { navController.navigate(Routes.note(0)) },
                        onOpenNote = { navController.navigate(Routes.note(it)) },
                        onOpenCalendar = { navController.openTab(Tab.CALENDAR.route) }
                    )
                }
            }

            composable(Tab.CALENDAR.route) {
                Box(Modifier.statusBarsPadding()) {
                    CalendarScreen(onOpenTask = { navController.navigate(Routes.task(it)) })
                }
            }

            composable(Tab.TASKS.route) {
                Box(Modifier.statusBarsPadding()) {
                    TasksScreen(
                        onOpenTask = { navController.navigate(Routes.task(it)) },
                        onNewTask = { navController.navigate(Routes.task(0)) }
                    )
                }
            }

            composable(Tab.NOTES.route) {
                Box(Modifier.statusBarsPadding()) {
                    NotesScreen(
                        onOpenNote = { navController.navigate(Routes.note(it)) },
                        onNewNote = { navController.navigate(Routes.note(0)) }
                    )
                }
            }

            composable(Tab.INSIGHTS.route) {
                Box(Modifier.statusBarsPadding()) { InsightsScreen() }
            }

            // ----- Form soạn công việc -----
            composable(
                route = Routes.TASK_EDITOR,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
                enterTransition = { editorEnter() },
                popExitTransition = { editorPopExit() }
            ) { entry ->
                // Lấy tham số `id` từ đường dẫn.
                val taskId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L

                // navigationBarsPadding(): chừa chỗ cho thanh điều hướng của hệ thống ở đáy.
                Box(Modifier.navigationBarsPadding()) {
                    TaskEditorScreen(
                        taskId = taskId,
                        onBack = { navController.popBackStack() }, // popBackStack = quay lại màn trước
                        onOpenNote = { navController.navigate(Routes.note(it)) },
                        onNewNoteForTask = { navController.navigate(Routes.note(0, taskId = it)) }
                    )
                }
            }

            // ----- Form soạn ghi chú -----
            composable(
                route = Routes.NOTE_EDITOR,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_TASK) { type = NavType.LongType; defaultValue = Routes.NO_TASK },
                    navArgument(Routes.ARG_DATE) { type = NavType.LongType; defaultValue = Routes.NO_DATE }
                ),
                enterTransition = { editorEnter() },
                popExitTransition = { editorPopExit() }
            ) { entry ->
                val args = entry.arguments
                val noteId = args?.getLong(Routes.ARG_ID) ?: 0L
                // Đổi giá trị "không có" thành null để màn hình dễ hiểu.
                val presetTaskId = args?.getLong(Routes.ARG_TASK)?.takeIf { it != Routes.NO_TASK }
                val presetDate = args?.getLong(Routes.ARG_DATE)?.takeIf { it != Routes.NO_DATE }

                Box(Modifier.navigationBarsPadding()) {
                    NoteEditorScreen(
                        noteId = noteId,
                        presetTaskId = presetTaskId,
                        presetDate = presetDate,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/** Thanh tab ở đáy màn hình. */
@Composable
private fun BottomTabs(currentRoute: String?, onTabSelected: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onTabSelected(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}

/**
 * Lắng nghe "hộp thư" thông báo và hiện từng tin dưới dạng snackbar.
 * Nếu thông báo có nút (ví dụ "Hoàn tác") và người dùng bấm, gọi hàm `onAction` của thông báo đó.
 */
@Composable
private fun ShowMessages(messenger: UiMessenger, snackbarHostState: SnackbarHostState) {
    // LaunchedEffect: chạy khối code này một lần khi composable xuất hiện (và chạy lại nếu `messenger` đổi).
    LaunchedEffect(messenger) {
        // collectLatest: có tin mới đến thì bỏ tin cũ đang hiện.
        messenger.messages.collectLatest { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.onAction?.invoke()
            }
        }
    }
}

/**
 * Chuyển sang một tab. Các tuỳ chọn giúp chuyển tab "đúng kiểu" của ứng dụng có thanh tab:
 *  - popUpTo: không chất chồng nhiều màn hình khi bấm tab liên tục.
 *  - launchSingleTop: bấm lại tab đang mở thì không mở bản sao.
 *  - saveState / restoreState: nhớ vị trí cuộn của từng tab.
 */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
