package com.streamer.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class Frame(val jpeg: ByteArray, val width: Int, val height: Int, val ts: Long)

object FrameBus {
    private val _frames = MutableSharedFlow<Frame>(replay = 1, extraBufferCapacity = 2)
    val frames: SharedFlow<Frame> = _frames.asSharedFlow()

    private val _latest = MutableStateFlow<Frame?>(null)
    val latest: StateFlow<Frame?> = _latest.asStateFlow()

    private val _motion = MutableSharedFlow<MotionEvent>(replay = 0, extraBufferCapacity = 32)
    val motion: SharedFlow<MotionEvent> = _motion.asSharedFlow()

    suspend fun publish(frame: Frame) {
        _latest.value = frame
        _frames.emit(frame)
    }

    suspend fun publishMotion(evt: MotionEvent) = _motion.emit(evt)
}

data class MotionEvent(val ts: Long, val magnitude: Double)
