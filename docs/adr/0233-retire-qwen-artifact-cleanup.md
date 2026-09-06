# ADR-0233: 退役済みQwenモデルの起動時artifact cleanupを終了する

- Status: Accepted
- Date: 2026-09-06
- Supersedes: [ADR-0020](0020-local-ai-runtime-options.md) の旧Qwen artifactを起動時に削除し続ける判断
- Refines: [ADR-0059](0059-current-version-compatibility-baseline.md), [ADR-0060](0060-converge-to-current-persisted-data-formats.md)

## Context

ADR-0020 は2026-08-16にローカルAIをGemma 4 / LiteRT-LMへ統一し、Qwen2.5 0.5B、Qwen2.5 1.5B、Qwen3 4Bをcatalogから削除した。その移行時、旧Qwenモデルをダウンロード済みの端末からストレージを回収するため、`LocalModelManager` は起動時に旧モデルID、旧ファイル名、旧runtime cacheを認識して削除する一度限りの互換処理を持った。

現在のモデルcatalogはGemma 4だけを扱い、旧Qwen IDやファイル名は現行モデルの選択、ダウンロード、推論、検証には利用されない。ADR-0059 / ADR-0060 は現在配布中の更新ベースラインへ収束した後、移行専用runtime codeを恒久的に残さない方針を定めている。

## Decision

`LocalModelManager` から旧Qwen専用の起動時cleanupを削除する。

削除対象は次とする。

- `cleanupRetiredModelArtifacts()`
- `RETIRED_MODEL_IDS`
- `RETIRED_MODEL_FILES`
- `LocalModelManager` 初期化時の旧Qwen cleanup呼び出し

現行Gemmaモデルに対する `cleanupOutdatedModelArtifacts()` は維持する。この処理は現在のcatalogに属するartifactについて、期待サイズと `artifactRevision` が一致しない古い／不完全なファイルを除去する現行validity contractであり、退役済みモデル互換ではない。

現在の更新ベースラインより古いAPKから直接更新し、かつ旧Qwen artifactが端末に残っている状態をこのcleanupで救済することは今後保証しない。旧Qwen artifactの名前を現行runtimeへ保持するだけのために、その更新経路を延命しない。

## Consequences

### Positive

- 現行モデルcatalogが知らない旧Qwen ID・ファイル名をproduction runtimeから削除できる。
- Local AI startupが現在のGemma artifact管理だけを扱う。
- 一度限りの移行処理が恒久的な互換分岐として残らない。

### Negative

- 現在の更新ベースラインを経由せず、旧Qwen artifactだけが残った非常に古い端末では、そのファイルを本処理が自動削除することはなくなる。
- その古いファイルは現行catalogから参照されないため推論には使用されないが、端末ストレージに残る可能性がある。

## Verification

- `CurrentCompatibilityBaselineSourceTest` で `cleanupRetiredModelArtifacts` と3つの旧Qwen model IDがproduction sourceへ戻らないことを固定する。
- 同テストで `cleanupOutdatedModelArtifacts` が残り、現行artifact validity cleanupを維持することを確認する。
- PR CIのPublic repository / Architecture / Test / Lint / R8を通す。

## Public repository review

削除対象は公開済みmodel ID・artifact名のみで、ユーザーデータ、credential、端末固有情報、private pathを新たに追加しない。
