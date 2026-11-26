package com.xb18.webbox

import android.content.Context

/** 全局偏好：目前仅网页文字选择开关，默认关闭。 */
object Prefs {
    private const val NAME = "webbox_prefs"
    private const val KEY_TEXT_SELECTION = "text_selection"

    fun textSelectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TEXT_SELECTION, false)

    fun setTextSelectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TEXT_SELECTION, enabled).apply()
    }
}
