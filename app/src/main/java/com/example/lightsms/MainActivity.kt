package com.example.lightsms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

        binding.btnBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        binding.btnAppSettings.setOnClickListener {
            startActivity(
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
        if (!TorchController.setTorch(this, on)) {
            Toast.makeText(this, R.string.torch_unavailable, Toast.LENGTH_LONG).show()
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
            if (binding.txtWarnings.text.isNullOrBlank()) android.view.View.GONE
            else android.view.View.VISIBLE
    }

    private fun buildWarnings(): String {
        val warnings = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            warnings += getString(R.string.warn_no_sms_permission)
        }
        if (!TorchController.hasFlash(this)) {
            warnings += getString(R.string.warn_no_flash)
        }
        return warnings.joinToString("\n")
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
