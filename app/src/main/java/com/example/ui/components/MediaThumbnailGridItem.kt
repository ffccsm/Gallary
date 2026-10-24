package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.MediaItem
import com.example.ui.theme.IosHeartRed
import com.example.util.GalleryImageLoader

private val RoundedTileShape = RoundedCornerShape(6.dp)
private val SquareTileShape = RoundedCornerShape(1.dp)
private val VideoBadgeShape = RoundedCornerShape(6.dp)
private val BottomScrimBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black.copy(alpha = 0.48f)
    )
)
private val VideoBadgeBgColor = Color.Black.copy(alpha = 0.55f)
private val SelectedOverlayColor = Color.White.copy(alpha = 0.25f)
private val UnselectedOverlayColor = Color.Black.copy(alpha = 0.08f)
private val UnselectedCheckBgColor = Color.Black.copy(alpha = 0.35f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailGridItem(
    item: MediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    roundedCorners: Boolean,
    showVideoDuration: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailPx: Int = 220
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { GalleryImageLoader.get(context) }
    val shape = if (roundedCorners) RoundedTileShape else SquareTileShape

    val imageRequest = remember(item.id, item.dateModifiedMillis, thumbnailPx) {
        GalleryImageLoader.buildGridThumbnailRequest(
            context = context,
            item = item,
            thumbnailPx = thumbnailPx
        )
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_item_${item.id}")
    ) {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle bottom gradient only when favorite or video badge is visible
        if (item.isFavorite || item.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BottomScrimBrush)
            )
        }

        // Bottom-left Favorite heart indicator
        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = IosHeartRed,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(15.dp)
            )
        }

        // Bottom-right Video Duration indicator
        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(
                        color = VideoBadgeBgColor,
                        shape = VideoBadgeShape
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                if (showVideoDuration && item.formattedDuration.isNotEmpty()) {
                    Text(
                        text = item.formattedDuration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Lightweight selection overlay only composed when in selection mode
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) SelectedOverlayColor else UnselectedOverlayColor)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else UnselectedCheckBgColor
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
