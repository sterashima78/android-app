# ADR-0149: 共有可能なクラッシュ診断を保存前にサニタイズする

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0136](0136-public-repository-content-verification.md), [ADR-0139](0139-app-entrypoint-and-worker-runtime-baseline.md), [ADR-0145](0145-bound-vision-inference-memory-lifetime.md)

## Context

Mosaic は起動時クラッシュを調査できるよう、uncaught exception の stack trace を SharedPreferences に保存し、次回起動時に画面へ表示してコピーできる。

SMB 書籍を内部 URI で開いていた時期のクラッシュでは `yomitori://smb-book/open?...` に server identifier や path が含まれたため、SMB query 専用の redaction を導入していた。

ADR-0145 では Android の memory-related process exit も起動時診断へ追加し、`ApplicationExitInfo.description` と local AI memory diagnostics を同じ共有可能レポートへ含めるようになった。診断面が広がった一方、一般の exception message には HTTP URL の path/query、メールアドレス、credential-like parameter、端末内 private path 等が含まれる可能性があり、SMB 専用 redaction だけでは共有前の保護として不十分である。

## Decision

### 1. crash diagnostic sanitizer を独立した pure component とする

`CrashDiagnosticsSanitizer` を app entry point の診断処理から分離し、文字列を入力してサニタイズ済み文字列を返す pure transformation とする。

### 2. レポート全体を保存前にサニタイズする

uncaught exception report と previous process-exit report は、個別 field を部分的に redaction するのではなく、最終的な report 全体を sanitizer に通してから SharedPreferences に保存する。

これにより、画面表示・clipboard copy・後から追加される diagnostic section は同じ保護境界を通る。

### 3. 診断価値を残しつつ private detail を伏せる

現在の sanitizer は次を対象とする。

- SMB internal URI の query 全体
- HTTP / HTTPS URL の path、query、fragment。scheme と authority は診断用に残す
- その他 URI の query component
- メールアドレス
- `token`、`password`、`secret`、`api_key`、`authorization` 等の assignment value
- Bearer token
- Android app/private storage を示す代表的な absolute path

version、versionCode、commit、SDK、device model、ABI、process-exit reason、PSS / RSS 等の高レベル診断値は維持する。

### 4. sanitizer の test data は人工データに限定する

redaction test では `.invalid` domain、synthetic token、人工 path を利用し、実ユーザー URL、メール、蔵書名、SMB server 情報等を fixture に入れない。

## Consequences

### Positive

- ユーザーがクラッシュ情報をコピーして issue / chat 等へ共有する際の accidental disclosure risk を下げられる。
- ADR-0145 で増えた process-exit / AI memory diagnostics も同じ保護境界に入る。
- SMB 固有 regex に privacy responsibility を集中させず、今後の診断追加でも保存前 sanitizer を再利用できる。

### Negative

- URL path や private file path が原因特定に重要なケースでは診断情報が減る。
- regex ベースの sanitizer は任意の秘密情報を完全に識別できるものではなく、高機密情報を diagnostic report に意図的に追加してよい根拠にはならない。

## Verification

- `StartupCrashStoreTest` で SMB URI、Web URL、メール、credential-like assignment、Bearer token、Android private path を人工値から redaction することを確認する。
- 機密情報を含まない通常の exception text は変更しないことを確認する。
- process-exit classification の既存 test を維持する。
- public repository verifier、unit tests、release lint、architecture verification を実行する。

## Documentation

- `docs/spec.md` の Privacy / security に crash diagnostic sanitization を追加する。
- `docs/architecture/platform.md` に Android process-exit diagnostics と共有境界の現在形を記載する。

## Public repository review

実装と test は人工値だけを含む。credential、token、OAuth secret、実ユーザー URL / メール、SMB connection、実ファイル path、database / backup artifact は追加しない。
