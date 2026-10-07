package ca.ilianokokoro.umihi.music.wear

/** Do not stop the source device merely because the destination accepted a queue. */
fun handoffDestinationReady(
    expectedMediaId: String,
    actualMediaId: String?,
    playWhenReady: Boolean,
    isPlaying: Boolean,
    isReady: Boolean,
): Boolean = actualMediaId == expectedMediaId && if (playWhenReady) isPlaying else isReady
