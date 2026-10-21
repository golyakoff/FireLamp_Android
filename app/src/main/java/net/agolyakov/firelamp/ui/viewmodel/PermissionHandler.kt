package net.agolyakov.firelamp.ui.viewmodel

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun MyRequestPermission(
    permissions: List<String>,
    onPermissionsResult: (allGranted: Boolean) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        onPermissionsResult(allGranted)
    }

    // Check permissions and auto-launch the request if missing
    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onPermissionsResult(true)
        } else {
            launcher.launch(permissions.toTypedArray())
        }
    }
}
