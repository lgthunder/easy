package com.lei.save_box.manager

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricHelper {

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricType(context: Context): String {
        return "生物识别"
    }
}
