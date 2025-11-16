package com.example.myapplication.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.analysis.OCRAnalyzer
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService

enum class CameraMode { LIVE_SCAN, CAPTURE }

// -------------------------------------------------------------
// CAMERA SCREEN (handles permission + mode switching)
// -------------------------------------------------------------

@Composable
fun CameraScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera permission required.",
                color = Color.White
            )
        }
        return
    }

    var cameraMode by remember { mutableStateOf(CameraMode.LIVE_SCAN) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Docu-Clarify") }
            )
        },
        bottomBar = {
            ModeSwitcherBar(
                currentMode = cameraMode,
                onModeSelected = { cameraMode = it }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            CameraPreviewWithOCR(
                cameraExecutor = cameraExecutor,
                cameraMode = cameraMode
            )
        }
    }
}

@Composable
private fun ModeSwitcherBar(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = currentMode == CameraMode.LIVE_SCAN,
                onClick = { onModeSelected(CameraMode.LIVE_SCAN) },
                label = { Text("Live Scan") }
            )
            FilterChip(
                selected = currentMode == CameraMode.CAPTURE,
                onClick = { onModeSelected(CameraMode.CAPTURE) },
                label = { Text("Capture") }
            )
        }
    }
}

// -------------------------------------------------------------
// CAMERA PREVIEW WITH OCR
// -------------------------------------------------------------

@Composable
private fun CameraPreviewWithOCR(
    cameraExecutor: ExecutorService,
    cameraMode: CameraMode
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { image ->
                    OCRAnalyzer.processImageProxy(
                        imageProxy = image,
                        onResult = { text ->
                            if (text.isNotBlank()) {
                                Log.d("OCR", "Detected text: ${text.take(120)}")
                            }
                        },
                        onError = { e ->
                            Log.e("OCR", "Error running OCR", e)
                        }
                    )
                }
            }
    }

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Binding failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (cameraMode == CameraMode.CAPTURE) {
            FloatingActionButton(
                onClick = {
                    takePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        executor = cameraExecutor
                    )
                },
                shape = CircleShape,
                containerColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Take Photo",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService
) {
    val photoDir = context.externalMediaDirs.firstOrNull()
        ?: context.filesDir

    val photoFile = File(
        photoDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("CameraCapture", "Saved: ${photoFile.absolutePath}")
                // If you want, you can run OCR on this saved image too.
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Failed: ${exception.message}", exception)
            }
        }
    )
}

// -------------------------------------------------------------
// SPLASH + LANDING SCREENS
// -------------------------------------------------------------

@Composable
fun SplashScreen(onReady: () -> Unit, isReady: Boolean, onTimeout: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            ProcessCameraProvider.getInstance(context).get()
        } catch (_: Exception) {
        }
        onReady()
    }

    LaunchedEffect(isReady) {
        if (isReady) {
            delay(500)
            onTimeout()
        }
    }

    val pulseState = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pulseState.value = true }

    val scale by animateFloatAsState(
        targetValue = if (pulseState.value) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E2A38)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
        )

        Spacer(Modifier.height(32.dp))

        if (!isReady) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Text("Ready!", color = Color.Green)
        }
    }
}

@Composable
fun LandingScreen(onGetStartedClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Docu-Clarify",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Simplify documents. Understand instantly.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGetStartedClicked) {
            Text("Get Started")
        }
    }
}
