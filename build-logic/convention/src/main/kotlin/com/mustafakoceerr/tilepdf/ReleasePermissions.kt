package com.mustafakoceerr.tilepdf

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Everything the release may ask for. A new permission must be a deliberate
 * change made here, with docs/play/data-safety.md updated to match.
 */
val RELEASE_PERMISSION_ALLOWLIST = setOf(
    // Firebase only: crash reports and, if the user allows them, statistics.
    // Its advertising permissions are removed in the app manifest.
    "android.permission.INTERNET",
    // WorkManager, which saves annotations in the background.
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.FOREGROUND_SERVICE",
    // androidx.core: guards the app's own non-exported receivers.
    "com.mustafakoceerr.tilepdf.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

private val PERMISSION_TAGS = listOf("uses-permission", "uses-permission-sdk-23")
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** Permissions [manifestXml] asks for that [allowed] doesn't contain, in manifest order. */
fun unexpectedPermissions(manifestXml: String, allowed: Set<String>): List<String> {
    val document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(manifestXml.byteInputStream())
    return PERMISSION_TAGS.flatMap { tag ->
        val nodes = document.getElementsByTagName(tag)
        (0 until nodes.length).map { i -> nodes.item(i).attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue }
    }.filterNot { it in allowed }.distinct()
}

/** Fails the release build when the merged manifest asks for a permission outside the allowlist. */
abstract class CheckReleasePermissionsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun check() {
        val unexpected = unexpectedPermissions(mergedManifest.get().asFile.readText(), RELEASE_PERMISSION_ALLOWLIST)
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "The release asks for permissions TilePDF doesn't allow: ${unexpected.joinToString()}. " +
                    "Remove them with tools:node=\"remove\" or, if really needed, add them to RELEASE_PERMISSION_ALLOWLIST " +
                    "and update docs/play/data-safety.md.",
            )
        }
    }
}
