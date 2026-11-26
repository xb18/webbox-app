package com.xb18.webbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * App 更新：检查更新源 → 下载 APK → 安装。
 * 更新源默认取自建更新索引，可通过 setUpdateSource 改为任意 JSON 接口。
 * 所有能力经 MainActivity 的 JS API 暴露，这里只做原生实现，不含 UI。
 */
class Updater(
    private val activity: Activity,
    private val callback: Callback,
) {
    interface Callback {
        fun onEvent(type: String, data: JSONObject)
    }

    private val prefs: SharedPreferences =
        activity.getSharedPreferences("webbox_shell_update", Context.MODE_PRIVATE)
    private val executor = Executors.newFixedThreadPool(2)
    private val apkFile: File
        get() = File(activity.getExternalFilesDir("update"), "update.apk")

    fun getUpdateSource(): String = prefs.getString(KEY_SOURCE, DEFAULT_SOURCE) ?: DEFAULT_SOURCE

    fun setUpdateSource(url: String) {
        prefs.edit().putString(KEY_SOURCE, url.trim()).apply()
    }

    fun resetUpdateSource() {
        prefs.edit().remove(KEY_SOURCE).apply()
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
        }
    }

    /**
     * 检查更新。source 为空时用已保存的自定义源，仍为空则回退 GitHub Releases。
     * 自定义源返回 JSON：{version, url, notes?, size?}；version 为版本号字符串。
     */
    fun checkUpdate(source: String = "") {
        executor.execute {
            val result = JSONObject()
            val explicit = source.trim().ifEmpty { getUpdateSource() }
            try {
                val info = if (explicit.isEmpty()) fetchGithub() else fetchCustom(explicit)
                result.put("ok", true)
                if (info == null || !isNewer(info.optString("version"))) {
                    result.put("hasUpdate", false)
                    if (info != null) result.put("current", BuildConfig.VERSION_NAME)
                } else {
                    result.put("hasUpdate", true)
                    result.put("version", info.optString("version"))
                    result.put("current", BuildConfig.VERSION_NAME)
                    result.put("url", info.optString("url"))
                    result.put("notes", info.optString("notes"))
                    result.put("size", info.optLong("size", 0))
                    result.put("source", if (explicit.isEmpty()) "github" else "custom")
                }
            } catch (e: Exception) {
                result.put("ok", false).put("error", e.message ?: "检查失败")
            }
            callback.onEvent(EVENT_CHECK, result)
        }
    }

    private fun fetchGithub(): JSONObject? {
        val url = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=20")
        val text = get(url)
        val releases = JSONArray(text)
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            if (!rel.optString("tag_name", "").startsWith("android-v")) continue
            val assets = rel.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            var size = 0L
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name", "").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url", "")
                    size = a.optLong("size", 0)
                    break
                }
            }
            return JSONObject()
                .put("version", rel.getString("tag_name").removePrefix("android-v"))
                .put("url", apkUrl)
                .put("notes", rel.optString("body", ""))
                .put("size", size)
        }
        return null
    }

    private fun fetchCustom(endpoint: String): JSONObject {
        val text = get(URL(endpoint))
        return JSONObject(text)
    }

    private fun get(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "WebBoxShell/${BuildConfig.VERSION_NAME}")
        return conn.inputStream.bufferedReader().use(BufferedReader::readText).also { conn.disconnect() }
    }

    fun downloadUpdate(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            callback.onEvent(EVENT_DOWNLOAD_ERROR, JSONObject().put("error", "非法下载地址"))
            return
        }
        executor.execute {
            try {
                val file = apkFile
                file.parentFile?.mkdirs()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "WebBoxShell/${BuildConfig.VERSION_NAME}")
                val total = conn.contentLength
                val input = conn.inputStream
                val out = FileOutputStream(file, false)
                val buf = ByteArray(16 * 1024)
                var read: Int
                var done = 0L
                var last = -1
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != last && pct % 5 == 0) {
                            last = pct
                            callback.onEvent(EVENT_PROGRESS, JSONObject().put("percent", pct))
                        }
                    }
                }
                out.flush(); out.close(); input.close(); conn.disconnect()
                callback.onEvent(EVENT_DOWNLOAD_DONE, JSONObject().put("size", file.length()))
            } catch (e: Exception) {
                callback.onEvent(EVENT_DOWNLOAD_ERROR, JSONObject().put("error", e.message ?: "下载失败"))
            }
        }
    }

    fun installUpdate() {
        val file = apkFile
        if (!file.exists() || file.length() == 0L) {
            callback.onEvent(EVENT_INSTALL_ERROR, JSONObject().put("error", "安装包不存在，请先下载"))
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            callback.onEvent(EVENT_INSTALL_ERROR, JSONObject().put("error", e.message ?: "安装失败"))
        }
    }

    private fun isNewer(version: String): Boolean = compareVersion(version, BuildConfig.VERSION_NAME) > 0

    /** 语义版本比较：1.10.2 > 1.9.9；非数字段按 0 处理。 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    companion object {
        private const val KEY_SOURCE = "update_source"
        // 更新包走 Cloudflare R2 自定义域：公共文件夹 appdist/<app> 下按 app 分目录，端侧匿名下载
        private const val DEFAULT_SOURCE = "https://dl.202921.xyz/appdist/webbox/webbox-app-update.txt"
        const val EVENT_CHECK = "updateCheck"
        const val EVENT_PROGRESS = "downloadProgress"
        const val EVENT_DOWNLOAD_DONE = "downloadComplete"
        const val EVENT_DOWNLOAD_ERROR = "downloadError"
        const val EVENT_INSTALL_ERROR = "installError"
    }
}
