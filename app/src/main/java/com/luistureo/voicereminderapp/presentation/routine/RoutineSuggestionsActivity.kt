package com.luistureo.voicereminderapp.presentation.routine

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.routine.RoutinePreferenceStore
import com.luistureo.voicereminderapp.core.routine.RoutineSuggestionCoordinator
import com.luistureo.voicereminderapp.core.speech.VoiceAssistantSpeaker
import com.luistureo.voicereminderapp.presentation.assistant.AssistantDialogueBubbleView
import com.luistureo.voicereminderapp.domain.routine.service.RoutineSuggestionPolicy
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import java.time.LocalDate
import kotlinx.coroutines.launch

class RoutineSuggestionsActivity : ComponentActivity() {
    private lateinit var container: LinearLayout
    private lateinit var empty: TextView
    private lateinit var bubble: AssistantDialogueBubbleView
    private lateinit var speaker: VoiceAssistantSpeaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine_suggestions)
        findViewById<ImageButton>(R.id.btnBackRoutineSuggestions).setOnClickListener { finish() }
        container = findViewById(R.id.containerRoutineSuggestions)
        empty = findViewById(R.id.tvRoutineSuggestionsEmpty)
        bubble = findViewById(R.id.bubbleRoutineSuggestion)
        speaker = VoiceAssistantSpeaker(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val repository = RoutineRepositoryImpl(ReminderDatabase.getDatabase(applicationContext).routineDao())
            val preferences = RoutinePreferenceStore(applicationContext)
            val created = RoutineSuggestionCoordinator(repository, preferences).evaluate()
            created?.let { suggestion ->
                val output = RoutineSuggestionPolicy.output(preferences.getSuggestionSettings(), false)
                if (output.first) bubble.showMessage(suggestion.message, animateText = true, playTypingSound = false)
                if (output.second) speaker.speakText(suggestion.message)
            }
            val suggestions = repository.getActiveSuggestions()
            container.removeAllViews()
            empty.visibility = if (suggestions.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            suggestions.forEach { suggestion ->
                val routineName = repository.getRoutineById(suggestion.routineId)?.name.orEmpty()
                val card = layoutInflater.inflate(R.layout.item_routine_suggestion, container, false) as MaterialCardView
                card.findViewById<TextView>(R.id.tvRoutineSuggestionTitle).text = routineName
                card.findViewById<TextView>(R.id.tvRoutineSuggestionMessage).text = suggestion.message
                card.findViewById<MaterialButton>(R.id.btnRoutineSuggestionPrimary).apply {
                    text = suggestion.primaryAction
                    setOnClickListener {
                        startActivity(Intent(this@RoutineSuggestionsActivity, RoutineEditorActivity::class.java)
                            .putExtra(RoutineEditorActivity.EXTRA_ROUTINE_ID, suggestion.routineId))
                    }
                }
                card.findViewById<MaterialButton>(R.id.btnRoutineSuggestionDismiss).setOnClickListener {
                    lifecycleScope.launch {
                        repository.dismissSuggestion(suggestion.id, LocalDate.now())
                        onResume()
                    }
                }
                container.addView(card)
            }
        }
    }

    override fun onDestroy() {
        bubble.stopAllEffects()
        speaker.shutdown()
        super.onDestroy()
    }
}
