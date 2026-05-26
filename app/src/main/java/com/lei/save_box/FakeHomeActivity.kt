package com.lei.save_box

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.lei.save_box.databinding.ActivityFakeHomeBinding
import com.lei.save_box.manager.BiometricHelper
import com.lei.save_box.manager.SettingsManager
import java.util.concurrent.Executor

class FakeHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeHomeBinding
    private lateinit var settingsManager: SettingsManager
    private var clickCount = 0
    private val requiredClicks = 5
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsManager = SettingsManager(this)

        binding.rootLayout.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 2000) {
                clickCount = 0
            }
            lastClickTime = now
            clickCount++

            if (clickCount >= requiredClicks) {
                clickCount = 0
                if (settingsManager.isBiometricEnabled) {
                    authenticateAndEnter()
                } else {
                    enterVault()
                }
            }
        }
    }

    private fun authenticateAndEnter() {
        if (!BiometricHelper.canAuthenticate(this)) {
            Toast.makeText(this, R.string.biometric_not_available, Toast.LENGTH_LONG).show()
            return
        }

        val executor: Executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    enterVault()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@FakeHomeActivity, R.string.biometric_failed, Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@FakeHomeActivity, R.string.biometric_error, Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun enterVault() {
        startActivity(Intent(this, VaultActivity::class.java))
        finish()
    }

    override fun onPause() {
        super.onPause()
        clickCount = 0
    }
}
