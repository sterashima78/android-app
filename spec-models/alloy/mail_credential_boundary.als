// Natural-language specifications:
// - docs/spec/06-mail.md
//
// Covers:
// - MAIL-CREDENTIAL-BOUNDARY-001@sha256:658ed1ee649dddacc6403616a7166a74fd5cc6d125fd78fa4e855022a32c1ef4 -> CredentialFlowsOnlyThroughRuntimeOrRequest
// - MAIL-CREDENTIAL-BOUNDARY-001@sha256:658ed1ee649dddacc6403616a7166a74fd5cc6d125fd78fa4e855022a32c1ef4 -> CredentialNeverCrossesPresentationOrDomain
// - MAIL-CREDENTIAL-BOUNDARY-001@sha256:658ed1ee649dddacc6403616a7166a74fd5cc6d125fd78fa4e855022a32c1ef4 -> CredentialNeverPersists
// - MAIL-CREDENTIAL-BOUNDARY-001@sha256:658ed1ee649dddacc6403616a7166a74fd5cc6d125fd78fa4e855022a32c1ef4 -> WorkerInputContainsOnlyBoundedMetadata
//
// Scope:
// This model describes where Mail authentication credentials may flow. It does
// not model token refresh/expiry or platform authorization internals. Account
// identity, display metadata, sync checkpoints, and bounded worker control data
// are intentionally distinct from credential/token values.

module mail_credential_boundary

abstract sig DataClass {}
one sig AccountIdentity, DisplayMetadata, SyncCheckpoint, SyncControlMetadata, CredentialToken extends DataClass {}

abstract sig Sink {}
one sig AuthorizationRuntime, MailRequestRuntime, ApiRequest extends Sink {}
one sig PresentationApi, DomainRepositoryApi extends Sink {}
one sig MailDatabase, MailPreferences, WorkerInput, BackupArchive, LogSink extends Sink {}

sig Flow {
  datum: one DataClass,
  sink: one Sink
}

fact CredentialBoundary {
  all flow: Flow |
    flow.datum = CredentialToken implies
      flow.sink in AuthorizationRuntime + MailRequestRuntime + ApiRequest
}

fact WorkerMetadataBoundary {
  all flow: Flow |
    flow.sink = WorkerInput implies
      flow.datum in AccountIdentity + SyncCheckpoint + SyncControlMetadata
}

assert CredentialFlowsOnlyThroughRuntimeOrRequest {
  all flow: Flow |
    flow.datum = CredentialToken implies
      flow.sink in AuthorizationRuntime + MailRequestRuntime + ApiRequest
}

assert CredentialNeverCrossesPresentationOrDomain {
  no flow: Flow |
    flow.datum = CredentialToken and
    flow.sink in PresentationApi + DomainRepositoryApi
}

assert CredentialNeverPersists {
  no flow: Flow |
    flow.datum = CredentialToken and
    flow.sink in MailDatabase + MailPreferences + BackupArchive + LogSink
}

assert WorkerInputContainsOnlyBoundedMetadata {
  all flow: Flow |
    flow.sink = WorkerInput implies
      flow.datum in AccountIdentity + SyncCheckpoint + SyncControlMetadata
}

check CredentialFlowsOnlyThroughRuntimeOrRequest for 10 expect 0
check CredentialNeverCrossesPresentationOrDomain for 10 expect 0
check CredentialNeverPersists for 10 expect 0
check WorkerInputContainsOnlyBoundedMetadata for 10 expect 0

run RepresentativeCredentialBoundary {
  some flow: Flow | flow.datum = CredentialToken and flow.sink = AuthorizationRuntime
  some flow: Flow | flow.datum = CredentialToken and flow.sink = MailRequestRuntime
  some flow: Flow | flow.datum = CredentialToken and flow.sink = ApiRequest
  some flow: Flow | flow.datum = AccountIdentity and flow.sink = PresentationApi
  some flow: Flow | flow.datum = AccountIdentity and flow.sink = MailDatabase
  some flow: Flow | flow.datum = SyncCheckpoint and flow.sink = WorkerInput
} for 10 expect 1
