package com.rbxtool.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rbxtool.app.data.Account
import com.rbxtool.app.data.AccountStorage
import com.rbxtool.app.databinding.FragmentAccountsBinding
import com.rbxtool.app.util.CaptchaSolver
import com.rbxtool.app.util.CookieManager
import com.rbxtool.app.util.RobloxAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountsFragment : Fragment() {
    private var _b: FragmentAccountsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: AccountAdapter
    private lateinit var storage: AccountStorage

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAccountsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        storage = AccountStorage(requireContext())
        adapter = AccountAdapter(
            emptyList(),
            { acc, onDone -> injectAccount(acc, onDone) },
            { acc, onDone -> refreshAccount(acc, onDone) },
            ::deleteAccount
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter
        b.btnClearAll.setOnClickListener {
            storage.clear()
            refreshList()
        }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    // Fix: hide/show fragment doesn't trigger onResume — use onHiddenChanged instead
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshList()
    }

    private fun refreshList() {
        val list = storage.getAll()
        adapter.update(list)
        if (list.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            b.recyclerView.visibility = View.GONE
        } else {
            b.tvEmpty.visibility = View.GONE
            b.recyclerView.visibility = View.VISIBLE
        }
    }

    fun injectAccount(account: Account, onDone: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                CookieManager(requireContext()).injectCookie(account.cookie, account.packageName)
            }
            onDone(ok)
            val msg = if (ok) "✅ @${account.username} diinject! Buka Roblox manual."
                      else "❌ Inject gagal. Cek root & package."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAccount(account: Account, onDone: (Boolean) -> Unit) {
        if (account.password.isEmpty()) {
            showPasswordDialog(account) { password ->
                if (password.isBlank()) { onDone(false); return@showPasswordDialog }
                val updated = account.copy(password = password)
                storage.save(updated)
                doRefresh(updated, onDone)
            }
        } else {
            doRefresh(account, onDone)
        }
    }

    private fun getSolverConfig(): CaptchaSolver.SolverConfig {
        val prefs = requireContext().getSharedPreferences("rbx_settings", Context.MODE_PRIVATE)
        val typeName = prefs.getString("captcha_solver_type", CaptchaSolver.SolverType.NONE.name)
        val key = prefs.getString("captcha_solver_key", "") ?: ""
        val type = try { CaptchaSolver.SolverType.valueOf(typeName ?: "") }
                   catch (e: Exception) { CaptchaSolver.SolverType.NONE }
        return CaptchaSolver.SolverConfig(type, key)
    }

    private fun doRefresh(account: Account, onDone: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RobloxAuth.login(account.username, account.password, getSolverConfig())
            }
            when {
                result.cookie != null -> {
                    val updated = account.copy(cookie = result.cookie)
                    storage.save(updated)
                    refreshList()
                    onDone(true)
                    Toast.makeText(requireContext(),
                        "✅ Cookie @${account.username} diperbarui!", Toast.LENGTH_SHORT).show()
                }
                result.needsCaptcha -> {
                    onDone(false)
                    Toast.makeText(requireContext(),
                        "⚠️ Butuh captcha solver. Tambah API key di Settings.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    onDone(false)
                    Toast.makeText(requireContext(),
                        "❌ ${result.error}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPasswordDialog(account: Account, onPassword: (String) -> Unit) {
        val et = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password @${account.username}"
            setTextColor(0xFFF0F0F0.toInt())
            setHintTextColor(0xFF555555.toInt())
            setPadding(40, 24, 40, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Masukkan Password")
            .setMessage("Password disimpan lokal. Dipakai untuk refresh cookie otomatis.")
            .setView(et)
            .setPositiveButton("Simpan & Refresh") { _, _ -> onPassword(et.text.toString()) }
            .setNegativeButton("Batal") { _, _ -> onPassword("") }
            .show()
    }

    private fun deleteAccount(account: Account) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Akun?")
            .setMessage("@${account.username} akan dihapus dari daftar.")
            .setPositiveButton("Hapus") { _, _ ->
                storage.delete(account.id)
                refreshList()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
