package com.rbxtool.app.util

import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramHelper {
    fun sendMessage(token: String, chatId: String, text: String): Boolean = try {
        val conn = URL("https://api.telegram.org/bot$token/sendMessage")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.connectTimeout = 15000
        val params = "chat_id=${enc(chatId)}&parse_mode=HTML&disable_web_page_preview=true&text=${enc(text)}"
        OutputStreamWriter(conn.outputStream).use { it.write(params) }
        conn.responseCode == 200
    } catch (e: Exception) { false }

    fun sendFile(token: String, chatId: String, file: File, caption: String = ""): Boolean = try {
        val boundary = "RbxToolBoundary"
        val conn = URL("https://api.telegram.org/bot$token/sendDocument")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.connectTimeout = 30000
        conn.outputStream.use { os ->
            fun part(name: String, v: String) =
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$v\r\n".toByteArray())
            part("chat_id", chatId)
            part("caption", caption)
            os.write("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\nContent-Type: text/plain\r\n\r\n".toByteArray())
            os.write(file.readBytes())
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }
        conn.responseCode == 200
    } catch (e: Exception) { false }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
