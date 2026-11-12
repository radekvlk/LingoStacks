package cz.slovickaspeak.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cz.slovickaspeak.app.BuildConfig
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OtaReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val publishedAt: String
)

enum class OtaInstallResult {
    LAUNCHED_INSTALLER,
    NEEDS_INSTALL_PERMISSION,
    ERROR
}

sealed interface OtaCheckResult {
    data class UpdateAvailable(val release: OtaReleaseInfo) : OtaCheckResult
    data class UpToDate(val currentVersion: String) : OtaCheckResult
    data class RepoNotReady(val message: String) : OtaCheckResult
    data class Error(val message: String) : OtaCheckResult
}

object OtaUpdater {
    const val DEFAULT_GITHUB_REPO = "radekvlk/LingoStacks"

    /**
     * Normalizes either "owner/repo" or "https://github.com/owner/repo" into "owner/repo".
     */
    fun normalizeRepoSlug(rawInput: String): String {
        val cleaned = rawInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else cleaned
    }

    /**
     * Extracts localized section from bilingual release notes marked with [CS] and [EN].
     */
    fun extractLocalizedChangelog(rawBody: String, isCzech: Boolean): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ""

        val csPattern = Regex("(?s)\\[CS](.*?)(?=\\[EN]|\\z)")
        val enPattern = Regex("(?s)\\[EN](.*?)(?=\\[CS]|\\z)")
        val csMatch = csPattern.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val enMatch = enPattern.find(trimmed)?.groupValues?.getOrNull(1)?.trim()

        val rawSelected = if (!csMatch.isNullOrBlank() || !enMatch.isNullOrBlank()) {
            if (isCzech) {
                csMatch.orEmpty().ifBlank { enMatch.orEmpty() }
            } else {
                enMatch.orEmpty().ifBlank { csMatch.orEmpty() }
            }
        } else {
            trimmed
        }

