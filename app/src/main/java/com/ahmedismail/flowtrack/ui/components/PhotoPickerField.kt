package com.ahmedismail.flowtrack.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Add-a-photo control for Add Progress Entry: tapping it (or the existing
 * thumbnail) opens a small chooser between Camera and Gallery. Picking
 * Camera requests android.permission.CAMERA at runtime the first time and
 * explains why if the person denies it once; picking Gallery uses the
 * system Photo Picker, which needs no runtime permission at all.
 */
@Composable
fun PhotoPickerField(photoUri: String?, onPhotoChanged: (String?) -> Unit) {
    val context = LocalContext.current
    var showChooser by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPermissionDeniedNotice by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            // Best-effort: keeps the photo readable for report export later,
            // even after this process/session ends. Some picker providers
            // don't support persisting the grant — safe to ignore if so.
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* no-op: grant is still valid for this session */ }
            onPhotoChanged(it.toString())
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) pendingCameraUri?.let { onPhotoChanged(it.toString()) }
    }

    fun launchCamera() {
        val photoFile = createCameraOutputFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() else showPermissionDeniedNotice = true }

    fun onCameraChosen() {
        showChooser = false
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun onGalleryChosen() {
        showChooser = false
        galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(Modifier.padding(bottom = 10.dp)) {
        Text(stringResource(R.string.field_photo), style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))

        if (photoUri != null) {
            Box(modifier = Modifier.size(width = 120.dp, height = 90.dp)) {
                AsyncImage(
                    model = Uri.parse(photoUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Line, RoundedCornerShape(10.dp))
                        .clickable { showChooser = true }
                )
                // The badge stays small on the thumbnail, but the touch target
                // around it is a full 44dp so it is easy to hit one-handed.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp)
                        .clickable { onPhotoChanged(null) },
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Navy)
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_photo), tint = CardWhite, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Steel, RoundedCornerShape(10.dp))
                    .clickable { showChooser = true }
                    .padding(9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.add_photo), color = Steel, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (showPermissionDeniedNotice) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.camera_permission_denied), style = MaterialTheme.typography.bodySmall, color = Orange)
        }
    }

    if (showChooser) {
        PhotoSourceDialog(
            onDismiss = { showChooser = false },
            onCameraSelected = ::onCameraChosen,
            onGallerySelected = ::onGalleryChosen
        )
    }
}

@Composable
private fun PhotoSourceDialog(onDismiss: () -> Unit, onCameraSelected: () -> Unit, onGallerySelected: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_photo_source)) },
        text = {
            Column {
                SourceOptionRow(icon = Icons.Filled.CameraAlt, label = stringResource(R.string.photo_source_camera), onClick = onCameraSelected)
                Spacer(Modifier.height(8.dp))
                SourceOptionRow(icon = Icons.Filled.PhotoLibrary, label = stringResource(R.string.photo_source_gallery), onClick = onGallerySelected)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun SourceOptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Steel)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

private fun createCameraOutputFile(context: android.content.Context): File {
    val dir = File(context.getExternalFilesDir(null), "photos").apply { if (!exists()) mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(dir, "entry_$timestamp.jpg")
}
