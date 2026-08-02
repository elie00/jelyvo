package ca.ilianokokoro.umihi.music.wearapp

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.ResourceBuilders
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
        Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    LOGO_RESOURCE_ID,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_jelyvo_watch)
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )

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
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(LOGO_RESOURCE_ID)
                    .setWidth(dp(36f))
                    .setHeight(dp(36f))
                    .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
                    .build(),
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(9f)).build())
            .addContent(tileText(title, 22f, 0xFFFFFFFF.toInt(), bold = true))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).build())
            .addContent(tileText(subtitle, 14f, 0xFFBDBDBD.toInt(), bold = false))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(16f)).build())
            .addContent(
                tileText(
                    if (state.isPlaying) "Ⅱ" else "▶",
                    23f,
                    0xFFFFF8ED.toInt(),
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
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size))
                .setColor(argb(color))
                .setWeight(
                    if (bold) {
                        LayoutElementBuilders.FONT_WEIGHT_BOLD
                    } else {
                        LayoutElementBuilders.FONT_WEIGHT_NORMAL
                    },
                )
                .build(),
        )
        .build()

    private companion object {
        const val RESOURCES_VERSION = "2"
        const val LOGO_RESOURCE_ID = "jelyvo_logo"
    }
}
