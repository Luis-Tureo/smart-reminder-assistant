package com.luistureo.voicereminderapp.presentation.assistant

import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import com.luistureo.voicereminderapp.R

class AssistantAnimator(
    private val assistantView: AssistantView
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentState: AssistantVisualState = AssistantVisualState.IDLE
    private var currentFaceState: AssistantFaceState = AssistantFaceState.RELAXED
    private var currentSequence: AssistantFrameSequence = sequenceFor(AssistantVisualState.IDLE)
    private var currentFrameIndex: Int = 0
    private var currentSequenceVersion: Int = 0
    private var isResumed: Boolean = false
    private var isSpeechPlaybackActive: Boolean = false

    fun render(
        state: AssistantVisualState,
        faceState: AssistantFaceState = state.defaultFaceState
    ) {
        val previousState = currentState
        currentState = state
        currentFaceState = faceState
        currentSequence = resolvedSequenceFor(state)
        currentFrameIndex = 0
        currentSequenceVersion++

        val shouldAnimateTransition = previousState != state
        showFrame(0, shouldAnimateTransition)
        scheduleNextFrame(currentSequenceVersion)
    }

    fun resume() {
        isResumed = true
        showFrame(currentFrameIndex, false)
        scheduleNextFrame(currentSequenceVersion)
    }

    fun stop() {
        isResumed = false
        currentSequenceVersion++
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun setSpeechPlaybackActive(isActive: Boolean) {
        isSpeechPlaybackActive = isActive

        if (currentState != AssistantVisualState.SPEAKING) return

        currentSequence = resolvedSequenceFor(currentState)
        currentFrameIndex = 0
        currentSequenceVersion++
        showFrame(0, false)
        scheduleNextFrame(currentSequenceVersion)
    }

    fun setSpeechIntensity(level: Float) {
        // Reservado para una futura seleccion de frames mas precisa.
    }

    private fun resolvedSequenceFor(state: AssistantVisualState): AssistantFrameSequence {
        return if (state == AssistantVisualState.SPEAKING && !isSpeechPlaybackActive) {
            sequenceFor(AssistantVisualState.IDLE)
        } else {
            sequenceFor(state)
        }
    }

    private fun scheduleNextFrame(version: Int) {
        mainHandler.removeCallbacksAndMessages(null)

        if (!isResumed || version != currentSequenceVersion) return
        if (currentSequence.frames.size <= 1) return

        mainHandler.postDelayed({
            advanceFrame(version)
        }, currentSequence.frameDurationMs)
    }

    private fun advanceFrame(version: Int) {
        if (!isResumed || version != currentSequenceVersion) return

        currentFrameIndex = (currentFrameIndex + 1) % currentSequence.frames.size
        showFrame(currentFrameIndex, false)
        scheduleNextFrame(version)
    }

    private fun showFrame(index: Int, animateTransition: Boolean) {
        assistantView.updateFrame(
            frameResId = currentSequence.frames[index],
            animateTransition = animateTransition
        )
    }

    private fun sequenceFor(state: AssistantVisualState): AssistantFrameSequence {
        return when (state) {
            AssistantVisualState.IDLE -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_idle),
                frameDurationMs = 820L
            )

            AssistantVisualState.LISTENING -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_listening),
                frameDurationMs = 760L
            )

            AssistantVisualState.THINKING -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_thinking),
                frameDurationMs = 780L
            )

            AssistantVisualState.ASKING_TIME -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_asking_time),
                frameDurationMs = 760L
            )

            AssistantVisualState.SUCCESS -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_success),
                frameDurationMs = 900L
            )

            AssistantVisualState.SPEAKING -> AssistantFrameSequence(
                frames = intArrayOf(
                    R.drawable.assistant_speaking_frame_2,
                    R.drawable.assistant_speaking_frame_1,
                    R.drawable.assistant_speaking_frame_2
                ),
                frameDurationMs = 150L
            )
        }
    }
}

private data class AssistantFrameSequence(
    val frames: IntArray,
    val frameDurationMs: Long
)

private val AssistantVisualState.defaultFaceState: AssistantFaceState
    get() = when (this) {
        AssistantVisualState.IDLE -> AssistantFaceState.RELAXED
        AssistantVisualState.LISTENING -> AssistantFaceState.ATTENTIVE
        AssistantVisualState.THINKING -> AssistantFaceState.NEUTRAL_PAUSE
        AssistantVisualState.ASKING_TIME -> AssistantFaceState.SURPRISE
        AssistantVisualState.SPEAKING -> AssistantFaceState.NATURAL_SPEAKING
        AssistantVisualState.SUCCESS -> AssistantFaceState.SATISFIED
    }
