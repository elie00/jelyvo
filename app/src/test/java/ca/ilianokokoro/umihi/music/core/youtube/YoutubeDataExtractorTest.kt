package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.models.AlbumHeaderInfo
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guards for the InnerTube JSON parsers of [YoutubeDataExtractor]. The fixtures are
 * hand-written minimal responses matching the exact path each parser walks, so they pin *our*
 * traversal — not YouTube's API. A shape change upstream still breaks the app silently and cannot
 * be caught here.
 *
 * [ca.ilianokokoro.umihi.music.models.Song] compares by (youtubeId, uid) and uid is random per
 * instance, so parsed songs are asserted field by field instead of with a whole-object assertEquals.
 */
class YoutubeDataExtractorTest {

    @Test
    fun `getBestThumbnailUrl takes the last and largest thumbnail`() {
        val thumbnail = Json.parseToJsonElement(
            """
            {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://img/small.jpg", "width": 60, "height": 60 },
                    { "url": "https://img/large.jpg", "width": 544, "height": 544 }
                  ]
                }
              }
            }
            """
        )

        assertEquals("https://img/large.jpg", YoutubeDataExtractor.getBestThumbnailUrl(thumbnail))
        // Album/artist rows may omit the per-track thumbnail entirely.
        assertEquals("", YoutubeDataExtractor.getBestThumbnailUrl(Json.parseToJsonElement("{}")))
    }

