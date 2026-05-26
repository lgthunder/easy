package com.lei.save_box.manager

import android.content.Context

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("vault_settings", Context.MODE_PRIVATE)

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()
        }

    companion object {
        private const val KEY_BIOMETRIC = "biometric_enabled"
    }
}
