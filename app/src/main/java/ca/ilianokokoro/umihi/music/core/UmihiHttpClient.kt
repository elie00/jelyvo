package ca.ilianokokoro.umihi.music.core

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object UmihiHttpClient {
    /**
     * Shared across the API client and (via [newBuilder]) the download/Coil clients so all traffic
     * draws from one thread pool. The per-host cap is raised from OkHttp's default of 5 because the
     * artwork CDN and the innertube API resolve to a handful of shared Google hosts: with the
     * default a burst of artwork fetches can starve concurrent API calls (and vice versa).
     */
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 10
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}