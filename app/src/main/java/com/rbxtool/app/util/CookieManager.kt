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
            // Step 1: Wipe all app data
            RootUtils.exec("am force-stop $pkg")
            Thread.sleep(500)
            RootUtils.exec("pm clear $pkg")
            Thread.sleep(1000)

            // Step 2: Boot Roblox briefly so WebView creates proper Cookies DB
            RootUtils.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null")
            Thread.sleep(7000) // Wait for WebView to init and create Cookies DB

            // Step 3: Force stop again before we modify
            RootUtils.exec("am force-stop $pkg")
            Thread.sleep(1000)

            // Step 4: Copy the properly-formed Cookies DB that Roblox created
            RootUtils.exec("cp '$dbPath' '${tmp.absolutePath}' && chmod 666 '${tmp.absolutePath}'")

            // If Roblox didn't create the DB yet (slow device), create dir and continue
            if (!tmp.exists()) {
                RootUtils.exec("mkdir -p '$cookieDir'")
                tmp.createNewFile()
                val freshDb = SQLiteDatabase.openOrCreateDatabase(tmp.absolutePath, null)
                freshDb.execSQL("CREATE TABLE IF NOT EXISTS meta(key LONGVARCHAR NOT NULL UNIQUE PRIMARY KEY, value LONGVARCHAR)")
                freshDb.execSQL("INSERT OR IGNORE INTO meta VALUES('version','20')")
                freshDb.execSQL("INSERT OR IGNORE INTO meta VALUES('last_compatible_version','20')")
                freshDb.execSQL("CREATE TABLE IF NOT EXISTS cookies (creation_utc INTEGER NOT NULL UNIQUE PRIMARY KEY, host_key TEXT NOT NULL, top_frame_site_key TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, encrypted_value BLOB DEFAULT '', path TEXT NOT NULL, expires_utc INTEGER NOT NULL, is_secure INTEGER NOT NULL, is_httponly INTEGER NOT NULL, last_access_utc INTEGER NOT NULL, has_expires INTEGER NOT NULL DEFAULT 1, is_persistent INTEGER NOT NULL DEFAULT 1, priority INTEGER NOT NULL DEFAULT 1, samesite INTEGER NOT NULL DEFAULT -1, source_scheme INTEGER NOT NULL DEFAULT 0, source_port INTEGER NOT NULL DEFAULT -1, last_update_utc INTEGER NOT NULL DEFAULT 0, source_type INTEGER NOT NULL DEFAULT 0, has_cross_site_ancestor INTEGER NOT NULL DEFAULT 0)")
                freshDb.close()
            }

            // Step 5: Inject cookie into the DB
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

            // Step 6: Copy back with correct ownership + fix SELinux context
            val appUid = RootUtils.exec("stat -c '%u' '/data/data/$pkg'").trim()
            RootUtils.exec(
                "cp '${tmp.absolutePath}' '$dbPath' && " +
                "chmod 600 '$dbPath' && " +
                "chown $appUid:$appUid '$dbPath' && " +
                "restorecon '$dbPath' 2>/dev/null; " +
                "rm -f '${dbPath}-shm' '${dbPath}-wal'"
            )
            tmp.delete()

            // Step 7: Launch - tap "Sign in to another account" on the screen that appears
            Thread.sleep(500)
            RootUtils.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null")
            true
        } catch (e: Exception) { tmp.delete(); false }
    }
}
