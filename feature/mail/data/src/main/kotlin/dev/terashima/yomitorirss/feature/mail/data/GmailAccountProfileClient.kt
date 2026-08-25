package dev.terashima.yomitorirss.feature.mail.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import org.json.JSONObject

internal class GmailAccountProfileClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun email(accessToken: String): String {
    val response = httpClient.execute(
      HttpRequest(
        url = PROFILE_URL,
        headers = mapOf(
          "Authorization" to "Bearer $accessToken",
          "Accept" to "application/json",
        ),
        maxResponseBytes = 256L * 1024,
      ),
    )
    if (!response.isSuccessful) {
      val details = response.body.toString(Charsets.UTF_8).take(1_000)
      throw IllegalStateException(
        "Gmail プロフィールを取得できませんでした (HTTP ${response.statusCode}): ${details.ifBlank { response.reasonPhrase }}",
      )
    }
    val email = JSONObject(response.body.toString(Charsets.UTF_8))
      .optString("emailAddress")
      .trim()
    require(email.isNotBlank()) { "Gmail プロフィールからメールアドレスを取得できませんでした" }
    return email
  }

  private companion object {
    const val PROFILE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/profile"
  }
}
