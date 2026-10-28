package de.jw.einkaufsliste.feature.scan

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.products.OpenFactsApi
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.feature.camera.AddItemSheet
import de.jw.einkaufsliste.feature.camera.CameraPermissionMissing
import de.jw.einkaufsliste.feature.camera.CameraPreview
import de.jw.einkaufsliste.feature.camera.ItemDraft
import de.jw.einkaufsliste.feature.camera.rememberCameraPermission
import de.jw.einkaufsliste.feature.products.LookupResult
import de.jw.einkaufsliste.feature.products.productSourceLabel
import java.util.concurrent.Executors

/**
 * Adds items by scanning their barcode. Stays open after adding, so several products can
 * be scanned in a row; the user leaves with back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel = viewModel { BarcodeScanViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permission = rememberCameraPermission()
    val haptics = LocalHapticFeedback.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    // CameraX switches the torch off when the camera stops (app in background) and does not restore it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { torchOn = false }

    val analyzer = remember {
        BarcodeAnalyzer { code ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.onBarcode(code)
        }
    }
    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }
    val scanning = state is ScanState.Scanning
    LaunchedEffect(scanning) {
        if (scanning) analyzer.resume() else analyzer.pause()
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode scannen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    val boundCamera = camera
                    if (permission.granted && boundCamera != null && boundCamera.cameraInfo.hasFlashUnit()) {
                        IconButton(
                            onClick = {
                                torchOn = !torchOn
                                boundCamera.cameraControl.enableTorch(torchOn)
                            },
                        ) {
                            Icon(
                                if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = if (torchOn) "Licht ausschalten" else "Licht einschalten",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Includes the keyboard, so the "Barcode eintippen" field moves up with it.
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (permission.granted) {
                    ScannerView(
                        analyzer = analyzer,
                        cameraError = cameraError,
                        onCamera = {
                            camera = it
                            torchOn = false
                        },
                        onError = { cameraError = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CameraPermissionMissing(
                        text = "Zum Scannen braucht die App die Kamera. Die Bilder werden nur auf dem Handy " +
                            "ausgewertet. Du kannst den Barcode auch unten eintippen.",
                        permission = permission,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            BottomPanel(
                state = state,
                onSubmit = viewModel::onBarcode,
                onCancelLookup = viewModel::cancelLookup,
            )
        }
    }

    val ready = state as? ScanState.Ready
    if (ready != null) {
        ResultSheet(
            ready = ready,
            categories = categories,
            onAdd = viewModel::add,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun ScannerView(
    analyzer: BarcodeAnalyzer,
    cameraError: String?,
    onCamera: (Camera?) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
    }
    val useCases = remember(imageAnalysis) { listOf(imageAnalysis) }
    DisposableEffect(imageAnalysis, analyzer) {
        val executor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(executor, analyzer)
        onDispose {
            imageAnalysis.clearAnalyzer()
            executor.shutdown()
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        CameraPreview(
            useCases = useCases,
            onCamera = onCamera,
            onError = onError,
            modifier = Modifier.fillMaxSize(),
        )
        if (cameraError != null) {
            OverlayText(cameraError, Modifier.padding(24.dp))
        } else {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1.8f)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp)),
            )
            OverlayText(
                "Halte den Barcode in den Rahmen.",
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun OverlayText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Lookup progress, the "Barcode eintippen" fallback and the data attribution. */
@Composable
private fun BottomPanel(
    state: ScanState,
    onSubmit: (String) -> Unit,
    onCancelLookup: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val idle = state is ScanState.Scanning

    fun submit() {
        val code = text.trim()
        if (code.isEmpty() || !idle) return
        if (!OpenFactsApi.isValidBarcode(code)) {
            error = "Kein gültiger EAN- oder UPC-Code (8, 12 oder 13 Ziffern)."
            return
        }
        error = null
        text = ""
        keyboard?.hide()
        onSubmit(code)
    }

    val errorText = error
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state is ScanState.LookingUp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Suche Produkt ${state.barcode} …",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelLookup) { Text("Abbrechen") }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter { it.isDigit() }.take(MAX_BARCODE_LENGTH)
                    error = null
                },
                label = { Text("Barcode eintippen") },
                singleLine = true,
                isError = error != null,
                supportingText = if (errorText != null) {
                    { Text(errorText) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }, enabled = idle && text.isNotEmpty()) {
                        Icon(Icons.Filled.Search, contentDescription = "Suchen")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Produktdaten: Open Food Facts und Schwesterprojekte (ODbL)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultSheet(
    ready: ScanState.Ready,
    categories: List<CategoryEntity>,
    onAdd: (ItemDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitle = "Barcode ${ready.barcode}"
    when (val result = ready.result) {
        is LookupResult.Found -> {
            val product = result.product
            val origin = productSourceLabel(product.source)
            AddItemSheet(
                title = "Zur Liste hinzufügen",
                subtitle = subtitle,
                info = product.brand?.let { "Marke: $it · $origin" } ?: origin,
                categories = categories,
                initial = ready.draft,
                onAdd = onAdd,
                onDismiss = onDismiss,
            )
        }
        LookupResult.NotFound -> AddItemSheet(
            title = "Produkt unbekannt",
            subtitle = subtitle,
            message = "Diesen Barcode kennt die Produktdatenbank noch nicht. Wie heißt der Artikel? " +
                "Beim nächsten Scan weiß die App es dann.",
            categories = categories,
            initial = ready.draft,
            guessCategory = true,
            onAdd = onAdd,
            onDismiss = onDismiss,
        )
        is LookupResult.Error -> AddItemSheet(
            title = "Keine Produktdaten",
            subtitle = subtitle,
            message = result.message,
            categories = categories,
            initial = ready.draft,
            guessCategory = true,
            onAdd = onAdd,
            onDismiss = onDismiss,
        )
    }
}

private const val MAX_BARCODE_LENGTH = 13
