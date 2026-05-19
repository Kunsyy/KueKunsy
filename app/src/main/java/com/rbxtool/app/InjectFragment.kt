package com.rbxtool.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rbxtool.app.databinding.FragmentInjectBinding
import com.rbxtool.app.util.CookieManager
import com.rbxtool.app.util.RobloxApi
import com.rbxtool.app.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InjectFragment : Fragment() {
    private var _b: FragmentInjectBinding? = null
    private val b get() = _b!!
    private var packages = listOf<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentInjectBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        loadPackages()
        b.btnInject.setOnClickListener { doInject() }
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            packages = withContext(Dispatchers.IO) { RootUtils.getRobloxPackages() }
            val labels = packages.map { it.removePrefix("com.roblox.") }
                .ifEmpty { listOf("(tidak ada Roblox terinstall)") }
            val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, labels)
            adapter.setDropDownViewResource(R.layout.item_spinner)
            b.spinnerPackage.adapter = adapter
        }
    }

    private fun doInject() {
        val cookie = b.etCookie.text?.toString()?.trim() ?: ""
        if (cookie.isEmpty()) { showResult(false, "Cookie kosong!"); return }
        if (!cookie.startsWith("_|WARNING")) {
            showResult(false, "Format salah. Harus diawali _|WARNING:-DO-NOT-SHARE...")
            return
        }
        if (packages.isEmpty()) { showResult(false, "Tidak ada Roblox ditemukan."); return }
        val selectedPkg = packages.getOrNull(b.spinnerPackage.selectedItemPosition) ?: return

        b.btnInject.isEnabled = false
        b.progress.visibility = View.VISIBLE
        b.cardResult.visibility = View.GONE

        lifecycleScope.launch {
            // Verify cookie & get user info BEFORE injecting
            val user = withContext(Dispatchers.IO) { RobloxApi.getUser(cookie) }
            if (user == null) {
                showResult(false, "❌ Cookie invalid atau expired.\n\nPastikan cookie masih fresh.")
                b.btnInject.isEnabled = true
                b.progress.visibility = View.GONE
                return@launch
            }

            // Inject cookie + update SharedPreferences
            val ok = withContext(Dispatchers.IO) {
                CookieManager(requireContext()).injectCookie(
                    cookie, selectedPkg,
                    userId = user.id,
                    username = user.name,
                    displayName = user.displayName
                )
            }

            if (ok)
                showResult(true, "✅ Berhasil!\n\n👤 @${user.name}\n💰 ${user.robux} Robux\n\nRoblox sudah dibuka otomatis.")
            else
                showResult(false, "❌ Inject gagal.\n\nPastikan:\n• Roblox pernah login minimal sekali\n• Root aktif\n• Pilih package yang benar")

            b.btnInject.isEnabled = true
            b.progress.visibility = View.GONE
        }
    }

    private fun showResult(success: Boolean, msg: String) {
        b.cardResult.visibility = View.VISIBLE
        b.tvResult.text = msg
        b.tvResult.setTextColor(
            if (success) resources.getColor(R.color.success, null)
            else resources.getColor(R.color.error, null)
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
