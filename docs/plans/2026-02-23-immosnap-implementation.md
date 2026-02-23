# ImmoSnap Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android app that photographs for-sale signs, extracts real estate info via OCR, matches to Belgian listing sites, and opens the listing in the browser.

**Architecture:** 3-screen Compose app (Camera → Processing → Result). On-device: CameraX + ML Kit OCR + GPS. Cloud: Google Maps Geocoding, Google Custom Search, Gemini Flash image matching. No backend.

**Tech Stack:** Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition, Google Maps Geocoding API, Google Custom Search API, Gemini Flash (google-ai-client SDK)

---

### Task 1: Scaffold Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/immosnap/MainActivity.kt`
- Create: `app/src/main/java/com/immosnap/ImmoSnapApp.kt`
- Create: `gradle.properties`
- Create: `local.properties.example`
- Create: `.gitignore`

**Step 1: Create root build.gradle.kts**

```kotlin
// build.gradle.kts (root)
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
```

**Step 2: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ImmoSnap"
include(":app")
```

**Step 3: Create app/build.gradle.kts with all dependencies**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.immosnap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.immosnap"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val localProps = rootProject.file("local.properties")
        if (localProps.exists()) {
            val props = java.util.Properties().apply { load(localProps.inputStream()) }
            buildConfigField("String", "MAPS_API_KEY", "\"${props["MAPS_API_KEY"] ?: ""}\"")
            buildConfigField("String", "SEARCH_API_KEY", "\"${props["SEARCH_API_KEY"] ?: ""}\"")
            buildConfigField("String", "SEARCH_ENGINE_ID", "\"${props["SEARCH_ENGINE_ID"] ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${props["GEMINI_API_KEY"] ?: ""}\"")
        } else {
            buildConfigField("String", "MAPS_API_KEY", "\"\"")
            buildConfigField("String", "SEARCH_API_KEY", "\"\"")
            buildConfigField("String", "SEARCH_ENGINE_ID", "\"\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.1.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
```

**Step 4: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="ImmoSnap"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DynamicColors.DayNight">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 5: Create MainActivity.kt**

```kotlin
package com.immosnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    ImmoSnapApp()
                }
            }
        }
    }
}
```

**Step 6: Create ImmoSnapApp.kt with navigation shell**

```kotlin
package com.immosnap

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ImmoSnapApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            // CameraScreen - Task 3
        }
        composable("processing") {
            // ProcessingScreen - Task 6
        }
        composable("result") {
            // ResultScreen - Task 7
        }
    }
}
```

**Step 7: Create .gitignore**

```
*.iml
.gradle
/local.properties
/.idea
/build
/app/build
/captures
.externalNativeBuild
.cxx
*.apk
*.aab
```

**Step 8: Create local.properties.example**

```properties
# Copy to local.properties and fill in your API keys
MAPS_API_KEY=your_google_maps_api_key
SEARCH_API_KEY=your_google_custom_search_api_key
SEARCH_ENGINE_ID=your_custom_search_engine_id
GEMINI_API_KEY=your_gemini_api_key
```

