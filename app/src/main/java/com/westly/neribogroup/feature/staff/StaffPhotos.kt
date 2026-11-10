package com.westly.neribogroup.feature.staff

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.westly.neribogroup.data.MAX_STAFF_IMAGES
import com.westly.neribogroup.data.StaffImage
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A small in-memory cache of decoded photos, so lists and the viewer scroll smoothly. */
private object PhotoCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 12L).toInt().coerceAtLeast(4 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

/** Decodes a photo file at about [maxSide] pixels (inSampleSize: never the full picture for a thumbnail). */
private fun decodePhoto(file: File, maxSide: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }
}

/** The photo as an image, loaded off the main thread. Null while loading or when the file is missing. */
@Composable
private fun rememberPhoto(image: StaffImage, file: File, maxSide: Int, tick: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, image.id, image.sha256, maxSide, tick) {
        value = withContext(Dispatchers.IO) {
            val key = image.id + "|" + image.sha256 + "|" + maxSide
            val cached = PhotoCache.get(key)
            val bitmap = cached ?: if (file.exists()) decodePhoto(file, maxSide)?.also { PhotoCache.put(key, it) } else null
            bitmap?.asImageBitmap()
        }
    }
    return state.value
}

/**
 * "Photos (n/5)" on the staff page: thumbnails, an Add photo button (the system photo picker, no storage
 * permission), and a full-screen viewer with swipe and Delete. Photos belong to this staff member only.
 */
@Composable
fun StaffPhotosSection(
    images: List<StaffImage>,
    fileFor: (StaffImage) -> File,
    mediaTick: Int,
    busy: Boolean,
    waitingIds: Set<String>,
    onAdd: (List<Uri>) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var viewerIndex by rememberSaveable { mutableStateOf(-1) }
    val remaining = MAX_STAFF_IMAGES - images.size

    val pickerAvailable = remember { ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context) }
    // The photo picker needs at least 2 as its limit; any extra picks beyond the room left are ignored later.
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, remaining))
    ) { uris -> if (uris.isNotEmpty()) onAdd(uris) }
    val fallbackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) onAdd(uris) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Photos (" + images.size + "/" + MAX_STAFF_IMAGES + ")")

        if (images.isEmpty()) {
            Text(
                text = "No photos yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (index in 0 until MAX_STAFF_IMAGES) {
                    val image = images.getOrNull(index)
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        if (image != null) {
                            val bitmap = rememberPhoto(image, fileFor(image), 256, mediaTick)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewerIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Photo " + (index + 1),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // The file is still coming from the cloud.
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Adding photos…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = {
                if (pickerAvailable) {
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    fallbackLauncher.launch("image/*")
                }
            },
            enabled = remaining > 0 && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (remaining > 0) "Add photo" else "Maximum $MAX_STAFF_IMAGES photos")
        }

        Text(
            text = "Photos are not inside the backup file; they sync through the cloud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (viewerIndex >= 0 && images.isNotEmpty()) {
        PhotoViewer(
            images = images,
            startIndex = viewerIndex.coerceIn(0, images.lastIndex),
            fileFor = fileFor,
            mediaTick = mediaTick,
            waitingIds = waitingIds,
            onDelete = { id ->
                onDelete(id)
                viewerIndex = -1
            },
            onClose = { viewerIndex = -1 }
        )
    } else if (viewerIndex >= 0) {
        viewerIndex = -1
    }
}

@Composable
private fun PhotoViewer(
    images: List<StaffImage>,
    startIndex: Int,
    fileFor: (StaffImage) -> File,
    mediaTick: Int,
    waitingIds: Set<String>,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { images.size })
    var confirmDelete by remember { mutableStateOf(false) }
    val current = images.getOrNull(pager.currentPage)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val image = images[page]
                val bitmap = rememberPhoto(image, fileFor(image), 1600, mediaTick)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Photo " + (page + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("Close", color = Color.White) }
                Text(
                    text = (pager.currentPage + 1).toString() + " of " + images.size,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            if (current != null && current.id in waitingIds) {
                Text(
                    text = "Waiting to upload",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (confirmDelete && current != null) {
        ConfirmDialog(
            heading = "Delete this photo?",
            message = "It is removed from this phone and from the cloud.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete(current.id)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
