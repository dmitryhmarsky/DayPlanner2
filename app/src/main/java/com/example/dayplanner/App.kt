package com.example.dayplanner

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Настройки приложения. Сейчас — только выбранная тема. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_NIGHT = "night_mode"

    /** AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM / MODE_NIGHT_NO / MODE_NIGHT_YES */
    fun nightMode(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    /** Сохраняет выбор и сразу применяет его (экран при необходимости пересоздаётся). */
    fun setNightMode(context: Context, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NIGHT, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs.nightMode(this))
    }
}
