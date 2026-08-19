package dev.terashima.yomitorirss.feature.mail.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailBodyDecoderTest {
  @Test
  fun `UTF-8本文をbase64urlから復号する`() {
    val source = "日本語のHTMLメール"
    val encoded = encode(source, StandardCharsets.UTF_8)

    assertEquals(source, decodeMailBody(encoded, "text/html; charset=UTF-8"))
  }

  @Test
  fun `MIMEのcharsetに従って日本語本文を復号する`() {
    val charset = Charset.forName("ISO-2022-JP")
    val source = "日本語メール"
    val encoded = encode(source, charset)

    assertEquals(source, decodeMailBody(encoded, "text/html; charset=\"ISO-2022-JP\""))
  }

  @Test
  fun `未知のcharsetはUTF-8へフォールバックする`() {
    val source = "本文"
    val encoded = encode(source, StandardCharsets.UTF_8)

    assertEquals(source, decodeMailBody(encoded, "text/plain; charset=unknown-mail-charset"))
  }

  @Test
  fun `添付HTMLは表示本文候補から除外する`() {
    assertFalse(isDisplayMailBodyPart("attachment.html", ""))
    assertFalse(isDisplayMailBodyPart("", "attachment; filename=message.html"))
    assertTrue(isDisplayMailBodyPart("", "inline"))
    assertTrue(isDisplayMailBodyPart("", ""))
  }

  private fun encode(value: String, charset: Charset): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(charset))
}
