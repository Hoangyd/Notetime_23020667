package com.example.notes.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Một thông báo ngắn hiện ở dưới màn hình (snackbar).
 * Nếu có [actionLabel] (ví dụ "Hoàn tác") thì snackbar có thêm nút; bấm nút sẽ gọi [onAction].
 */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * "Hộp thư" toàn app cho thông báo.
 * Bất kỳ ViewModel nào cũng có thể gọi `messenger.post("Đã xoá")`; khung ứng dụng (NotesApp)
 * lắng nghe [messages] và hiện snackbar. Nhờ vậy ViewModel không cần biết gì về giao diện.
 *
 * SharedFlow khác StateFlow ở chỗ: nó phát "sự kiện" một lần (thông báo), không giữ giá trị cuối.
 */
class UiMessenger {
    // MutableSharedFlow: phía trong (có thể ghi). extraBufferCapacity cho phép gửi nhanh mà không bị chặn.
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

    // Phía ngoài chỉ đọc (không ghi được).
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun post(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    fun post(message: UiMessage) {
        _messages.tryEmit(message)
    }
}
