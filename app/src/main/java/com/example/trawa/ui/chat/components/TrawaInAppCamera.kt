package com.example.trawa.ui.chat.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

@Composable
fun TrawaInAppCamera(
  onDismiss: () -> Unit,
  onPhotoCaptured: (Uri) -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
  var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
  var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
  var isTorchOn by remember { mutableStateOf(false) }

  val imageCapture = remember {
    ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setFlashMode(flashMode)
      .build()
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      // Camera Preview
      AndroidView(
        factory = { ctx ->
          val previewView = PreviewView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
          }

          val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
          cameraProviderFuture.addListener({
            try {
              val cameraProvider = cameraProviderFuture.get()
              val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
              }
              val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

              cameraProvider.unbindAll()
              val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
              )
              cameraControl = camera.cameraControl
            } catch (e: Exception) {
              Toast.makeText(ctx, "Camera error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
          }, ContextCompat.getMainExecutor(ctx))

          previewView
        },
        modifier = Modifier.fillMaxSize()
      )

      // Top Action Bar: Close & Flash Toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Close Button
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("camera_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Camera",
            tint = Color.White
          )
        }

        // Flash / Torch Toggle Button
        IconButton(
          onClick = {
            isTorchOn = !isTorchOn
            flashMode = if (isTorchOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            imageCapture.flashMode = flashMode
            cameraControl?.enableTorch(isTorchOn)
          },
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("camera_flash_toggle")
        ) {
          Icon(
            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = if (isTorchOn) "Flash Torch On" else "Flash Off",
            tint = if (isTorchOn) Color.Yellow else Color.White
          )
        }
      }

      // Bottom Control Bar: Switch Lens & Shutter Button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 28.dp, start = 32.dp, end = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Spacer placeholder
        Spacer(modifier = Modifier.size(44.dp))

        // Center: Circular Shutter Button
        Box(
          modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable {
              val photoFile = File(context.cacheDir, "trawa_snap_${System.currentTimeMillis()}.jpg")
              val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

              imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    onPhotoCaptured(savedUri)
                    onDismiss()
                  }

                  override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                  }
                }
              )
            }
            .testTag("camera_shutter_button"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(62.dp)
              .clip(CircleShape)
              .background(Color.White)
          )
        }

        // Switch Camera (Front/Back)
        IconButton(
          onClick = {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
              CameraSelector.LENS_FACING_FRONT
            } else {
              CameraSelector.LENS_FACING_BACK
            }
          },
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("camera_switch_lens")
        ) {
          Icon(
            imageVector = Icons.Default.FlipCameraAndroid,
            contentDescription = "Flip Camera",
            tint = Color.White
          )
        }
      }
    }
  }
}
