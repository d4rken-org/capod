package eu.darken.capod.wear.core

import androidx.wear.tiles.*
import com.google.android.horologist.tiles.CoroutinesTileService
import eu.darken.capod.R

class CAPodTileService : CoroutinesTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources = ResourceBuilders.Resources.Builder().apply {
        setVersion(RESOURCES_VERSION)
    }.build()

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile = TileBuilders.Tile.Builder().apply {
        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder().apply {
                    setLayout(
                        LayoutElementBuilders.Layout.Builder().apply {
                            setRoot(tileLayout())
                        }.build()
                    )
                }.build()
            )
            .build()
        setResourcesVersion(RESOURCES_VERSION)
        setTimeline(singleTileTimeline)
    }.build()

    private fun tileLayout(): LayoutElementBuilders.LayoutElement {
        val text = getString(R.string.app_name)
        return LayoutElementBuilders.Box.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(text)
                    .build()
            )
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "0"
    }
}