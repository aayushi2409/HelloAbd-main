package com.example.hello_abd.repository

import android.content.Context
import androidx.core.content.edit

data class AppSettings(
    val isWakeWordEnabled: Boolean,
    val isVoiceReplyEnabled: Boolean,
    val speechRate: Float,
    val speechPitch: Float
)

class SettingsRepository(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        val isWakeWordEnabled = sharedPreferences.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        val isVoiceReplyEnabled = sharedPreferences.getBoolean(KEY_VOICE_REPLY_ENABLED, true)
        val speechRate = sharedPreferences.getFloat(KEY_SPEECH_RATE, 1.0f)
        val speechPitch = sharedPreferences.getFloat(KEY_SPEECH_PITCH, 1.0f)
        return AppSettings(isWakeWordEnabled, isVoiceReplyEnabled, speechRate, speechPitch)
    }

    fun saveWakeWordEnabled(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_WAKE_WORD_ENABLED, isEnabled)
        }
    }

    fun saveVoiceReplyEnabled(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_VOICE_REPLY_ENABLED, isEnabled)
        }
    }

    fun saveSpeechRate(rate: Float) {
        sharedPreferences.edit {
            putFloat(KEY_SPEECH_RATE, rate)
        }
    }

    fun saveSpeechPitch(pitch: Float) {
        sharedPreferences.edit {
            putFloat(KEY_SPEECH_PITCH, pitch)
        }
    }

    companion object {
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_VOICE_REPLY_ENABLED = "voice_reply_enabled"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
    }
}
