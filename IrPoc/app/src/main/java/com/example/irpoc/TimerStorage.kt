package com.example.irpoc

import android.content.Context
import com.example.irpoc.model.AcFan
import com.example.irpoc.model.AcMode
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
        put("mode", task.mode.name)
        put("fan", task.fan.name)
        put("sleep", task.sleep)
        put("quiet", task.quiet)
        put("repeatType", task.repeatType.name)
        put("enabled", task.enabled)
        put("alarmTime", task.alarmTime)
    }

    private fun parseTask(obj: JSONObject): AcTimerTask = AcTimerTask(
        id = obj.getString("id"),
        name = obj.getString("name"),
        hour = obj.getInt("hour"),
        minute = obj.getInt("minute"),
        targetTemp = obj.getInt("targetTemp"),
        mode = try { AcMode.valueOf(obj.optString("mode", AcMode.COOL.name)) } catch (e: Exception) { AcMode.COOL },
        fan = try { AcFan.valueOf(obj.optString("fan", AcFan.AUTO.name)) } catch (e: Exception) { AcFan.AUTO },
        sleep = obj.optBoolean("sleep", false),
        quiet = obj.optBoolean("quiet", false),
        repeatType = try { RepeatType.valueOf(obj.getString("repeatType")) } catch (e: Exception) { RepeatType.WORKDAY },
        enabled = obj.getBoolean("enabled"),
        alarmTime = obj.optLong("alarmTime", 0),
    )

    companion object {
        private const val KEY_TASKS = "tasks"
        private const val KEY_AC_STATE = "ac_state"
    }

    // ── AC 下发状态持久化 ──────────────────────────────────
    fun loadAcState(): AcState? {
        val json = prefs.getString(KEY_AC_STATE, null) ?: return null
        return try {
            val obj = JSONObject(json)
            AcState(
                powerOn = obj.getBoolean("powerOn"),
                targetTemp = obj.getInt("targetTemp"),
                mode = obj.getInt("mode"),
                fan = obj.getInt("fan"),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveAcState(state: AcState) {
        val obj = JSONObject().apply {
            put("powerOn", state.powerOn)
            put("targetTemp", state.targetTemp)
            put("mode", state.mode)
            put("fan", state.fan)
        }
        prefs.edit().putString(KEY_AC_STATE, obj.toString()).apply()
    }
}

data class AcState(
    val powerOn: Boolean,
    val targetTemp: Int,
    val mode: Int,
    val fan: Int,
)