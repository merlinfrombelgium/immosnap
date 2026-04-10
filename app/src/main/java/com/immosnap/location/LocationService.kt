package com.immosnap.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationService(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Request a current, high-accuracy GPS fix. Throws:
     *  - [SecurityException] if the ACCESS_FINE_LOCATION permission is missing.
     *  - [LocationTimeoutException] if no fix arrives within [timeoutMs] — GPS can take a long
     *    time from a cold start or fail silently indoors, and we never want the whole pipeline
     *    to hang on "Finding address..." for minutes.
     */
    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Location {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Location permission not granted")
        }

        val cts = CancellationTokenSource()
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { location ->
                            if (location != null) cont.resume(location)
                            else cont.resumeWithException(Exception("Location unavailable"))
                        }
                        .addOnFailureListener { cont.resumeWithException(it) }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }
        } catch (e: TimeoutCancellationException) {
            cts.cancel()
            throw LocationTimeoutException(
                "No GPS fix after ${timeoutMs / 1000}s. Move outside or pick a photo with EXIF GPS from the gallery.",
                e
            )
        }
    }

    companion object {
        /** 15s is enough for a warm GPS fix and short enough that a cold fix fails loudly. */
        const val DEFAULT_TIMEOUT_MS: Long = 15_000L
    }
}

class LocationTimeoutException(message: String, cause: Throwable? = null) : Exception(message, cause)
