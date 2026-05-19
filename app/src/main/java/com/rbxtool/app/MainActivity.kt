package com.rbxtool.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.rbxtool.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val fragments = mapOf(
            R.id.nav_grab to GrabFragment(),
            R.id.nav_inject to InjectFragment(),
            R.id.nav_accounts to AccountsFragment(),
            R.id.nav_settings to SettingsFragment()
        )

        b.bottomNav.setOnItemSelectedListener { item ->
            fragments[item.itemId]?.let { showFragment(it, item.itemId.toString()) }
            true
        }

        if (savedInstanceState == null) {
            showFragment(GrabFragment(), R.id.nav_grab.toString())
            b.bottomNav.selectedItemId = R.id.nav_grab
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val ft = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.forEach { ft.hide(it) }
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing == null) ft.add(R.id.fragmentContainer, fragment, tag).show(fragment)
        else ft.show(existing)
        ft.commit()
    }
}
