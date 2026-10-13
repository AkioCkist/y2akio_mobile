package com.example.y2akio_mobile.service

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One-time setup of the embedded Python + yt-dlp + FFmpeg (the first run unpacks ~30MB, which
 * takes a few seconds). Every yt-dlp call awaits [ensureReady]; the UI shows [state].
 */
class YtDlpRuntime(private val context: Context) {
    sealed interface State {
        data object Initializing : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow<State>(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun ensureReady() {
        if (_state.value == State.Ready) return
        mutex.withLock {
            if (_state.value == State.Ready) return
            _state.value = State.Initializing
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                }
                _state.value = State.Ready
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.toString())
                throw IllegalStateException("yt-dlp couldn't start: ${e.message}", e)
            }
        }
    }

    suspend fun version(): String = withContext(Dispatchers.IO) {
        ensureReady()
        YoutubeDL.getInstance().version(context) ?: "?"
    }

    /** Updates the embedded yt-dlp from GitHub releases. Returns true when a new version was installed. */
    suspend fun update(): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE) == YoutubeDL.UpdateStatus.DONE
    }
}
