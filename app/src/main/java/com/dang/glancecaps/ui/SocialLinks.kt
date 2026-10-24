package com.dang.glancecaps.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dang.glancecaps.R
import com.dang.glancecaps.social.DiscordProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class SocialLink(
    val name: String,
    val handle: String,
    @DrawableRes val icon: Int,
    val tile: Color,
    val url: String?,
)

private val links = listOf(
    SocialLink("Reddit", "u/Dang9535", R.drawable.ic_social_reddit, Color(0xFFFF4500), "https://www.reddit.com/u/Dang9535/s/pRB5u18en3"),
    SocialLink("TikTok", "@dang_9535", R.drawable.ic_social_tiktok, Color(0xFF111111), "https://www.tiktok.com/@dang_9535"),
    SocialLink("YouTube", "@dang_9535", R.drawable.ic_social_youtube, Color(0xFFFF0000), "https://youtube.com/@dang_9535?si=nlqaMvB_R6Vl95lf"),
    SocialLink("Discord", "@dang_9535", R.drawable.ic_social_discord, Color(0xFF5865F2), null)
)

@Composable
fun SocialLinks(fallbackAvatar: Bitmap?) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    var pending by remember { mutableStateOf<String?>(null) }
    var discordOpen by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<DiscordProfiles.Profile?>(null) }
    var avatar by remember { mutableStateOf<Bitmap?>(null) }
    var banner by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(discordOpen) {
        if (discordOpen && profile == null) {
            val data = withContext(Dispatchers.IO) { DiscordProfiles.fetch() } ?: DiscordProfiles.fallback
            profile = data
            avatar = withContext(Dispatchers.IO) { DiscordProfiles.image(data.avatarUrl, 256) }
            banner = withContext(Dispatchers.IO) { DiscordProfiles.image(data.bannerUrl, 720) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        Haptics.tick(view)
                        if (link.url == null) discordOpen = true else pending = link.url
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(link.tile),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(link.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.name, fontSize = 17.sp, color = cs.onSurface)
                    Text(link.handle, fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    val target = pending
    if (target != null) {
        GlanceDialog(
            title = "Are you sure?",
            message = "You will be taken to one of my socials, are you sure you wanna do that? (hehehhe invincible reference)",
            cancelText = "No",
            confirmText = "Take me there captain!",
            onCancel = { pending = null },
            onConfirm = {
                pending = null
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
        )
    }

    if (discordOpen) {
        GlanceDialog(
            title = "",
            message = "",
            cancelText = "Close",
            confirmText = null,
            onCancel = { discordOpen = false },
            body = { DiscordCard(profile ?: DiscordProfiles.fallback, avatar ?: fallbackAvatar, banner) }
        )
    }
}

@Composable
private fun DiscordCard(p: DiscordProfiles.Profile, avatar: Bitmap?, banner: Bitmap?) {
    val cs = MaterialTheme.colorScheme
    val ring = cs.surfaceContainerHigh
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(p.bannerColor ?: 0xFF5865F2.toInt()))
            ) {
                if (banner != null) {
                    Image(
                        bitmap = banner.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp)
                    .offset(y = 38.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ring)
                    .border(5.dp, ring, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5865F2)),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_social_discord), contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(
            if (p.pronouns.isBlank()) p.username else p.username + "  \u2022  " + p.pronouns,
            fontSize = 14.sp,
            color = cs.onSurfaceVariant
        )
        if (p.bio.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainerHighest)
                    .padding(14.dp)
            ) {
                Text("About me", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(p.bio, fontSize = 15.sp, lineHeight = 21.sp, color = cs.onSurface)
            }
        }
    }
}
