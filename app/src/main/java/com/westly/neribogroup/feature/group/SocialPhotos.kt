package com.westly.neribogroup.feature.group

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.SocialAccount
import com.westly.neribogroup.feature.staff.PhotoCache
import com.westly.neribogroup.feature.staff.decodePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The profile circle of a social account. Without a picture it is an empty circle with a thin outline:
 * no silhouette, no letters and no placeholder picture, ever. With a picture the picture fills the circle.
 * [mediaTick] goes up when a picture file changed on the phone, so the circle draws again.
 */
@Composable
fun SocialProfileCircle(
    account: SocialAccount,
    file: File,
    mediaTick: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null
) {
    val image = account.profileImage
    val bitmap: ImageBitmap? = if (image == null) {
        null
    } else {
        val state = produceState<ImageBitmap?>(
            initialValue = null, account.id, image.version, image.sha256, mediaTick
        ) {
            value = withContext(Dispatchers.IO) {
                val key = "social|" + account.id + "|" + image.version + "|" + image.sha256
                val cached = PhotoCache.get(key)
                val decoded = cached ?: if (file.exists()) decodePhoto(file, 256)?.also { PhotoCache.put(key, it) } else null
                decoded?.asImageBitmap()
            }
        }
        state.value
    }

    var box = modifier
        .size(size)
        .clip(CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    if (onClick != null) box = box.clickable(onClick = onClick)
    Box(modifier = box, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = account.name + " profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (image != null && !file.exists()) {
            // The picture is still coming from the cloud.
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * The system photo picker for ONE picture (no storage permission). Returns a function that opens it; the
 * chosen picture is given to [onPicked]. Phones without the photo picker use the plain image chooser.
 */
@Composable
fun rememberSinglePhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val pickerAvailable = remember { ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return {
        if (pickerAvailable) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            fallback.launch("image/*")
        }
    }
}
