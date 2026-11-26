package com.xb18.webbox

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 桌面快捷方式：为指定网址生成可直达的图标（类似小程序添加到桌面）。
 * API 26+ 用 ShortcutManager 的 createShortcutResultIntent 交给桌面弹确认框放置；
 * 更低版本用旧版 ACTION_INSTALL_SHORTCUT 广播。
 * 图标优先取站点 favicon，取不到则回退到带首字母的色块图标。
 */
object ShortcutHelper {

    const val REQUEST_PIN_SHORTCUT = 1001

    /** 抓取站点 favicon 并缓存到内部存储；失败返回 null。 */
    fun fetchFavicon(context: Context, siteUrl: String): File? {
        val host = try {
            Uri.parse(siteUrl).host
        } catch (_: Exception) {
            null
        } ?: return null
        val cacheDir = File(context.filesDir, "icons").apply { mkdirs() }
        val target = File(cacheDir, "$host.png")
        if (target.exists() && target.length() > 0) return target

        val candidates = listOf(
            "https://$host/favicon.ico",
            "https://$host/apple-touch-icon.png",
        )
        for (url in candidates) {
            val bytes = download(url) ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            try {
                FileOutputStream(target).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                return target
            } catch (_: Exception) {
                // 单个候选失败则继续尝试下一个
            }
        }
        return null
    }

    private fun download(url: String): ByteArray? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) WebBox")
        }
        conn.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** 创建快捷方式图标：优先 favicon 文件，否则用首字母色块。 */
    fun buildIcon(site: Site, favicon: File?): Icon {
        val bitmap = favicon?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?: letterBitmap(site.name.ifBlank { site.url })
        return Icon.createWithBitmap(bitmap)
    }

    /** 首字母色块图标，避免 favicon 抓取失败时图标空白。 */
    private fun letterBitmap(label: String): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF5B4BE6.toInt())
        val text = label.trim().take(1).uppercase().ifEmpty { "W" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.55f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, paint)
        return bmp
    }

    /**
     * 请求创建桌面快捷方式。返回 true 表示已发起放置流程（可能需用户在桌面确认）。
     * API 26+ 优先走 ShortcutManager；不支持时回退旧版广播，兼容小米等定制桌面。
     * shortcutId 用 site.id，重复添加同一网址会更新而不是堆叠。
     */
    fun create(activity: Activity, site: Site, favicon: File?): Boolean {
        val name = site.name.ifBlank { hostOf(site.url) }
        val intent = Intent(activity, WebViewActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(WebViewActivity.EXTRA_URL, site.url)
            putExtra(WebViewActivity.EXTRA_NAME, site.name)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = activity.getSystemService(ShortcutManager::class.java)
            if (manager != null && manager.isRequestPinShortcutSupported) {
                val info = ShortcutInfo.Builder(activity, site.id)
                    .setShortLabel(name)
                    .setLongLabel(name)
                    .setIcon(buildIcon(site, favicon))
                    .setIntent(intent)
                    .build()
                val result = manager.createShortcutResultIntent(info)
                if (result != null) {
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    val pending = PendingIntent.getActivity(activity, 0, result, flags)
                    activity.startIntentSenderForResult(
                        pending.intentSender, REQUEST_PIN_SHORTCUT, null, 0, 0, 0, null)
                    return true
                }
            }
            // 标准流程不可用，回退旧广播（小米/澎湃等定制桌面常依赖此方式）
            return sendLegacyBroadcast(activity, site, name, intent, favicon)
        } else {
            return sendLegacyBroadcast(activity, site, name, intent, favicon)
        }
    }

    private fun sendLegacyBroadcast(
        activity: Activity, site: Site, name: String, intent: Intent, favicon: File?,
    ): Boolean = try {
        @Suppress("DEPRECATION")
        val legacy = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
            putExtra("duplicate", false)
            putExtra(Intent.EXTRA_SHORTCUT_ICON, legacyIcon(site, favicon))
        }
        activity.sendBroadcast(legacy)
        true
    } catch (_: Exception) {
        false
    }

    private fun legacyIcon(site: Site, favicon: File?): Bitmap =
        favicon?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?: letterBitmap(site.name.ifBlank { site.url })

    private fun hostOf(url: String): String = try {
        Uri.parse(SiteStore.normalizeUrl(url)).host ?: url
    } catch (_: Exception) { url }
}
