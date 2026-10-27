package com.example.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notes.ui.nav.NotesApp
import com.example.notes.ui.theme.NotesTheme

/**
 * Activity duy nhất của app: điểm vào khi người dùng mở ứng dụng.
 * Toàn bộ giao diện được vẽ bằng Compose bên trong `setContent { ... }`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cho giao diện vẽ tràn ra dưới thanh trạng thái và thanh điều hướng của hệ thống.
        enableEdgeToEdge()

        // NotesApplication giữ các repository và "hộp thư" thông báo dùng chung cho cả app.
        val app = application as NotesApplication

        setContent {
            NotesTheme { // áp dụng màu và kiểu chữ của app
                NotesApp(messenger = app.messenger)
            }
        }
    }
}
