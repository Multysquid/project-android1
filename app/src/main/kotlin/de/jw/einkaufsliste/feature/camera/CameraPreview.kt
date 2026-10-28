package de.jw.einkaufsliste.feature.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/** German message for [CameraPreview]'s `onError`. */
const val CAMERA_START_FAILED = "Die Kamera konnte nicht gestartet werden."

/**
 * Back-camera preview with [useCases] (image analysis or capture) bound to the current
 * lifecycle. [onCamera] receives the bound camera (torch control) and null once it is
 * unbound; [onError] receives a German message when the camera could not be started.
 * [useCases] must be remembered by the caller: a new list rebinds the camera.
 */
@Composable
fun CameraPreview(
    useCases: List<UseCase>,
    onCamera: (Camera?) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCamera by rememberUpdatedState(onCamera)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-based: behaves inside Compose layouts, overlays and transitions.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val preview = remember { Preview.Builder().build() }

    DisposableEffect(lifecycleOwner, useCases) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (disposed) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        *useCases.toTypedArray(),
                    )
                    currentOnCamera(camera)
                } catch (e: Exception) {
                    currentOnError(CAMERA_START_FAILED)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbind(preview, *useCases.toTypedArray())
            currentOnCamera(null)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
