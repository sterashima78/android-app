# ADR-0181: RSS 固有設定を RSS 設定タブへ集約する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0049](0049-rss-feed-folders-and-management-tab.md), [ADR-0180](0180-rss-custom-web-scraping-rules.md)

## Context

RSS には未読、あとで読む、フィード管理の3タブがあり、フィード管理は購読フィードとフォルダを扱う。一方、ADR-0180 で追加した Web 取得ルールは RSS の取得方式そのものを設定する durable configuration であるにもかかわらず、フィード管理画面の FAB からダイアログを開く導線になっていた。

今後 RSS 固有の設定項目が増える可能性もあり、購読対象の管理と取得方式などの設定を同じ画面上の一時的なダイアログに混在させるより、RSS Context 内に設定の恒常的な置き場所を持つ方が navigation と責務が明確になる。

## Decision

RSS の bottom navigation を次の4タブとする。

1. 未読
2. あとで読む
3. フィード管理
4. 設定

RSS 設定タブは RSS Context が所有する設定の集約先とする。初期実装では ADR-0180 の Web 取得ルール設定をここへ移す。

Web 取得ルール一覧は設定タブの通常コンテンツとして表示し、フィード管理画面の Web 取得ルール FAB と一覧ダイアログは削除する。ルールの追加・編集および保存前の実行テストは ADR-0180 の方針を維持し、bottom sheet editor で行う。

アプリ全体の設定画面とは分離する。RSS 固有の取得・購読動作に関する設定は RSS 設定タブ、バックアップや AI 等の横断設定は従来のアプリ設定画面が所有する。

## Consequences

- RSS 固有設定へ bottom navigation から直接到達できる。
- フィード管理は購読フィードとフォルダの管理に集中する。
- Web 取得ルール一覧が一時ダイアログではなく通常画面になるため、ルール数が増えても一覧性を保ちやすい。
- RSS 固有設定が追加された場合も同じタブへ段階的に配置できる。
- RSS の bottom navigation は4項目になるため、各ラベルは1行に収まる短い名称を維持する。

## Verification

- `RssTab.SETTINGS` と対応する app-level tab が相互変換できることを unit test する。
- RSS 設定タブが RSS section に属し、RSS 用の画面タイトルを持つことを unit test する。
- Web 取得ルールの CRUD と実行テストが設定タブから利用できることを code review / CI で確認する。
- フィード管理画面に Web 取得ルール FAB / 一覧ダイアログが残っていないことを確認する。
- public repository に実利用中の URL pattern や function code を追加しないという ADR-0180 の制約を維持する。
