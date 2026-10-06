// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - AUDIO-PLAYBACK-BOUNDARY-001@sha256:36d52906e8d0111a7bf31b52e93597de845c93ea49d82718b27ad016326e13c4 -> QueueHasUniqueContent
// - AUDIO-PLAYBACK-BOUNDARY-001@sha256:36d52906e8d0111a7bf31b52e93597de845c93ea49d82718b27ad016326e13c4 -> AudioOwnsNoDurableUserState
// - AUDIO-PLAYBACK-BOUNDARY-001@sha256:36d52906e8d0111a7bf31b52e93597de845c93ea49d82718b27ad016326e13c4 -> AudioOwnsNoContentOrCurationCommand
// - AUDIO-PLAYBACK-BOUNDARY-001@sha256:36d52906e8d0111a7bf31b52e93597de845c93ea49d82718b27ad016326e13c4 -> GeneratedAudioIsRegenerableCache
// - AUDIO-PLAYBACK-BOUNDARY-001@sha256:36d52906e8d0111a7bf31b52e93597de845c93ea49d82718b27ad016326e13c4 -> GeneratedAudioIsNotBackupOrExport
//
// Scope:
// The model represents Audio ownership boundaries and queue identity, not
// Media3/TTS timing. Queue entries and playback position are runtime state.
// Reading/requesting Summary data is intentionally outside the modeled
// Content/Curation mutation boundary.

module audio_playback_boundary

sig ContentIdentity {}

sig QueueEntry {
  content: one ContentIdentity
}

abstract sig Owner {}
one sig AudioOwner, ContentOwner, CurationOwner extends Owner {}

abstract sig DurableUserState {
  owner: one Owner
}
sig ContentReadingState extends DurableUserState {}
sig CurationMembershipState extends DurableUserState {}

abstract sig CommandCapability {
  owner: one Owner
}
one sig ContentReadingCommand, CurationMembershipCommand extends CommandCapability {}

sig AudioRuntimeState {}

sig AudioCacheArtifact {
  regenerable: one Bool,
  backedUp: one Bool,
  exported: one Bool
}

abstract sig Bool {}
one sig Yes, No extends Bool {}

fact QueueIdentity {
  all identity: ContentIdentity |
    lone { entry: QueueEntry | entry.content = identity }
}

fact DurableOwnership {
  all state: ContentReadingState | state.owner = ContentOwner
  all state: CurationMembershipState | state.owner = CurationOwner
  no state: DurableUserState | state.owner = AudioOwner
}

fact CommandOwnership {
  ContentReadingCommand.owner = ContentOwner
  CurationMembershipCommand.owner = CurationOwner
  no command: CommandCapability | command.owner = AudioOwner
}

fact AudioCachePolicy {
  all artifact: AudioCacheArtifact |
    artifact.regenerable = Yes and
    artifact.backedUp = No and
    artifact.exported = No
}

assert QueueHasUniqueContent {
  all identity: ContentIdentity |
    lone { entry: QueueEntry | entry.content = identity }
}

assert AudioOwnsNoDurableUserState {
  no state: DurableUserState | state.owner = AudioOwner
}

assert AudioOwnsNoContentOrCurationCommand {
  no command: CommandCapability | command.owner = AudioOwner
}

assert GeneratedAudioIsRegenerableCache {
  all artifact: AudioCacheArtifact |
    artifact.regenerable = Yes
}

assert GeneratedAudioIsNotBackupOrExport {
  all artifact: AudioCacheArtifact |
    artifact.backedUp = No and artifact.exported = No
}

check QueueHasUniqueContent for 8 expect 0
check AudioOwnsNoDurableUserState for 8 expect 0
check AudioOwnsNoContentOrCurationCommand for 8 expect 0
check GeneratedAudioIsRegenerableCache for 8 expect 0
check GeneratedAudioIsNotBackupOrExport for 8 expect 0

run RepresentativePlaybackBoundary {
  #ContentIdentity = 2
  #QueueEntry = 2
  some AudioRuntimeState
  some AudioCacheArtifact
  some ContentReadingState
  some CurationMembershipState
} for 8 expect 1
