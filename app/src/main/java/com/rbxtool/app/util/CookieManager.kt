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
        val dbPath = "/data/data/$pkg/app_webview/Default/Cookies"
        val prefsPath = "/data/data/$pkg/shared_prefs/prefs.xml"
        val tmp = File(context.cacheDir, "rbx_inject.db")

        RootUtils.exec("am force-stop $pkg")
        Thread.sleep(900)

        // Inject WebView cookie DB
        RootUtils.exec("cp '$dbPath' '${tmp.absolutePath}' && chmod 666 '${tmp.absolutePath}'")
        if (!tmp.exists()) return false

        return try {
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

            val owner = RootUtils.exec("stat -c '%U:%G' '$dbPath'").trim()
            RootUtils.exec(
                "cp '${tmp.absolutePath}' '$dbPath' && " +
                "chmod 600 '$dbPath' && " +
                "chown $owner '$dbPath' && " +
                "rm -f '${dbPath}-shm' '${dbPath}-wal'"
            )
            tmp.delete()

            // Update prefs.xml so app recognizes the new account
            if (userId > 0) {
                RootUtils.exec("""
                    sed -i 's|<long name="userid_long" value="[0-9]*"|<long name="userid_long" value="$userId"|g' '$prefsPath'
                    sed -i 's|<string name="username">[^<]*</string>|<string name="username">$username</string>|g' '$prefsPath'
                    sed -i 's|<string name="displayName">[^<]*</string>|<string name="displayName">$displayName</string>|g' '$prefsPath'
                """.trimIndent())
            }

            Thread.sleep(400)
            RootUtils.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            true
        } catch (e: Exception) { tmp.delete(); false }
    }
}
