package com.mustafakoceerr.tilepdf.feature.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.data.thumbnail.Thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Material's alpha for disabled content. */
private const val DIMMED_ALPHA = 0.38f

/**
 * A row's first-page picture, or a page icon until the reader has drawn one.
 * [dimmed] for a document that can't be opened now.
 */
@Composable
internal fun DocumentThumbnail(thumbnail: Thumbnail?, dimmed: Boolean, modifier: Modifier = Modifier) {
    // Keyed on the whole thumbnail: a redrawn picture has a new modifiedAt.
    val image by produceState<ImageBitmap?>(initialValue = null, thumbnail) {
        value = thumbnail?.let {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it.file.path)?.asImageBitmap() }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 40.dp, height = 56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                alpha = if (dimmed) DIMMED_ALPHA else 1f,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(TilePdfIcons.Document),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
