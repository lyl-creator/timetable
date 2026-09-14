package com.lyl.timetable.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本更新检查。
 *
 * 通过 GitHub Releases 接口读取最新发布版本，与当前版本比对。
 *
 * 注意：**私有仓库**的该接口对匿名请求返回 404，此时会给出明确提示并允许
 * 手动打开 Release 页面；仓库公开后即可自动比对版本。
 */
object UpdateChecker {

    const val REPO = "lyl-creator/timetable"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val TIMEOUT_MS = 10_000

    data class UpdateInfo(
        val tag: String,
        val version: String,
        val releaseName: String,
        val notes: String,
        val releaseUrl: String,
        val apkUrl: String?
    )

    sealed interface Result {
        /** 有可用新版本 */
        data class Available(val info: UpdateInfo) : Result

        /** 已是最新 */
        data object UpToDate : Result

        /** 检查失败（网络、私有仓库、频率限制等） */
        data class Failed(val message: String, val canOpenPage: Boolean = true) : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Timetable-Android/$currentVersion")
            }
        } catch (t: Throwable) {
            return@withContext Result.Failed("无法发起网络请求：${t.message ?: "未知错误"}")
        }

        try {
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val info = parse(body)
                    if (info == null) {
                        Result.Failed("GitHub 返回的数据无法解析。")
                    } else if (compareVersions(info.version, currentVersion) > 0) {
                        Result.Available(info)
                    } else {
                        Result.UpToDate
                    }
                }

                HttpURLConnection.HTTP_NOT_FOUND -> Result.Failed(
                    "仓库尚未对外公开（或不存在），无法自动检查更新。\n" +
                            "可将仓库设为公开后重试，或直接打开 Release 页面查看。",
                    canOpenPage = true
                )

                403 -> Result.Failed(
                    "GitHub 接口访问受限（可能超出频率限制），请稍后重试。",
                    canOpenPage = true
                )

                else -> Result.Failed("检查更新失败（HTTP $code）。", canOpenPage = true)
            }
        } catch (t: Throwable) {
            Result.Failed("检查更新失败：${t.message ?: "网络异常"}")
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parse(body: String): UpdateInfo? = runCatching {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").ifBlank { return null }
        val apkUrl = json.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() }
            }
        UpdateInfo(
            tag = tag,
            version = tag.trimStart('v', 'V'),
            releaseName = json.optString("name").ifBlank { tag },
            notes = json.optString("body").trim(),
            releaseUrl = json.optString("html_url").ifBlank { RELEASES_PAGE },
            apkUrl = apkUrl
        )
    }.getOrNull()

    /** 语义化版本比较，返回 >0 表示 a 比 b 新 */
    fun compareVersions(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private fun segments(version: String): List<Int> =
        version.trim()
            .trimStart('v', 'V')
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
