package com.devi.overlay

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A Quick Settings tile: swipe down, tap Devi, and she appears (on the lock screen too). */
class DeviTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            icon = android.graphics.drawable.Icon.createWithResource(this@DeviTileService, R.drawable.ic_tile_devi)
            state = if (ImageStore.forContext(this@DeviTileService).main() != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        if (ImageStore.forContext(this).main() == null) return
        val intent = ViewerActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
