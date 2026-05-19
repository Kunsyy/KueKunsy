package com.rbxtool.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rbxtool.app.data.Account
import com.rbxtool.app.databinding.ItemAccountBinding

class AccountAdapter(
    private var items: List<Account>,
    private val onInject: (Account) -> Unit,
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
        h.b.btnInject.setOnClickListener { onInject(acc) }
        h.b.btnDelete.setOnClickListener { onDelete(acc) }
    }

    fun update(newItems: List<Account>) {
        items = newItems
        notifyDataSetChanged()
    }
}
