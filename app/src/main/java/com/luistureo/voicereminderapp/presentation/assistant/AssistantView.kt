package com.luistureo.voicereminderapp.presentation.assistant

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView

class AssistantView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var currentFrameResId: Int? = null

    init {
        adjustViewBounds = false
        scaleType = ScaleType.FIT_CENTER
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    fun updateFrame(
        @DrawableRes frameResId: Int,
        animateTransition: Boolean
    ) {
        if (currentFrameResId == frameResId) return

        currentFrameResId = frameResId

        if (!animateTransition) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            setImageResource(frameResId)
            return
        }

        animate().cancel()
        animate()
            .alpha(0.86f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(80L)
            .withEndAction {
                setImageResource(frameResId)
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140L)
                    .start()
            }
            .start()
    }
}
