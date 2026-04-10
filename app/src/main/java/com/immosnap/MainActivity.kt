package com.immosnap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    /**
     * The set of permissions we actually request at startup. CAMERA and ACCESS_FINE_LOCATION
     * are load-bearing — the app cannot function without them. ACCESS_MEDIA_LOCATION is a
     * nice-to-have that lets the gallery picker read EXIF GPS from photos; denying it just
     * means gallery snaps fall back to no-location behaviour, which is still a working path.
     */
    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            return perms.toTypedArray()
        }

    /**
     * Only checks the two load-bearing permissions — see [requiredPermissions] for why
     * ACCESS_MEDIA_LOCATION is intentionally not part of this gate.
     */
    private fun hasRequiredPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }

                    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) {
                        // Re-check instead of trusting the result map: the user might have granted
                        // only a subset, or the OS might have auto-granted previously-granted perms.
                        permissionsGranted = hasRequiredPermissions()
                    }

                    // Re-check permissions on every ON_RESUME — the user might have toggled perms
                    // off in system Settings while the app was backgrounded, in which case we need
                    // to drop back to the permissions screen rather than blindly keep the camera
                    // open and crash at runtime.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                permissionsGranted = hasRequiredPermissions()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    LaunchedEffect(Unit) {
                        if (!permissionsGranted) {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }

                    if (permissionsGranted) {
                        ImmoSnapApp()
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "ImmoSnap needs camera and location access to read for-sale signs and find the matching listing.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(
                                onClick = { permissionLauncher.launch(requiredPermissions) },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("Grant permissions")
                            }
                        }
                    }
                }
            }
        }
    }
}
