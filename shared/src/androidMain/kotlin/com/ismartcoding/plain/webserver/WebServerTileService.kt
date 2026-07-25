package com.ismartcoding.plain.webserver

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ismartcoding.plain.web.WebServerController

/**
 * Quick Settings tile (spec §8) that toggles the embedded web server and reflects its running
 * state, so the console can be started/stopped from the notification shade.
 */
class WebServerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (AndroidWebServer.running.value) {
            WebServerController.stop()
        } else {
            WebServerController.start()
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val running = AndroidWebServer.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "MWI Web"
        tile.updateTile()
    }
}
