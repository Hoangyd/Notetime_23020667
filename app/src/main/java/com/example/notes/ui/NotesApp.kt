package com.example.notes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notes.ui.screen.NoteEditorScreen
import com.example.notes.ui.screen.NotesListScreen
import kotlinx.coroutines.flow.collectLatest

@Composable
fun NotesApp(viewModel: NotesViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(context.contentResolver, uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(context.contentResolver, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NotesDestination.List.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = NotesDestination.List.route) {
                NotesListScreen(
                    uiState = uiState,
                    onSearchChange = viewModel::updateSearchQuery,
                    onCategorySelected = viewModel::updateSelectedCategory,
                    onToggleArchivedFilter = viewModel::toggleArchiveFilter,
                    onTogglePinned = viewModel::togglePinned,
                    onToggleArchived = viewModel::toggleArchived,
                    onDelete = viewModel::deleteNote,
                    onAddNote = {
                        navController.navigate(NotesDestination.Editor.createRoute())
                    },
                    onEditNote = { noteId ->
                        navController.navigate(NotesDestination.Editor.createRoute(noteId))
                    },
                    onExport = {
                        createDocumentLauncher.launch("notes-${System.currentTimeMillis()}.json")
                    },
                    onImport = {
                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                )
            }

            composable(
                route = NotesDestination.Editor.route,
                arguments = listOf(
                    navArgument(NotesDestination.Editor.NOTE_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong(NotesDestination.Editor.NOTE_ID_ARG) ?: -1L
                if (noteId > 0L) {
                    val existingNote by viewModel.observeNote(noteId)
                        .collectAsStateWithLifecycle(initialValue = null)

                    NoteEditorScreen(
                        note = existingNote,
                        onSave = { title, content, category, onResult ->
                            viewModel.saveNote(
                                noteId = noteId,
                                title = title,
                                content = content,
                                category = category,
                                onResult = onResult
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    NoteEditorScreen(
                        note = null,
                        onSave = { title, content, category, onResult ->
                            viewModel.saveNote(
                                noteId = null,
                                title = title,
                                content = content,
                                category = category,
                                onResult = onResult
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private sealed class NotesDestination(val route: String) {
    data object List : NotesDestination("notes")

    data object Editor : NotesDestination("editor/{noteId}") {
        const val NOTE_ID_ARG = "noteId"

        fun createRoute(noteId: Long = -1L): String {
            return "editor/$noteId"
        }
    }
}
