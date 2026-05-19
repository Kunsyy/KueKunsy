package com.rbxtool.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AccountStorage(context: Context) {
    private val prefs = context.getSharedPreferences("rbx_accounts", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<Account> {
        val json = prefs.getString("list", "[]") ?: "[]"
        return gson.fromJson(json, object : TypeToken<List<Account>>() {}.type)
    }

    fun save(account: Account) {
        val list = getAll().toMutableList().also { it.removeAll { a -> a.id == account.id } }
        list.add(0, account)
        prefs.edit().putString("list", gson.toJson(list)).apply()
    }

    fun delete(id: Long) {
        prefs.edit().putString("list", gson.toJson(getAll().filter { it.id != id })).apply()
    }

    fun clear() = prefs.edit().remove("list").apply()
}
