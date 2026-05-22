package com.rbxtool.app.util

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object RobloxAuth {

    data class LoginResult(
        val cookie: String? = null,
        val needsCaptcha: Boolean = false,
        val error: String? = null
    )

    private const val LOGIN_URL = "https://auth.roblox.com/v2/login"

    fun login(
        username: String,
        password: String,
        solverConfig: CaptchaSolver.SolverConfig = CaptchaSolver.SolverConfig(CaptchaSolver.SolverType.NONE, "")
    ): LoginResult {
        return try {
            val csrf = getCsrfToken() ?: return LoginResult(error = "Gagal ambil CSRF token")
            val result = attemptLogin(username, password, csrf)

            // Jika butuh captcha dan solver tersedia → solve lalu retry
            if (result.needsCaptcha && solverConfig.type != CaptchaSolver.SolverType.NONE) {
                val token = CaptchaSolver.solve(solverConfig)
                    ?: return LoginResult(error = "Captcha solver gagal. Cek API key & saldo.")
                attemptLogin(username, password, csrf, captchaToken = token)
            } else {
                result
            }
        } catch (e: Exception) {
            LoginResult(error = e.message ?: "Unknown error")
        }
    }

    private fun attemptLogin(
        username: String,
        password: String,
        csrf: String,
        captchaToken: String = ""
    ): LoginResult {
        val conn = URL(LOGIN_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-CSRF-TOKEN", csrf)
        conn.setRequestProperty("User-Agent", "Roblox/WinInet")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = false

        if (captchaToken.isNotEmpty()) {
            conn.setRequestProperty("rblx-challenge-type", "captcha")
            conn.setRequestProperty("rblx-challenge-metadata", captchaToken)
        }

        val body = JSONObject().apply {
            put("ctype", "Username")
            put("cvalue", username)
            put("password", password)
        }.toString()
        OutputStreamWriter(conn.outputStream).use { it.write(body.toByteArray()) }

        return when (val code = conn.responseCode) {
            200 -> {
                val cookieValue = conn.headerFields["Set-Cookie"]
                    ?.find { it.contains(".ROBLOSECURITY=") }
                    ?.split(";")?.firstOrNull()
                    ?.trim()?.substringAfter(".ROBLOSECURITY=")
                if (cookieValue != null) LoginResult(cookie = cookieValue)
                else LoginResult(error = "Cookie tidak ada di response")
            }
            403 -> {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                if (errBody.contains("\"code\":2") || errBody.contains("Captcha")) {
                    LoginResult(needsCaptcha = true)
                } else {
                    LoginResult(error = "Login ditolak (403)")
                }
            }
            401 -> LoginResult(error = "Username atau password salah")
            429 -> LoginResult(error = "Terlalu banyak percobaan, tunggu beberapa menit")
            else -> LoginResult(error = "Error HTTP $code")
        }
    }

    private fun getCsrfToken(): String? {
        return try {
            val conn = URL(LOGIN_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            OutputStreamWriter(conn.outputStream).use { it.write("{}".toByteArray()) }
            conn.responseCode
            conn.headerFields["x-csrf-token"]?.firstOrNull()
        } catch (e: Exception) { null }
    }
}
