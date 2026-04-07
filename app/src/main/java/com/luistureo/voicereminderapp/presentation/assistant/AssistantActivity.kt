package com.luistureo.voicereminderapp.presentation.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R

class AssistantActivity : ComponentActivity() {

    private lateinit var assistantView: AssistantView
    private lateinit var statusButton: MaterialButton

    private lateinit var assistantAnimator: AssistantAnimator

    private var currentState: AssistantVisualState = AssistantVisualState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)

        assistantView = findViewById(R.id.assistantView)
        statusButton = findViewById(R.id.btnAssistantStatus)

        assistantAnimator = AssistantAnimator(assistantView)
        renderAssistantState(AssistantVisualState.IDLE)
    }

    override fun onStart() {
        super.onStart()
        assistantAnimator.resume()
    }

    override fun onStop() {
        assistantAnimator.stop()
        super.onStop()
    }

    private fun renderAssistantState(state: AssistantVisualState) {
        currentState = state
        assistantAnimator.render(state)
        statusButton.text = state.label
    }

    private val AssistantVisualState.label: String
        get() = when (this) {
            AssistantVisualState.IDLE -> "Estoy lista"
            AssistantVisualState.LISTENING -> "Te escucho"
            AssistantVisualState.THINKING -> "Un momento..."
            AssistantVisualState.ASKING_TIME -> "¿A qué hora?"
            AssistantVisualState.SPEAKING -> ""
            AssistantVisualState.SUCCESS -> "Listo, ya lo guardé"
        }
}