    @Test
    fun `extractSong maps a full search row`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "thumbnail": {
                  "musicThumbnailRenderer": {
                    "thumbnail": {
                      "thumbnails": [
                        { "url": "https://img/small.jpg", "width": 60, "height": 60 },
                        { "url": "https://img/big.jpg", "width": 226, "height": 226 }
                      ]
                    }
                  }
                },
                "flexColumns": [
                  {
                    "musicResponsiveListItemFlexColumnRenderer": {
                      "text": {
                        "runs": [
                          {
                            "text": "Never Gonna Give You Up",
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "dQw4w9WgXcQ",
                                "watchEndpointMusicSupportedConfigs": {
                                  "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
                                }
                              }
                            }
                          }
                        ]
                      }
                    }
                  },
                  {
                    "musicResponsiveListItemFlexColumnRenderer": {
                      "text": {
                        "runs": [
                          {
                            "text": "Rick Astley",
                            "navigationEndpoint": { "browseEndpoint": { "browseId": "UCuAXFkgsw1L7xaCfnd5JJOw" } }
                          },
                          { "text": " - " },
                          {
                            "text": "Whenever You Need Somebody",
                            "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_abc123" } }
                          }
                        ]
                      }
                    }
                  }
                ],
                "fixedColumns": [
                  { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [ { "text": "3:32" } ] } } }
                ],
                "playlistItemData": { "videoId": "dQw4w9WgXcQ" },
                "badges": [
                  { "musicInlineBadgeRenderer": { "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" } } }
                ],
                "menu": {
                  "menuRenderer": {
                    "topLevelButtons": [ { "likeButtonRenderer": { "likeStatus": "LIKE" } } ]
                  }
                }
              }
            }
            """
        )

        val song = requireNotNull(YoutubeDataExtractor.extractSong(row))

        assertEquals("dQw4w9WgXcQ", song.youtubeId)
        assertEquals("Never Gonna Give You Up", song.title)
        // Only the first run of the subtitle column is read, never the joined runs.
        assertEquals("Rick Astley", song.artist)
        assertEquals("3:32", song.duration)
        assertEquals("https://img/big.jpg", song.thumbnailHref)
        assertTrue(song.isExplicit)
        assertEquals(true, song.isLiked)
        assertEquals("MPREb_abc123", song.albumId)
        assertEquals("Whenever You Need Somebody", song.albumName)
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", song.artistId)
        // ATV is official audio, not a clip.
        assertFalse(song.isVideo)
        assertNull(song.viewCount)
    }

    @Test
    fun `extractSong returns null without a video id`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Unplayable" } ] } } }
                ]
              }
            }
            """
        )

        assertNull(YoutubeDataExtractor.extractSong(row))
        assertNull(YoutubeDataExtractor.extractSong(Json.parseToJsonElement("{}")))
    }

    @Test
    fun `extractSearchItems collects song rows anywhere in the tree and dedupes them`() {
        val response = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "selected": true,
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "musicShelfRenderer": {
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "A" } ] } } }
                                        ],
                                        "playlistItemData": { "videoId": "idA" }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "B" } ] } } }
                                        ],
                                        "playlistItemData": { "videoId": "idB" }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "A" } ] } } }
                                        ],
                                        "playlistItemData": { "videoId": "idA" }
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """

        val items = YoutubeDataExtractor.extractSearchItems(response, SearchFilter.Songs)

        assertEquals(listOf("idA", "idB"), items.map(SearchItem::id))
        assertTrue(items.all { it is SearchItem.Track })
    }

    @Test
    fun `extractSearchItems marks Videos tab rows as videos and reads their view count`() {
        val response = """
            {
              "contents": [
                {
                  "musicResponsiveListItemRenderer": {
                    "flexColumns": [
                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Some clip" } ] } } },
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": {
                            "runs": [
                              { "text": "Some artist" },
                              { "text": " - " },
                              { "text": "1.2M views" }
                            ]
                          }
                        }
                      }
                    ],
                    "playlistItemData": { "videoId": "clipId" }
                  }
                }
              ]
            }
            """

        val items = YoutubeDataExtractor.extractSearchItems(response, SearchFilter.Videos)

        val song = (items.single() as SearchItem.Track).song
        // The Videos tab has no musicVideoType on its rows; the filter itself declares them videos.
        assertTrue(song.isVideo)
        assertEquals("1.2M views", song.viewCount)
    }

    @Test
    fun `extractSpellCorrection reads an auto-corrected result`() {
        val response = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "showingResultsForRenderer": {
                        "correctedQuery": { "runs": [ { "text": "daft punk" } ] },
                        "correctedQueryEndpoint": {
                          "searchEndpoint": { "query": "daft punk", "params": "CORRECTED_PARAMS" }
                        },
                        "originalQuery": { "runs": [ { "text": "dfat punk" } ] },
                        "originalQueryEndpoint": {
                          "searchEndpoint": { "query": "dfat punk", "params": "ORIGINAL_PARAMS" }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """

        assertEquals(
            SpellCorrection(
                correctedQuery = "daft punk",
                correctedParams = "CORRECTED_PARAMS",
                originalQuery = "dfat punk",
                originalParams = "ORIGINAL_PARAMS",
                autoCorrected = true,
            ),
            YoutubeDataExtractor.extractSpellCorrection(response),
        )
    }

    @Test
    fun `extractSpellCorrection falls back to the runs text of a did-you-mean`() {
        val response = """
            {
              "contents": {
                "didYouMeanRenderer": {
                  "correctedQuery": { "runs": [ { "text": "beat" }, { "text": "les" } ] }
                }
              }
            }
            """

        assertEquals(
            SpellCorrection(
                // Runs are concatenated, not space-joined.
                correctedQuery = "beatles",
                correctedParams = null,
                originalQuery = null,
                originalParams = null,
                autoCorrected = false,
            ),
            YoutubeDataExtractor.extractSpellCorrection(response),
        )
    }

    @Test
    fun `extractContinuationToken finds both continuation shapes`() {
        assertEquals(
            "TOKEN_A",
            YoutubeDataExtractor.extractContinuationToken(
                """{ "continuations": [ { "nextContinuationData": { "continuation": "TOKEN_A" } } ] }"""
            ),
        )
        assertEquals(
            "TOKEN_B",
            YoutubeDataExtractor.extractContinuationToken(
                """{ "continuationItemRenderer": { "continuationEndpoint": { "continuationCommand": { "token": "TOKEN_B" } } } }"""
            ),
        )
    }

    @Test
    fun `extractLargestThumbnail prefers square art over a wider frame`() {
        val response = """
            {
              "header": {
                "thumbnails": [
                  { "url": "https://img/wide.jpg", "width": 1280, "height": 720 },
                  { "url": "https://img/square.jpg", "width": 544, "height": 544 },
                  { "url": "https://img/square-small.jpg", "width": 120, "height": 120 }
                ]
              }
            }
            """

        // A 16:9 clip frame is wider but is never the album art.
        assertEquals("https://img/square.jpg", YoutubeDataExtractor.extractLargestThumbnail(response))
    }

    @Test
    fun `extractAlbumHeaderInfo maps the album header`() {
        val response = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "musicResponsiveHeaderRenderer": {
                        "title": { "runs": [ { "text": "Random Access Memories" } ] },
                        "straplineTextOne": {
                          "runs": [
                            {
                              "text": "Daft Punk",
                              "navigationEndpoint": { "browseEndpoint": { "browseId": "UC_kRDKYrUlrbtrSiyu5Tflg" } }
                            }
                          ]
                        },
                        "subtitle": { "runs": [ { "text": "Album" }, { "text": " - " }, { "text": "2013" } ] },
                        "secondSubtitle": { "runs": [ { "text": "13 songs" }, { "text": " - " }, { "text": "74 minutes" } ] },
                        "description": {
                          "musicDescriptionShelfRenderer": {
                            "description": { "runs": [ { "text": "Fourth studio album." } ] }
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """

        assertEquals(
            AlbumHeaderInfo(
                title = "Random Access Memories",
                artist = "Daft Punk",
                artistId = "UC_kRDKYrUlrbtrSiyu5Tflg",
                subtitle = "Album - 2013",
                secondSubtitle = "13 songs - 74 minutes",
                description = "Fourth studio album.",
            ),
            YoutubeDataExtractor.extractAlbumHeaderInfo(response),
        )
    }

    @Test
    fun `extractSongInfo maps a player response`() {
        val response = """
            {
              "videoDetails": {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "author": "Rick Astley",
                "lengthSeconds": "3752",
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://img/wide.jpg", "width": 1280, "height": 720 },
                    { "url": "https://img/square.jpg", "width": 544, "height": 544 }
                  ]
                }
              },
              "microformat": { "microformatDataRenderer": { "familySafe": false } }
            }
            """

        val song = YoutubeDataExtractor.extractSongInfo(response)

        assertEquals("dQw4w9WgXcQ", song.youtubeId)
        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals("Rick Astley", song.artist)
        // 3752 s crosses the hour boundary, which switches the display format.
        assertEquals("1:02:32", song.duration)
        assertEquals("https://img/square.jpg", song.thumbnailHref)
        assertTrue(song.isExplicit)
    }

    @Test
    fun `parsers degrade to null or empty on a response without the expected keys`() {
        assertNull(YoutubeDataExtractor.extractContinuationToken("{}"))
        assertNull(YoutubeDataExtractor.extractAlbumHeaderInfo("{}"))
        assertNull(YoutubeDataExtractor.extractLargestThumbnail("{}"))
        assertNull(YoutubeDataExtractor.extractSpellCorrection("{}"))
        assertTrue(YoutubeDataExtractor.extractSearchItems("{}", SearchFilter.Songs).isEmpty())

        val song = YoutubeDataExtractor.extractSongInfo("{}")
        assertEquals("", song.youtubeId)
        assertEquals("0:00", song.duration)
        assertEquals("", song.thumbnailHref)
        assertFalse(song.isExplicit)
    }

    /** Documents current behaviour: unparseable input propagates out of the extractor. */
    @Test(expected = SerializationException::class)
    fun `extractSongInfo throws on unparseable json`() {
        YoutubeDataExtractor.extractSongInfo("")
    }

    /** Documents current behaviour: a non-object root fails the unguarded jsonObject cast. */
    @Test(expected = IllegalArgumentException::class)
    fun `extractSongInfo throws on a non-object response`() {
        YoutubeDataExtractor.extractSongInfo("[]")
    }
}
