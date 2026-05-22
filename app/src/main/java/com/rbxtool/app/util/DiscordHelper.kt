package com.rbxtool.app.util

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object DiscordHelper {
    fun sendMessage(webhookUrl: String, content: String): Boolean = try {
        if (webhookUrl.contains("GANTI_")) return false
        val conn = URL(webhookUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15000
        // Escape content for JSON
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        val body = """{"content":"$escaped"}"""
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
        val code = conn.responseCode
        code in 200..204
    } catch (e: Exception) { false }
}
