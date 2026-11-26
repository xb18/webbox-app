package com.xb18.webbox

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/** 网址管理主界面：手动增删网址、打开网页、添加桌面快捷方式、检查壳更新。 */
class MainActivity : Activity() {
    private lateinit var store: SiteStore
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var updater: Updater
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val primary = 0xFF5B4BE6.toInt()
    private val textDark = 0xFF1F2233.toInt()
    private val textGray = 0xFF9AA0AE.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(this)
        setupUpdater()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7FB.toInt())
        }

        // 顶部紫色头部
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, dp(52), 24, dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF6C5CE7.toInt(), primary))
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "把常用网页装进手机 · 一键直达"
            textSize = 13f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(title)
        header.addView(subtitle)

        // 操作按钮行悬浮在头部下方
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, dp(16), 24, 0)
        }
        val addBtn = primaryButton("＋  ${getString(R.string.add_site)}")
        addBtn.setOnClickListener { showSiteDialog(null) }
        val updateBtn = ghostButton(getString(R.string.menu_update))
        updateBtn.setOnClickListener { updater.checkUpdate() }
        actionsRow.addView(addBtn, LinearLayout.LayoutParams(0, dp(44), 1.4f).apply { setMargins(0, 0, 8, 0) })
        actionsRow.addView(updateBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(8, 0, 0, 0) })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(80), 0, 0)
            val icon = TextView(this@MainActivity).apply {
                text = "🌐"
                textSize = 52f
                gravity = Gravity.CENTER
            }
            val hint = TextView(this@MainActivity).apply {
                text = getString(R.string.empty_hint)
                textSize = 14f
                setTextColor(textGray)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            }
            addView(icon); addView(hint)
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(listContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(emptyState, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(this).apply {
            setPadding(24, dp(16), 24, dp(24))
            addView(scrollContent, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.apply {
            addView(header)
            addView(actionsRow)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupUpdater() {
        updater = Updater(this, object : Updater.Callback {
            override fun onEvent(type: String, data: org.json.JSONObject) {
                if (type == Updater.EVENT_DOWNLOAD_DONE) {
                    mainHandler.post {
                        if (updater.canInstall()) updater.installUpdate() else updater.requestInstallPermission()
                    }
                    return
                }
                if (type != Updater.EVENT_CHECK) return
                mainHandler.post {
                    if (data.optBoolean("ok") && data.optBoolean("hasUpdate")) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("发现新版本 ${data.optString("version")}")
                            .setMessage(data.optString("notes").ifBlank { "是否下载安装？" })
                            .setPositiveButton("更新") { _, _ ->
                                updater.downloadUpdate(data.optString("url"))
                                Toast.makeText(this@MainActivity, "开始下载，完成后自动安装", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("稍后", null)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.up_to_date, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun refreshList() {
        val sites = store.list()
        listContainer.removeAllViews()
        emptyState.visibility = if (sites.isEmpty()) View.VISIBLE else View.GONE
        sites.forEach { listContainer.addView(buildSiteCard(it)) }
    }

    private fun buildSiteCard(site: Site): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground()
            setPadding(dp(16), dp(14), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val avatar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            background = letterAvatar()
        }
        val avatarText = TextView(this).apply {
            text = site.name.trim().take(1).uppercase().ifBlank { "W" }
            textSize = 18f; setTextColor(primary); setTypeface(null, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
        }
        val avatarImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(42), dp(42))
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        avatar.addView(avatarText); avatar.addView(avatarImage)
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(12), 0, 0, 0) }
        }
        val name = TextView(this).apply {
            text = site.name.ifBlank { hostOf(site.url) }
            textSize = 16f; setTextColor(textDark); setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }
        val urlText = TextView(this).apply {
            text = hostOf(site.url)
            textSize = 12f; setTextColor(textGray); maxLines = 1
        }
        texts.addView(name); texts.addView(urlText)
        head.addView(avatar); head.addView(texts)

        // 异步加载 favicon 到头像
        executor.execute {
            val f = ShortcutHelper.fetchFavicon(this, site.url)
            mainHandler.post {
                f?.let { file ->
                    try {
                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            avatarImage.setImageBitmap(bmp)
                            avatarImage.visibility = View.VISIBLE
                            avatarText.visibility = View.GONE
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        val divider = View(this).apply {
            setBackgroundColor(0x14000000)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = dp(12); bottomMargin = dp(4)
            }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val open = pillButton(getString(R.string.open), primary, true)
        open.setOnClickListener { openSite(site) }
        val edit = pillButton(getString(R.string.edit), 0xFF0EA5A0.toInt(), false)
        edit.setOnClickListener { showSiteDialog(site) }
        val shortcut = pillButton(getString(R.string.shortcut_short), 0xFF6C5CE7.toInt(), false)
        shortcut.setOnClickListener { requestAddShortcut(site) }
        val del = pillButton(getString(R.string.delete), 0xFFE5484D.toInt(), false)
        del.setOnClickListener { store.remove(site.id); refreshList() }
        actions.addView(open, LinearLayout.LayoutParams(0, dp(36), 1f))
        actions.addView(edit, LinearLayout.LayoutParams(0, dp(36), 0.9f).apply { setMargins(dp(5), 0, 0, 0) })
        actions.addView(shortcut, LinearLayout.LayoutParams(0, dp(36), 0.9f).apply { setMargins(dp(5), 0, dp(5), 0) })
        actions.addView(del, LinearLayout.LayoutParams(0, dp(36), 0.8f))

        card.apply { addView(head); addView(divider); addView(actions) }
        return card
    }

    private fun openSite(site: Site) {
        startActivity(Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_URL, site.url)
            putExtra(WebViewActivity.EXTRA_NAME, site.name)
        })
    }

    private fun requestAddShortcut(site: Site) {
        executor.execute {
            val favicon = ShortcutHelper.fetchFavicon(this, site.url)
            mainHandler.post {
                val started = try {
                    ShortcutHelper.create(this, site, favicon)
                } catch (_: Exception) {
                    false
                }
                if (!started) {
                    Toast.makeText(this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ShortcutHelper.REQUEST_PIN_SHORTCUT &&
            resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
        }
    }

    /** site 为 null 时是新增，否则编辑。 */
    private fun showSiteDialog(site: Site?) {
        val padding = dp(16)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, dp(4))
        }
        val nameLabel = TextView(this).apply {
            text = getString(R.string.site_name); textSize = 13f
            setTextColor(textGray); setPadding(dp(2), 0, 0, dp(4))
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.site_name_hint)
            setText(site?.name.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT; textSize = 15f
            setTextColor(textDark)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = inputBackground()
        }
        val urlLabel = TextView(this).apply {
            text = getString(R.string.site_url); textSize = 13f
            setTextColor(textGray); setPadding(dp(2), 0, 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(14), 0, 0) }
        }
        val urlInput = EditText(this).apply {
            hint = getString(R.string.site_url_hint)
            setText(site?.url.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textSize = 15f; setTextColor(textDark)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = inputBackground()
        }
        layout.apply { addView(nameLabel); addView(nameInput); addView(urlLabel); addView(urlInput) }

        AlertDialog.Builder(this)
            .setTitle(if (site == null) R.string.add_site else R.string.edit_site)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = urlInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = nameInput.text.toString()
                if (site == null) store.add(name, url) else store.update(site.id, name, url)
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun inputBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(0xFFF7F7FB.toInt())
        setStroke(dp(1), 0xFFE3E3EC.toInt())
        cornerRadius = dp(10).toFloat()
    }

    // ---------- UI 辅助 ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun primaryButton(text: String): Button = Button(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.WHITE)
        background = roundedFill(primary)
    }

    private fun ghostButton(text: String): Button = Button(this).apply {
        this.text = text; textSize = 14f; setTextColor(primary)
        background = roundedStroke(primary)
    }

    private fun pillButton(text: String, color: Int, filled: Boolean): Button = Button(this).apply {
        this.text = text; textSize = 12f
        setTextColor(if (filled) Color.WHITE else color)
        background = if (filled) roundedFill(color, dp(18)) else roundedStroke(color, dp(18))
        setPadding(0, 0, 0, 0)
    }

    private fun roundedFill(color: Int, radius: Int = dp(22)): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun roundedStroke(color: Int, radius: Int = dp(22)): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.WHITE); setStroke(dp(1), color); cornerRadius = radius.toFloat()
        }

    private fun cardBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE); cornerRadius = dp(16).toFloat()
    }

    private fun letterAvatar(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFFEDEBFF.toInt())
    }

    private fun hostOf(url: String): String = try {
        android.net.Uri.parse(SiteStore.normalizeUrl(url)).host ?: url
    } catch (_: Exception) { url }
}
