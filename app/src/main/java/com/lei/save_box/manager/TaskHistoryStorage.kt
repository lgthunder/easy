package com.lei.save_box.manager

import android.content.Context
import com.lei.save_box.model.BackgroundTask
import com.lei.save_box.model.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

class TaskHistoryStorage(context: Context) {

    private val prefs = context.getSharedPreferences("task_history", Context.MODE_PRIVATE)

    fun saveAll(tasks: List<BackgroundTask>) {
        val arr = JSONArray()
        for (task in tasks) {
            arr.put(task.toJson())
        }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    fun loadAll(): List<BackgroundTask> {
        val json = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<BackgroundTask>()
            for (i in 0 until arr.length()) {
                list.add(fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTask(task: BackgroundTask) {
        val list = loadAll().toMutableList()
        val idx = list.indexOfFirst { it.id == task.id }
        if (idx >= 0) list[idx] = task else list.add(task)
        saveAll(list)
    }

    fun removeTask(taskId: String) {
        val list = loadAll().toMutableList()
        list.removeAll { it.id == taskId }
        saveAll(list)
    }

    companion object {
        private const val KEY_TASKS = "tasks"

        fun fromJson(json: JSONObject): BackgroundTask {
            return BackgroundTask(
                id = json.optString("id", ""),
                sourcePath = json.optString("sourcePath", ""),
                outputPath = json.optString("outputPath", ""),
                startMs = json.optLong("startMs", 0),
                endMs = json.optLong("endMs", 0),
                sourceName = json.optString("sourceName", ""),
                status = try { TaskStatus.valueOf(json.optString("status", "PENDING")) } catch (_: Exception) { TaskStatus.COMPLETED },
                progress = json.optInt("progress", 0),
                createdAt = json.optLong("createdAt", 0),
                completedAt = json.optLong("completedAt", 0),
                errorMessage = json.optString("errorMessage", "")
            )
        }
    }

    private fun BackgroundTask.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("sourcePath", sourcePath)
            put("outputPath", outputPath)
            put("startMs", startMs)
            put("endMs", endMs)
            put("sourceName", sourceName)
            put("status", status.name)
            put("progress", progress)
            put("createdAt", createdAt)
            put("completedAt", completedAt)
            put("errorMessage", errorMessage)
        }
    }
}
