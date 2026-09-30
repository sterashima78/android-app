// Natural-language specifications:
// - docs/spec/07-library-reader.md
//
// Covers:
// - WEB-LIBRARY-OWNERSHIP-001@sha256:9452cbe72bd3175f0840b873e1d07e3b92f05d59ec53b2423a000b07cb549c3f -> NoDuplicateDurableOwnership
//
// Scope:
// The model checks stable durable states before or after a move. It does not
// model the implementation ordering used while performing the move.

module web_library_ownership

sig Content {}

sig BookmarkRecord {
  content: one Content
}

sig WebLibraryRecord {
  content: one Content
}

fact StableOwnership {
  no BookmarkRecord.content & WebLibraryRecord.content
}

assert NoDuplicateDurableOwnership {
  no content: Content |
    content in BookmarkRecord.content and content in WebLibraryRecord.content
}

check NoDuplicateDurableOwnership for 4 expect 0

run BookmarkOwnedContent {
  some BookmarkRecord
  no WebLibraryRecord
} for 4 expect 1

run WebLibraryOwnedContent {
  no BookmarkRecord
  some WebLibraryRecord
} for 4 expect 1

run DistinctOwners {
  some BookmarkRecord
  some WebLibraryRecord
} for 4 expect 1