**Step 9: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**Step 10: Download Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.11`
(Or manually create `gradle/wrapper/gradle-wrapper.properties`)

**Step 11: Commit**

```bash
git add -A && git commit -m "feat: scaffold Android project with all dependencies"
```

---

### Task 2: Location Service

**Files:**
- Create: `app/src/main/java/com/immosnap/location/LocationService.kt`
- Test: `app/src/test/java/com/immosnap/location/LocationServiceTest.kt`

**Step 1: Write LocationService**

```kotlin
package com.immosnap.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationService(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Location {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Location permission not granted")
        }

        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) cont.resume(location)
                    else cont.resumeWithException(Exception("Location unavailable"))
                }
                .addOnFailureListener { cont.resumeWithException(it) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/immosnap/location/
git commit -m "feat: add location service with FusedLocationProvider"
```

---

### Task 3: Camera Screen

**Files:**
- Create: `app/src/main/java/com/immosnap/ui/camera/CameraScreen.kt`
- Create: `app/src/main/java/com/immosnap/ui/camera/CameraViewModel.kt`

**Step 1: Write CameraViewModel**

```kotlin
package com.immosnap.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel : ViewModel() {
    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage = _capturedImage.asStateFlow()

    fun onImageCaptured(bitmap: Bitmap) {
        _capturedImage.value = bitmap
    }
}
```

**Step 2: Write CameraScreen**

```kotlin
package com.immosnap.ui.camera

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraScreen(onPhotoTaken: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            image.close()
                            onPhotoTaken(bitmap)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            // Handle error
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {}
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/immosnap/ui/camera/
git commit -m "feat: add camera screen with CameraX capture"
```

---

### Task 4: OCR Service

**Files:**
- Create: `app/src/main/java/com/immosnap/ocr/OcrService.kt`
- Create: `app/src/main/java/com/immosnap/ocr/SignInfo.kt`
- Test: `app/src/test/java/com/immosnap/ocr/SignInfoParserTest.kt`

**Step 1: Write SignInfo data class**

```kotlin
package com.immosnap.ocr

data class SignInfo(
    val agencyName: String?,
    val phoneNumber: String?,
    val referenceNumber: String?,
    val rawText: String
)
```

**Step 2: Write the test for sign text parsing**

```kotlin
package com.immosnap.ocr

import org.junit.Assert.*
import org.junit.Test

class SignInfoParserTest {

    @Test
    fun `extracts phone number from sign text`() {
        val text = "Century 21\nTe Koop\n03 123 45 67\nRef: AB-1234"
        val result = SignInfoParser.parse(text)
        assertEquals("03 123 45 67", result.phoneNumber)
    }

    @Test
    fun `extracts reference number`() {
        val text = "Immobilien Schmidt\nA Vendre\nRef: 12345\n0476 12 34 56"
        val result = SignInfoParser.parse(text)
        assertEquals("12345", result.referenceNumber)
    }

    @Test
    fun `extracts agency name as first non-keyword line`() {
        val text = "ERA Vastgoed\nTe Koop\n09 222 33 44"
        val result = SignInfoParser.parse(text)
        assertEquals("ERA Vastgoed", result.agencyName)
    }

    @Test
    fun `handles text with no recognizable fields`() {
        val text = "some random text"
        val result = SignInfoParser.parse(text)
        assertNull(result.phoneNumber)
        assertNull(result.referenceNumber)
        assertEquals("some random text", result.rawText)
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.immosnap.ocr.SignInfoParserTest"`
Expected: FAIL — `SignInfoParser` not found

**Step 4: Write SignInfoParser**

```kotlin
package com.immosnap.ocr

object SignInfoParser {

    private val PHONE_REGEX = Regex("""(\+32|0)\s*\d[\d\s]{7,12}\d""")
    private val REF_REGEX = Regex("""(?:ref|réf|reference)[.:# ]*\s*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE)
    private val KEYWORDS = setOf(
        "te koop", "a vendre", "for sale", "te huur", "a louer",
        "sold", "vendu", "verkocht", "open huis", "open deur",
        "nieuw", "nouveau", "new"
    )

    fun parse(text: String): SignInfo {
        val phone = PHONE_REGEX.find(text)?.value?.trim()
        val ref = REF_REGEX.find(text)?.groupValues?.get(1)

        val agencyName = text.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank()
                    && KEYWORDS.none { kw -> line.equals(kw, ignoreCase = true) }
                    && PHONE_REGEX.find(line) == null
                    && REF_REGEX.find(line) == null
            }

        return SignInfo(
            agencyName = agencyName,
            phoneNumber = phone,
            referenceNumber = ref,
            rawText = text
        )
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.immosnap.ocr.SignInfoParserTest"`
Expected: PASS (4 tests)

**Step 6: Write OcrService (ML Kit wrapper)**

```kotlin
package com.immosnap.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap): SignInfo {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = suspendCancellableCoroutine<String> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return SignInfoParser.parse(text)
    }
}
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/immosnap/ocr/ app/src/test/java/com/immosnap/ocr/
git commit -m "feat: add OCR service with ML Kit and sign text parser"
```

---

### Task 5: Geocoding + Listing Search Services

**Files:**
- Create: `app/src/main/java/com/immosnap/search/GeocodingService.kt`
- Create: `app/src/main/java/com/immosnap/search/ListingSearchService.kt`
- Create: `app/src/main/java/com/immosnap/search/ListingCandidate.kt`
- Test: `app/src/test/java/com/immosnap/search/ListingSearchServiceTest.kt`

**Step 1: Write ListingCandidate data class**

```kotlin
package com.immosnap.search

data class AddressInfo(
    val street: String?,
    val postalCode: String?,
    val city: String?
)

data class ListingCandidate(
    val title: String,
    val url: String,
    val snippet: String,
    val thumbnailUrl: String?,
    val source: String // "immoweb", "zimmo", "immovlan"
)
```

**Step 2: Write GeocodingService**

```kotlin
package com.immosnap.search

import com.immosnap.BuildConfig
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeocodingService {

    private val client = OkHttpClient()

    suspend fun reverseGeocode(lat: Double, lng: Double): AddressInfo = withContext(Dispatchers.IO) {
        val url = "https://maps.googleapis.com/maps/api/geocode/json" +
            "?latlng=$lat,$lng&key=${BuildConfig.MAPS_API_KEY}&language=nl"

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = Json.parseToJsonElement(response.body!!.string()).jsonObject
        val components = json["results"]!!.jsonArray
            .firstOrNull()?.jsonObject
            ?.get("address_components")?.jsonArray ?: return@withContext AddressInfo(null, null, null)

        var street: String? = null
        var postalCode: String? = null
        var city: String? = null

        for (comp in components) {
            val types = comp.jsonObject["types"]!!.jsonArray.map { it.jsonPrimitive.content }
            val name = comp.jsonObject["long_name"]!!.jsonPrimitive.content
            when {
                "route" in types -> street = name
                "postal_code" in types -> postalCode = name
                "locality" in types -> city = name
            }
        }
        AddressInfo(street, postalCode, city)
    }
}
```

**Step 3: Write ListingSearchService**

```kotlin
package com.immosnap.search

import com.immosnap.BuildConfig
import com.immosnap.ocr.SignInfo
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ListingSearchService {

    private val client = OkHttpClient()

    suspend fun search(signInfo: SignInfo, address: AddressInfo): List<ListingCandidate> =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()

            // Add location info
            address.street?.let { queryParts.add(it) }
            address.postalCode?.let { queryParts.add(it) }
            address.city?.let { queryParts.add(it) }

            // Add agency name if found
            signInfo.agencyName?.let { queryParts.add("\"$it\"") }

            // If we have a ref number, add it too
            signInfo.referenceNumber?.let { queryParts.add(it) }

            val siteFilter = "site:immoweb.be OR site:zimmo.be OR site:immovlan.be"
            val query = "${queryParts.joinToString(" ")} $siteFilter"

            val url = "https://www.googleapis.com/customsearch/v1" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&key=${BuildConfig.SEARCH_API_KEY}" +
                "&cx=${BuildConfig.SEARCH_ENGINE_ID}" +
                "&num=5"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val json = Json.parseToJsonElement(response.body!!.string()).jsonObject

            val items = json["items"]?.jsonArray ?: return@withContext emptyList()

            items.map { item ->
                val obj = item.jsonObject
                val link = obj["link"]!!.jsonPrimitive.content
                val source = when {
                    "immoweb.be" in link -> "immoweb"
                    "zimmo.be" in link -> "zimmo"
                    "immovlan.be" in link -> "immovlan"
                    else -> "other"
                }
                ListingCandidate(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    url = link,
                    snippet = obj["snippet"]?.jsonPrimitive?.content ?: "",
                    thumbnailUrl = obj["pagemap"]?.jsonObject
                        ?.get("cse_thumbnail")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("src")?.jsonPrimitive?.content,
                    source = source
                )
            }
        }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/immosnap/search/
git commit -m "feat: add geocoding and listing search services"
```

---

### Task 6: Gemini Image Matching Service

**Files:**
- Create: `app/src/main/java/com/immosnap/matching/ImageMatchService.kt`
- Create: `app/src/main/java/com/immosnap/matching/MatchResult.kt`

**Step 1: Write MatchResult**

```kotlin
package com.immosnap.matching

import com.immosnap.search.ListingCandidate

data class MatchResult(
    val candidate: ListingCandidate,
    val confidence: Float, // 0.0 to 1.0
    val reasoning: String
)
```

**Step 2: Write ImageMatchService**

```kotlin
package com.immosnap.matching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.immosnap.BuildConfig
import com.immosnap.search.ListingCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

class ImageMatchService {

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    private val httpClient = OkHttpClient()

    suspend fun rankCandidates(
        photo: Bitmap,
        candidates: List<ListingCandidate>
    ): List<MatchResult> {
        if (candidates.isEmpty()) return emptyList()

        // Download thumbnails for candidates that have them
        val thumbnails = candidates.mapNotNull { candidate ->
            candidate.thumbnailUrl?.let { url ->
                try {
                    val bitmap = downloadImage(url)
                    candidate to bitmap
                } catch (e: Exception) {
                    null
                }
            }
        }

        if (thumbnails.isEmpty()) {
            // No thumbnails to compare — return candidates as-is with neutral confidence
            return candidates.map { MatchResult(it, 0.5f, "No listing photo available for comparison") }
        }

        val response = model.generateContent(
            content {
                image(photo)
                thumbnails.forEachIndexed { index, (candidate, thumb) ->
                    text("Listing ${index + 1}: ${candidate.title} (${candidate.url})")
                    image(thumb)
                }
                text(
                    """You are comparing a user's photo of a house with listing photos.
                    |Which listing photo shows the same building as the user's photo?
                    |Compare facade, roof, windows, colors, and architectural style.
                    |
                    |Respond as JSON array: [{"index": 1, "confidence": 0.95, "reasoning": "..."}]
                    |Order by confidence descending. Index is 1-based matching the listing numbers above.
                    """.trimMargin()
                )
            }
        )

        val text = response.text ?: return candidates.map {
            MatchResult(it, 0.5f, "Gemini returned no response")
        }

        return try {
            val jsonText = text.substringAfter("[").substringBefore("]").let { "[$it]" }
            val results = Json.parseToJsonElement(jsonText).jsonArray
            results.mapNotNull { elem ->
                val obj = elem.jsonObject
                val index = obj["index"]!!.jsonPrimitive.int - 1
                val conf = obj["confidence"]!!.jsonPrimitive.float
                val reason = obj["reasoning"]?.jsonPrimitive?.content ?: ""
                if (index in thumbnails.indices) {
                    MatchResult(thumbnails[index].first, conf, reason)
                } else null
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            candidates.map { MatchResult(it, 0.5f, "Failed to parse Gemini response") }
        }
    }

    private suspend fun downloadImage(url: String): Bitmap = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val bytes = response.body!!.bytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/immosnap/matching/
git commit -m "feat: add Gemini Flash image matching service"
```

---

### Task 7: Processing Pipeline & ViewModel

**Files:**
- Create: `app/src/main/java/com/immosnap/pipeline/SnapPipeline.kt`
- Create: `app/src/main/java/com/immosnap/pipeline/PipelineState.kt`
- Create: `app/src/main/java/com/immosnap/pipeline/PipelineViewModel.kt`

**Step 1: Write PipelineState**

```kotlin
package com.immosnap.pipeline

import com.immosnap.matching.MatchResult

sealed class PipelineState {
    data object Idle : PipelineState()
    data class Processing(val step: String) : PipelineState()
    data class Success(val results: List<MatchResult>) : PipelineState()
    data class Error(val message: String) : PipelineState()
}
```

**Step 2: Write SnapPipeline**

```kotlin
package com.immosnap.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.immosnap.location.LocationService
import com.immosnap.matching.ImageMatchService
import com.immosnap.matching.MatchResult
import com.immosnap.ocr.OcrService
import com.immosnap.search.GeocodingService
import com.immosnap.search.ListingSearchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SnapPipeline(context: Context) {

    private val locationService = LocationService(context)
    private val ocrService = OcrService()
    private val geocodingService = GeocodingService()
    private val listingSearchService = ListingSearchService()
    private val imageMatchService = ImageMatchService()

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state = _state.asStateFlow()

    suspend fun process(photo: Bitmap) {
        try {
            _state.value = PipelineState.Processing("Reading sign...")
            val signInfo = ocrService.extractText(photo)

            _state.value = PipelineState.Processing("Finding address...")
            val location = locationService.getCurrentLocation()
            val address = geocodingService.reverseGeocode(location.latitude, location.longitude)

            _state.value = PipelineState.Processing("Searching listings...")
            val candidates = listingSearchService.search(signInfo, address)

            if (candidates.isEmpty()) {
                _state.value = PipelineState.Error("No listings found nearby")
                return
            }

            _state.value = PipelineState.Processing("Matching photos...")
            val results = imageMatchService.rankCandidates(photo, candidates)

            _state.value = PipelineState.Success(results)
        } catch (e: Exception) {
            _state.value = PipelineState.Error(e.message ?: "Unknown error")
        }
    }

    fun reset() {
        _state.value = PipelineState.Idle
    }
}
```

**Step 3: Write PipelineViewModel**

```kotlin
package com.immosnap.pipeline

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PipelineViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline = SnapPipeline(application)
    val state = pipeline.state

    private var capturedPhoto: Bitmap? = null

    fun onPhotoTaken(bitmap: Bitmap) {
        capturedPhoto = bitmap
        viewModelScope.launch {
            pipeline.process(bitmap)
        }
    }

    fun retry() {
        capturedPhoto?.let { photo ->
            pipeline.reset()
            viewModelScope.launch { pipeline.process(photo) }
        }
    }

    fun reset() {
        capturedPhoto = null
        pipeline.reset()
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/immosnap/pipeline/
git commit -m "feat: add processing pipeline with state management"
```

---

### Task 8: Processing Screen & Result Screen

**Files:**
- Create: `app/src/main/java/com/immosnap/ui/processing/ProcessingScreen.kt`
- Create: `app/src/main/java/com/immosnap/ui/result/ResultScreen.kt`

**Step 1: Write ProcessingScreen**

```kotlin
package com.immosnap.ui.processing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingScreen(stepMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stepMessage, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
```

**Step 2: Write ResultScreen**

```kotlin
package com.immosnap.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.immosnap.matching.MatchResult

@Composable
fun ResultScreen(
    results: List<MatchResult>,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No listings found", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Try Again") }
            }
        }
        return
    }

    // If top result has high confidence, show it prominently
    val topResult = results.first()
    val showSingleResult = topResult.confidence >= 0.8f && results.size > 1

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSingleResult) {
            item {
                Text("Match Found!", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                MatchCard(topResult, highlighted = true) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(topResult.candidate.url)))
                }
            }
        } else {
            item {
                Text(
                    "Select the correct listing:",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            items(results.take(3)) { result ->
                MatchCard(result, highlighted = false) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.candidate.url)))
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Scan Another")
            }
        }
    }
}

@Composable
private fun MatchCard(
    result: MatchResult,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (highlighted) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            result.candidate.thumbnailUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Listing photo",
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                result.candidate.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(result.candidate.snippet, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "${result.candidate.source} • ${(result.confidence * 100).toInt()}% match",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (highlighted) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Listing")
                }
            }
        }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/immosnap/ui/
git commit -m "feat: add processing and result screens"
```

---

### Task 9: Wire Up Navigation & Permissions

**Files:**
- Modify: `app/src/main/java/com/immosnap/ImmoSnapApp.kt`
- Modify: `app/src/main/java/com/immosnap/MainActivity.kt`

**Step 1: Update ImmoSnapApp.kt with full navigation**

```kotlin
package com.immosnap

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.immosnap.pipeline.PipelineState
import com.immosnap.pipeline.PipelineViewModel
import com.immosnap.ui.camera.CameraScreen
import com.immosnap.ui.processing.ProcessingScreen
import com.immosnap.ui.result.ResultScreen

@Composable
fun ImmoSnapApp(viewModel: PipelineViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    // React to state changes for navigation
    LaunchedEffect(state) {
        when (state) {
            is PipelineState.Processing -> navController.navigate("processing") {
                launchSingleTop = true
            }
            is PipelineState.Success, is PipelineState.Error -> navController.navigate("result") {
                launchSingleTop = true
            }
            else -> {}
        }
    }

    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(onPhotoTaken = { bitmap ->
                viewModel.onPhotoTaken(bitmap)
            })
        }
        composable("processing") {
            val processingState = state
            if (processingState is PipelineState.Processing) {
                ProcessingScreen(stepMessage = processingState.step)
            }
        }
        composable("result") {
            when (val resultState = state) {
                is PipelineState.Success -> ResultScreen(
                    results = resultState.results,
                    onRetry = {
                        viewModel.reset()
                        navController.popBackStack("camera", inclusive = false)
                    }
                )
                is PipelineState.Error -> ResultScreen(
                    results = emptyList(),
                    onRetry = {
                        viewModel.reset()
                        navController.popBackStack("camera", inclusive = false)
                    }
                )
                else -> {}
            }
        }
    }
}
```

**Step 2: Update MainActivity.kt with permission handling**

```kotlin
package com.immosnap

import android.Manifest
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
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }

                    // Check permissions on each recomposition
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
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/immosnap/
git commit -m "feat: wire up navigation, permissions, and full app flow"
```

---

### Task 10: Build & Smoke Test

**Step 1: Build the project**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Run unit tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 3: Fix any compilation errors**

Address any import issues or API mismatches found during build.

**Step 4: Commit any fixes**

```bash
git add -A && git commit -m "fix: resolve build issues from integration"
```
