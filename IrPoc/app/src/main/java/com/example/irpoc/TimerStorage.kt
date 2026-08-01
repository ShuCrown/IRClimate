package com.example.irpoc

import android.content.Context
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.RepeatType
import org.json.JSONArray
import org.json.JSONObject

class TimerStorage(context: Context) {

    private val prefs = context.getSharedPreferences("timer_tasks", Context.MODE_PRIVATE)

    fun loadTasks(): List<AcTimerTask> {
        val json = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> parseTask(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTasks(tasks: List<AcTimerTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    private fun toJson(task: AcTimerTask): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("name", task.name)
        put("hour", task.hour)
        put("minute", task.minute)
        put("targetTemp", task.targetTemp)
        put("repeatType", task.repeatType.name)
        put("enabled", task.enabled)
    }

    private fun parseTask(obj: JSONObject): AcTimerTask = AcTimerTask(
        id = obj.getString("id"),
        name = obj.getString("name"),
        hour = obj.getInt("hour"),
        minute = obj.getInt("minute"),
        targetTemp = obj.getInt("targetTemp"),
        repeatType = try { RepeatType.valueOf(obj.getString("repeatType")) } catch (e: Exception) { RepeatType.WORKDAY },
        enabled = obj.getBoolean("enabled"),
    )

    companion object {
        private const val KEY_TASKS = "tasks"
    }
}