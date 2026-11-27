package com.xb18.webbox

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Site(
    val id: String,
    val name: String,
    val url: String,
    /** 自定义图标地址；为空时自动抓取站点 favicon。 */
    val iconUrl: String = "",
)

/** 本地网址列表：SharedPreferences 存 JSON 数组，纯手动增删。 */
class SiteStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("webbox_sites", Context.MODE_PRIVATE)

    fun list(): List<Site> {
        val raw = prefs.getString(KEY_SITES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Site(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    iconUrl = o.optString("iconUrl"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(sites: List<Site>) {
        val arr = JSONArray()
        sites.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("iconUrl", s.iconUrl)
            })
        }
        prefs.edit().putString(KEY_SITES, arr.toString()).apply()
    }

    fun add(name: String, url: String, iconUrl: String = ""): Site {
        val site = Site(
            id = "${System.currentTimeMillis()}",
            name = name.trim(),
            url = normalizeUrl(url),
            iconUrl = iconUrl.trim(),
        )
        save(list() + site)
        return site
    }

    fun update(id: String, name: String, url: String, iconUrl: String) {
        save(list().map { site ->
            if (site.id == id) {
                site.copy(name = name.trim(), url = normalizeUrl(url), iconUrl = iconUrl.trim())
            } else {
                site
            }
        })
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    companion object {
        private const val KEY_SITES = "sites"

        fun normalizeUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return url
        }
    }
}
