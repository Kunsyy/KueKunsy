package com.rbxtool.app.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RobloxUser(
    val id: Long,
    val name: String,
    val displayName: String,
    val robux: Int = 0
)

object RobloxApi {
    fun getUser(cookie: String): RobloxUser? = try {
        val conn = URL("https://users.roblox.com/v1/users/authenticated")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", ".ROBLOSECURITY=$cookie")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        if (conn.responseCode == 200) {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val id = json.getLong("id")
            RobloxUser(id, json.getString("name"), json.getString("displayName"), getRobux(cookie, id))
        } else null
    } catch (e: Exception) { null }

    private fun getRobux(cookie: String, userId: Long): Int = try {
        val conn = URL("https://economy.roblox.com/v1/users/$userId/currency")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", ".ROBLOSECURITY=$cookie")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        if (conn.responseCode == 200)
            JSONObject(conn.inputStream.bufferedReader().readText()).optInt("robux", 0)
        else 0
    } catch (e: Exception) { 0 }
}
