package com.rbxtool.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rbxtool.app.databinding.FragmentSettingsBinding
import com.rbxtool.app.util.CaptchaSolver
import com.rbxtool.app.util.SolverType
import com.rbxtool.app.util.Constants
import com.rbxtool.app.util.DiscordHelper
import com.rbxtool.app.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val solverTypes = CaptchaSolver.allTypes()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)

        val prefs = requireContext().getSharedPreferences("rbx_settings", Context.MODE_PRIVATE)

        // Setup solver spinner
        val labels = solverTypes.map { CaptchaSolver.displayName(it) }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        b.spinnerSolver.adapter = adapter

        // Restore saved solver selection
        val savedSolverName = prefs.getString("captcha_solver_type", SolverType.NONE.name)
        val savedIdx = solverTypes.indexOfFirst { it.name == savedSolverName }.coerceAtLeast(0)
        b.spinnerSolver.setSelection(savedIdx)

        // Restore saved API key
        b.etSolverKey.setText(prefs.getString("captcha_solver_key", ""))

        // Root status
        lifecycleScope.launch {
            val rooted = withContext(Dispatchers.IO) { RootUtils.isRooted() }
            b.tvRootStatus.text = if (rooted) "✅ ROOT AKTIF" else "❌ ROOT TIDAK TERDETEKSI"
            b.tvRootStatus.setTextColor(
                if (rooted) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
            )
        }

        // Save solver config
        b.btnSaveSolver.setOnClickListener {
            val selectedType = solverTypes[b.spinnerSolver.selectedItemPosition]
            val key = b.etSolverKey.text?.toString()?.trim() ?: ""
            prefs.edit()
                .putString("captcha_solver_type", selectedType.name)
                .putString("captcha_solver_key", key)
                .apply()
            val msg = if (selectedType == SolverType.NONE || key.isEmpty())
                "Solver dikosongkan"
            else
                "✅ ${CaptchaSolver.displayName(selectedType)} disimpan!"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Test Discord
        b.btnTest.setOnClickListener {
            b.tvTestResult.setTextColor(Color.parseColor("#FBBF24"))
            b.tvTestResult.text = "Mengirim test..."
            b.btnTest.isEnabled = false
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    DiscordHelper.sendMessage(Constants.DISCORD_WEBHOOK, "✅ **RbxTool** terhubung ke Discord!")
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
