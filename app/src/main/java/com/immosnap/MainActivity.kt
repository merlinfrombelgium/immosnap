package com.immosnap

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* handled by recomposition */ }

        setContent {
            MaterialTheme {
                Surface {
                    var permissionsGranted by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val perms = mutableListOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            perms.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    }

                    permissionsGranted = checkSelfPermission(Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (permissionsGranted) {
                        ImmoSnapApp()
                    } else {
                        Text("Camera and location permissions are required to use ImmoSnap.")
                    }
                }
            }
        }
    }
}
