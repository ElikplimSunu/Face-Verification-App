package com.sunueric.faceverificationapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf

class FaceVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceVerificationScreen()
        }
    }
}

enum class VerificationState {
    INITIAL,
    STARTED,
    LOOK_LEFT,
    LOOK_RIGHT,
    LOOK_DOWN,
    SUCCESS,
    FAILED
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun FaceVerificationScreen() {
    val coroutineScope = rememberCoroutineScope()
    var verificationState by remember { mutableStateOf(VerificationState.INITIAL) }
    var borderColor by remember { mutableStateOf(Color.White) }
    val context = LocalContext.current
    var currentInstruction by remember { mutableStateOf("Look Straight Ahead") }
    var isFaceInsideOval by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Face Detection setup
    val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.3f)
        .enableTracking()
        .build()

    val faceDetector = remember { FaceDetection.getClient(faceDetectorOptions) }
    //Get the width and height of the cameraPreview;
    val screenMetrics = context.resources.displayMetrics
    var cameraPreviewWidth by remember {
        mutableIntStateOf(screenMetrics.widthPixels)
    }
    var cameraPreviewHeight by remember {
        mutableIntStateOf(screenMetrics.heightPixels)
    }
    val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
    val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
    // Function to process faces and update state
    fun processFaces(faces: List<Face>) {
        if (faces.isNotEmpty()) {
            val face = faces.first()

            // Calculate the oval's bounding box based on the current view size
            fun calculateOvalBoundingBox(previewWidth: Int, previewHeight: Int): FaceVerificationUtils.OvalBoundingBox {
                val width = previewWidth * 0.55f
                val height = previewHeight * 0.4f
                val left = (previewWidth - width) / 2
                val top = (previewHeight - height) / 2
                return FaceVerificationUtils.OvalBoundingBox(left, top, width, height)
            }


            // Check if the face is inside the oval
            isFaceInsideOval = FaceVerificationUtils.isFaceInsideOval(face, calculateOvalBoundingBox(cameraPreviewWidth, cameraPreviewHeight), cameraPreviewWidth, cameraPreviewHeight, screenWidth, screenHeight)
            Log.d("FaceVerification", "Is face inside oval: $isFaceInsideOval")

            Log.d("FaceBox", "Face Bounding Box: Left=${face.boundingBox.left}, Top=${face.boundingBox.top}, Right=${face.boundingBox.right}, Bottom=${face.boundingBox.bottom}")
            Log.d("PreviewSize", "Camera Preview Width=$cameraPreviewWidth, Height=$cameraPreviewHeight")
            Log.d("ScreenSize", "Screen Width=$screenWidth, Height=$screenHeight")


            // Process face based on current instruction
            when (verificationState) {
                VerificationState.LOOK_LEFT -> {
                    if (face.headEulerAngleY > 20 && isFaceInsideOval) {
                        coroutineScope.launch {
                            borderColor = Color.Green
                            delay(1000)
                            verificationState = VerificationState.LOOK_RIGHT
                            currentInstruction = "Look Right"
                            borderColor = Color.White // Reset border color
                        }
                    }
                }

                VerificationState.LOOK_RIGHT -> {
                    if (face.headEulerAngleY < -20 && isFaceInsideOval) {
                        coroutineScope.launch {
                            borderColor = Color.Green
                            delay(1000)
                            verificationState = VerificationState.LOOK_DOWN
                            currentInstruction = "Look Down"
                            borderColor = Color.White // Reset border color
                        }
                    }
                }

                VerificationState.LOOK_DOWN -> {
                    if (face.headEulerAngleX > 20 && isFaceInsideOval) {
                        coroutineScope.launch {
                            borderColor = Color.Green
                            delay(1000)
                            verificationState = VerificationState.SUCCESS
                            currentInstruction = "Verification Successful!"
                            borderColor = Color.Green // Verification Complete
                        }
                    }
                }

                else -> {
                    // Do nothing for other states
                }
            }

        } else {
            isFaceInsideOval = false
        }
    }

