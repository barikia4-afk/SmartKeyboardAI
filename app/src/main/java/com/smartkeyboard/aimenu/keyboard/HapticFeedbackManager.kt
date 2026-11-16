package com.smartkeyboard.aimenu.keyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.smartkeyboard.aimenu.data.KeyboardPreferences

class HapticFeedbackManager(private val context: Context) {

    private val prefs = KeyboardPreferences(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun performKeyFeedback(isSpecial: Boolean = false) {
        // Haptic feedback
        if (prefs.vibrationEnabled && vibrator?.hasVibrator() == true) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effectType = if (isSpecial) {
                        VibrationEffect.EFFECT_HEAVY_CLICK
                    } else {
                        VibrationEffect.EFFECT_CLICK
                    }
                    vibrator?.vibrate(VibrationEffect.createPredefined(effectType))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(if (isSpecial) 25L else 15L)
                }
            } catch (_: Exception) {
                // Ignore vibration failure
            }
        }

        // Sound feedback
        if (prefs.soundEnabled && audioManager != null) {
            try {
                val soundEffect = if (isSpecial) {
                    AudioManager.FX_KEYPRESS_DELETE
                } else {
                    AudioManager.FX_KEYPRESS_STANDARD
                }
                audioManager.playSoundEffect(soundEffect)
            } catch (_: Exception) {
                // Ignore audio failure
            }
        }
    }
}
