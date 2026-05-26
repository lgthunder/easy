package com.lei.save_box

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lei.save_box.databinding.ActivityFakeHomeBinding
import com.lei.save_box.manager.BiometricHelper
import com.lei.save_box.manager.FileManager
import com.lei.save_box.manager.SettingsManager
import com.lei.save_box.view.ProgressDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        handleShareIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        if (intent.type == null) return

        val uris = mutableListOf<Uri>()

        if (action == Intent.ACTION_SEND_MULTIPLE) {
            intent.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
        }

        if (uris.isEmpty()) return

        val fileManager = FileManager(this)
        val helper = ProgressDialogHelper(this)
        val total = uris.size
        val mainHandler = Handler(Looper.getMainLooper())

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                helper.show(getString(R.string.processing_share), 100)
            }
            var successCount = 0
            for (uri in uris) {
                withContext(Dispatchers.Main) {
                    helper.updateProgress(0, "$successCount / $total")
                }
                val ok = fileManager.copyToVault(uri) { progress ->
                    mainHandler.post {
                        helper.updateProgress(progress, "$successCount / $total")
                    }
                }
                if (ok) successCount++
                withContext(Dispatchers.Main) {
                    helper.updateProgress(100, "$successCount / $total")
                }
            }
            withContext(Dispatchers.Main) {
                helper.dismiss()
                if (successCount > 0) {
                    Toast.makeText(this@FakeHomeActivity, getString(R.string.share_import_success, successCount), Toast.LENGTH_SHORT).show()
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
