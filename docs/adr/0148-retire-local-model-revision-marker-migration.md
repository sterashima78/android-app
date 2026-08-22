# ADR-0148: local model revision marker の互換 migration を終了する

- Status: Accepted
- Date: 2026-08-23
- Amends: [ADR-0059](0059-current-version-compatibility-baseline.md), [ADR-0060](0060-converge-to-current-persisted-data-formats.md)
- Refines: [ADR-0137](0137-retire-summary-execution-preference-migration.md)

## Context

`LocalModelManager` は現在のモデル artifact を download した際、`model_revision.<modelId>` に catalog の exact artifact revision を保存する。モデルの有効性は artifact size とこの revision marker の一致で判定している。

一方、過去には revision marker 導入前にダウンロード済みだった artifact を継続利用するため、起動時に `migrateLegacyCurrentModelRevisionMarkers()` を実行していた。この処理は marker がないファイルでも size が現在の artifact と一致すれば current revision marker を補完する一度限りの互換処理だった。

ADR-0059 / ADR-0060 は、現在配布中の最新版を次版への更新互換性 baseline とし、収束済みの一度限り migration を runtime に恒久保持しない方針を採っている。current app は model download 時に revision marker を保存するため、この旧 marker 補完は現在の baseline には不要である。

## Decision

### 1. legacy revision marker migration を削除する

`LocalModelManager` の起動時に `migrateLegacyCurrentModelRevisionMarkers()` を実行しない。関数自体も削除する。

### 2. exact artifact revision を唯一の現行 validity marker とする

モデル artifact は次の両方を満たす場合だけ current artifact として扱う。

- file size が catalog の expected size と一致する
- 保存済み `model_revision.<modelId>` が catalog の `artifactRevision` と完全一致する

marker のない artifact を file size だけで current artifact とみなさない。

### 3. marker のない旧 artifact は outdated artifact として扱う

marker がない、または revision が一致しない既存ファイルは `cleanupOutdatedModelArtifacts()` の対象となる。必要な場合は current catalog から再ダウンロードする。

これは古い app から現在版へ直接更新する互換性を維持するための fallback を廃止する意図した挙動であり、現在配布中の最新版から次版への更新契約には影響しない。

## Consequences

### Positive

- current artifact の identity が size 推定ではなく exact revision marker へ一本化される。
- 過去 artifact を誤って current revision として採用する互換 fallback がなくなる。
- `LocalModelManager` の startup compatibility code を削減できる。
- ADR-0060 の current-version compatibility baseline と実装が揃う。

### Negative

- revision marker 導入前の古い app から直接更新し、古い model artifact だけが残るケースでは再ダウンロードが必要になる。
- artifact size が同じでも marker がなければ current artifact として再利用しない。

## Verification

- `CurrentCompatibilityBaselineSourceTest` で退役済み migration が production source に戻らないことを固定する。
- 同テストで exact `artifactRevision` comparison が現行 validity check に残ることを確認する。
- model download は current revision marker を保存する既存経路を維持する。
- 既存 unit tests、release lint、architecture verification を実行する。

## Documentation

- `docs/architecture/principles.md` に一度限り migration の終了基準と local model artifact revision の current baseline を反映する。

## Public repository review

本変更は local model の公開 catalog revision と compatibility code のみを扱う。credential、token、個人データ、private artifact、実ユーザーのモデルファイルは repository に追加しない。
