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
        val tmp = File(context.cacheDir, "rbx_inject.db")

        return try {
            // Step 1: Force stop Roblox (no pm clear — pebletz doesn't wipe data)
            RootUtils.exec("am force-stop $pkg")
            Thread.sleep(800)

            // Step 2: Find the correct Cookies DB path (Android 10 vs 12 may differ)
            val dbPath = listOf(
                "/data/data/$pkg/app_webview/Default/Cookies",
                "/data/data/$pkg/app_webview/Default/Network/Cookies"
            ).firstOrNull { path ->
                RootUtils.exec("[ -f '$path' ] && echo yes").trim() == "yes"
            } ?: return false

            val cookieDir = dbPath.substringBeforeLast("/")

            // Step 3: Copy existing Cookies DB to temp (keep original structure intact)
            RootUtils.exec("cp '$dbPath' '${tmp.absolutePath}' && chmod 666 '${tmp.absolutePath}'")
            if (!tmp.exists()) return false

            // Step 4: Inject cookie into the DB
            val db = SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val now = (System.currentTimeMillis() / 1000L + 11644473600L) * 1_000_000L

            val schemaCur = db.rawQuery("PRAGMA table_info(cookies)", null)
            val cols = mutableSetOf<String>()
            while (schemaCur.moveToNext()) cols.add(schemaCur.getString(1))
            schemaCur.close()

            db.delete("cookies", "name=?", arrayOf(".ROBLOSECURITY"))
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
                if ("top_frame_site_key" in cols) put("top_frame_site_key", "")
                if ("source_scheme" in cols) put("source_scheme", 2)
                if ("source_port" in cols) put("source_port", 443)
                if ("last_update_utc" in cols) put("last_update_utc", now)
                if ("source_type" in cols) put("source_type", 0)
                if ("has_cross_site_ancestor" in cols) put("has_cross_site_ancestor", 0)
            }
            db.insert("cookies", null, cv)
            db.close()

            // Step 5: Copy back with correct ownership + fix SELinux (handles Android 10 & 12)
            val appUid = RootUtils.exec("stat -c '%u' '/data/data/$pkg'").trim()
            RootUtils.exec(
                "cp '${tmp.absolutePath}' '$dbPath' && " +
                "chmod 600 '$dbPath' && " +
                "chown $appUid:$appUid '$dbPath' && " +
                "restorecon -R '$cookieDir' 2>/dev/null; " +
                "rm -f '${dbPath}-shm' '${dbPath}-wal'"
            )
            tmp.delete()

            true
        } catch (e: Exception) { tmp.delete(); false }
    }
}
