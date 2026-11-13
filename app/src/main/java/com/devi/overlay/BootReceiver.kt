package com.devi.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the button listener after a restart or an update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> DeviListenerService.sync(context)
        }
    }
}
