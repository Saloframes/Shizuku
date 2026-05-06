package moe.shizuku.manager.adb

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AdbPairingTutorialActivityBinding
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppBarActivity() {

    private companion object {
        private const val ANDROID_17 = 37
        private const val REQUEST_ACCESS_LOCAL_NETWORK = 1
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    private lateinit var binding: AdbPairingTutorialActivityBinding

    private var notificationEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this

        binding = AdbPairingTutorialActivityBinding.inflate(layoutInflater, rootView, true)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        notificationEnabled = isNotificationEnabled()

        if (notificationEnabled) {
            startPairingServiceIfAllowed()
        }

        binding.apply {
            syncNotificationEnabled()

            if (DeviceCompatibility.isMiui()) {
                miui.isVisible = true
            }

            developerOptions.setOnClickListener {
                SettingsHelper.launchOrHighlightWirelessDebugging(context)
            }

            notificationOptions.setOnClickListener {
                SettingsPage.Notifications.NotificationSettings.launch(context)
            }
        }
    }

    private fun syncNotificationEnabled() {
        binding.apply {
            step1.isVisible = notificationEnabled
            step2.isVisible = notificationEnabled
            step3.isVisible = notificationEnabled
            network.isVisible = notificationEnabled
            notification.isVisible = notificationEnabled
            notificationDisabled.isGone = notificationEnabled
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val context = this

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun onResume() {
        super.onResume()

        val newNotificationEnabled = isNotificationEnabled()
        if (newNotificationEnabled != notificationEnabled) {
            notificationEnabled = newNotificationEnabled
            syncNotificationEnabled()

            if (newNotificationEnabled) {
                startPairingServiceIfAllowed()
            }
        }
    }

    private fun hasLocalNetworkPermission(): Boolean {
        return Build.VERSION.SDK_INT < ANDROID_17 ||
                checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }

    private fun startPairingServiceIfAllowed() {
        if (hasLocalNetworkPermission()) {
            startPairingService()
        } else {
            requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK), REQUEST_ACCESS_LOCAL_NETWORK)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACCESS_LOCAL_NETWORK &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startPairingService()
        }
    }

    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(this)
        try {
            startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(this, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                startService(intent)
            }
        }
    }
}
