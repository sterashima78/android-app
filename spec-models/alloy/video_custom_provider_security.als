// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - VIDEO-CUSTOM-PROVIDER-SECURITY-001@sha256:c4a96f5e62150defbf05b5faa8c99a04afa275de2069dfd4f28b0cf18c9206fc -> OnlyHostFetchCapability
// - VIDEO-CUSTOM-PROVIDER-SECURITY-001@sha256:c4a96f5e62150defbf05b5faa8c99a04afa275de2069dfd4f28b0cf18c9206fc -> NoInheritedCredentials
// - VIDEO-CUSTOM-PROVIDER-SECURITY-001@sha256:c4a96f5e62150defbf05b5faa8c99a04afa275de2069dfd4f28b0cf18c9206fc -> HostRequestUsesHttps
// - VIDEO-CUSTOM-PROVIDER-SECURITY-001@sha256:c4a96f5e62150defbf05b5faa8c99a04afa275de2069dfd4f28b0cf18c9206fc -> HostRequestHasNoUserInfo
// - VIDEO-CUSTOM-PROVIDER-SECURITY-001@sha256:c4a96f5e62150defbf05b5faa8c99a04afa275de2069dfd4f28b0cf18c9206fc -> CredentialHeadersAreForbidden
// - VIDEO-CUSTOM-PROVIDER-SECURITY-001@sha256:c4a96f5e62150defbf05b5faa8c99a04afa275de2069dfd4f28b0cf18c9206fc -> RuntimeDoesNotAcceptCookies
//
// Scope:
// The model captures capability and credential flow, not concrete byte/time
// limits. Request count, request/response/body/function sizes, execution timeout,
// renderer lifecycle, and feed parsing remain implementation-test concerns.

module video_custom_provider_security

abstract sig Capability {}
one sig HostFetch,
        DirectNetwork,
        DatabaseAccess,
        FilesystemAccess,
        AndroidObjectAccess,
        OtherContextRepositoryAccess extends Capability {}

abstract sig Credential {}
one sig ExistingWebCookie,
        MailCredential,
        SmbCredential,
        CloudToken extends Credential {}

abstract sig Scheme {}
one sig Http, Https extends Scheme {}

abstract sig HeaderKind {}
one sig OrdinaryHeader,
        AuthorizationHeader,
        CookieHeader,
        ProxyAuthorizationHeader,
        ApiKeyHeader extends HeaderKind {}

abstract sig Toggle {}
one sig On, Off extends Toggle {}

sig CustomProviderExecution {
  capabilities: set Capability,
  inheritedCredentials: set Credential,
  acceptsCookies: one Toggle
}

sig HostRequest {
  scheme: one Scheme,
  userInfoPresent: one Toggle,
  headers: set HeaderKind
}

fact RuntimeBoundary {
  all execution: CustomProviderExecution |
    execution.capabilities = HostFetch

  all execution: CustomProviderExecution |
    no execution.inheritedCredentials

  all execution: CustomProviderExecution |
    execution.acceptsCookies = Off
}

fact HostFetchBoundary {
  all request: HostRequest |
    request.scheme = Https

  all request: HostRequest |
    request.userInfoPresent = Off

  all request: HostRequest |
    no request.headers & (
      AuthorizationHeader +
      CookieHeader +
      ProxyAuthorizationHeader +
      ApiKeyHeader
    )
}

assert OnlyHostFetchCapability {
  all execution: CustomProviderExecution |
    execution.capabilities = HostFetch
}

assert NoInheritedCredentials {
  all execution: CustomProviderExecution |
    no execution.inheritedCredentials
}

assert HostRequestUsesHttps {
  all request: HostRequest |
    request.scheme = Https
}

assert HostRequestHasNoUserInfo {
  all request: HostRequest |
    request.userInfoPresent = Off
}

assert CredentialHeadersAreForbidden {
  all request: HostRequest |
    no request.headers & (
      AuthorizationHeader +
      CookieHeader +
      ProxyAuthorizationHeader +
      ApiKeyHeader
    )
}

assert RuntimeDoesNotAcceptCookies {
  all execution: CustomProviderExecution |
    execution.acceptsCookies = Off
}

check OnlyHostFetchCapability for 8 expect 0
check NoInheritedCredentials for 8 expect 0
check HostRequestUsesHttps for 8 expect 0
check HostRequestHasNoUserInfo for 8 expect 0
check CredentialHeadersAreForbidden for 8 expect 0
check RuntimeDoesNotAcceptCookies for 8 expect 0

run RepresentativeSafeExecution {
  some execution: CustomProviderExecution |
    execution.capabilities = HostFetch and
    no execution.inheritedCredentials and
    execution.acceptsCookies = Off

  some request: HostRequest |
    request.scheme = Https and
    request.userInfoPresent = Off and
    request.headers = OrdinaryHeader
} for 8 expect 1
