package com.rbxtool.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rbxtool.app.data.Account
import com.rbxtool.app.data.AccountStorage
import com.rbxtool.app.databinding.FragmentAccountsBinding
import com.rbxtool.app.util.CookieManager
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
        adapter = AccountAdapter(emptyList(), { acc, onDone -> injectAccount(acc, onDone) }, ::deleteAccount)
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
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAccount(account: Account) {
        storage.delete(account.id)
        refreshList()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
