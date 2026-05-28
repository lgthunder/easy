package com.lei.save_box.model

import java.util.UUID

enum class TaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class BackgroundTask(
    val id: String = UUID.randomUUID().toString(),
    val sourcePath: String = "",
    val outputPath: String = "",
    val startMs: Long = 0,
    val endMs: Long = 0,
    val sourceName: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val errorMessage: String = ""
)
