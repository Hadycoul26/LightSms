package com.example.lightsms

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lightsms.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val smsGranted = result[Manifest.permission.RECEIVE_SMS] != false
        if (smsGranted) {
            activate()
        } else {
            Toast.makeText(this, R.string.sms_permission_required, Toast.LENGTH_LONG).show()
        }
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchEnable.setOnClickListener {
            if (binding.switchEnable.isChecked) {
                requestMissingPermissionsThenActivate()
            } else {
                deactivate()
            }
        }

        binding.btnTestOn.setOnClickListener { testTorch(true) }
        binding.btnTestOff.setOnClickListener { testTorch(false) }
        binding.btnRefresh.setOnClickListener { refreshUi() }

        binding.btnClearLog.setOnClickListener {
            EventLog.clear(this)
            refreshUi()
        }

        binding.btnBattery.setOnClickListener {
            openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        binding.btnAppSettings.setOnClickListener {
            openSettings(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    // --- Actions ---------------------------------------------------------

    private fun requestMissingPermissionsThenActivate() {
        val missing = buildList {
            if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
                add(Manifest.permission.RECEIVE_SMS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isEmpty()) {
            activate()
            refreshUi()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun activate() {
        Prefs.setEnabled(this, true)
        LightService.start(this)
    }

    private fun deactivate() {
        Prefs.setEnabled(this, false)
        TorchController.setTorch(this, false)
        LightService.stop(this)
        refreshUi()
    }

    private fun testTorch(on: Boolean) {
        val result = TorchController.setTorch(this, on)
        if (!result.ok) {
            Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show()
        }
        refreshUi()
    }

    // --- UI --------------------------------------------------------------

    private fun refreshUi() {
        val enabled = Prefs.isEnabled(this)
        binding.switchEnable.isChecked = enabled

        binding.txtStatus.setText(
            if (enabled) R.string.status_listening else R.string.status_stopped
        )

        binding.txtTorch.text = getString(
            R.string.torch_state,
            getString(if (Prefs.isTorchOn(this)) R.string.torch_on else R.string.torch_off)
        )

        binding.txtLastEvent.text = Prefs.lastEvent(this) ?: getString(R.string.no_event_yet)

        binding.txtWarnings.text = buildWarnings()
        binding.txtWarnings.visibility =
            if (binding.txtWarnings.text.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.txtDiag.text = buildDiagnostics()

        val log = EventLog.format(this)
        binding.txtLog.text = if (log.isBlank()) getString(R.string.log_empty) else log
    }

    private fun buildDiagnostics(): String {
        val smsOk = hasPermission(Manifest.permission.RECEIVE_SMS)
        val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)

        return listOf(
            "Permission SMS   : " + yn(smsOk, "accordee", "REFUSEE"),
            "Notifications    : " + yn(notifOk, "accordees", "refusees"),
            "Ecoute activee   : " + yn(Prefs.isEnabled(this), "oui", "NON"),
            "Service actif    : " + yn(LightService.isRunning, "oui", "non"),
            "Flash disponible : " + yn(TorchController.hasFlash(this), "oui", "NON"),
            "SMS vus (max 5)  : " + EventLog.count(this)
        ).joinToString("\n")
    }

    private fun yn(value: Boolean, yes: String, no: String) = if (value) yes else no

    private fun buildWarnings(): String {
        val warnings = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            warnings += getString(R.string.warn_no_sms_permission)
        }
        if (!TorchController.hasFlash(this)) {
            warnings += getString(R.string.warn_no_flash)
        }
        if (Prefs.isEnabled(this) && EventLog.count(this) == 0) {
            warnings += getString(R.string.warn_no_sms_seen)
        }
        return warnings.joinToString("\n\n")
    }

    /** Certaines surcouches ne fournissent pas l'ecran vise : on evite le crash. */
    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
