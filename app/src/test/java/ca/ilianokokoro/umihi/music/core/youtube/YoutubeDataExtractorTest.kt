package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.models.AlbumHeaderInfo
import ca.ilianokokoro.umihi.music.models.Cookies
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.runBlocking
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

    /** Logged-out settings: the album fixtures below never trigger a continuation request. */
    private val testSettings = UmihiSettings(cookies = Cookies(), dataSyncId = null)

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
    fun `extractSong keeps like status unknown when the row has no like button`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  {
                    "musicResponsiveListItemFlexColumnRenderer": {
                      "text": { "runs": [ { "text": "Track" } ] }
                    }
                  }
                ],
                "playlistItemData": { "videoId": "dQw4w9WgXcQ" }
              }
            }
            """
        )

        val song = requireNotNull(YoutubeDataExtractor.extractSong(row))

        assertNull(song.isLiked)
    }

    @Test
    fun `watch next resolves artist and album navigation for the current track`() {
        val response = """
            {
              "contents": {
                "playlistPanelVideoRenderer": {
                  "videoId": "dQw4w9WgXcQ",
                  "longBylineText": {
                    "runs": [
                      {
                        "text": "Rick Astley",
                        "navigationEndpoint": {
                          "browseEndpoint": { "browseId": "UCuAXFkgsw1L7xaCfnd5JJOw" }
                        }
                      },
                      { "text": " • " },
                      {
                        "text": "Whenever You Need Somebody",
                        "navigationEndpoint": {
                          "browseEndpoint": { "browseId": "MPREb_abc123" }
                        }
                      }
                    ]
                  }
                }
              }
            }
        """

        assertEquals(
            "UCuAXFkgsw1L7xaCfnd5JJOw",
            YoutubeDataExtractor.extractArtistBrowseId(response, "dQw4w9WgXcQ"),
        )
        assertEquals(
            "MPREb_abc123" to "Whenever You Need Somebody",
            YoutubeDataExtractor.extractAlbumBrowseInfo(response, "dQw4w9WgXcQ"),
        )
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

    /**
     * A "Fans might also like" shelf holds the same card shape as an album shelf but with channel
     * ids. Those cards used to match neither parser, which emptied the section and made
     * extractArtistSections drop it entirely.
     */
    @Test
    fun `extractArtistSections keeps a related-artists shelf`() {
        val response = """
            {
              "contents": {
                "musicCarouselShelfRenderer": {
                  "header": {
                    "musicCarouselShelfBasicHeaderRenderer": {
                      "title": { "runs": [ { "text": "Fans might also like" } ] }
                    }
                  },
                  "contents": [
                    {
                      "musicTwoRowItemRenderer": {
                        "title": { "runs": [ { "text": "a-ha" } ] },
                        "navigationEndpoint": { "browseEndpoint": { "browseId": "UCVeMw72tepFl1Zt5fvf9QKQ" } },
                        "thumbnailRenderer": {
                          "musicThumbnailRenderer": {
                            "thumbnail": { "thumbnails": [ { "url": "https://img/aha.jpg", "width": 226, "height": 226 } ] }
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """

        val section = YoutubeDataExtractor.extractArtistSections(response).single()

        assertEquals("Fans might also like", section.title)
        assertEquals(listOf("a-ha"), section.artists.map { it.name })
        assertEquals("UCVeMw72tepFl1Zt5fvf9QKQ", section.artists.single().channelUrl)
        // A channel card is not an album and not a video.
        assertTrue(section.albums.isEmpty())
        assertTrue(section.videos.isEmpty())
    }

    @Test
    fun `extractArtistDescription and subscriber count read the header`() {
        val response = """
            {
              "header": {
                "musicImmersiveHeaderRenderer": {
                  "title": { "runs": [ { "text": "a-ha" } ] },
                  "description": { "runs": [ { "text": "Norwegian band formed in Oslo in 1982." } ] },
                  "subscriptionButton": {
                    "subscribeButtonRenderer": {
                      "subscriberCountText": { "runs": [ { "text": "3.1M subscribers" } ] }
                    }
                  }
                }
              }
            }
            """

        assertEquals(
            "Norwegian band formed in Oslo in 1982.",
            YoutubeDataExtractor.extractArtistDescription(response),
        )
        assertEquals(
            "3.1M subscribers",
            YoutubeDataExtractor.extractArtistSubscriberCount(response),
        )
        assertNull(YoutubeDataExtractor.extractArtistDescription("{}"))
        assertNull(YoutubeDataExtractor.extractArtistSubscriberCount("{}"))
    }

    /**
     * Artist-page song rows carry a play count instead of a view count, and it is what makes the
     * "most streamed" ranking readable. It must be picked up on a plain (non-video) row.
     */
    @Test
    fun `extractSong reads the play count of an artist page row`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Take On Me" } ] } } },
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "1.9B plays" } ] } } }
                ],
                "playlistItemData": { "videoId": "djV11Xbc914" }
              }
            }
            """
        )

        val song = requireNotNull(YoutubeDataExtractor.extractSong(row))

        assertEquals("1.9B plays", song.playCount)
        // A play count is not a video view count, and must not turn the row into a video.
        assertFalse(song.isVideo)
        assertNull(song.viewCount)
    }

    @Test
    fun `extractSong leaves the play count null when the row has none`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Album track" } ] } } },
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Some Artist" } ] } } }
                ],
                "playlistItemData": { "videoId": "abcdefghijk" }
              }
            }
            """
        )

        assertNull(requireNotNull(YoutubeDataExtractor.extractSong(row)).playCount)
    }

    /**
     * Album rows print their track number in an `index` node next to flexColumns. It is the only
     * signal that lets a multi-CD album be split, so it must survive the parse; other row types
     * (search, playlist, artist) have no such node and must stay null.
     */
    @Test
    fun `extractSong reads the track number of an album row`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "index": { "runs": [ { "text": "3" } ] },
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Album track" } ] } } },
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Some Artist" } ] } } }
                ],
                "playlistItemData": { "videoId": "abcdefghijk" }
              }
            }
            """
        )

        assertEquals(3, requireNotNull(YoutubeDataExtractor.extractSong(row)).trackNumber)
    }

    @Test
    fun `extractSong leaves the track number null on a row without an index`() {
        val row = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Album track" } ] } } }
                ],
                "playlistItemData": { "videoId": "abcdefghijk" }
              }
            }
            """
        )

        assertNull(requireNotNull(YoutubeDataExtractor.extractSong(row)).trackNumber)
    }

    /**
     * A multi-CD album can send one track shelf per disc. The playlist path only ever reads the
     * first shelf, which silently dropped every track of disc 2; the album path must read them all
     * and tag each song with its disc.
     */
    @Test
    fun `extractAlbumSongs reads every track shelf and numbers the discs`() = runBlocking {
        fun row(videoId: String, index: Int) = """
            {
              "musicResponsiveListItemRenderer": {
                "index": { "runs": [ { "text": "$index" } ] },
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Track $index" } ] } } }
                ],
                "playlistItemData": { "videoId": "$videoId" }
              }
            }
            """

        val firstShelf = """
            { "musicPlaylistShelfRenderer": { "contents": [
                ${row("aaaaaaaaaaa", 1)}, ${row("bbbbbbbbbbb", 2)}
            ] } }
            """
        val secondShelf = """
            { "musicPlaylistShelfRenderer": { "contents": [ ${row("ccccccccccc", 1)} ] } }
            """

        val response = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [ $firstShelf, $secondShelf ]
                    }
                  }
                }
              }
            }
            """

        val songs = YoutubeDataExtractor.extractAlbumSongs(response, testSettings)

        assertEquals(
            listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc"),
            songs.map { it.youtubeId },
        )
        assertEquals(listOf(1, 1, 2), songs.map { it.discNumber })
        assertEquals(listOf(1, 2, 1), songs.map { it.trackNumber })
    }

    /** A single-shelf album must stay flat: no disc number, hence no "Disc 1" header on screen. */
    @Test
    fun `extractAlbumSongs leaves a single disc album without disc numbers`() = runBlocking {
        val response = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "musicPlaylistShelfRenderer": {
                            "contents": [
                              {
                                "musicResponsiveListItemRenderer": {
                                  "index": { "runs": [ { "text": "1" } ] },
                                  "flexColumns": [
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Track 1" } ] } } }
                                  ],
                                  "playlistItemData": { "videoId": "aaaaaaaaaaa" }
                                }
                              },
                              {
                                "musicResponsiveListItemRenderer": {
                                  "index": { "runs": [ { "text": "2" } ] },
                                  "flexColumns": [
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Track 2" } ] } } }
                                  ],
                                  "playlistItemData": { "videoId": "bbbbbbbbbbb" }
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
            }
            """

        val songs = YoutubeDataExtractor.extractAlbumSongs(response, testSettings)

        assertEquals(2, songs.size)
        songs.forEach { assertNull(it.discNumber) }
    }

    /**
     * lengthSeconds is a free-form string in the player response; a non-numeric value must degrade
     * to "0:00" instead of failing the whole parse.
     */
    @Test
    fun `extractSongInfo falls back to a zero duration on a non-numeric lengthSeconds`() {
        val response = """
            {
              "videoDetails": {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "author": "Rick Astley",
                "lengthSeconds": "N/A"
              }
            }
            """

        val song = YoutubeDataExtractor.extractSongInfo(response)

        assertEquals("dQw4w9WgXcQ", song.youtubeId)
        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals("0:00", song.duration)
    }

    /** videoDetails present but the field absent — live streams ship responses like this. */
    @Test
    fun `extractSongInfo falls back to a zero duration when lengthSeconds is missing`() {
        val response = """
            {
              "videoDetails": {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "author": "Rick Astley"
              }
            }
            """

        val song = YoutubeDataExtractor.extractSongInfo(response)

        assertEquals("dQw4w9WgXcQ", song.youtubeId)
        assertEquals("Rick Astley", song.artist)
        assertEquals("0:00", song.duration)
    }

    /** 99999999999 is numeric but overflows Int; it must degrade like any other bad value. */
    @Test
    fun `extractSongInfo falls back to a zero duration on an out-of-range lengthSeconds`() {
        val response = """
            {
              "videoDetails": {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "author": "Rick Astley",
                "lengthSeconds": "99999999999"
              }
            }
            """

        val song = YoutubeDataExtractor.extractSongInfo(response)

        assertEquals("dQw4w9WgXcQ", song.youtubeId)
        assertEquals("0:00", song.duration)
    }
}
