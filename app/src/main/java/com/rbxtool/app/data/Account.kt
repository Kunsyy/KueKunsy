package com.rbxtool.app.data

data class Account(
    val id: Long,
    val username: String,
    val displayName: String,
    val robux: Int,
    val cookie: String,
    val packageName: String,
    val savedAt: Long = System.currentTimeMillis(),
    val password: String = ""
)
