package com.xb18.webbox

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 设置页：网页文字选择开关（默认关，关闭后长按网页不弹出复制菜单）。 */
class SettingsActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val primary = 0xFF5B4BE6.toInt()
    private val textDark = 0xFF1F2233.toInt()
    private val textGray = 0xFF9AA0AE.toInt()

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
            text = "网页文字选择"
            textSize = 16f
            setTextColor(textDark)
            setTypeface(null, Typeface.BOLD)
        }
        val desc = TextView(this).apply {
            text = "开启后长按网页可选中并复制文字；默认关闭"
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
            text = "提示：关闭后可避免误触系统自带的复制菜单；如需从网页复制内容，可临时开启。"
            textSize = 12f
            setTextColor(textGray)
            setPadding(dp(24), dp(16), dp(24), 0)
        }

        val scroll = ScrollView(this).apply {
            addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(card)
                addView(hint)
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
