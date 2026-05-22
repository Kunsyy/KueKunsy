package com.rbxtool.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rbxtool.app.data.Account
import com.rbxtool.app.databinding.ItemAccountBinding

class AccountAdapter(
    private var items: List<Account>,
    private val onInject: (Account, (Boolean) -> Unit) -> Unit,
    private val onRefresh: (Account, (Boolean) -> Unit) -> Unit,
    private val onDelete: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.VH>() {

    inner class VH(val b: ItemAccountBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val acc = items[pos]
        h.b.tvUsername.text = "@${acc.username}"
        h.b.tvRobux.text = "💰 ${acc.robux} Robux"
        h.b.tvSource.text = acc.packageName.removePrefix("com.roblox.")
        h.b.tvHasPass.visibility = if (acc.password.isNotEmpty()) View.VISIBLE else View.GONE

        h.b.btnInject.setOnClickListener {
            h.b.btnInject.isEnabled = false
            h.b.btnInject.text = "..."
            onInject(acc) { ok ->
                h.b.btnInject.isEnabled = true
                h.b.btnInject.text = if (ok) "✓ OK" else "INJECT"
                h.b.btnInject.postDelayed({ h.b.btnInject.text = "INJECT" }, 2000)
            }
        }

        h.b.btnRefresh.setOnClickListener {
            h.b.btnRefresh.isEnabled = false
            h.b.btnRefresh.text = "..."
            onRefresh(acc) { ok ->
                h.b.btnRefresh.isEnabled = true
                h.b.btnRefresh.text = if (ok) "✓ FRESH" else "REFRESH"
                h.b.btnRefresh.postDelayed({ h.b.btnRefresh.text = "REFRESH" }, 2000)
            }
        }

        h.b.btnDelete.setOnClickListener { onDelete(acc) }
    }

    fun update(newItems: List<Account>) {
        items = newItems
        notifyDataSetChanged()
    }
}
