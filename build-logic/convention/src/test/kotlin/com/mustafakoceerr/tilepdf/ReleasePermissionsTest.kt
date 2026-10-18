package com.mustafakoceerr.tilepdf

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleasePermissionsTest {

    private fun manifest(vararg permissions: String, tag: String = "uses-permission") = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="x">
            ${permissions.joinToString("\n") { "<$tag android:name=\"$it\" />" }}
            <application />
        </manifest>
    """.trimIndent()

    @Test
    fun allowedPermissionsPass() = assertEquals(
        emptyList<String>(),
        unexpectedPermissions(manifest("android.permission.WAKE_LOCK", "android.permission.ACCESS_NETWORK_STATE"), RELEASE_PERMISSION_ALLOWLIST),
    )

    @Test
    fun internetIsReported() = assertEquals(
        listOf("android.permission.INTERNET"),
        unexpectedPermissions(manifest("android.permission.WAKE_LOCK", "android.permission.INTERNET"), RELEASE_PERMISSION_ALLOWLIST),
    )

    @Test
    fun sdk23PermissionsCountToo() = assertEquals(
        listOf("android.permission.INTERNET"),
        unexpectedPermissions(manifest("android.permission.INTERNET", tag = "uses-permission-sdk-23"), RELEASE_PERMISSION_ALLOWLIST),
    )

    @Test
    fun allowlistMatchesThePlan() = assertEquals(
        setOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
            "com.mustafakoceerr.tilepdf.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        ),
        RELEASE_PERMISSION_ALLOWLIST,
    )
}
