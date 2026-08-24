package dev.terashima.yomitorirss.core.network

interface HttpClient {
  suspend fun execute(request: HttpRequest): HttpResponse

  companion object {
    /**
     * Returns a wrapper around the process-wide HTTP transport.
     *
     * Application composition should pass the versioned external User-Agent explicitly. Isolated
     * adapters and tests may use the generic default without creating another OkHttp connection pool.
     */
    fun create(userAgent: String = DEFAULT_USER_AGENT): HttpClient =
      OkHttpHttpClientFactory.create(userAgent)
  }
}

private const val DEFAULT_USER_AGENT = "Mosaic (Android)"
