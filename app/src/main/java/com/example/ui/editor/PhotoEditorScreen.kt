package com.example.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.CropAspectRatio
import com.example.domain.model.EditorAdjustments
import com.example.ui.theme.IosYellow
import com.example.util.PhotoEditorEngine
import java.util.Locale
import kotlin.math.min

private enum class EditorTab(val label: String) {
    ADJUST("Adjust"),
    CROP("Crop"),
    TRANSFORM("Rotate & Flip")
}

private enum class AdjustmentControl(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation")
}

@Composable
fun PhotoEditorScreen(
    bitmap: Bitmap?,
    isLoadingBitmap: Boolean,
    adjustments: EditorAdjustments,
    canUndo: Boolean,
    canRedo: Boolean,
    isSaving: Boolean,
    onUpdateAdjustments: (EditorAdjustments, Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetAll: () -> Unit,
    onCancel: () -> Unit,
    onSaveCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(EditorTab.ADJUST) }
    var activeAdjustment by remember { mutableStateOf(AdjustmentControl.BRIGHTNESS) }
    var showBeforePreview by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val handleCancel = {
        if (adjustments.isModified) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler { handleCancel() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .testTag("photo_editor_screen")
    ) {
        // Top Editor Bar (Cancel, Undo, Redo, Compare Original, Reset, Done/Save)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TextButton(
                onClick = handleCancel,
                modifier = Modifier.testTag("editor_cancel_button")
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.Medium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.testTag("editor_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color.Gray.copy(alpha = 0.45f)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.testTag("editor_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color.Gray.copy(alpha = 0.45f)
                    )
                }

                // Hold or tap to toggle Before / After Original preview
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (showBeforePreview) IosYellow.copy(alpha = 0.25f)
                            else Color.Transparent
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    showBeforePreview = true
                                    tryAwaitRelease()
                                    showBeforePreview = false
                                },
                                onTap = {
                                    showBeforePreview = !showBeforePreview
                                }
                            )
                        }
                        .padding(8.dp)
                        .testTag("editor_compare_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = "Before and After Preview",
                        tint = if (showBeforePreview) IosYellow else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (adjustments.isModified) {
                    TextButton(
                        onClick = onResetAll,
                        modifier = Modifier.testTag("editor_reset_all_button")
                    ) {
                        Text("Reset", color = Color(0xFFFF453A))
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = if (isSaving) Color.Gray else IosYellow,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !isSaving && bitmap != null) { onSaveCopy() }
                    .testTag("editor_save_button")
            ) {
                Text(
                    text = if (isSaving) "Saving…" else "Done",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }

        // Main Image Preview Canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when {
                isLoadingBitmap -> {
                    CircularProgressIndicator(color = IosYellow)
                }
                bitmap == null -> {
                    Text(
                        text = "Unable to decode photo for editing.",
                        color = Color.White
                    )
                }
                else -> {
                    val effectiveAdjustments = if (showBeforePreview) {
                        EditorAdjustments()
                    } else {
                        adjustments
                    }
                    val colorMatrix = remember(
                        effectiveAdjustments.brightness,
                        effectiveAdjustments.contrast,
                        effectiveAdjustments.saturation
                    ) {
                        PhotoEditorEngine.buildComposeColorMatrix(effectiveAdjustments)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(
                            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                            contentDescription = "Edited Photo Preview",
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = effectiveAdjustments.rotationDegrees.toFloat()
                                    scaleX = if (effectiveAdjustments.flipHorizontal) -1f else 1f
                                    scaleY = if (effectiveAdjustments.flipVertical) -1f else 1f
                                }
                        )

                        // Interactive Crop Frame Overlay
                        if (!showBeforePreview && (activeTab == EditorTab.CROP ||
                                adjustments.cropLeft > 0.001f ||
                                adjustments.cropTop > 0.001f ||
                                adjustments.cropRight < 0.999f ||
                                adjustments.cropBottom < 0.999f)
                        ) {
                            CropFrameOverlay(
                                left = adjustments.cropLeft,
                                top = adjustments.cropTop,
                                right = adjustments.cropRight,
                                bottom = adjustments.cropBottom,
                                showGrid = activeTab == EditorTab.CROP
                            )
                        }

                        if (showBeforePreview) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "ORIGINAL",
                                    color = IosYellow,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls Sheet
        Surface(
            color = Color(0xFF161618),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                when (activeTab) {
                    EditorTab.ADJUST -> {
                        AdjustControlsPanel(
                            activeControl = activeAdjustment,
                            adjustments = adjustments,
                            onSelectControl = { activeAdjustment = it },
                            onUpdateAdjustments = onUpdateAdjustments
                        )
                    }
                    EditorTab.CROP -> {
                        CropControlsPanel(
                            bitmap = bitmap,
                            adjustments = adjustments,
                            onUpdateAdjustments = onUpdateAdjustments
                        )
                    }
                    EditorTab.TRANSFORM -> {
                        TransformControlsPanel(
                            adjustments = adjustments,
                            onUpdateAdjustments = onUpdateAdjustments
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Switcher Bar (Adjust / Crop / Rotate & Flip)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF242428), CircleShape)
                        .padding(4.dp)
                ) {
                    EditorTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        val icon = when (tab) {
                            EditorTab.ADJUST -> Icons.Filled.Tune
                            EditorTab.CROP -> Icons.Filled.Crop
                            EditorTab.TRANSFORM -> Icons.AutoMirrored.Filled.RotateRight
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (selected) IosYellow else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp)
                                .testTag("editor_tab_${tab.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to discard your photo edits?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancel()
                    }
                ) {
                    Text("Discard Changes", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

@Composable
private fun AdjustControlsPanel(
    activeControl: AdjustmentControl,
    adjustments: EditorAdjustments,
    onSelectControl: (AdjustmentControl) -> Unit,
    onUpdateAdjustments: (EditorAdjustments, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Parameter selector pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AdjustmentControl.entries.forEach { control ->
                val selected = control == activeControl
                val valueBadge = when (control) {
                    AdjustmentControl.BRIGHTNESS -> String.format(Locale.US, "%+.0f", adjustments.brightness * 100f)
                    AdjustmentControl.CONTRAST -> String.format(Locale.US, "%+.0f", (adjustments.contrast - 1f) * 100f)
                    AdjustmentControl.SATURATION -> String.format(Locale.US, "%+.0f", (adjustments.saturation - 1f) * 100f)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) Color(0xFF2C2C32) else Color(0xFF1E1E22),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectControl(control) }
                        .testTag("adjust_control_${control.name.lowercase()}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = control.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) IosYellow else Color.White
                        )
                        Text(
                            text = valueBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Slider + Individual Reset Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (activeControl) {
                AdjustmentControl.BRIGHTNESS -> {
                    Slider(
                        value = adjustments.brightness,
                        onValueChange = { value ->
                            onUpdateAdjustments(adjustments.copy(brightness = value), true)
                        },
                        valueRange = -0.6f..0.6f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_brightness")
                    )
                    IconButton(
                        onClick = { onUpdateAdjustments(adjustments.copy(brightness = 0f), true) },
                        enabled = adjustments.brightness != 0f,
                        modifier = Modifier.testTag("reset_brightness_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Brightness",
                            tint = if (adjustments.brightness != 0f) IosYellow else Color.Gray
                        )
                    }
                }
                AdjustmentControl.CONTRAST -> {
                    Slider(
                        value = adjustments.contrast,
                        onValueChange = { value ->
                            onUpdateAdjustments(adjustments.copy(contrast = value), true)
                        },
                        valueRange = 0.5f..1.6f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_contrast")
                    )
                    IconButton(
                        onClick = { onUpdateAdjustments(adjustments.copy(contrast = 1f), true) },
                        enabled = adjustments.contrast != 1f,
                        modifier = Modifier.testTag("reset_contrast_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Contrast",
                            tint = if (adjustments.contrast != 1f) IosYellow else Color.Gray
                        )
                    }
                }
                AdjustmentControl.SATURATION -> {
                    Slider(
                        value = adjustments.saturation,
                        onValueChange = { value ->
                            onUpdateAdjustments(adjustments.copy(saturation = value), true)
                        },
                        valueRange = 0f..2f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_saturation")
                    )
                    IconButton(
                        onClick = { onUpdateAdjustments(adjustments.copy(saturation = 1f), true) },
                        enabled = adjustments.saturation != 1f,
                        modifier = Modifier.testTag("reset_saturation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Saturation",
                            tint = if (adjustments.saturation != 1f) IosYellow else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropControlsPanel(
    bitmap: Bitmap?,
    adjustments: EditorAdjustments,
    onUpdateAdjustments: (EditorAdjustments, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Aspect Ratio Preset Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            CropAspectRatio.entries.forEach { preset ->
                val selected = adjustments.cropAspectRatio == preset
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = applyAspectRatioCrop(bitmap, adjustments, preset)
                        onUpdateAdjustments(updated, true)
                    },
                    label = {
                        Text(
                            text = preset.label,
                            color = if (selected) IosYellow else Color.White
                        )
                    },
                    modifier = Modifier.testTag("crop_ratio_${preset.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Free crop / inset adjustment controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Horizontal Crop (${((adjustments.cropRight - adjustments.cropLeft) * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
                Slider(
                    value = adjustments.cropLeft,
                    onValueChange = { left ->
                        val clampedLeft = left.coerceIn(0f, 0.4f)
                        val right = (1f - clampedLeft).coerceIn(clampedLeft + 0.2f, 1f)
                        onUpdateAdjustments(
                            adjustments.copy(
                                cropAspectRatio = CropAspectRatio.FREE,
                                cropLeft = clampedLeft,
                                cropRight = right
                            ),
                            true
                        )
                    },
                    valueRange = 0f..0.38f
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vertical Crop (${((adjustments.cropBottom - adjustments.cropTop) * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
                Slider(
                    value = adjustments.cropTop,
                    onValueChange = { top ->
                        val clampedTop = top.coerceIn(0f, 0.4f)
                        val bottom = (1f - clampedTop).coerceIn(clampedTop + 0.2f, 1f)
                        onUpdateAdjustments(
                            adjustments.copy(
                                cropAspectRatio = CropAspectRatio.FREE,
                                cropTop = clampedTop,
                                cropBottom = bottom
                            ),
                            true
                        )
                    },
                    valueRange = 0f..0.38f
                )
            }

            TextButton(
                onClick = {
                    onUpdateAdjustments(
                        adjustments.copy(
                            cropAspectRatio = CropAspectRatio.FREE,
                            cropLeft = 0f,
                            cropTop = 0f,
                            cropRight = 1f,
                            cropBottom = 1f
                        ),
                        true
                    )
                }
            ) {
                Text("Reset", color = IosYellow)
            }
        }
    }
}

private fun applyAspectRatioCrop(
    bitmap: Bitmap?,
    current: EditorAdjustments,
    preset: CropAspectRatio
): EditorAdjustments {
    if (preset == CropAspectRatio.FREE || preset == CropAspectRatio.ORIGINAL || bitmap == null) {
        return current.copy(
            cropAspectRatio = preset,
            cropLeft = 0f,
            cropTop = 0f,
            cropRight = 1f,
            cropBottom = 1f
        )
    }

    val targetRatio = preset.ratio ?: return current
    val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
    val imgH = bitmap.height.toFloat().coerceAtLeast(1f)
    val currentRatio = imgW / imgH

    return if (currentRatio > targetRatio) {
        // Image is wider than target ratio -> crop left & right
        val normalizedW = (targetRatio / currentRatio).coerceIn(0.2f, 1f)
        val marginX = (1f - normalizedW) / 2f
        current.copy(
            cropAspectRatio = preset,
            cropLeft = marginX,
            cropTop = 0f,
            cropRight = 1f - marginX,
            cropBottom = 1f
        )
    } else {
        // Image is taller than target ratio -> crop top & bottom
        val normalizedH = (currentRatio / targetRatio).coerceIn(0.2f, 1f)
        val marginY = (1f - normalizedH) / 2f
        current.copy(
            cropAspectRatio = preset,
            cropLeft = 0f,
            cropTop = marginY,
            cropRight = 1f,
            cropBottom = 1f - marginY
        )
    }
}

@Composable
private fun TransformControlsPanel(
    adjustments: EditorAdjustments,
    onUpdateAdjustments: (EditorAdjustments, Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        TransformActionButton(
            icon = Icons.AutoMirrored.Filled.RotateLeft,
            label = "Rotate -90°",
            testTag = "editor_rotate_left_button",
            onClick = {
                val nextRot = ((adjustments.rotationDegrees - 90) % 360 + 360) % 360
                onUpdateAdjustments(adjustments.copy(rotationDegrees = nextRot), true)
            }
        )

        TransformActionButton(
            icon = Icons.AutoMirrored.Filled.RotateRight,
            label = "Rotate +90°",
            testTag = "editor_rotate_right_button",
            onClick = {
                val nextRot = (adjustments.rotationDegrees + 90) % 360
                onUpdateAdjustments(adjustments.copy(rotationDegrees = nextRot), true)
            }
        )

        TransformActionButton(
            icon = Icons.Filled.Flip,
            label = if (adjustments.flipHorizontal) "Flipped H" else "Flip H",
            testTag = "editor_flip_horizontal_button",
            onClick = {
                onUpdateAdjustments(
                    adjustments.copy(flipHorizontal = !adjustments.flipHorizontal),
                    true
                )
            }
        )

        TransformActionButton(
            icon = Icons.Filled.Flip,
            label = if (adjustments.flipVertical) "Flipped V" else "Flip V",
            testTag = "editor_flip_vertical_button",
            onClick = {
                onUpdateAdjustments(
                    adjustments.copy(flipVertical = !adjustments.flipVertical),
                    true
                )
            }
        )
    }
}

@Composable
private fun TransformActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF2C2C32),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun CropFrameOverlay(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    showGrid: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = min(size.width, size.height) * 0.9f
        val offsetX = (size.width - side) / 2f
        val offsetY = (size.height - side) / 2f

        val rectLeft = offsetX + side * left
        val rectTop = offsetY + side * top
        val rectW = side * (right - left)
        val rectH = side * (bottom - top)

        // Outer crop border
        drawRect(
            color = IosYellow,
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectW, rectH),
            style = Stroke(width = 2.dp.toPx())
        )

        if (showGrid) {
            // Rule of thirds lines
            val thirdW = rectW / 3f
            val thirdH = rectH / 3f
            for (i in 1..2) {
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(rectLeft + thirdW * i, rectTop),
                    end = Offset(rectLeft + thirdW * i, rectTop + rectH),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(rectLeft, rectTop + thirdH * i),
                    end = Offset(rectLeft + rectW, rectTop + thirdH * i),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
