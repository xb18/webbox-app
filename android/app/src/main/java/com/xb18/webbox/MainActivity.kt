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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** 网址管理主界面：手动增删网址、打开网页、添加桌面快捷方式、设置与更新。 */
class MainActivity : Activity() {
    private lateinit var store: SiteStore
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var updater: Updater
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressDialog: android.app.Dialog? = null
    private var progressBar: android.widget.ProgressBar? = null
    private var progressText: TextView? = null

    private val primary = 0xFF5B4BE6.toInt()
    private val textDark = 0xFF1F2233.toInt()
    private val textGray = 0xFF9AA0AE.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(this)
        setupUpdater()
        setContentView(buildRoot())
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7FB.toInt())
        }
        val statusH = statusBarHeightPx()

        // 品牌头部
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), statusH + dp(20), dp(24), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7B6CF0.toInt(), 0xFF5B4BE6.toInt()))
        }
        val title = TextView(this).apply {
            text = "网页盒"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "把常用网页装进手机 · 一键直达"
            textSize = 13f
            setTextColor(0xD9FFFFFF.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(title); header.addView(subtitle)

        // 主操作：醒目的添加按钮
        val addBar = primaryButton("＋  添加网址") { showSiteDialog(null) }
        val addWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(6))
            addView(addBar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }

        // 次操作：设置 / 检查更新，等宽卡片
        val secondary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(6))
        }
        secondary.addView(secondaryButton("⚙  设置") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(5), 0) })
        secondary.addView(secondaryButton("↻  检查更新") { updater.checkUpdate() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(5), 0, 0, 0) })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(70), 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "🌐"; textSize = 50f; gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.empty_hint)
                textSize = 14f; setTextColor(textGray)
                gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
            })
        }
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
            addView(listContainer, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(emptyState, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(this).apply {
            addView(scrollContent, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.apply {
            addView(header)
            addView(addWrap)
            addView(secondary)
            addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        return root
    }

    /** 醒目主按钮：纯色胶囊 + 白字。 */
    private fun primaryButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(primary); cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { onClick() }
    }

    /** 次要按钮：白底描边胶囊 + 主色文字。 */
    private fun secondaryButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(primary)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.WHITE); setStroke(dp(1), 0xFFDDD9F7.toInt())
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun setupUpdater() {
        updater = Updater(this, object : Updater.Callback {
            override fun onEvent(type: String, data: org.json.JSONObject) {
                when (type) {
                    Updater.EVENT_PROGRESS -> mainHandler.post {
                        showProgress(data.optInt("percent"))
                    }
                    Updater.EVENT_DOWNLOAD_DONE -> mainHandler.post {
                        dismissProgress()
                        if (updater.canInstall()) updater.installUpdate() else updater.requestInstallPermission()
                    }
                    Updater.EVENT_DOWNLOAD_ERROR -> mainHandler.post {
                        dismissProgress()
                        Toast.makeText(
                            this@MainActivity,
                            "下载失败：${data.optString("error")}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    Updater.EVENT_CHECK -> mainHandler.post {
                        if (data.optBoolean("ok") && data.optBoolean("hasUpdate")) {
                            showUpdateDialog(data)
                        } else {
                            Toast.makeText(this@MainActivity, R.string.up_to_date, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    /** 显示/更新下载进度对话框。 */
    private fun showProgress(percent: Int) {
        if (progressDialog == null) {
            val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
            }
            val label = TextView(this).apply {
                text = "正在下载更新…"
                textSize = 14f; setTextColor(textDark); setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(10))
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(22), dp(24), dp(22))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE); cornerRadius = dp(20).toFloat()
                }
                addView(label)
                addView(bar, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            progressBar = bar
            progressText = label
            progressDialog = android.app.Dialog(this).apply {
                setContentView(box)
                setCancelable(false)
            }
            progressDialog?.show()
        }
        progressBar?.progress = percent.coerceIn(0, 100)
        progressText?.text = "正在下载更新… $percent%"
    }

    private fun dismissProgress() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressText = null
    }

    /** 自定义更新弹框：版本徽标 + 说明 + 圆角操作按钮。 */
    private fun showUpdateDialog(data: org.json.JSONObject) {
        val version = data.optString("version")
        val notes = data.optString("notes").ifBlank { "发现新版本，是否下载安装？" }
        val size = data.optLong("size")
        val sizeText = if (size > 0) "%.1f MB".format(size / 1024f / 1024f) else ""

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(20).toFloat()
            }
        }
        view.addView(TextView(this).apply {
            text = "↻"
            textSize = 26f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(primary)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        view.addView(TextView(this).apply {
            text = "发现新版本 $version"
            textSize = 19f; setTextColor(textDark); setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(14), 0, 0)
        })
        view.addView(TextView(this).apply {
            text = notes
            textSize = 14f; setTextColor(0xFF555566.toInt())
            setPadding(0, dp(10), 0, 0)
        })
        if (sizeText.isNotEmpty()) {
            view.addView(TextView(this).apply {
                text = "安装包 $sizeText"
                textSize = 12f; setTextColor(textGray)
                setPadding(0, dp(8), 0, 0)
            })
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(22), 0, 0)
        }
        val later = TextView(this).apply {
            text = "稍后"; textSize = 14f; setTextColor(textGray)
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
            background = GradientDrawable().apply {
                setColor(0xFFF0F0F5.toInt()); cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(0, 0, dp(6), 0) }
        }
        val confirm = TextView(this).apply {
            text = "立即更新"; textSize = 14f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(10))
            background = GradientDrawable().apply {
                setColor(primary); cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f)
                .apply { setMargins(dp(6), 0, 0, 0) }
        }
        val dialog = android.app.Dialog(this).apply {
            setContentView(view)
            setCancelable(true)
        }
        later.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            dialog.dismiss()
            updater.downloadUpdate(data.optString("url"))
            Toast.makeText(this, "开始下载，完成后自动安装", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
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
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }
            setOnClickListener { openSite(site) }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(0xFFEDEBFF.toInt())
            }
        }
        val avatarText = TextView(this).apply {
            text = site.name.trim().take(1).uppercase().ifBlank { "W" }
            textSize = 18f; setTextColor(primary); setTypeface(null, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        val avatarImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(44), dp(44))
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        avatar.addView(avatarText); avatar.addView(avatarImage)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(12), 0, 0, 0) }
        }
        texts.addView(TextView(this).apply {
            text = site.name.ifBlank { hostOf(site.url) }
            textSize = 16f; setTextColor(textDark); setTypeface(null, Typeface.BOLD)
            maxLines = 1
        })
        texts.addView(TextView(this).apply {
            text = hostOf(site.url); textSize = 12f; setTextColor(textGray); maxLines = 1
        })
        val arrow = TextView(this).apply {
            text = "›"; textSize = 26f; setTextColor(textGray)
            setPadding(dp(8), 0, 0, 0)
        }
        head.addView(avatar); head.addView(texts); head.addView(arrow)

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
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = dp(12); bottomMargin = dp(4) }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionPill("打开", primary, true) { openSite(site) },
            LinearLayout.LayoutParams(0, dp(34), 1f))
        actions.addView(actionPill("编辑", 0xFF0EA5A0.toInt(), false) { showSiteDialog(site) },
            LinearLayout.LayoutParams(0, dp(34), 0.9f).apply { setMargins(dp(5), 0, 0, 0) })
        actions.addView(actionPill("桌面", 0xFF6C5CE7.toInt(), false) { requestAddShortcut(site) },
            LinearLayout.LayoutParams(0, dp(34), 0.9f).apply { setMargins(dp(5), 0, dp(5), 0) })
        actions.addView(actionPill("删除", 0xFFE5484D.toInt(), false) {
            store.remove(site.id); refreshList()
        }, LinearLayout.LayoutParams(0, dp(34), 0.8f))

        card.apply { addView(head); addView(divider); addView(actions) }
        return card
    }

    private fun actionPill(text: String, color: Int, filled: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text; textSize = 12f
            setTextColor(if (filled) Color.WHITE else color)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                if (filled) setColor(color) else { setColor(Color.WHITE); setStroke(dp(1), color) }
            }
            setOnClickListener { onClick() }
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
                val started = try { ShortcutHelper.create(this, site, favicon) }
                catch (_: Exception) { false }
                if (started) {
                    Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ShortcutHelper.REQUEST_PIN_SHORTCUT && resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
        }
    }

    /** site 为 null 时是新增，否则编辑。 */
    private fun showSiteDialog(site: Site?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(4))
        }
        val nameLabel = TextView(this).apply {
            text = getString(R.string.site_name); textSize = 13f
            setTextColor(textGray); setPadding(dp(2), 0, 0, dp(4))
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.site_name_hint)
            setText(site?.name.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT; textSize = 15f
            setTextColor(textDark); setPadding(dp(12), dp(12), dp(12), dp(12))
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
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = inputBackground()
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
        setColor(0xFFF7F7FB.toInt()); setStroke(dp(1), 0xFFE3E3EC.toInt())
        cornerRadius = dp(10).toFloat()
    }

    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun hostOf(url: String): String = try {
        android.net.Uri.parse(SiteStore.normalizeUrl(url)).host ?: url
    } catch (_: Exception) { url }
}
