package com.example.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.notes.ui.NotesApp
import com.example.notes.ui.NotesViewModel
import com.example.notes.ui.NotesViewModelFactory
import com.example.notes.ui.theme.NotesTheme

class MainActivity : ComponentActivity() {
    private val notesViewModel: NotesViewModel by viewModels {
        NotesViewModelFactory((application as NotesApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotesTheme {
                NotesApp(viewModel = notesViewModel)
            }
        }
    }
}
