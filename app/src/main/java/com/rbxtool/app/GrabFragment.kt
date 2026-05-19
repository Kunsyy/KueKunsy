package com.rbxtool.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.rbxtool.app.data.Account
import com.rbxtool.app.data.AccountStorage
import com.rbxtool.app.databinding.FragmentGrabBinding
import com.rbxtool.app.util.CookieManager
import com.rbxtool.app.util.RobloxApi
import com.rbxtool.app.util.RootUtils
import com.rbxtool.app.util.TelegramHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrabFragment : Fragment() {
    private var _b: FragmentGrabBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentGrabBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        b.btnGrab.setOnClickListener { startGrab() }
    }

    private fun startGrab() {
        b.btnGrab.isEnabled = false
        b.progress.visibility = View.VISIBLE
        b.resultsContainer.removeAllViews()
        b.tvStatus.text = "Scanning..."

        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val mgr = CookieManager(requireContext())
                val pkgs = RootUtils.getRobloxPackages()
                if (pkgs.isEmpty()) return@withContext emptyList<Triple<String, String, Account?>>()
                pkgs.mapNotNull { pkg ->
                    val cookie = mgr.grabCookie(pkg) ?: return@mapNotNull null
                    val user = RobloxApi.getUser(cookie)
                    Triple(pkg, cookie, user?.let { Account(it.id, it.name, it.displayName, it.robux, cookie, pkg) })
                }
            }

            b.progress.visibility = View.GONE
            b.btnGrab.isEnabled = true

            if (results.isEmpty()) {
                b.tvStatus.text = "❌ Nggak ada Roblox instance / belum login"
                return@launch
            }

            b.tvStatus.text = "✅ Ketemu ${results.size} cookie"

            val prefs = requireContext().getSharedPreferences("rbx_settings", Context.MODE_PRIVATE)
            val token = prefs.getString("bot_token", "") ?: ""
            val chatId = prefs.getString("chat_id", "") ?: ""

            results.forEach { (pkg, cookie, account) ->
                addResultCard(pkg, cookie, account, token, chatId)
            }
        }
    }

    private fun addResultCard(pkg: String, cookie: String, account: Account?, token: String, chatId: String) {
        val ctx = requireContext()
        val card = MaterialCardView(ctx).apply {
            setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            radius = 12f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 16
            layoutParams = lp
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val shortPkg = pkg.removePrefix("com.roblox.")
        val header = if (account != null)
            "👤 @${account.username}  💰 ${account.robux} Robux\n📦 $shortPkg"
        else
            "📦 $shortPkg\n⚠️ Cookie valid tapi API gagal"

        inner.addView(TextView(ctx).apply {
            text = header
            setTextColor(Color.parseColor("#EEEEEE"))
            textSize = 14f
        })

        // Cookie preview
        inner.addView(TextView(ctx).apply {
            text = cookie.take(60) + "..."
            setTextColor(Color.parseColor("#888888"))
            textSize = 10f
            setPadding(0, 8, 0, 12)
        })

        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        // Save button
        btnRow.addView(MaterialButton(ctx).apply {
            text = "💾 Save"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1565C0"))
            setOnClickListener {
                account?.let {
                    AccountStorage(ctx).save(it)
                    isEnabled = false
                    text = "✓ Saved"
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 8
            layoutParams = lp
        })

        // Send TG button
        btnRow.addView(MaterialButton(ctx).apply {
            text = "📤 TG"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#0288D1"))
            setOnClickListener {
                if (token.isEmpty() || chatId.isEmpty()) {
                    text = "❌ Set TG dulu"
                    return@setOnClickListener
                }
                isEnabled = false
                text = "Sending..."
                lifecycleScope.launch {
                    val msg = buildTgMessage(account, pkg, cookie)
                    val ok = withContext(Dispatchers.IO) { TelegramHelper.sendMessage(token, chatId, msg) }
                    text = if (ok) "✓ Sent!" else "❌ Gagal"
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        inner.addView(btnRow)
        card.addView(inner)
        b.resultsContainer.addView(card)
    }

    private fun buildTgMessage(account: Account?, pkg: String, cookie: String) = buildString {
        appendLine("🔥 <b>COOKIE GRABBED</b>")
        appendLine()
        if (account != null) {
            appendLine("👤 <b>Username:</b> <code>${account.username}</code>")
            appendLine("📛 <b>Display:</b> ${account.displayName}")
            appendLine("🆔 <b>UserID:</b> <code>${account.id}</code>")
            appendLine("💰 <b>Robux:</b> ${account.robux}")
            appendLine()
        }
        appendLine("📦 <b>App:</b> $pkg")
        appendLine()
        append("🍪 <b>Cookie:</b>\n<code>$cookie</code>")
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
