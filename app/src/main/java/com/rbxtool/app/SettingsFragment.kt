package com.rbxtool.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rbxtool.app.databinding.FragmentSettingsBinding
import com.rbxtool.app.util.Constants
import com.rbxtool.app.util.RootUtils
import com.rbxtool.app.util.DiscordHelper
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

        lifecycleScope.launch {
            val rooted = withContext(Dispatchers.IO) { RootUtils.isRooted() }
            b.tvRootStatus.text = if (rooted) "✅ ROOT AKTIF" else "❌ ROOT TIDAK TERDETEKSI"
            b.tvRootStatus.setTextColor(
                if (rooted) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
            )
        }

        b.btnTest.setOnClickListener {
            b.tvTestResult.setTextColor(Color.parseColor("#FBBF24"))
            b.tvTestResult.text = "Mengirim test..."
            b.btnTest.isEnabled = false
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    DiscordHelper.sendMessage(
                        Constants.DISCORD_WEBHOOK,
                        "✅ **RbxTool** terhubung ke Discord!"
                    )
                }
                b.btnTest.isEnabled = true
                if (ok) {
                    b.tvTestResult.setTextColor(Color.parseColor("#4ADE80"))
                    b.tvTestResult.text = "✅ Terhubung! Cek Discord."
                } else {
                    b.tvTestResult.setTextColor(Color.parseColor("#F87171"))
                    b.tvTestResult.text = "❌ Gagal. Cek webhook URL atau koneksi."
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