        // Strip Markdown headings/bold markers so release notes read cleanly inside native Text
        return rawSelected.lines()
            .filterNot { it.trim().startsWith("### ") }
            .joinToString("\n") { line ->
                line.replace("**", "")
                    .replace("`", "")
                    .replace(Regex("^\\s*-\\s+"), "• ")
            }
            .trim()
    }

    /**
     * Checks GitHub Releases API for the latest published release with an attached .apk asset.
     * Falls back automatically to GitHub's rate-limit-free web redirect endpoint if API returns 403/429.
     */
    fun checkLatestRelease(
        repoSlug: String,
        isCzech: Boolean
    ): OtaCheckResult {
        val slug = normalizeRepoSlug(repoSlug)
        if (!slug.contains("/")) {
            return OtaCheckResult.Error(
                if (isCzech) "Neplatný formát repozitáře (použijte uzivatel/repozitar)." else "Invalid repository format (use owner/repo)."
            )
        }

        val isBeta = BuildConfig.IS_BETA
        return try {
            val apiUrl = if (isBeta) {
                "https://api.github.com/repos/$slug/releases?per_page=15"
            } else {
                "https://api.github.com/repos/$slug/releases/latest"
            }
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LingoStacks-Android/${BuildConfig.VERSION_NAME}")
            }

            val code = conn.responseCode
            if (code == 403 || code == 429) {
                conn.disconnect()
                return checkViaWebRedirect(slug, isCzech)
            }
            if (code == 404) {
                conn.disconnect()
                return OtaCheckResult.RepoNotReady(
                    if (isCzech) {
                        "V repozitáři $slug zatím není žádné vydání (Release)."
                    } else {
                        "No published release found yet in $slug."
                    }
                )
            }
            if (code !in 200..299) {
                conn.disconnect()
                return checkViaWebRedirect(slug, isCzech)
            }

            val bodyStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val targetJson: JSONObject = if (isBeta) {
                val array = org.json.JSONArray(bodyStr)
                var found: JSONObject? = null
                for (j in 0 until array.length()) {
                    val rel = array.optJSONObject(j) ?: continue
                    val tag = rel.optString("tag_name", "").lowercase()
                    val isPre = rel.optBoolean("prerelease", false)
                    if (isPre || tag.contains("beta")) {
                        found = rel
                        break
                    }
                }
                found ?: return OtaCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            } else {
                JSONObject(bodyStr)
            }

            val tagName = targetJson.optString("tag_name", "").trim()
            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseTitle = targetJson.optString("name", tagName).ifBlank { "v$cleanRemoteVersion" }
            val rawChangelog = targetJson.optString("body", "").trim()
            val publishedAt = targetJson.optString("published_at", "").take(10)

            val assets = targetJson.optJSONArray("assets")
            var chosenUrl = ""
            var chosenName = ""
            var chosenSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val dlUrl = asset.optString("browser_download_url", "")
                        val size = asset.optLong("size", 0L)
                        val nameLower = name.lowercase()
                        if (isBeta) {
                            if (nameLower.contains("beta")) {
                                chosenUrl = dlUrl
                                chosenName = name
                                chosenSize = size
                                break
                            }
                        } else {
                            if (!nameLower.contains("beta") && (chosenUrl.isEmpty() || name.contains("LingoStacks", ignoreCase = true))) {
                                chosenUrl = dlUrl
                                chosenName = name
                                chosenSize = size
                            }
                        }
                    }
                }
            }

            if (cleanRemoteVersion.isEmpty() || chosenUrl.isEmpty()) {
                return OtaCheckResult.RepoNotReady(
                    if (isCzech) {
                        "Vydání $tagName neobsahuje přiložený odpovídající .apk soubor."
                    } else {
                        "Release $tagName does not contain a matching .apk file."
                    }
                )
            }

            val newer = isRemoteNewer(
                remoteVersion = cleanRemoteVersion,
                currentVersion = BuildConfig.VERSION_NAME,
                changelog = rawChangelog,
                currentVersionCode = BuildConfig.VERSION_CODE
            )

            if (newer) {
                OtaCheckResult.UpdateAvailable(
                    OtaReleaseInfo(
                        tagName = tagName,
                        versionName = cleanRemoteVersion,
                        title = releaseTitle,
                        changelog = rawChangelog,
                        apkDownloadUrl = chosenUrl,
                        apkFileName = chosenName,
                        apkSizeBytes = chosenSize,
                        publishedAt = publishedAt
                    )
                )
            } else {
                OtaCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
        } catch (_: Exception) {
            checkViaWebRedirect(slug, isCzech)
        }
    }

    /**
     * Unlimited fallback via GitHub web redirect (https://github.com/owner/repo/releases/latest -> 302 tag).
     * Never hits api.github.com 60 req/hour rate limits!
     */
    private fun checkViaWebRedirect(slug: String, isCzech: Boolean): OtaCheckResult {
        return try {
            val latestUrl = URL("https://github.com/$slug/releases/latest")
            val conn = (latestUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "HEAD"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "LingoStacks-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location") ?: ""
            conn.disconnect()

            if (code !in 300..399 || !location.contains("/releases/tag/")) {
                return OtaCheckResult.RepoNotReady(
                    if (isCzech) {
                        "V repozitáři $slug zatím není žádné vydání (Release)."
                    } else {
                        "No published release found yet in $slug."
                    }
                )
            }

            val tagName = location.substringAfterLast("/releases/tag/").substringBefore("?").trim()
            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            if (cleanRemoteVersion.isEmpty()) {
                return OtaCheckResult.Error(
                    if (isCzech) "Nepodařilo se přečíst verzi vydání." else "Could not parse release version."
                )
            }

            if (!isRemoteNewer(cleanRemoteVersion, BuildConfig.VERSION_NAME)) {
                return OtaCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }

            // Inspect expanded_assets HTML fragment to find the exact .apk download link
            var apkUrl = "https://github.com/$slug/releases/download/$tagName/LingoStacks_$tagName.apk"
            var apkName = "LingoStacks_$tagName.apk"
            try {
                val assetsUrl = URL("https://github.com/$slug/releases/expanded_assets/$tagName")
                val assetsConn = (assetsUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "LingoStacks-Android/${BuildConfig.VERSION_NAME}")
                }
                if (assetsConn.responseCode in 200..299) {
                    val html = assetsConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val apkHrefMatch = Regex("""href="(/[^"]+/releases/download/[^"]+\.apk)"""").find(html)
                    val relHref = apkHrefMatch?.groupValues?.getOrNull(1)
                    if (!relHref.isNullOrBlank()) {
                        apkUrl = "https://github.com$relHref"
                        apkName = relHref.substringAfterLast("/")
                    }
                }
                assetsConn.disconnect()
            } catch (_: Exception) {
            }

            OtaCheckResult.UpdateAvailable(
                OtaReleaseInfo(
                    tagName = tagName,
                    versionName = cleanRemoteVersion,
                    title = "LingoStacks $tagName",
                    changelog = if (isCzech) {
                        "Nová verze LingoStacks $tagName je připravena ke stažení a instalaci."
                    } else {
                        "New LingoStacks $tagName update is ready to download and install."
                    },
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
                    apkSizeBytes = 0L,
                    publishedAt = ""
                )
            )
        } catch (e: Exception) {
            OtaCheckResult.Error(
                if (isCzech) {
                    "Nepodařilo se spojit s GitHubem (${e.javaClass.simpleName})."
                } else {
                    "Failed to contact GitHub (${e.javaClass.simpleName})."
                }
            )
        }
    }

    /**
     * Compares semver strings (e.g. "1.6.1" vs "1.6.0"), or optional "versionCode: N" tag in release notes.
     */
    fun isRemoteNewer(
        remoteVersion: String,
        currentVersion: String,
        changelog: String = "",
        currentVersionCode: Int = 0
    ): Boolean {
        val codeMatch = Regex("versionCode\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE).find(changelog)
        val remoteCode = codeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (remoteCode != null && currentVersionCode > 0 && remoteCode > currentVersionCode) {
            return true
        }

        val rParts = remoteVersion.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val cParts = currentVersion.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Removes any downloaded OTA APK installers from cacheDir/updates/ once no longer needed.
     */
    fun cleanupCachedUpdates(context: Context) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Downloads the APK from GitHub CDN (handling cross-host 302 redirects) into cacheDir/updates/.
     */
    fun downloadApk(
        context: Context,
        release: OtaReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File {
        cleanupCachedUpdates(context)
        val updatesDir = File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }

        val targetFile = File(updatesDir, "LingoStacks_v${release.versionName}.apk")
        val tempFile = File(updatesDir, "LingoStacks_v${release.versionName}.apk.tmp")

        var currentUrl = release.apkDownloadUrl
        var conn: HttpURLConnection? = null
        for (redirectCount in 0 until 6) {
            val url = URL(currentUrl)
            val attempt = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 20000
                setRequestProperty("User-Agent", "LingoStacks-Android/${BuildConfig.VERSION_NAME}")
            }
            val status = attempt.responseCode
            if (status in 300..399) {
                val loc = attempt.getHeaderField("Location")
                attempt.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Empty redirect location")
                currentUrl = URL(url, loc).toString()
            } else {
                conn = attempt
                break
            }
        }

        val activeConn = conn ?: throw IllegalStateException("Too many redirects")
        try {
            val code = activeConn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            val totalBytes = activeConn.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            var downloaded = 0L

            BufferedInputStream(activeConn.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var lastNotifyTime = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime >= 80L) {
                            lastNotifyTime = now
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(downloaded, totalBytes)

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile
        } finally {
            activeConn.disconnect()
        }
    }

    fun installApk(context: Context, apkFile: File): OtaInstallResult {
        if (!apkFile.exists()) return OtaInstallResult.ERROR

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return OtaInstallResult.NEEDS_INSTALL_PERMISSION
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            OtaInstallResult.LAUNCHED_INSTALLER
        } catch (_: Exception) {
            OtaInstallResult.ERROR
        }
    }

    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
