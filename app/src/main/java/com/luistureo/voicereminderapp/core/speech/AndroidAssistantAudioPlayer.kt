package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AndroidAssistantAudioPlayer(
    context: Context
) : AssistantAudioPlayer {

    private val cacheDir = File(context.applicationContext.cacheDir, "assistant_tts").apply {
        mkdirs()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null

    override fun play(
        audio: AssistantTtsAudio,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
        onError: () -> Unit
    ): Boolean {
        if (!audio.isPlayable) return false

        stop()

        return runCatching {
            val file = File.createTempFile("assistant_tts_", audio.extension, cacheDir)
            file.writeBytes(audio.bytes)
            audioFile = file

            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { player ->
                    onStarted()
                    player.start()
                }
                setOnCompletionListener {
                    cleanup()
                    onFinished()
                }
                setOnErrorListener { _, _, _ ->
                    cleanup()
                    onError()
                    true
                }
                prepareAsync()
            }

            true
        }.getOrElse {
            cleanup()
            false
        }
    }

    override fun stop() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        audioFile?.delete()
        audioFile = null
    }

    override fun release() {
        stop()
    }

    private fun cleanup() {
        mediaPlayer?.let { player ->
            runCatching { player.release() }
        }
        mediaPlayer = null
        audioFile?.delete()
        audioFile = null
    }

    private val AssistantTtsAudio.extension: String
        get() = when {
            mimeType.contains("wav", ignoreCase = true) -> ".wav"
            mimeType.contains("ogg", ignoreCase = true) -> ".ogg"
            else -> ".mp3"
        }
}
