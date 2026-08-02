package ca.ilianokokoro.umihi.music.wearapp

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures

/** Glanceable entry point shown in the Wear OS tile carousel. */
class JelyvoTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        Futures.immediateFuture(
            Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(60_000L)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(tileContent())
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun tileContent(): LayoutElementBuilders.LayoutElement {
        val state = WearPlaybackCache(this).read()
        val title = if (state.hasMedia) {
            state.title.ifBlank { getString(R.string.unknown_title) }
        } else {
            getString(R.string.nothing_playing)
        }
        val subtitle = if (state.hasMedia) {
            state.artist.ifBlank { getString(R.string.unknown_artist) }
        } else {
            getString(R.string.start_on_phone)
        }
        val launchApp = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build(),
            )
            .build()
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_player")
            .setOnClick(launchApp)
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(expand()).build())
            .addContent(tileText("JELYVO", 13f, 0xFFE8492A.toInt(), bold = true))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(14f)).build())
            .addContent(tileText(title, 22f, 0xFFFFFFFF.toInt(), bold = true))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).build())
            .addContent(tileText(subtitle, 14f, 0xFFBDBDBD.toInt(), bold = false))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(16f)).build())
            .addContent(
                tileText(
                    if (state.isPlaying) "Ⅱ" else "▶",
                    23f,
                    0xFFFFFFFF.toInt(),
                    bold = true,
                ),
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(expand()).build())
            .build()
    }

    private fun tileText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
    ): LayoutElementBuilders.Text = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setMaxLines(1)
        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size))
                .setColor(argb(color))
                .setWeight(if (bold) 700 else 400)
                .build(),
        )
        .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
