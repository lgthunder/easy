package com.lei.save_box

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lei.save_box.databinding.ActivityFakeHomeBinding

class FakeHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeHomeBinding
    private var clickCount = 0
    private val requiredClicks = 5
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rootLayout.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 2000) {
                clickCount = 0
            }
            lastClickTime = now
            clickCount++

            if (clickCount >= requiredClicks) {
                clickCount = 0
                startActivity(android.content.Intent(this, VaultActivity::class.java))
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        clickCount = 0
    }
}
