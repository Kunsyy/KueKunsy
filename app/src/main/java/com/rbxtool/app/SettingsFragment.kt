package com.rbxtool.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rbxtool.app.databinding.FragmentSettingsBinding
import com.rbxtool.app.util.RootUtils
import com.rbxtool.app.util.TelegramHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val prefs = requireContext().getSharedPreferences("rbx_settings", Context.MODE_PRIVATE)

        // Load saved values
        b.etBotToken.setText(prefs.getString("bot_token", ""))
        b.etChatId.setText(prefs.getString("chat_id", ""))

        // Check root status
        lifecycleScope.launch {
            val rooted = withContext(Dispatchers.IO) { RootUtils.isRooted() }
            b.tvRootStatus.text = if (rooted) "✅ ROOT AKTIF" else "❌ ROOT TIDAK TERDETEKSI"
            b.tvRootStatus.setTextColor(
                if (rooted) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            )
        }

        b.btnSave.setOnClickListener {
            val token = b.etBotToken.text?.toString()?.trim() ?: ""
            val chatId = b.etChatId.text?.toString()?.trim() ?: ""
            prefs.edit()
                .putString("bot_token", token)
                .putString("chat_id", chatId)
                .apply()
            b.tvTestResult.setTextColor(Color.parseColor("#4CAF50"))
            b.tvTestResult.text = "✅ Tersimpan!"
        }

        b.btnTest.setOnClickListener {
            val token = b.etBotToken.text?.toString()?.trim() ?: ""
            val chatId = b.etChatId.text?.toString()?.trim() ?: ""
            if (token.isEmpty() || chatId.isEmpty()) {
                b.tvTestResult.setTextColor(Color.parseColor("#F44336"))
                b.tvTestResult.text = "❌ Isi BOT_TOKEN dan CHAT_ID dulu!"
                return@setOnClickListener
            }
            b.tvTestResult.setTextColor(Color.parseColor("#FFC107"))
            b.tvTestResult.text = "Mengirim test..."
            b.btnTest.isEnabled = false
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    TelegramHelper.sendMessage(token, chatId, "✅ <b>RbxTool</b> terhubung ke Telegram!")
                }
                b.btnTest.isEnabled = true
                if (ok) {
                    b.tvTestResult.setTextColor(Color.parseColor("#4CAF50"))
                    b.tvTestResult.text = "✅ Berhasil! Cek Telegram lo."
                } else {
                    b.tvTestResult.setTextColor(Color.parseColor("#F44336"))
                    b.tvTestResult.text = "❌ Gagal. BOT_TOKEN atau CHAT_ID salah."
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
