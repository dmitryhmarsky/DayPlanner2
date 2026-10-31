package com.example.dayplanner

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

// AppCompatActivity нужен, чтобы выбранная тема (светлая/тёмная) применялась ко всему приложению,
// включая системные диалоги, а не только к Compose-части.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerTheme {
                PlannerScreen()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // на случай, если заметка была изменена прямо перед сворачиванием
        TodayWidget.updateAll(this)
    }
}