    // Camera permission handling
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted // Update state based on the result
        if (!isGranted) {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        } else {
            // Permission granted, you can now safely access the camera
            // You might want to trigger a UI update or start the camera here
            Log.d("FaceVerification", "Camera permission granted!")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            Log.d("FaceVerification", "Camera permission already granted")
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera View with Face Analysis
            FullScreenCameraView(
                onImageCaptured = { imageProxy ->
                    val image = imageProxy.image
                    if (image != null) {
                        val inputImage = InputImage.fromMediaImage(
                            image,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        faceDetector.process(inputImage)
                            .addOnSuccessListener { faces ->
                                processFaces(faces)
                            }
                            .addOnFailureListener { e ->
                                Log.e("FaceDetectionError", "Face detection failed", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close() // Important: Close the ImageProxy
                            }
                    } else {
                        imageProxy.close()
                    }
                },
                onPreviewReady = {width, height ->
                    cameraPreviewWidth = width
                    cameraPreviewHeight = height
                }
            )

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

                // Draw border
                drawOval(
                    color = borderColor,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 8.dp.toPx())
                )
            }

            // UI elements based on verification state
            when (verificationState) {
                VerificationState.INITIAL,
                VerificationState.STARTED,
                VerificationState.LOOK_LEFT,
                VerificationState.LOOK_RIGHT,
                VerificationState.LOOK_DOWN -> {
                    Text(
                        text = currentInstruction,
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
                                currentInstruction = "Look Straight Ahead" // Reset instruction
                            }
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }

            // Start Button
            Button(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                onClick = {
                    if (verificationState == VerificationState.INITIAL) {
                        verificationState = VerificationState.LOOK_LEFT
                        currentInstruction = "Look Left"
                        borderColor = Color.White
                    }
                },
                enabled = verificationState == VerificationState.INITIAL
            ) {
                Text("Start Face Verification")
            }
        }
    } else {
        // Display a message to the user explaining why the permission is needed
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is required to use this feature.", color = Color.Red)
            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Request Permission")
            }
        }

    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun FullScreenCameraView(onImageCaptured: (ImageProxy) -> Unit, onPreviewReady: (Int, Int) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            previewView.apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }

                    // Image Analysis
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                val image = imageProxy.image
                                if (image != null) {
                                    onImageCaptured(imageProxy)
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalysis // Add image analysis to the use case
                        )
                    } catch (e: Exception) {
                        Log.e("CameraError", "Failed to bind camera", e)
                    }

                }, ContextCompat.getMainExecutor(context))
            }
        },
        update = { _ ->
            // Nothing in update lambda now

        }
    )

    val viewTreeObserver = previewView.viewTreeObserver
    val listener = remember {
        ViewTreeObserver.OnGlobalLayoutListener {
            onPreviewReady(previewView.width, previewView.height)
        }
    }
    DisposableEffect(Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
}

object FaceVerificationUtils {
    data class OvalBoundingBox(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    fun isFaceInsideOval(
        face: Face,
        ovalBoundingBox: OvalBoundingBox,
        previewWidth: Int,
        previewHeight: Int,
        screenWidth: Float,
        screenHeight: Float
    ): Boolean {
        val faceBox = face.boundingBox

        // Correct scaling based on preview size instead of screen size
        val scaleX = previewWidth.toFloat() / screenWidth
        val scaleY = previewHeight.toFloat() / screenHeight

        val scaledLeft = faceBox.left / scaleX
        val scaledTop = faceBox.top / scaleY
        val scaledRight = faceBox.right / scaleX
        val scaledBottom = faceBox.bottom / scaleY

        Log.d("ScaledFaceBox", "Left=$scaledLeft, Top=$scaledTop, Right=$scaledRight, Bottom=$scaledBottom")

        return scaledLeft >= ovalBoundingBox.left &&
                scaledRight <= (ovalBoundingBox.left + ovalBoundingBox.width) &&
                scaledTop >= ovalBoundingBox.top &&
                scaledBottom <= (ovalBoundingBox.top + ovalBoundingBox.height)
    }
}