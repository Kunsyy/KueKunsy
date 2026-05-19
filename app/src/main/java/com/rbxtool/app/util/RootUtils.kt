package com.rbxtool.app.util

import java.io.DataOutputStream

object RootUtils {
    fun exec(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()
            val result = p.inputStream.bufferedReader().readText()
            p.waitFor()
            result.trim()
        } catch (e: Exception) { "" }
    }

    fun isRooted(): Boolean = exec("id").contains("uid=0")

    fun getRobloxPackages(): List<String> =
        exec("ls /data/data/ | grep -i roblox")
            .split("\n")
            .filter { it.isNotBlank() }
}
