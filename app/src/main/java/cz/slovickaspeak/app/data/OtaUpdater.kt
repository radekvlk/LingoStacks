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
    const val DEFAULT_GITHUB_REPO = "radekvlk/LingoWords"

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
     * Checks GitHub Releases API for the latest published release with an attached .apk asset.
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

        return try {
            val url = URL("https://api.github.com/repos/$slug/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LingoWords-Android/${BuildConfig.VERSION_NAME}")
            }

            val code = conn.responseCode
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
                return OtaCheckResult.Error(
                    if (isCzech) "GitHub odpověděl chybou HTTP $code." else "GitHub returned HTTP $code."
                )
            }

            val bodyStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val json = JSONObject(bodyStr)
            val tagName = json.optString("tag_name", "").trim()
            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseTitle = json.optString("name", tagName).ifBlank { "v$cleanRemoteVersion" }
            val changelog = json.optString("body", "").trim()
            val publishedAt = json.optString("published_at", "").take(10)

            val assets = json.optJSONArray("assets")
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
                        if (chosenUrl.isEmpty() || name.contains("LingoWords", ignoreCase = true)) {
                            chosenUrl = dlUrl
                            chosenName = name
                            chosenSize = size
                        }
                    }
                }
            }

            if (cleanRemoteVersion.isEmpty() || chosenUrl.isEmpty()) {
                return OtaCheckResult.RepoNotReady(
                    if (isCzech) {
                        "Vydání $tagName neobsahuje přiložený .apk soubor."
                    } else {
                        "Release $tagName does not contain an attached .apk file."
                    }
                )
            }

            val newer = isRemoteNewer(
                remoteVersion = cleanRemoteVersion,
                currentVersion = BuildConfig.VERSION_NAME,
                changelog = changelog,
                currentVersionCode = BuildConfig.VERSION_CODE
            )

            if (newer) {
                OtaCheckResult.UpdateAvailable(
                    OtaReleaseInfo(
                        tagName = tagName,
                        versionName = cleanRemoteVersion,
                        title = releaseTitle,
                        changelog = changelog,
                        apkDownloadUrl = chosenUrl,
                        apkFileName = chosenName,
                        apkSizeBytes = chosenSize,
                        publishedAt = publishedAt
                    )
                )
            } else {
                OtaCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
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
     * Downloads the APK from GitHub CDN (handling cross-host 302 redirects) into cacheDir/updates/.
     */
    fun downloadApk(
        context: Context,
        release: OtaReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File {
        val updatesDir = File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }
        // Clean previous APKs in cache to keep storage tidy
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }

        val targetFile = File(updatesDir, "LingoWords_v${release.versionName}.apk")
        val tempFile = File(updatesDir, "LingoWords_v${release.versionName}.apk.tmp")

        var currentUrl = release.apkDownloadUrl
        var conn: HttpURLConnection? = null
        for (redirectCount in 0 until 6) {
            val url = URL(currentUrl)
            val attempt = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 20000
                setRequestProperty("User-Agent", "LingoWords-Android/${BuildConfig.VERSION_NAME}")
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
