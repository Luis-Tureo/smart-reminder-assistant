package com.luistureo.voicereminderapp.presentation.assistant

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.luistureo.voicereminderapp.R

class AssistantDialogueBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dialogueTextView: TextView
    private val soundPlayer = AssistantDialogueSoundPlayer(context.applicationContext)

    private var fullText: String = ""
    private var currentCharacterIndex: Int = 0
    private var isTyping: Boolean = false
    private var shouldPlayTypingSound: Boolean = false

    private val revealRunnable = object : Runnable {
        override fun run() {
            revealNextCharacter()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_assistant_dialogue_bubble, this, true)
        dialogueTextView = findViewById(R.id.tvAssistantDialogue)

        alpha = 0f
        scaleX = 0.985f
        scaleY = 0.985f
        visibility = INVISIBLE
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(::applyResponsiveBounds)
    }

    fun showMessage(
        text: String,
        animateText: Boolean,
        playTypingSound: Boolean
    ) {
        val normalizedText = text.trim()

        if (normalizedText.isBlank()) {
            hideBubble()
            return
        }

        if (
            visibility == VISIBLE &&
            fullText == normalizedText &&
            !isTyping &&
            currentCharacterIndex >= normalizedText.length
        ) {
            return
        }

        ensureVisible()
        applyResponsiveBounds()

        fullText = normalizedText
        shouldPlayTypingSound = animateText && playTypingSound

        if (animateText) {
            startTypewriter()
        } else {
            stopTypingEffects()
            currentCharacterIndex = fullText.length
            dialogueTextView.text = fullText
        }
    }

    fun hideBubble() {
        stopTypingEffects()

        if (visibility != VISIBLE && alpha <= 0f) {
            fullText = ""
            currentCharacterIndex = 0
            scaleX = 0.985f
            scaleY = 0.985f
            dialogueTextView.text = ""
            return
        }

        animate().cancel()
        animate()
            .alpha(0f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(140L)
            .withEndAction {
                visibility = INVISIBLE
                fullText = ""
                currentCharacterIndex = 0
                scaleX = 0.985f
                scaleY = 0.985f
                dialogueTextView.text = ""
            }
            .start()
    }

    fun completeTypingImmediately() {
        if (!isTyping) return

        mainHandler.removeCallbacks(revealRunnable)
        isTyping = false
        currentCharacterIndex = fullText.length
        dialogueTextView.text = fullText
        soundPlayer.stop()
    }

    fun isTypewriterRunning(): Boolean = isTyping

    fun stopAllEffects() {
        stopTypingEffects()

        if (fullText.isNotBlank()) {
            currentCharacterIndex = fullText.length
            dialogueTextView.text = fullText
        }
    }

    override fun onDetachedFromWindow() {
        stopTypingEffects()
        soundPlayer.release()
        super.onDetachedFromWindow()
    }

    private fun ensureVisible() {
        if (visibility == VISIBLE && alpha >= 1f) return

        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.985f
        scaleY = 0.985f
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(165L)
            .start()
    }

    private fun applyResponsiveBounds() {
        val parentView = parent as? View
        val parentWidth = parentView?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val parentHeight = parentView?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val resourceMaxWidth = resources.getDimensionPixelSize(R.dimen.assistant_dialogue_max_width)
        val resourceMaxHeight = resources.getDimensionPixelSize(R.dimen.assistant_dialogue_max_height)
        val minWidth = resources.getDimensionPixelSize(R.dimen.assistant_dialogue_min_width)
        val maxWidth = (parentWidth * 0.68f).toInt()
            .coerceAtMost(resourceMaxWidth)
            .coerceAtLeast(minWidth)
        val maxHeight = (parentHeight * 0.24f).toInt().coerceAtMost(resourceMaxHeight)

        dialogueTextView.width = maxWidth
        dialogueTextView.maxWidth = maxWidth
        dialogueTextView.maxHeight = maxHeight
        dialogueTextView.minWidth = minWidth
    }

    private fun startTypewriter() {
        stopTypingEffects()

        currentCharacterIndex = 0
        isTyping = true
        renderVisibleCharacters(0)
        scheduleNextCharacter(36L)
    }

    private fun revealNextCharacter() {
        if (!isTyping) return

        if (currentCharacterIndex >= fullText.length) {
            finishTyping()
            return
        }

        currentCharacterIndex++
        renderVisibleCharacters(currentCharacterIndex)

        val revealedCharacter = fullText[currentCharacterIndex - 1]
        if (
            shouldPlayTypingSound &&
            revealedCharacter.isLetterOrDigit() &&
            currentCharacterIndex % 2 == 0
        ) {
            soundPlayer.playBlip(currentCharacterIndex)
        }

        if (currentCharacterIndex >= fullText.length) {
            finishTyping()
            return
        }

        scheduleNextCharacter(delayFor(revealedCharacter))
    }

    private fun renderVisibleCharacters(visibleCharacterCount: Int) {
        if (fullText.isEmpty()) {
            dialogueTextView.text = ""
            return
        }

        if (visibleCharacterCount >= fullText.length) {
            dialogueTextView.text = fullText
            return
        }

        val spannableText = SpannableString(fullText)
        spannableText.setSpan(
            ForegroundColorSpan(Color.TRANSPARENT),
            visibleCharacterCount,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        dialogueTextView.text = spannableText
    }

    private fun scheduleNextCharacter(delayMs: Long) {
        mainHandler.removeCallbacks(revealRunnable)
        mainHandler.postDelayed(revealRunnable, delayMs)
    }

    private fun finishTyping() {
        isTyping = false
        dialogueTextView.text = fullText
        soundPlayer.stop()
        mainHandler.removeCallbacks(revealRunnable)
    }

    private fun stopTypingEffects() {
        isTyping = false
        mainHandler.removeCallbacks(revealRunnable)
        soundPlayer.stop()
    }

    private fun delayFor(character: Char): Long {
        return when {
            character == '.' -> 92L
            character == ',' -> 88L
            character == '!' || character == '?' -> 102L
            character == ':' || character == ';' -> 84L
            character.isWhitespace() -> 18L
            else -> 30L
        }
    }
}
