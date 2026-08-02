package com.example.irpoc

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
)

class AppUpdater(private val context: Context) {

    companion object {
        private const val GITHUB_API = "https://api.github.com/repos/ShuCrown/IRClimate/releases/latest"
        private const val GITHUB_TOKEN = "" // 如需私有仓库，在此填入 token
        private const val APK_FILE = "irpoc-update.apk"
        private const val PREF_LAST_TAG = "last_update_tag"
    }

    private val prefs = context.getSharedPreferences("app_updater", Context.MODE_PRIVATE)

    /** 检查是否有新版本，返回 UpdateInfo 或 null */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (GITHUB_TOKEN.isNotBlank()) {
                conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            }
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val bodyText = json.optString("body", "新版本可用")

            // 和上次记录的 tag 比较，避免重复提示
            val lastTag = prefs.getString(PREF_LAST_TAG, "") ?: ""
            if (tagName == lastTag) return@withContext null

            // 从 assets 中找到第一个 apk
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return@withContext null

            UpdateInfo(
                versionName = tagName,
                apkUrl = apkUrl,
                releaseNotes = bodyText,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 记录已提示/已安装的版本，避免重复弹窗 */
    fun dismissVersion(tag: String) {
        prefs.edit().putString(PREF_LAST_TAG, tag).apply()
    }

    /** 下载 APK 并触发安装 */
    fun downloadAndInstall(update: UpdateInfo) {
        // 删除旧文件
        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE)
        dest.delete()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("IR PoC 更新")
            .setDescription("正在下载 ${update.versionName} ...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(dest))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadManager.enqueue(request)

        // 注册广播接收下载完成
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id >= 0) {
                    ctx.unregisterReceiver(this)
                    installApk(dest)
                }
            }
        }
        context.registerReceiver(
            receiver,
            android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else null
        )
    }

    private fun installApk(file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "下载文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}