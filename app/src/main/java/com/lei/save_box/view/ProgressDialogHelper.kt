package com.lei.save_box.view

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.lei.save_box.R

class ProgressDialogHelper(private val context: Context) {

    private val dialog: AlertDialog
    private val progressBar: ProgressBar
    private val tvProgress: TextView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null)
        progressBar = view.findViewById(R.id.progressBar)
        tvProgress = view.findViewById(R.id.tvProgress)
        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()
    }

    fun show(message: String, max: Int = 100) {
        tvProgress.text = message
        progressBar.max = max
        progressBar.progress = 0
        dialog.show()
    }

    fun updateProgress(progress: Int, message: String) {
        progressBar.progress = progress
        tvProgress.text = message
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
