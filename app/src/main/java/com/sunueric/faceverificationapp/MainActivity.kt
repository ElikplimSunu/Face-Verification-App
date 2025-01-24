package com.sunueric.faceverificationapp

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class FaceVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceVerificationScreen()
        }
    }
}

enum class VerificationState { INITIAL, STARTED, VERIFYING, SUCCESS, FAILED }


@Composable
fun FaceVerificationScreen() {
    val coroutineScope = rememberCoroutineScope()
    var verificationState by remember { mutableStateOf(VerificationState.INITIAL) }
    var borderColor by remember { mutableStateOf(Color.White) }
    val context = LocalContext.current
    var currentDirection by remember { mutableStateOf(LookDirection.STRAIGHT) }


    // Camera permission handling
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FullScreenCameraView()

        // Overlay with oval cutout and border
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dark overlay
            drawRect(color = Color.Black.copy(alpha = 0.6f))

            // Oval dimensions (adjust these values as needed)
            val ovalWidth = size.width * 0.55f
            val ovalHeight = size.height * 0.4f
            val ovalTopLeft = Offset(
                (size.width - ovalWidth) / 2,
                (size.height - ovalHeight) / 2
            )
            val ovalSize = Size(ovalWidth, ovalHeight)

            // Clear oval area
            drawOval(
                color = Color.Transparent,
                topLeft = ovalTopLeft,
                size = ovalSize,
                blendMode = BlendMode.Clear
            )

            // Draw border based on current direction
            when (currentDirection) {
                LookDirection.STRAIGHT -> drawOval(
                    color = borderColor,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 8.dp.toPx())
                )
                LookDirection.RIGHT -> drawArc(
                    color = borderColor,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 8.dp.toPx())
                )
                LookDirection.LEFT -> drawArc(
                    color = borderColor,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 8.dp.toPx())
                )
                LookDirection.UP -> drawArc(
                    color = borderColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 8.dp.toPx())
                )
                LookDirection.DOWN -> drawArc(
                    color = borderColor,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 8.dp.toPx())
                )
            }
        }

        // Text displays for different states
        when (verificationState) {
            VerificationState.STARTED, VerificationState.VERIFYING -> {
                Text(
                    text = when (currentDirection) {
                        LookDirection.STRAIGHT -> "Look straight ahead"
                        LookDirection.RIGHT -> "Look to your right"
                        LookDirection.LEFT -> "Look to your left"
                        LookDirection.UP -> "Look up"
                        LookDirection.DOWN -> "Look down"
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    color = Color.White
                )
            }
            VerificationState.SUCCESS -> {
                Text(
                    text = "Verification Successful!",
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    color = Color.Green
                )
            }
            VerificationState.FAILED -> {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Verification Failed",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            verificationState = VerificationState.INITIAL
                            borderColor = Color.White
                            currentDirection = LookDirection.STRAIGHT
                        }
                    ) {
                        Text("Try Again")
                    }
                }
            }
            else -> {}
        }

        var isLoading by remember { mutableStateOf(false) }

        Button(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            onClick = {
                if (!isLoading && verificationState == VerificationState.INITIAL) {
                    verificationState = VerificationState.STARTED
                    borderColor = Color.White
                    currentDirection = LookDirection.STRAIGHT
                    coroutineScope.launch {
                        isLoading = true
                        startVerification(
                            onDirectionChange = { direction ->
                                currentDirection = direction
                                verificationState = VerificationState.VERIFYING
                                borderColor = Color.White
                            },
                            onDirectionSuccess = {
                                borderColor = Color.Green
                            },
                            onComplete = { success ->
                                verificationState = if (success) VerificationState.SUCCESS else VerificationState.FAILED
                                borderColor = if (success) Color.Green else Color.Red
                                currentDirection = LookDirection.STRAIGHT
                                isLoading = false
                            }
                        )
                    }
                }
            }
        ) {
            Text(if (isLoading) "Verifying..." else "Start Face Verification")
        }
    }
}

@Composable
fun FullScreenCameraView() {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview
                        )
                    } catch (e: Exception) {
                        Log.e("CameraError", "Failed to bind camera", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    )
}

enum class LookDirection { STRAIGHT, RIGHT, LEFT, UP, DOWN }


suspend fun startVerification(
    onDirectionChange: (LookDirection) -> Unit,
    onDirectionSuccess: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    val directions = LookDirection.entries.shuffled()
    var success = true

    for (direction in directions) {
        onDirectionChange(direction)
        delay(2000) // Simulate verification time for each direction

        // Simulate a random success/failure for each direction
        if (Random.nextFloat() > 0.2f) {
            onDirectionSuccess()
            delay(500) // Show green border for a short time
        } else {
            success = false
            break
        }
    }

    onComplete(success)
}



