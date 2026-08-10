package dev.terashima.yomitorirss.core.network

interface HttpClient {
  suspend fun execute(request: HttpRequest): HttpResponse

  companion object {
    fun create(): HttpClient = OkHttpHttpClient()
  }
}
