package dev.terashima.yomitorirss.core.network

interface HttpClient {
  suspend fun execute(request: HttpRequest): HttpResponse

  companion object {
    private val shared: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      OkHttpHttpClient()
    }

    /**
     * Returns the process-wide HTTP transport.
     *
     * Application composition should pass this instance to feature runtimes explicitly. The
     * factory remains available to isolated adapters and tests without creating parallel OkHttp
     * connection pools.
     */
    fun create(): HttpClient = shared
  }
}
