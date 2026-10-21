package net.agolyakov.firelamp

import android.Manifest
import android.app.Activity.RESULT_CANCELED
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ScrollCaptureCallback
import android.view.ScrollCaptureSession
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import net.agolyakov.firelamp.data.local.LanguagePreferences
import net.agolyakov.firelamp.data.local.withAppLanguage
import net.agolyakov.firelamp.navigation.SetupNavGraph
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.ui.viewmodel.MyRequestPermission
import net.agolyakov.firelamp.ui.theme.FireLampTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bluetoothService: BluetoothService

    // Apply the in-app language before anything resolves a resource; switching it recreates
    // the activity, which runs this again
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage(LanguagePreferences(newBase).language))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The action bar title comes from the manifest label, which the system resolves in its
        // own locale — set it from our resources so it follows the in-app language
        title = getString(R.string.app_name)

        // Enable ScrollCapture for scrolling screenshots (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableScrollCapture()
        }

        setContent {
            MainContent()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun enableScrollCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.decorView.setScrollCaptureHint(View.SCROLL_CAPTURE_HINT_INCLUDE)
        }
    }

    override fun onPause() {
        super.onPause()
        // isChangingConfigurations: a rotation or language switch pauses the activity only to
        // recreate it — that must not drop the session (or an OTA transfer riding on it)
        if (!isChangingConfigurations && !bluetoothService.shouldPreserveConnection()) {
            bluetoothService.disconnect()
        }
    }

    override fun onResume() {
        super.onResume()
        bluetoothService.tryReconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        // A configuration change (rotation, in-app language switch) destroys and recreates
        // the activity — the BLE session, and any OTA transfer running over it, must survive
        // that. Only a real exit tears the connection down.
        if (isFinishing) {
            bluetoothService.disconnect()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun MainContent() {
    FireLampTheme {
        val navController = rememberNavController()
        val context = LocalContext.current

        // Launcher for enabling Bluetooth
        val enableBluetoothLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(context, R.string.perm_bluetooth_is_off, Toast.LENGTH_SHORT).show()
            }
        }

        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT)
            else
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION)

        // Request permissions
        MyRequestPermission(permissions) { granted ->
            if (granted) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                Toast.makeText(context, R.string.perm_not_enough_permissions, Toast.LENGTH_SHORT).show()
            }
        }

        // Navigation
        SetupNavGraph(navController = navController)
    }
}

fun getBlePermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}