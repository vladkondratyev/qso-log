package ru.r3xed.qsolog.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Xiaomi, Redmi and Poco (MIUI, HyperOS) keep their own permissions on top of Android's: without "Отображать
 * всплывающие окна, когда запущено в фоновом режиме" an app may not open other apps, and Android says nothing about it.
 */
object Miui {
    /** The MIUI app-op of starting activities from the background; not in Android's API. */
    private const val OP_BACKGROUND_START = 10021

    val isMiui: Boolean by lazy {
        Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco") || !systemProperty("ro.miui.ui.version.name").isNullOrBlank()
    }

    /** True or false when the system tells, null when it does not (another phone, or the check is closed). */
    fun backgroundStartAllowed(context: Context): Boolean? {
        if (!isMiui) return null
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val m = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            m.invoke(ops, OP_BACKGROUND_START, android.os.Process.myUid(), context.packageName) as Int == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            null
        }
    }

    /** The "Другие разрешения" screen of QSO-LOG in the security app; Android's app details when it is not there. */
    fun openPermissions(context: Context) {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName)
        val android = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        for (i in listOf(miui, android)) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    private fun systemProperty(name: String): String? = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    } catch (e: Exception) {
        null
    }
}
