package com.luistureo.voicereminderapp.presentation.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.luistureo.voicereminderapp.R

internal class AssistantDialogueSoundPlayer(
    context: Context
) {

    private val soundPool: SoundPool
    private val sampleId: Int
    private val activeStreamIds = LinkedHashSet<Int>()

    private var isLoaded: Boolean = false
    private var lastPlayTimestamp: Long = 0L

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(2)
            .build()

        sampleId = soundPool.load(context, R.raw.assistant_dialogue_blip, 1)

        soundPool.setOnLoadCompleteListener { _, loadedSampleId, status ->
            isLoaded = loadedSampleId == sampleId && status == 0
        }
    }

    fun playBlip(step: Int) {
        if (!isLoaded) return

        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTimestamp < 36L) return

        lastPlayTimestamp = now

        val playbackRate = when (step % 4) {
            0 -> 1.05f
            2 -> 0.97f
            else -> 1.0f
        }

        val streamId = soundPool.play(
            sampleId,
            0.11f,
            0.11f,
            1,
            0,
            playbackRate
        )

        if (streamId == 0) return

        activeStreamIds.add(streamId)
        trimActiveStreams()
    }

    fun stop() {
        activeStreamIds.forEach(soundPool::stop)
        activeStreamIds.clear()
    }

    fun release() {
        stop()
        soundPool.release()
    }

    private fun trimActiveStreams() {
        while (activeStreamIds.size > 2) {
            val streamId = activeStreamIds.firstOrNull() ?: break
            soundPool.stop(streamId)
            activeStreamIds.remove(streamId)
        }
    }
}
