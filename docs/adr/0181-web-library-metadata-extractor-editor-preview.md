# ADR-0181: Web Library metadata extractor 編集を bottom sheet 化し、保存前実行テストを提供する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0173](0173-web-library-custom-metadata-extractors.md)
- Refines: [ADR-0176](0176-web-library-extractor-result-diagnostics.md)
- Refines: [ADR-0177](0177-web-library-early-custom-extraction-and-rule-timeout.md)

## Context

ADR-0173 では Web Library の custom metadata extractor rule を Library 設定から追加・編集・削除することを決定した。現行 UI は URL pattern、timeout、function code を中央の `AlertDialog` で編集する。

function code は複数行になり、サイトごとの DOM 構造を確認しながら調整するため、中央 modal dialog は編集領域が狭い。また、保存後に蔵書の metadata 再取得を実行しないと rule の URL pattern、JavaScript contract、実際に取得した title / thumbnailUrl を確認できないため、調整の反復に不要な永続化が入る。

一方、実行テストのために編集中 rule を一時保存すると、テスト中断やプロセス終了時に未確定の URL / function code が durable user data として残る。公開 repository の制約に加え、backup 対象へドラフト値を混入させない必要がある。

## Decision

### extractor editor は画面下から開く bottom sheet とする

Library 設定の custom metadata extractor の追加・編集は、中央 `AlertDialog` ではなく画面下から開く bottom sheet で表示する。

bottom sheet は次の入力を同じ編集面に保持する。

- URL pattern
- WebView timeout
- function code
- 実行テスト用 URL

function code とテスト結果を縦スクロールで確認できる領域を確保し、保存・キャンセルは bottom sheet 下部から実行する。

### 保存前の draft rule を直接実行テストできるようにする

実行テストは現在の入力中 URL pattern、function code、timeout を使用し、テスト URL を既存の metadata 用専用 WebView で開く。

テスト結果では少なくとも次を表示する。

- rule が一致したか
- custom extractor execution status と診断 message
- custom function が返した title / thumbnailUrl
- standard rendered metadata との merge 後の最終 title / thumbnailUrl
- 最終 thumbnail がある場合の画像 preview

rule がテスト URL に一致しない場合も standard rendered metadata の取得結果を表示し、「rule 不一致」であることを明示する。

### draft rule とテスト結果は永続化しない

実行テストでは durable `WebLibraryMetadataExtractorRepository` を更新しない。Library Data は draft rule だけを返す read-only の一時 repository を生成し、それを既存 `AndroidWebViewLibraryMetadataClient` に渡す。

一時 rule ID、テスト URL、function code、実行結果は次へ保存しない。

- application database
- backup
- telemetry
- 永続ログ
- repository fixture / documentation

実行テスト後に保存ボタンを押した場合だけ、従来の durable extractor repository へ rule を保存する。

### WebView security boundary は既存仕様をそのまま利用する

実行テスト専用の JavaScript bridge や別 WebView capability は追加しない。ADR-0173 / ADR-0177 の metadata 用 `AndroidWebViewLibraryMetadataClient` をそのまま利用するため、HTTPS 制約、専用 profile、file/content access 無効化、mixed content 拒否、third-party Cookie 拒否、timeout、renderer exit handling を維持する。

## Consequences

- 長い function code を編集しやすくなり、編集面とテスト結果を同じ bottom sheet で確認できる。
- 保存前に URL pattern と custom function の挙動を確認でき、誤った rule を durable state へ保存する回数を減らせる。
- テスト自体は実ページ context で user-authored JavaScript を実行するため、ADR-0173 と同じく DOM 変更や同一 origin への network request 等の副作用は起こり得る。
- テスト URL と draft function は transient state のため、アプリ終了時には失われる。
- Library UI から Data 実装を直接参照せず、Domain の `WebLibraryMetadataExtractorTester` capability を app composition root から注入する。

## Verification

- editor の execution status 表示を unit test し、成功、Promise reject、timeout、不正 result を区別する。
- test URL と draft rule が durable extractor repository へ保存されない構造であることを code review する。
- UI module が Library Data implementation へ依存していないことを architecture verification で確認する。
- test / docs には `example.com` の架空 URL だけを使用し、実サイト URL、credential、token、ユーザー固有情報を追加しない。
- Library UI unit test、Library Data unit test、architecture verification、public repository verification、lint、assemble を CI で実行する。
