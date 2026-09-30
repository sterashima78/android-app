// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - VIDEO-SAVED-LIFECYCLE-001@sha256:7964be2c12777dfefee7af1548889868aef6a872dddee21dc2875863d7277560 -> deleteFolder
// - VIDEO-SAVED-LIFECYCLE-001@sha256:7964be2c12777dfefee7af1548889868aef6a872dddee21dc2875863d7277560 -> removeSaved
// - VIDEO-SAVED-LIFECYCLE-001@sha256:7964be2c12777dfefee7af1548889868aef6a872dddee21dc2875863d7277560 -> validFolderAssignment
// - VIDEO-SAVED-LIFECYCLE-001@sha256:7964be2c12777dfefee7af1548889868aef6a872dddee21dc2875863d7277560 -> recordedPlaybackIsPreserved
//
// Abstraction:
// The model follows one video and one optional user folder. Playback details are
// reduced to whether playback state has been recorded. Save/folder operations
// may change saved organization but must not erase recorded playback state.

module video_saved_folder_lifecycle {
  var saved: bool
  var folderExists: bool
  var folderAssigned: bool
  var playbackRecorded: bool
  var playbackRecordedEver: bool

  action init = all {
    saved' = false,
    folderExists' = false,
    folderAssigned' = false,
    playbackRecorded' = false,
    playbackRecordedEver' = false,
  }

  action recordPlayback = all {
    saved' = saved,
    folderExists' = folderExists,
    folderAssigned' = folderAssigned,
    playbackRecorded' = true,
    playbackRecordedEver' = true,
  }

  action saveUnclassified = all {
    saved' = true,
    folderExists' = folderExists,
    folderAssigned' = false,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action createFolder = all {
    saved' = saved,
    folderExists' = true,
    folderAssigned' = folderAssigned,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action assignFolder = all {
    saved,
    folderExists,
    saved' = true,
    folderExists' = true,
    folderAssigned' = true,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action removeFromFolder = all {
    saved,
    folderAssigned,
    saved' = true,
    folderExists' = folderExists,
    folderAssigned' = false,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action deleteFolder = all {
    folderExists,
    saved' = saved,
    folderExists' = false,
    folderAssigned' = false,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action removeSaved = all {
    saved,
    saved' = false,
    folderExists' = folderExists,
    folderAssigned' = false,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action idle = all {
    saved' = saved,
    folderExists' = folderExists,
    folderAssigned' = folderAssigned,
    playbackRecorded' = playbackRecorded,
    playbackRecordedEver' = playbackRecordedEver,
  }

  action step = any {
    recordPlayback,
    saveUnclassified,
    createFolder,
    assignFolder,
    removeFromFolder,
    deleteFolder,
    removeSaved,
    idle,
  }

  val validFolderAssignment =
    not(folderAssigned) or (saved and folderExists)

  val unsavedHasNoFolder =
    saved or not(folderAssigned)

  val recordedPlaybackIsPreserved =
    not(playbackRecordedEver) or playbackRecorded

  val safety =
    validFolderAssignment and
    unsavedHasNoFolder and
    recordedPlaybackIsPreserved
}
