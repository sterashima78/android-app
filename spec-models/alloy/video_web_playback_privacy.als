// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - VIDEO-WEB-PLAYBACK-PRIVACY-001@sha256:23b17cbbb284dab2dc35a442f3d6a11280ed013b7f6e17aa7c575484fdb9f741 -> CookieSharingRequiresOptIn
// - VIDEO-WEB-PLAYBACK-PRIVACY-001@sha256:23b17cbbb284dab2dc35a442f3d6a11280ed013b7f6e17aa7c575484fdb9f741 -> CookieValueIsNotCopiedByVideo
// - VIDEO-WEB-PLAYBACK-PRIVACY-001@sha256:23b17cbbb284dab2dc35a442f3d6a11280ed013b7f6e17aa7c575484fdb9f741 -> ReferrerPathRequiresExtractorOptIn
// - VIDEO-WEB-PLAYBACK-PRIVACY-001@sha256:23b17cbbb284dab2dc35a442f3d6a11280ed013b7f6e17aa7c575484fdb9f741 -> NonExtractorReferrerIsOriginOnly
// - VIDEO-WEB-PLAYBACK-PRIVACY-001@sha256:23b17cbbb284dab2dc35a442f3d6a11280ed013b7f6e17aa7c575484fdb9f741 -> SensitiveReferrerComponentsNotShared
// - VIDEO-WEB-PLAYBACK-PRIVACY-001@sha256:23b17cbbb284dab2dc35a442f3d6a11280ed013b7f6e17aa7c575484fdb9f741 -> OriginIsOriginOnly
//
// Scope:
// A PlaybackRequest represents the metadata policy applied to one resolved Web
// stream request. Cookie availability and concrete URL strings are abstracted;
// the model checks whether each class of metadata is permitted to cross the
// playback boundary. "Path shared" means a non-origin path may be sent.
// App-owned persistence/log/backup/export/error-UI fields express the
// specification contract. WebView profile storage is browser-managed session
// state and remains outside this model.

module video_web_playback_privacy

abstract sig Toggle {}
one sig On, Off extends Toggle {}

abstract sig ReferrerSource {}
one sig PageFallback, ExtractorReferrer, ObservedRequest extends ReferrerSource {}

sig PlaybackRequest {
  cookieOptIn: one Toggle,
  cookieShared: one Toggle,
  cookieCopiedToVideoDurableState: one Toggle,
  cookieBackedUp: one Toggle,
  cookieExported: one Toggle,
  cookieLogged: one Toggle,
  cookieShownInErrorUi: one Toggle,

  referrerPathOptIn: one Toggle,
  referrerSource: one ReferrerSource,
  referrerPathShared: one Toggle,
  referrerQueryShared: one Toggle,
  referrerFragmentShared: one Toggle,
  referrerUserInfoShared: one Toggle,
  originPathShared: one Toggle
}

fact PlaybackPrivacyPolicy {
  all request: PlaybackRequest |
    request.cookieOptIn = Off implies request.cookieShared = Off

  all request: PlaybackRequest |
    request.cookieCopiedToVideoDurableState = Off and
    request.cookieBackedUp = Off and
    request.cookieExported = Off and
    request.cookieLogged = Off and
    request.cookieShownInErrorUi = Off

  all request: PlaybackRequest |
    request.referrerPathShared = On implies (
      request.referrerPathOptIn = On and
      request.referrerSource = ExtractorReferrer
    )

  all request: PlaybackRequest |
    request.referrerSource != ExtractorReferrer implies
      request.referrerPathShared = Off

  all request: PlaybackRequest |
    request.referrerQueryShared = Off and
    request.referrerFragmentShared = Off and
    request.referrerUserInfoShared = Off and
    request.originPathShared = Off
}

assert CookieSharingRequiresOptIn {
  all request: PlaybackRequest |
    request.cookieShared = On implies request.cookieOptIn = On
}

assert CookieValueIsNotCopiedByVideo {
  all request: PlaybackRequest |
    request.cookieCopiedToVideoDurableState = Off and
    request.cookieBackedUp = Off and
    request.cookieExported = Off and
    request.cookieLogged = Off and
    request.cookieShownInErrorUi = Off
}

assert ReferrerPathRequiresExtractorOptIn {
  all request: PlaybackRequest |
    request.referrerPathShared = On implies (
      request.referrerPathOptIn = On and
      request.referrerSource = ExtractorReferrer
    )
}

assert NonExtractorReferrerIsOriginOnly {
  all request: PlaybackRequest |
    request.referrerSource != ExtractorReferrer implies
      request.referrerPathShared = Off
}

assert SensitiveReferrerComponentsNotShared {
  all request: PlaybackRequest |
    request.referrerQueryShared = Off and
    request.referrerFragmentShared = Off and
    request.referrerUserInfoShared = Off
}

assert OriginIsOriginOnly {
  all request: PlaybackRequest |
    request.originPathShared = Off
}

check CookieSharingRequiresOptIn for 6 expect 0
check CookieValueIsNotCopiedByVideo for 6 expect 0
check ReferrerPathRequiresExtractorOptIn for 6 expect 0
check NonExtractorReferrerIsOriginOnly for 6 expect 0
check SensitiveReferrerComponentsNotShared for 6 expect 0
check OriginIsOriginOnly for 6 expect 0

run RepresentativePrivacyCases {
  some disj cookieOff, cookieOn, extractorPath, observed: PlaybackRequest |
    cookieOff.cookieOptIn = Off and
    cookieOff.cookieShared = Off and
    cookieOn.cookieOptIn = On and
    cookieOn.cookieShared = On and
    extractorPath.referrerPathOptIn = On and
    extractorPath.referrerSource = ExtractorReferrer and
    extractorPath.referrerPathShared = On and
    observed.referrerPathOptIn = On and
    observed.referrerSource = ObservedRequest and
    observed.referrerPathShared = Off
} for 8 expect 1
