package com.luistureo.voicereminderapp.presentation.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.sin

internal class AssistantDialogueSoundPlayer(
    context: Context
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioAttributes: AudioAttributes
    private val audioFormat: AudioFormat
    private val activeTracks = LinkedHashSet<AudioTrack>()

    private var lastPlayTimestamp: Long = 0L

    init {
        audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
    }

    fun playBlip(step: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTimestamp < 36L) return

        lastPlayTimestamp = now

        val buffer = buildRetroBlipBuffer(step)
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(buffer.size * BYTES_PER_SAMPLE)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.setVolume(0.12f)
        track.play()

        activeTracks.add(track)
        trimActiveTracks()

        mainHandler.postDelayed({
            activeTracks.remove(track)
            runCatching {
                track.stop()
                track.release()
            }
        }, BLIP_DURATION_MS + 24L)
    }

    fun stop() {
        activeTracks.forEach { track ->
            runCatching {
                track.stop()
                track.release()
            }
        }
        activeTracks.clear()
    }

    fun release() {
        stop()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun trimActiveTracks() {
        while (activeTracks.size > MAX_STREAMS) {
            val track = activeTracks.firstOrNull() ?: break
            activeTracks.remove(track)
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    private fun buildRetroBlipBuffer(step: Int): ShortArray {
        val sampleCount = (SAMPLE_RATE * BLIP_DURATION_MS / 1000).toInt()
        val frequency = 560.0 + (step % 5) * 28.0

        return ShortArray(sampleCount) { index ->
            val progress = index.toFloat() / sampleCount.toFloat()
            val envelope = when {
                progress < 0.18f -> progress / 0.18f
                progress > 0.72f -> (1f - progress) / 0.28f
                else -> 1f
            }.coerceIn(0f, 1f)
            val wave = if (sin(2.0 * Math.PI * frequency * index / SAMPLE_RATE) >= 0.0) {
                1.0
            } else {
                -1.0
            }

            (wave * envelope * Short.MAX_VALUE * 0.2f).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val BLIP_DURATION_MS = 34L
        const val BYTES_PER_SAMPLE = 2
        const val MAX_STREAMS = 2
    }
}
