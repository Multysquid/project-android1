package de.jw.einkaufsliste.feature.photo

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemSource
import de.jw.einkaufsliste.feature.camera.ItemDraft
import de.jw.einkaufsliste.feature.gemini.GeminiSettingsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PhotoState {
    /** Camera ready for the next photo. */
    data object Camera : PhotoState

    /** Shutter pressed, waiting for the image. */
    data object Capturing : PhotoState

    data class Recognizing(val photo: Bitmap) : PhotoState

    /** [fixInSettings]: key or model is wrong, trying again would fail the same way. */
    data class Failed(val photo: Bitmap?, val message: String, val fixInSettings: Boolean = false) : PhotoState

    /** Add sheet open; [recognized] is false when the user types the name in. */
    data class Editing(val photo: Bitmap?, val draft: ItemDraft, val recognized: Boolean) : PhotoState
}

class PhotoCaptureViewModel(private val container: AppContainer) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<PhotoState>(PhotoState.Camera)
    val state: StateFlow<PhotoState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off snackbar texts. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var job: Job? = null

    /** Shutter pressed. Returns false (take no photo) while one is already being handled. */
    fun startCapture(): Boolean {
        if (_state.value != PhotoState.Camera) return false
        _state.value = PhotoState.Capturing
        return true
    }

    fun onCaptured(raw: RawPhoto) {
        if (_state.value != PhotoState.Capturing) return
        job = viewModelScope.launch {
            val photo = try {
                withContext(Dispatchers.Default) { PhotoEncoding.prepare(raw) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (photo == null) {
                _state.value = PhotoState.Failed(null, MESSAGE_CAPTURE_FAILED)
                return@launch
            }
            _state.value = PhotoState.Recognizing(photo.preview)
            val result = container.geminiClient.recognize(photo.jpeg)
            val item = result.getOrNull()
            _state.value = if (item != null) {
                val categoryId = container.categoryRepository.idForSlug(item.categorySlug)
                PhotoState.Editing(photo.preview, ItemDraft(item.name, item.quantity.orEmpty(), categoryId), recognized = true)
            } else {
                val error = result.exceptionOrNull()
                PhotoState.Failed(
                    photo.preview,
                    error?.message ?: MESSAGE_RECOGNITION_FAILED,
                    fixInSettings = error is GeminiSettingsException,
                )
            }
        }
    }

    fun onCaptureFailed() {
        if (_state.value == PhotoState.Capturing) _state.value = PhotoState.Failed(null, MESSAGE_CAPTURE_FAILED)
    }

    /** Back to the camera: "Nochmal", cancel while recognizing, or sheet closed. */
    fun backToCamera() {
        job?.cancel()
        job = null
        _state.value = PhotoState.Camera
    }

    /** "Selbst eintippen" after a failed recognition. */
    fun typeManually() {
        val failed = _state.value as? PhotoState.Failed ?: return
        _state.value = PhotoState.Editing(failed.photo, ItemDraft(), recognized = false)
    }

    fun add(draft: ItemDraft) {
        val editing = _state.value as? PhotoState.Editing ?: return
        val name = draft.name.trim()
        if (name.isEmpty()) return
        _state.value = PhotoState.Camera
        viewModelScope.launch {
            val saved = withContext(NonCancellable) {
                runCatching {
                    container.itemRepository.add(
                        name = name,
                        categoryId = draft.categoryId,
                        quantity = draft.quantity,
                        source = if (editing.recognized) ItemSource.PHOTO else ItemSource.MANUAL,
                    )
                }.isSuccess
            }
            _messages.send(if (saved) "„$name“ hinzugefügt" else "„$name“ konnte nicht gespeichert werden.")
        }
    }

    private companion object {
        const val MESSAGE_CAPTURE_FAILED = "Das Foto konnte nicht aufgenommen werden."
        const val MESSAGE_RECOGNITION_FAILED = "Die Erkennung ist fehlgeschlagen."
    }
}
