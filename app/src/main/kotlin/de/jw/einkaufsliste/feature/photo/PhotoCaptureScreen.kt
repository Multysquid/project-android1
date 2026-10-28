package de.jw.einkaufsliste.feature.photo

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.feature.camera.AddItemSheet
import de.jw.einkaufsliste.feature.camera.CameraPermissionMissing
import de.jw.einkaufsliste.feature.camera.CameraPreview
import de.jw.einkaufsliste.feature.camera.rememberCameraPermission

/**
 * Adds items by photographing them; Gemini (with the user's own API key) suggests name,
 * Menge and Unterliste. Stays open after adding for the next photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(container: AppContainer, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val hasKey by container.apiKeyStore.hasKey.collectAsStateWithLifecycle()
    val viewModel = viewModel { PhotoCaptureViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foto aufnehmen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        if (hasKey) {
            PhotoCamera(
                state = state,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                modifier = contentModifier,
            )
        } else {
            NoApiKey(onOpenSettings = onOpenSettings, modifier = contentModifier)
        }
    }

    val editing = state as? PhotoState.Editing
    if (editing != null && hasKey) {
        val photo = editing.photo
        val image = remember(photo) { photo?.asImageBitmap() }
        AddItemSheet(
            title = if (editing.recognized) "Erkannt" else "Selbst eintippen",
            info = if (editing.recognized) "Vorschlag von Gemini – bitte kurz prüfen" else null,
            categories = categories,
            initial = editing.draft,
            guessCategory = !editing.recognized,
            photo = image,
            onAdd = viewModel::add,
            onDismiss = viewModel::backToCamera,
        )
    }
}

@Composable
private fun NoApiKey(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Für Fotos brauchst du einen eigenen Gemini-API-Schlüssel.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Einen Schlüssel kannst du im Google AI Studio erstellen und in den Einstellungen eintragen. " +
                    "Danach erkennt Gemini fotografierte Artikel und schlägt Name und Unterliste vor.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onOpenSettings) { Text("Zu den Einstellungen") }
        }
    }
}

@Composable
private fun PhotoCamera(
    state: PhotoState,
    viewModel: PhotoCaptureViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = rememberCameraPermission()
    if (!permission.granted) {
        CameraPermissionMissing(
            text = "Zum Fotografieren von Artikeln braucht die App die Kamera. Nur die Fotos, die du aufnimmst, " +
                "gehen zur Erkennung an Gemini.",
            permission = permission,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    var cameraError by remember { mutableStateOf<String?>(null) }
    // takePicture fails until CameraX has bound the use case, which takes a moment after opening.
    var cameraReady by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        // No need for full sensor size: the photo is scaled to 1024 px anyway.
                        ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
    }
    val useCases = remember(imageCapture) { listOf(imageCapture) }

    fun takePhoto() {
        if (!viewModel.startCapture()) return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val raw = try {
                        PhotoEncoding.copy(image)
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (raw != null) viewModel.onCaptured(raw) else viewModel.onCaptureFailed()
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.onCaptureFailed()
                }
            },
        )
    }

    Column(modifier) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview(
                useCases = useCases,
                onCamera = { cameraReady = it != null },
                onError = { cameraError = it },
                modifier = Modifier.fillMaxSize(),
            )
            val error = cameraError
            when (state) {
                is PhotoState.Recognizing -> FrozenPhoto(state.photo) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Gemini erkennt den Artikel …", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = viewModel::backToCamera) { Text("Abbrechen", color = Color.White) }
                }
                is PhotoState.Failed -> FrozenPhoto(state.photo) {
                    FailureCard(
                        title = if (state.photo == null) "Kein Foto" else "Nicht erkannt",
                        message = state.message,
                        fixInSettings = state.fixInSettings,
                        onRetry = viewModel::backToCamera,
                        onTypeManually = viewModel::typeManually,
                        onOpenSettings = {
                            // Back from the settings, the camera is ready for the next try.
                            viewModel.backToCamera()
                            onOpenSettings()
                        },
                    )
                }
                is PhotoState.Editing -> FrozenPhoto(state.photo) {}
                PhotoState.Camera, PhotoState.Capturing -> if (error != null) {
                    Text(
                        error,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(24.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    )
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ready = state == PhotoState.Camera && cameraError == null && cameraReady
                FilledIconButton(
                    onClick = { takePhoto() },
                    enabled = ready,
                    modifier = Modifier.size(72.dp),
                ) {
                    if (state == PhotoState.Capturing) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Foto aufnehmen", modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Das Foto wird zur Erkennung an Google Gemini gesendet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The taken photo over the live camera, dimmed, with [content] centered on top. */
@Composable
private fun FrozenPhoto(photo: Bitmap?, content: @Composable () -> Unit) {
    val image = remember(photo) { photo?.asImageBitmap() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Aufgenommenes Foto",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
private fun FailureCard(
    title: String,
    message: String,
    fixInSettings: Boolean,
    onRetry: () -> Unit,
    onTypeManually: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onTypeManually) { Text("Selbst eintippen") }
                if (fixInSettings) {
                    Button(onClick = onOpenSettings) { Text("Einstellungen") }
                } else {
                    Button(onClick = onRetry) { Text("Nochmal") }
                }
            }
        }
    }
}
