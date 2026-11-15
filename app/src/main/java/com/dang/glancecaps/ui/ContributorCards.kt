package com.dang.glancecaps.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dang.glancecaps.R
import com.dang.glancecaps.social.Contributors
import com.dang.glancecaps.social.DiscordProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Modifier.tiltOnPress(tilt: Float = 8f, onLongPress: () -> Unit): Modifier {
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val spec = remember { spring<Float>(0.6f, 400f) }
    val currentLongPress by rememberUpdatedState(onLongPress)

    fun release() {
        scope.launch { tiltX.animateTo(0f, spec) }
        scope.launch { tiltY.animateTo(0f, spec) }
    }

    return this
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            cameraDistance = 12f * density
            transformOrigin = origin
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    val top = offset.y < size.height / 2f
                    val left = offset.x < size.width / 2f
                    origin = TransformOrigin(if (left) 1f else 0f, if (top) 1f else 0f)
                    scope.launch { tiltX.animateTo(if (top) tilt else -tilt, spec) }
                    scope.launch { tiltY.animateTo(if (left) -tilt else tilt, spec) }
                    tryAwaitRelease()
                    release()
                },
                onLongPress = {
                    Haptics.longPress(view)
                    release()
                    currentLongPress()
                }
            )
        }
}

@Composable
private fun ContributorCard(entry: Contributors.Entry) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var profile by remember { mutableStateOf<DiscordProfiles.Profile?>(entry.fallback) }
    var avatar by remember { mutableStateOf(Contributors.fallbackAvatar(ctx, entry)) }
    var banner by remember { mutableStateOf<Bitmap?>(null) }
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(entry.userId) {
        withContext(Dispatchers.IO) {
            Contributors.cachedProfile(ctx, entry.userId)?.let { profile = it }
            Contributors.cachedAvatar(ctx, entry.userId)?.let { avatar = it }
            Contributors.refresh(ctx, entry.userId)?.let { (p, a) ->
                profile = p
                if (a != null) avatar = a
            }
        }
    }

    LaunchedEffect(open) {
        val p = profile
        if (open && banner == null && p != null) {
            banner = withContext(Dispatchers.IO) { DiscordProfiles.image(p.bannerUrl, 720) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tiltOnPress { if (profile != null) open = true }
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerHighest)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF5865F2)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = avatar
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_social_discord),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                profile?.username?.let { "@$it" } ?: "Contributor",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.role,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (open) {
        GlanceDialog(
            title = "",
            message = "",
            cancelText = "Close",
            confirmText = null,
            onCancel = { open = false },
            body = {
                profile?.let { DiscordCard(it, avatar, banner) }
            }
        )
    }
}

@Composable
fun ContributorCards() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Contributors.all.forEach { ContributorCard(it) }
    }
}
