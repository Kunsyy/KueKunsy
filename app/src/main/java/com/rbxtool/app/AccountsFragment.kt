package com.rbxtool.app

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
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
import com.rbxtool.app.util.RobloxApi
import com.rbxtool.app.util.RobloxAuth
import com.rbxtool.app.util.SolverType
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

        b.btnAddAccount.setOnClickListener { showAddAccountDialog() }
        b.btnRefreshAll.setOnClickListener { refreshAll() }
        b.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Hapus Semua?")
                .setMessage("Semua akun tersimpan akan dihapus.")
                .setPositiveButton("Hapus Semua") { _, _ -> storage.clear(); refreshList() }
                .setNegativeButton("Batal", null)
                .show()
        }
        refreshList()
    }

    override fun onResume() { super.onResume(); refreshList() }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshList()
    }

    private fun refreshList() {
        val list = storage.getAll()
        adapter.update(list)
        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        b.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // ── Manual Add Account ────────────────────────────────────────────────────

    private fun showAddAccountDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }
        val etUser = EditText(ctx).apply {
            hint = "Username"
            setTextColor(0xFFF0F0F0.toInt())
            setHintTextColor(0xFF555555.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(0, 16, 0, 16)
        }
        val etPass = EditText(ctx).apply {
            hint = "Password"
            setTextColor(0xFFF0F0F0.toInt())
            setHintTextColor(0xFF555555.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(0, 16, 0, 16)
        }
        layout.addView(etUser)
        layout.addView(etPass)

        AlertDialog.Builder(ctx)
            .setTitle("Tambah Akun")
            .setMessage("Login otomatis untuk dapat cookie fresh.")
            .setView(layout)
            .setPositiveButton("Tambah") { _, _ ->
                val user = etUser.text.toString().trim()
                val pass = etPass.text.toString()
                if (user.isBlank() || pass.isBlank()) {
                    Toast.makeText(ctx, "Username & password tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                doAddAccount(user, pass)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun doAddAccount(username: String, password: String) {
        Toast.makeText(requireContext(), "⏳ Login @$username...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RobloxAuth.login(username, password, getSolverConfig())
            }
            when {
                result.cookie != null -> {
                    val user = withContext(Dispatchers.IO) { RobloxApi.getUser(result.cookie) }
                    val account = Account(
                        id = user?.id ?: System.currentTimeMillis(),
                        username = user?.name ?: username,
                        displayName = user?.displayName ?: username,
                        robux = user?.robux ?: 0,
                        cookie = result.cookie,
                        packageName = "com.roblox.client",
                        password = password
                    )
                    storage.save(account)
                    refreshList()
                    Toast.makeText(requireContext(), "✅ @${account.username} ditambahkan!", Toast.LENGTH_SHORT).show()
                }
                result.needsCaptcha -> Toast.makeText(requireContext(),
                    "⚠️ Butuh captcha solver. Set di Settings.", Toast.LENGTH_LONG).show()
                else -> Toast.makeText(requireContext(),
                    "❌ ${result.error}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Refresh All ───────────────────────────────────────────────────────────

    private fun refreshAll() {
        val accounts = storage.getAll().filter { it.password.isNotEmpty() }
        if (accounts.isEmpty()) {
            Toast.makeText(requireContext(),
                "Belum ada akun dengan password tersimpan.\nTap REFRESH di tiap akun dulu.",
                Toast.LENGTH_LONG).show()
            return
        }
        b.btnRefreshAll.isEnabled = false
        b.btnRefreshAll.text = "Refreshing..."
        lifecycleScope.launch {
            var success = 0
            accounts.forEachIndexed { i, acc ->
                withContext(Dispatchers.Main) {
                    b.btnRefreshAll.text = "Refreshing ${i + 1}/${accounts.size}..."
                }
                val result = withContext(Dispatchers.IO) {
                    RobloxAuth.login(acc.username, acc.password, getSolverConfig())
                }
                if (result.cookie != null) {
                    storage.save(acc.copy(cookie = result.cookie))
                    success++
                }
            }
            refreshList()
            b.btnRefreshAll.isEnabled = true
            b.btnRefreshAll.text = "↻  REFRESH SEMUA COOKIE"
            Toast.makeText(requireContext(),
                "✅ $success/${accounts.size} akun berhasil diperbarui!", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Inject ────────────────────────────────────────────────────────────────

    fun injectAccount(account: Account, onDone: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val packages = withContext(Dispatchers.IO) {
                com.rbxtool.app.util.RootUtils.getRobloxPackages()
            }
            if (packages.isEmpty()) {
                onDone(false)
                Toast.makeText(requireContext(), "❌ Tidak ada Roblox terdeteksi.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (packages.size == 1) {
                // Langsung inject jika hanya 1 clone
                doInject(account, packages[0], onDone)
            } else {
                // Pilih clone dulu
                val labels = packages.map { it.removePrefix("com.roblox.") }.toTypedArray()
                val defaultIdx = packages.indexOf(account.packageName).coerceAtLeast(0)
                var selected = defaultIdx
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Inject ke clone mana?")
                    .setSingleChoiceItems(labels, defaultIdx) { _, which -> selected = which }
                    .setPositiveButton("Inject") { _, _ ->
                        doInject(account, packages[selected], onDone)
                    }
                    .setNegativeButton("Batal") { _, _ -> onDone(false) }
                    .show()
            }
        }
    }

    private fun doInject(account: Account, pkg: String, onDone: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                CookieManager(requireContext()).injectCookie(account.cookie, pkg)
            }
            onDone(ok)
            Toast.makeText(requireContext(),
                if (ok) "✅ @${account.username} → ${pkg.removePrefix("com.roblox.")}! Buka Roblox manual."
                else "❌ Inject gagal. Cek root & package.",
                Toast.LENGTH_SHORT).show()
        }
    }

    // ── Refresh Single ────────────────────────────────────────────────────────

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

    private fun doRefresh(account: Account, onDone: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RobloxAuth.login(account.username, account.password, getSolverConfig())
            }
            when {
                result.cookie != null -> {
                    storage.save(account.copy(cookie = result.cookie))
                    refreshList()
                    onDone(true)
                    Toast.makeText(requireContext(),
                        "✅ Cookie @${account.username} diperbarui!", Toast.LENGTH_SHORT).show()
                }
                result.needsCaptcha -> {
                    onDone(false)
                    Toast.makeText(requireContext(),
                        "⚠️ Butuh captcha solver. Set di Settings.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    onDone(false)
                    Toast.makeText(requireContext(), "❌ ${result.error}", Toast.LENGTH_SHORT).show()
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
            .setMessage("Disimpan lokal untuk refresh cookie otomatis.")
            .setView(et)
            .setPositiveButton("Simpan & Refresh") { _, _ -> onPassword(et.text.toString()) }
            .setNegativeButton("Batal") { _, _ -> onPassword("") }
            .show()
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private fun deleteAccount(account: Account) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Akun?")
            .setMessage("@${account.username} akan dihapus dari daftar.")
            .setPositiveButton("Hapus") { _, _ -> storage.delete(account.id); refreshList() }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getSolverConfig(): CaptchaSolver.SolverConfig {
        val prefs = requireContext().getSharedPreferences("rbx_settings", Context.MODE_PRIVATE)
        val typeName = prefs.getString("captcha_solver_type", SolverType.NONE.name)
        val key = prefs.getString("captcha_solver_key", "") ?: ""
        val type = try { SolverType.valueOf(typeName ?: "") }
                   catch (e: Exception) { SolverType.NONE }
        return CaptchaSolver.SolverConfig(type, key)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
