package com.dang.glancecaps.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private fun loadProfile(ctx: Context): Bitmap? {
    for (name in listOf("profile.png", "profile.jpg", "profile.jpeg", "profile.webp")) {
        try {
            ctx.assets.open(name).use { return BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {

        }
    }
    return null
}

@Composable
fun AboutScreen() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val profile = remember { loadProfile(ctx) }

    ScreenFrame(title = "About", spacing = 0.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(12.dp))

        if (profile != null) {
            Image(
                bitmap = profile.asImageBitmap(),
                contentDescription = "Dang",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .border(3.dp, cs.primary, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                    .border(3.dp, cs.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("D", style = MaterialTheme.typography.displayLarge, color = cs.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Dang💤", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Vietnamese Indie Developer 🇻🇳",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Text(
            "Passionate Kasane Teto Lover",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Text(
            "15 y/o",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        SectionCard("Contact", Modifier.fillMaxWidth()) {
            SocialLinks(profile)
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "made with love by Dang💤, 2026",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        }
    }
}
