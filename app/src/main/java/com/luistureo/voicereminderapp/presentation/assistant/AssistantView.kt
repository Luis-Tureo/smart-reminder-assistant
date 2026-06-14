package com.luistureo.voicereminderapp.presentation.assistant

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView

class AssistantView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val frontImageView = createFrameImageView()
    private val backImageView = createFrameImageView()
    private val drawableCache = LinkedHashMap<Int, Drawable.ConstantState>()

    private var activeImageView: AppCompatImageView = frontImageView
    private var inactiveImageView: AppCompatImageView = backImageView
    private var currentFrameResId: Int? = null
    private var transitionVersion: Int = 0
    private val transitionInterpolator = DecelerateInterpolator()

    init {
        clipChildren = true
        clipToPadding = true
        addView(backImageView)
        addView(frontImageView)
        backImageView.alpha = 0f
        backImageView.visibility = View.INVISIBLE
    }

    fun preloadFrames(@DrawableRes vararg frameResIds: Int) {
        frameResIds.forEach { frameResId ->
            getCachedDrawable(frameResId)
        }
    }

    fun updateFrame(
        @DrawableRes frameResId: Int,
        animateTransition: Boolean
    ) {
        if (currentFrameResId == frameResId) return

        currentFrameResId = frameResId
        transitionVersion++
        val localTransitionVersion = transitionVersion
        val frameDrawable = getCachedDrawable(frameResId) ?: return

        activeImageView.animate().cancel()
        inactiveImageView.animate().cancel()

        if (!animateTransition || activeImageView.drawable == null) {
            inactiveImageView.visibility = View.INVISIBLE
            inactiveImageView.alpha = 0f
            activeImageView.alpha = 1f
            activeImageView.visibility = View.VISIBLE
            activeImageView.setImageDrawable(frameDrawable)
            return
        }

        inactiveImageView.setImageDrawable(frameDrawable)
        inactiveImageView.alpha = 0f
        inactiveImageView.visibility = View.VISIBLE
        inactiveImageView.bringToFront()
        inactiveImageView.scaleX = 1f
        inactiveImageView.scaleY = 1f

        activeImageView.animate()
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(transitionInterpolator)
            .start()

        inactiveImageView.animate()
            .alpha(1f)
            .setDuration(150L)
            .setInterpolator(transitionInterpolator)
            .withEndAction {
                if (localTransitionVersion != transitionVersion) return@withEndAction

                activeImageView.visibility = View.INVISIBLE
                activeImageView.alpha = 0f

                val previousActive = activeImageView
                activeImageView = inactiveImageView
                inactiveImageView = previousActive
                activeImageView.bringToFront()
            }
            .start()
    }

    private fun createFrameImageView(): AppCompatImageView {
        return AppCompatImageView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            adjustViewBounds = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 1f
            visibility = View.VISIBLE
        }
    }

    private fun getCachedDrawable(@DrawableRes frameResId: Int): Drawable? {
        val constantState = drawableCache[frameResId]
            ?: AppCompatResources.getDrawable(context, frameResId)?.constantState?.also {
                drawableCache[frameResId] = it
            }

        return constantState?.newDrawable(resources)?.mutate()
            ?: AppCompatResources.getDrawable(context, frameResId)?.mutate()
    }
}
