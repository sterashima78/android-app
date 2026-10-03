// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> startSession
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> prepareCurrent
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> skipCurrent
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> finishCurrent
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> resumeWhenPrepared
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> preparedOrderFollowsInput
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> playbackNeverOutrunsPreparation
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> playbackStateIsEphemeral
// - AUDIO-PROGRESSIVE-PLAYBACK-001@sha256:bd72dc07219d192e0f3ab414a45a95d5b4c3d45f1574fa4f1baf195dde066792 -> contentStateIsUnchanged
//
// Abstraction:
// The model represents three input items processed in fixed order. Each item is
// either skipped because no speech text can be obtained or appended after audio
// preparation. Playback may consume prepared items while later inputs are still
// being processed. Concrete TTS files, media-session APIs, summary polling, and
// user seek/speed controls are outside this lifecycle model.

module audio_progressive_playback {
  val IDLE = 0
  val PREPARING = 1
  val PLAYING = 2
  val WAITING = 3
  val COMPLETE = 4
  val STOPPED = 5

  val INPUT_COUNT = 3
  val AFTER_LAST = 4

  var phase: int
  var nextInput: int
  var preparedCount: int
  var playedCount: int
  var lastPreparedIndex: int
  var durablePlaybackStateSaved: bool
  var contentStateChanged: bool

  action init = all {
    phase' = IDLE,
    nextInput' = 1,
    preparedCount' = 0,
    playedCount' = 0,
    lastPreparedIndex' = 0,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action startSession = all {
    phase == IDLE,
    phase' = PREPARING,
    nextInput' = 1,
    preparedCount' = 0,
    playedCount' = 0,
    lastPreparedIndex' = 0,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action prepareCurrent = all {
    phase == PREPARING or phase == PLAYING or phase == WAITING,
    nextInput <= INPUT_COUNT,
    phase' = if (phase == PREPARING or phase == WAITING) PLAYING else phase,
    nextInput' = nextInput + 1,
    preparedCount' = preparedCount + 1,
    playedCount' = playedCount,
    lastPreparedIndex' = nextInput,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action skipCurrent = all {
    phase == PREPARING or phase == PLAYING or phase == WAITING,
    nextInput <= INPUT_COUNT,
    phase' =
      if (nextInput == INPUT_COUNT and (phase == PREPARING or phase == WAITING))
        COMPLETE
      else
        phase,
    nextInput' = nextInput + 1,
    preparedCount' = preparedCount,
    playedCount' = playedCount,
    lastPreparedIndex' = lastPreparedIndex,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action finishCurrent = all {
    phase == PLAYING,
    playedCount < preparedCount,
    phase' =
      if (playedCount + 1 < preparedCount)
        PLAYING
      else if (nextInput <= INPUT_COUNT)
        WAITING
      else
        COMPLETE,
    nextInput' = nextInput,
    preparedCount' = preparedCount,
    playedCount' = playedCount + 1,
    lastPreparedIndex' = lastPreparedIndex,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action resumeWhenPrepared = all {
    phase == WAITING,
    nextInput <= INPUT_COUNT,
    phase' = PLAYING,
    nextInput' = nextInput + 1,
    preparedCount' = preparedCount + 1,
    playedCount' = playedCount,
    lastPreparedIndex' = nextInput,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action skipWhileWaiting = all {
    phase == WAITING,
    nextInput <= INPUT_COUNT,
    phase' = if (nextInput == INPUT_COUNT) COMPLETE else WAITING,
    nextInput' = nextInput + 1,
    preparedCount' = preparedCount,
    playedCount' = playedCount,
    lastPreparedIndex' = lastPreparedIndex,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action stopSession = all {
    phase == PREPARING or phase == PLAYING or phase == WAITING,
    phase' = STOPPED,
    nextInput' = nextInput,
    preparedCount' = preparedCount,
    playedCount' = playedCount,
    lastPreparedIndex' = lastPreparedIndex,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action terminalIdle = all {
    phase == COMPLETE or phase == STOPPED,
    phase' = phase,
    nextInput' = nextInput,
    preparedCount' = preparedCount,
    playedCount' = playedCount,
    lastPreparedIndex' = lastPreparedIndex,
    durablePlaybackStateSaved' = false,
    contentStateChanged' = false,
  }

  action step = any {
    startSession,
    prepareCurrent,
    skipCurrent,
    finishCurrent,
    resumeWhenPrepared,
    skipWhileWaiting,
    stopSession,
    terminalIdle,
  }

  val knownPhase =
    phase == IDLE or phase == PREPARING or phase == PLAYING or
    phase == WAITING or phase == COMPLETE or phase == STOPPED

  val validInputCursor =
    nextInput >= 1 and nextInput <= AFTER_LAST

  val validCounts =
    playedCount >= 0 and
    preparedCount >= 0 and
    playedCount <= preparedCount and
    preparedCount <= INPUT_COUNT

  val preparedOrderFollowsInput =
    lastPreparedIndex >= 0 and
    lastPreparedIndex < nextInput and
    lastPreparedIndex <= INPUT_COUNT

  val playbackNeverOutrunsPreparation =
    playedCount <= preparedCount

  val playingHasPreparedItem =
    not(phase == PLAYING) or playedCount < preparedCount

  val waitingMeansPreparationLag =
    not(phase == WAITING) or
      (playedCount == preparedCount and nextInput <= INPUT_COUNT)

  val completeMeansInputsAndPlaybackConsumed =
    not(phase == COMPLETE) or
      (nextInput == AFTER_LAST and playedCount == preparedCount)

  val playbackStateIsEphemeral =
    not(durablePlaybackStateSaved)

  val contentStateIsUnchanged =
    not(contentStateChanged)

  val safety =
    knownPhase and
    validInputCursor and
    validCounts and
    preparedOrderFollowsInput and
    playbackNeverOutrunsPreparation and
    playingHasPreparedItem and
    waitingMeansPreparationLag and
    completeMeansInputsAndPlaybackConsumed and
    playbackStateIsEphemeral and
    contentStateIsUnchanged
}
