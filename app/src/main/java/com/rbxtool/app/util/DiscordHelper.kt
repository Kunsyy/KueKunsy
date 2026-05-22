package com.rbxtool.app.util

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object DiscordHelper {
    fun sendMessage(webhookUrl: String, content: String): Boolean {
        if (webhookUrl.contains("GANTI_")) return false
        return try {
            val conn = URL(webhookUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            val escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
            val body = """{"content":"$escaped"}"""
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            conn.responseCode in 200..204
        } catch (e: Exception) { false }
    }
}
