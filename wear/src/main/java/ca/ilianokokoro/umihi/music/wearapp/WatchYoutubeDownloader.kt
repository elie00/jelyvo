package ca.ilianokokoro.umihi.music.wearapp

import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class WatchYoutubeDownloader : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        return HTTP_CLIENT.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("YouTube rate limit", request.url())
            }
            Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private val initialized = AtomicBoolean(false)
        private val HTTP_CLIENT = okhttp3.OkHttpClient.Builder().build()

        fun ensureInitialized() {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(WatchYoutubeDownloader())
            }
        }
    }
}
