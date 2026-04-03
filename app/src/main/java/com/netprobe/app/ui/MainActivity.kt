package com.netprobe.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.netprobe.app.BuildConfig
import com.netprobe.app.R
import com.netprobe.app.databinding.ActivityMainBinding
import com.netprobe.app.ui.history.HistoryFragment
import com.netprobe.app.ui.network.NetworkScannerFragment
import com.netprobe.app.ui.scanner.PortScannerFragment
import com.netprobe.app.ui.tools.ToolsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var adView: AdView? = null

    companion object {
        private const val TAG_PORT_SCANNER = "fragment_port_scanner"
        private const val TAG_NETWORK_SCANNER = "fragment_network_scanner"
        private const val TAG_TOOLS = "fragment_tools"
        private const val TAG_HISTORY = "fragment_history"
        private const val PREFS_NAME = "netprobe_prefs"
        private const val KEY_DISCLAIMER_SHOWN = "disclaimer_shown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#11111B")

        setupBottomNavigation()
        setupAdBanner()

        if (savedInstanceState == null) {
            switchFragment(PortScannerFragment(), TAG_PORT_SCANNER)
            showDisclaimerIfFirstLaunch()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scanner -> {
                    switchFragment(PortScannerFragment(), TAG_PORT_SCANNER)
                    true
                }
                R.id.nav_network -> {
                    switchFragment(NetworkScannerFragment(), TAG_NETWORK_SCANNER)
                    true
                }
                R.id.nav_tools -> {
                    switchFragment(ToolsFragment(), TAG_TOOLS)
                    true
                }
                R.id.nav_history -> {
                    switchFragment(HistoryFragment(), TAG_HISTORY)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        val target = existing ?: fragment

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, target, tag)
            .commit()
    }

    private fun setupAdBanner() {
        adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_ID
        }
        binding.adContainer.addView(adView)
        adView?.loadAd(AdRequest.Builder().build())
    }

    private fun showDisclaimerIfFirstLaunch() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DISCLAIMER_SHOWN, false)) return

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_Dialog_Alert)
            .setTitle("Disclaimer")
            .setMessage(
                "NetProbe is a network diagnostic tool intended for authorized use only.\n\n" +
                    "You must only scan networks and devices that you own or have explicit " +
                    "authorization to test. Unauthorized scanning may violate laws and " +
                    "regulations in your jurisdiction.\n\n" +
                    "By continuing, you agree to use this tool responsibly and legally."
            )
            .setPositiveButton("I Agree") { dialog, _ ->
                prefs.edit().putBoolean(KEY_DISCLAIMER_SHOWN, true).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}
