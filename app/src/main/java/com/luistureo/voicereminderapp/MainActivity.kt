package com.luistureo.voicereminderapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luistureo.voicereminderapp.presentation.calendar.CalendarActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, CalendarActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}
