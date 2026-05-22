package com.rbxtool.app.util

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Roblox FunCaptcha public key
 */
private const val ROBLOX_FUNCAPTCHA_KEY = "476068BF-9607-4799-B53D-966BE98E2B81"
private const val ROBLOX_PAGE_URL = "https://www.roblox.com"

enum class SolverType {
    NONE,
    // Group 1: 2Captcha-compatible format
    TWOCAPTCHA,
    CAPMONSTER,
    AZCAPTCHA,
    SOLVECAPTCHA,
    // Group 2: createTask/getTaskResult format
    ANTICAPTCHA,
    CAPSOLVER,
    EZCAPTCHA,
    NEXTCAPTCHA,
    YESCAPTCHA,
    SOLVEX
}

object CaptchaSolver {

    data class SolverConfig(
        val type: SolverType,
        val apiKey: String
    )

    // Base URLs for each solver
    private val baseUrls = mapOf(
        SolverType.TWOCAPTCHA   to "https://2captcha.com",
        SolverType.CAPMONSTER   to "https://api.capmonster.cloud",
        SolverType.AZCAPTCHA    to "https://azcaptcha.com",
        SolverType.SOLVECAPTCHA to "https://api.solvecaptcha.com",
        SolverType.ANTICAPTCHA  to "https://api.anti-captcha.com",
        SolverType.CAPSOLVER    to "https://api.capsolver.com",
        SolverType.EZCAPTCHA    to "https://api.ez-captcha.com",
        SolverType.NEXTCAPTCHA  to "https://api.nextcaptcha.com",
        SolverType.YESCAPTCHA   to "https://api.yescaptcha.com",
        SolverType.SOLVEX       to "https://api.solvex.run"
    )

    // Group 1: 2Captcha-compatible format
    private val group1 = setOf(
        SolverType.TWOCAPTCHA, SolverType.CAPMONSTER,
        SolverType.AZCAPTCHA, SolverType.SOLVECAPTCHA
    )

    /**
     * Solve FunCaptcha and return token, or null if failed
     */
    fun solve(config: SolverConfig): String? {
        if (config.type == SolverType.NONE || config.apiKey.isBlank()) return null
        val base = baseUrls[config.type] ?: return null
        return try {
            if (config.type in group1) solveGroup1(base, config.apiKey)
            else solveGroup2(base, config.type, config.apiKey)
        } catch (e: Exception) { null }
    }

    // ── Group 1: 2Captcha-style ──────────────────────────────────────────────

    private fun solveGroup1(base: String, apiKey: String): String? {
        // Submit
        val conn = URL("$base/in.php").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.connectTimeout = 15000
        val params = "key=$apiKey&method=funcaptcha&publickey=$ROBLOX_FUNCAPTCHA_KEY&pageurl=$ROBLOX_PAGE_URL&json=1"
        OutputStreamWriter(conn.outputStream).use { it.write(params) }
        val submitResp = JSONObject(conn.inputStream.bufferedReader().readText())
        if (submitResp.optInt("status") != 1) return null
        val taskId = submitResp.getString("request")

        // Poll
        repeat(24) {
            Thread.sleep(5000)
            val res = URL("$base/res.php?key=$apiKey&action=get&id=$taskId&json=1")
                .openConnection() as HttpURLConnection
            res.connectTimeout = 10000
            val poll = JSONObject(res.inputStream.bufferedReader().readText())
            if (poll.optInt("status") == 1) return poll.getString("request")
        }
        return null
    }

    // ── Group 2: createTask/getTaskResult ────────────────────────────────────

    private fun solveGroup2(base: String, type: SolverType, apiKey: String): String? {
        // Task type varies slightly per service
        val taskType = if (type == SolverType.YESCAPTCHA) "FunCaptchaClassification"
                       else "FunCaptchaTaskProxyless"

        val task = JSONObject().apply {
            put("type", taskType)
            put("websiteURL", ROBLOX_PAGE_URL)
            put("websitePublicKey", ROBLOX_FUNCAPTCHA_KEY)
        }
        val submitBody = JSONObject().apply {
            put("clientKey", apiKey)
            put("task", task)
        }.toString()

        // Submit
        val conn = URL("$base/createTask").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15000
        OutputStreamWriter(conn.outputStream).use { it.write(submitBody) }
        val submitResp = JSONObject(conn.inputStream.bufferedReader().readText())
        if (submitResp.optInt("errorId", 1) != 0) return null
        val taskId = submitResp.getInt("taskId")

        // Poll
        val pollBody = JSONObject().apply {
            put("clientKey", apiKey)
            put("taskId", taskId)
        }.toString()
        repeat(24) {
            Thread.sleep(5000)
            val res = URL("$base/getTaskResult").openConnection() as HttpURLConnection
            res.requestMethod = "POST"
            res.doOutput = true
            res.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(res.outputStream).use { it.write(pollBody) }
            val poll = JSONObject(res.inputStream.bufferedReader().readText())
            if (poll.optString("status") == "ready") {
                return poll.optJSONObject("solution")?.optString("token")
            }
        }
        return null
    }

    fun displayName(type: SolverType) = when (type) {
        SolverType.NONE        -> "— Pilih Solver —"
        SolverType.TWOCAPTCHA  -> "2Captcha"
        SolverType.CAPMONSTER  -> "CapMonster"
        SolverType.AZCAPTCHA   -> "AZcaptcha"
        SolverType.SOLVECAPTCHA-> "SolveCaptcha"
        SolverType.ANTICAPTCHA -> "Anti-Captcha"
        SolverType.CAPSOLVER   -> "Capsolver"
        SolverType.EZCAPTCHA   -> "EzCaptcha"
        SolverType.NEXTCAPTCHA -> "NextCaptcha"
        SolverType.YESCAPTCHA  -> "YesCaptcha"
        SolverType.SOLVEX      -> "Solvex"
    }

    fun allTypes() = SolverType.entries.toList()
}
