# ADR-0093: main の APK ビルド結果をコミットステータスから追跡可能にする

- Status: Superseded
- Date: 2026-08-18
- Superseded by: [ADR-0200](0200-remove-main-apk-commit-status.md)

## Context

main への push では GitHub Actions が署名済み release APK を生成し、短期間の artifact として保存している。PR では品質確認だけを行い、APK の build job は main でのみ実行する。

一方、コミットから APK を生成した Actions run を機械的に特定する共通の参照点がなかった。artifact 自体をリポジトリへ保存したり永続公開したりせず、既存の短期 artifact 方針を維持したまま、main のどの run に APK が存在するかを追跡可能にする必要がある。

## Decision

main の build job で次の順序を維持する。

1. release APK をビルドする
2. `apksigner` で署名を検証する
3. `mosaic-android-apk` artifact を upload する
4. upload 成功後、対象コミットへ `apk/main` という GitHub commit status を `success` で登録する

`apk/main` の `target_url` は、その APK を生成した GitHub Actions run の URL とする。status は artifact upload 後にのみ作成するため、status が存在することを「その run に署名検証済み APK artifact が作成された」ことの観測点として扱える。

workflow の `GITHUB_TOKEN` 権限は `contents: read` と `statuses: write` に限定する。status へ記録するのは公開済みの commit SHA、固定 context/description、Actions run URL のみとし、署名鍵、パスワード、token、artifact 内容、端末内データなどは含めない。

artifact の保存期間、署名方法、APK ファイル名、main のみで build する方針は変更しない。

## Consequences

### Positive

- main のコミットから APK を生成した Actions run を安定して特定できる。
- artifact の短期保存方針を変えずに、配布処理から対象 run を追跡できる。
- status は upload 成功後に作られるため、単なる build 開始通知より意味が明確になる。
- workflow token の追加権限は commit status の書き込みだけに限定できる。

### Negative

- main build 成功ごとに `apk/main` の commit status が1件追加される。
- GitHub Status API への書き込みが失敗すると build job も失敗扱いになる。ただし APK artifact の upload 自体はその前に完了している。

## Relationship to existing ADRs

- ADR-0038: Android のテスト層とCI検証方針を維持する。本ADRは main build artifact の観測性だけを追加する。
- ADR-0055: 新規ADRとして現在の最大番号より大きい 0093 を使用する。
- ADR-0200: `apk/main` commit status を不要と判断し、本 ADR の決定を廃止する。
