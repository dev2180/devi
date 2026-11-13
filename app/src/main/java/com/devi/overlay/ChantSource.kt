package com.devi.overlay

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer

/** Opens a chant for playing or reading, whether it lives in her folder or ships inside the app. */
object ChantSource {
    fun exists(ctx: Context, id: String): Boolean =
        if (ChantStore.isAsset(id)) try { ctx.assets.openFd(ChantStore.assetPath(id)).close(); true } catch (_: Exception) { false }
        else ChantStore.forContext(ctx).file(id).exists()

    fun setOn(ctx: Context, id: String, player: MediaPlayer) {
        if (ChantStore.isAsset(id)) ctx.assets.openFd(ChantStore.assetPath(id)).use { player.setDataSource(it) }
        else player.setDataSource(ChantStore.forContext(ctx).file(id).absolutePath)
    }

    fun setOn(ctx: Context, id: String, r: MediaMetadataRetriever) {
        if (ChantStore.isAsset(id)) ctx.assets.openFd(ChantStore.assetPath(id)).use { r.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        else r.setDataSource(ChantStore.forContext(ctx).file(id).absolutePath)
    }
}
