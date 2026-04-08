package com.luistureo.voicereminderapp.presentation.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luistureo.voicereminderapp.R

class AssistantActivity : ComponentActivity() {

    private lateinit var assistantView: AssistantView
    private lateinit var assistantDialogueBubble: AssistantDialogueBubbleView

    private lateinit var assistantAnimator: AssistantAnimator

    private var currentState: AssistantVisualState = AssistantVisualState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)

        assistantView = findViewById(R.id.assistantView)
        assistantDialogueBubble = findViewById(R.id.assistantDialogueBubble)

        assistantAnimator = AssistantAnimator(assistantView)
        renderAssistantState(AssistantVisualState.IDLE)
    }

    override fun onStart() {
        super.onStart()
        assistantAnimator.resume()
    }

    override fun onStop() {
        assistantAnimator.stop()
        assistantDialogueBubble.stopAllEffects()
        super.onStop()
    }

    private fun renderAssistantState(state: AssistantVisualState) {
        currentState = state
        assistantAnimator.render(state)
        when (state) {
            AssistantVisualState.IDLE -> assistantDialogueBubble.hideBubble()
            AssistantVisualState.THINKING -> {
                assistantDialogueBubble.showMessage("...", false, false)
            }

            AssistantVisualState.LISTENING,
            AssistantVisualState.SUCCESS -> {
                assistantDialogueBubble.showMessage(state.label, false, false)
            }

            AssistantVisualState.ASKING_TIME,
            AssistantVisualState.SPEAKING -> {
                assistantDialogueBubble.showMessage(state.label, true, false)
            }
        }
    }

    private val AssistantVisualState.label: String
        get() = when (this) {
            AssistantVisualState.IDLE -> "Estoy lista"
            AssistantVisualState.LISTENING -> "Te escucho"
            AssistantVisualState.THINKING -> "Un momento..."
            AssistantVisualState.ASKING_TIME -> "\u00BFA qu\u00E9 hora?"
            AssistantVisualState.SPEAKING -> "Hablando..."
            AssistantVisualState.SUCCESS -> "Listo, ya lo guard\u00E9"
        }
}
