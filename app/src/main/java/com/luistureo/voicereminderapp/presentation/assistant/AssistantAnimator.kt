package com.luistureo.voicereminderapp.presentation.assistant

import android.os.Handler
import android.os.Looper
import com.luistureo.voicereminderapp.R

class AssistantAnimator(
    private val assistantView: AssistantView
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentState: AssistantVisualState = AssistantVisualState.IDLE
    private var currentSequence: AssistantFrameSequence = sequenceFor(AssistantVisualState.IDLE)
    private var currentFrameIndex: Int = 0
    private var currentSequenceVersion: Int = 0
    private var isResumed: Boolean = false

    init {
        assistantView.preloadFrames(*ALL_FRAME_RES_IDS)
    }

    fun render(
        state: AssistantVisualState,
        faceState: AssistantFaceState = state.defaultFaceState
    ) {
        val previousState = currentState
        currentState = state
        currentSequence = sequenceFor(state)
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
        if (currentState != AssistantVisualState.SPEAKING) return

        currentSequence = sequenceFor(currentState)
        currentFrameIndex = 0
        currentSequenceVersion++
        showFrame(0, isActive)
        scheduleNextFrame(currentSequenceVersion)
    }

    fun setSpeechIntensity(level: Float) {
        // Reservado para una futura seleccion de frames mas precisa.
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
                frames = intArrayOf(R.drawable.assistant_state_idle),
                frameDurationMs = 820L
            )

            AssistantVisualState.LISTENING -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_state_listening),
                frameDurationMs = 760L
            )

            AssistantVisualState.THINKING -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_state_thinking),
                frameDurationMs = 780L
            )

            AssistantVisualState.ASKING_TIME -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_state_asking_time),
                frameDurationMs = 760L
            )

            AssistantVisualState.SUCCESS -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_state_success),
                frameDurationMs = 900L
            )

            AssistantVisualState.SPEAKING -> AssistantFrameSequence(
                frames = intArrayOf(R.drawable.assistant_state_speaking),
                frameDurationMs = 220L
            )
        }
    }

    private companion object {
        val ALL_FRAME_RES_IDS = intArrayOf(
            R.drawable.assistant_state_idle,
            R.drawable.assistant_state_listening,
            R.drawable.assistant_state_thinking,
            R.drawable.assistant_state_asking_time,
            R.drawable.assistant_state_success,
            R.drawable.assistant_state_speaking
        )
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
