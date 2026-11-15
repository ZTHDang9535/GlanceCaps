package com.dang.glancecaps.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class ImageStore(private val name: String) {
    private var cached: Bitmap? = null
    private var cachedStamp = -1L

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, name)

    @Synchronized
    fun saveFrom(ctx: Context, uri: Uri): Boolean {
        val square = IconStore.loadSquare(ctx, uri, 192) ?: return false
        return try {
            FileOutputStream(file(ctx)).use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cached = null
            true
        } catch (e: Exception) {
            false
        }
    }

    fun has(ctx: Context): Boolean = file(ctx).exists()

    @Synchronized
    fun reset(ctx: Context) {
        file(ctx).delete()
        cached = null
    }

    @Synchronized
    fun bitmap(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        if (cached == null || cachedStamp != f.lastModified()) {
            cached = try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null }
            cachedStamp = f.lastModified()
        }
        return cached
    }

    fun stamp(ctx: Context): Long = file(ctx).let { if (it.exists()) it.lastModified() else 0L }
}

val PinnedIconStore = ImageStore("pinned_icon.png")
val CallPlaceholderStore = ImageStore("call_placeholder.png")
