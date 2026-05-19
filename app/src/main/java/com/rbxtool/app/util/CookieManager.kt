package com.rbxtool.app.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class CookieManager(private val context: Context) {

    fun grabCookie(pkg: String): String? {
        val tmp = File(context.cacheDir, "rbx_grab.db")
        val dbPath = "/data/data/$pkg/app_webview/Default/Cookies"
        RootUtils.exec("cp '$dbPath' '${tmp.absolutePath}' && chmod 666 '${tmp.absolutePath}'")
        if (!tmp.exists()) return null
        return try {
            val db = SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cur = db.rawQuery("SELECT value FROM cookies WHERE name='.ROBLOSECURITY' LIMIT 1", null)
            val v = if (cur.moveToFirst()) cur.getString(0) else null
            cur.close(); db.close(); tmp.delete()
            v?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { tmp.delete(); null }
    }

    fun injectCookie(
        cookie: String,
        pkg: String,
        userId: Long = 0L,
        username: String = "",
        displayName: String = ""
    ): Boolean {
        val cookieDir = "/data/data/$pkg/app_webview/Default"
        val dbPath = "$cookieDir/Cookies"
        val tmp = File(context.cacheDir, "rbx_inject.db")

        return try {
            // Stop app
            RootUtils.exec("am force-stop $pkg")
            Thread.sleep(500)

            // Wipe all app data so no stale session/account switcher data remains
            RootUtils.exec("pm clear $pkg")
            Thread.sleep(1000)

            // Build fresh Cookies DB locally
            tmp.delete()
            val db = SQLiteDatabase.openOrCreateDatabase(tmp.absolutePath, null)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cookies (
                    creation_utc INTEGER NOT NULL,
                    host_key TEXT NOT NULL,
                    name TEXT NOT NULL,
                    value TEXT NOT NULL,
                    path TEXT NOT NULL,
                    expires_utc INTEGER NOT NULL,
                    is_secure INTEGER NOT NULL,
                    is_httponly INTEGER NOT NULL,
                    last_access_utc INTEGER NOT NULL,
                    has_expires INTEGER NOT NULL,
                    is_persistent INTEGER NOT NULL,
                    priority INTEGER NOT NULL,
                    encrypted_value BLOB DEFAULT '',
                    samesite INTEGER NOT NULL DEFAULT -1,
                    source_scheme INTEGER NOT NULL DEFAULT 0,
                    source_port INTEGER NOT NULL DEFAULT -1,
                    last_update_utc INTEGER NOT NULL DEFAULT 0,
                    top_frame_site_key TEXT NOT NULL DEFAULT '',
                    source_type INTEGER NOT NULL DEFAULT 0,
                    has_cross_site_ancestor INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            val now = (System.currentTimeMillis() / 1000L + 11644473600L) * 1_000_000L
            val cv = ContentValues().apply {
                put("creation_utc", now)
                put("host_key", ".roblox.com")
                put("name", ".ROBLOSECURITY")
                put("value", cookie)
                put("encrypted_value", ByteArray(0))
                put("path", "/")
                put("expires_utc", now + 365L * 24 * 3600 * 1_000_000L)
                put("is_secure", 1)
                put("is_httponly", 1)
                put("last_access_utc", now)
                put("has_expires", 1)
                put("is_persistent", 1)
                put("priority", 1)
                put("samesite", -1)
                put("source_scheme", 2)
                put("source_port", 443)
                put("last_update_utc", now)
                put("top_frame_site_key", "")
                put("source_type", 0)
                put("has_cross_site_ancestor", 0)
            }
            db.insert("cookies", null, cv)
            db.close()

            // Get app UID for correct ownership
            val appUid = RootUtils.exec("stat -c '%u' '/data/data/$pkg'").trim()

            // Push Cookies DB into app data dir
            RootUtils.exec("mkdir -p '$cookieDir'")
            RootUtils.exec(
                "cp '${tmp.absolutePath}' '$dbPath' && " +
                "chmod 600 '$dbPath' && " +
                "chown $appUid:$appUid '$dbPath'"
            )
            tmp.delete()

            // Launch app — WebView will see the cookie on first load and auto-login
            Thread.sleep(400)
            RootUtils.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            true
        } catch (e: Exception) { tmp.delete(); false }
    }
}
