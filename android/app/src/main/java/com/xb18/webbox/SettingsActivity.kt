package com.xb18.webbox

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 设置页：网页选中悬浮菜单开关（默认关，关闭后仍可选中文字，只是不弹系统工具条）。 */
class SettingsActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val primary = 0xFF5B4BE6.toInt()
    private val textDark = 0xFF1F2233.toInt()
    private val textGray = 0xFF9AA0AE.toInt()

    companion object {
        const val RESULT_CHECK_UPDATE = 3001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7FB.toInt())
        }

        // 顶部标题栏（含状态栏高度）
        val statusH = statusBarHeightPx()
        val header = TextView(this).apply {
            text = "  设置"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(primary)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, statusH + dp(14), 24, dp(14))
        }
        header.setOnClickListener { finish() }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(20), dp(16), 0) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = "选中悬浮菜单"
            textSize = 16f
            setTextColor(textDark)
            setTypeface(null, Typeface.BOLD)
        }
        val desc = TextView(this).apply {
            text = "网页文字始终可选中；开启后选中时弹出系统复制工具条，默认关闭"
            textSize = 12f
            setTextColor(textGray)
            setPadding(0, dp(4), 0, 0)
        }
        texts.addView(title); texts.addView(desc)

        val switch = Switch(this).apply {
            isChecked = Prefs.textSelectionEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                Prefs.setTextSelectionEnabled(this@SettingsActivity, checked)
            }
        }
        row.addView(texts)
        row.addView(switch, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(12), 0, 0, 0) })
        card.addView(row)

        val hint = TextView(this).apply {
            text = "提示：关闭只隐藏系统悬浮菜单，长按选择文字仍可用；如需用系统复制/分享按钮，可临时开启。"
            textSize = 12f
            setTextColor(textGray)
            setPadding(dp(24), dp(16), dp(24), 0)
        }

        // 桌面快捷方式权限引导（小米/华为等 ROM 需手动开启）
        val shortcutCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(20), dp(16), 0) }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        shortcutCard.addView(TextView(this).apply {
            text = "桌面快捷方式权限"
            textSize = 16f; setTextColor(textDark); setTypeface(null, Typeface.BOLD)
        })
        shortcutCard.addView(TextView(this).apply {
            text = "若「添加到桌面」无反应，请在此应用的权限管理中开启「创建桌面快捷方式」后重试。"
            textSize = 12f; setTextColor(textGray); setPadding(0, dp(6), 0, 0)
        })
        shortcutCard.addView(TextView(this).apply {
            text = "打开应用设置"
            textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
            background = GradientDrawable().apply {
                setColor(primary); cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(14), 0, 0) }
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) { /* 部分 ROM 无此设置页 */ }
            }
        })

        // 检查更新
        val updateCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(12), dp(16), 0) }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                setResult(RESULT_CHECK_UPDATE)
                finish()
            }
        }
        updateCard.addView(TextView(this).apply {
            text = "↻   检查更新"
            textSize = 16f; setTextColor(textDark)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        updateCard.addView(TextView(this).apply {
            text = "›"; textSize = 20f; setTextColor(textGray)
        })

        // 版本号
        val versionText = TextView(this).apply {
            text = "网页盒 v${BuildConfig.VERSION_NAME}"
            textSize = 12f; setTextColor(textGray)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }

        val scroll = ScrollView(this).apply {
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(card)
                addView(hint)
                addView(shortcutCard)
                addView(updateCard)
                addView(versionText)
            }, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.apply {
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}
