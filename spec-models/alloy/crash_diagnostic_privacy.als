// Natural-language specifications:
// - docs/spec/15-privacy-security.md
//
// Covers:
// - CRASH-DIAGNOSTIC-PRIVACY-001@sha256:1b3b2b94eaa2e7d52c7fc1af1350f8c3885e616242707147982c717348f92c11 -> EveryRawDatumHasOneSanitizedOutput
// - CRASH-DIAGNOSTIC-PRIVACY-001@sha256:1b3b2b94eaa2e7d52c7fc1af1350f8c3885e616242707147982c717348f92c11 -> SensitiveDatumIsNeverPreserved
// - CRASH-DIAGNOSTIC-PRIVACY-001@sha256:1b3b2b94eaa2e7d52c7fc1af1350f8c3885e616242707147982c717348f92c11 -> ShareableDiagnosticDatumIsPreserved
//
// Scope:
// RawDatum represents a token class recognized by the shareable-report
// sanitization contract. The model verifies which classes may survive verbatim
// versus which must be replaced by a redacted representation. Regex matching,
// stack-trace construction, and platform exit-report APIs remain implementation
// concerns covered by unit and architecture tests.

module crash_diagnostic_privacy

abstract sig DiagnosticKind {}
one sig HttpUrl, GenericUriQuery, EmailAddress, CredentialAssignment,
  BearerToken, AndroidPrivatePath extends DiagnosticKind {}
one sig Version, Commit, Sdk, Device, ProcessName, ProcessId,
  ExitReason, MemoryMetric extends DiagnosticKind {}

abstract sig Representation {}
one sig Preserved, Redacted extends Representation {}

sig RawDatum {
  kind: one DiagnosticKind
}

sig ReportDatum {
  source: one RawDatum,
  representation: one Representation
}

one sig ShareableReport {
  contents: set ReportDatum
}

fun sensitiveKinds: set DiagnosticKind {
  HttpUrl + GenericUriQuery + EmailAddress + CredentialAssignment +
  BearerToken + AndroidPrivatePath
}

fun shareableKinds: set DiagnosticKind {
  Version + Commit + Sdk + Device + ProcessName + ProcessId +
  ExitReason + MemoryMetric
}

fact CompleteSanitizedProjection {
  ReportDatum = ShareableReport.contents
  all raw: RawDatum |
    one output: ReportDatum | output.source = raw
}

fact SensitiveDataIsRedacted {
  all output: ReportDatum |
    output.source.kind in sensitiveKinds implies output.representation = Redacted
}

fact HighLevelDiagnosticsArePreserved {
  all output: ReportDatum |
    output.source.kind in shareableKinds implies output.representation = Preserved
}

assert EveryRawDatumHasOneSanitizedOutput {
  all raw: RawDatum |
    one output: ShareableReport.contents | output.source = raw
}

assert SensitiveDatumIsNeverPreserved {
  no output: ShareableReport.contents |
    output.source.kind in sensitiveKinds and output.representation = Preserved
}

assert ShareableDiagnosticDatumIsPreserved {
  all output: ShareableReport.contents |
    output.source.kind in shareableKinds implies output.representation = Preserved
}

check EveryRawDatumHasOneSanitizedOutput for 10 expect 0
check SensitiveDatumIsNeverPreserved for 10 expect 0
check ShareableDiagnosticDatumIsPreserved for 10 expect 0

run RepresentativeSanitization {
  some sensitive: RawDatum | sensitive.kind = HttpUrl
  some allowed: RawDatum | allowed.kind = ExitReason
} for 10 expect 1
