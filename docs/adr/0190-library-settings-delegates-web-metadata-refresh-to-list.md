# ADR-0190: Web 蔵書 metadata 再取得操作を蔵書一覧へ集約する

- Status: Accepted
- Date: 2026-08-27
- Supersedes in part: [ADR-0176](0176-web-library-extractor-result-diagnostics.md)
- Supersedes in part: [ADR-0179](0179-web-library-thumbnail-preview-and-foreground-gating.md)

## Context

Web 蔵書の metadata 再取得は当初、蔵書設定の Web 蔵書セクションに次の UI を持っていた。

- metadata が不足している蔵書の一覧
- 個別の再取得
- 一括再取得
- 再取得の進捗
- 直近の再取得結果と詳細診断
- 保存済み thumbnail の診断 preview

その後、通常の蔵書一覧の各 Web 蔵書から個別に metadata 再取得を開始でき、同じ `WebLibraryRefreshUiState` による待機中・取得中・完了状態も一覧上で確認できるようになった。

この状態では設定画面の再取得操作と対象一覧が通常一覧の操作と重複する。設定画面には custom metadata extractor のルール管理という固有の責務もあるため、再取得 UI を併置し続けると設定画面の責務と表示量が増える。

## Decision

### metadata 再取得は通常の蔵書一覧から行う

Web 蔵書の個別 metadata 再取得は通常の蔵書一覧にある各書籍の操作メニューから行う。

設定画面から次を削除する。

- `metadata 再取得` セクション
- 一括再取得ボタン
- metadata 不足蔵書の対象一覧
- 再取得進捗表示
- `直近の再取得結果` セクション
- 設定画面専用の thumbnail 診断 preview

通常一覧にある個別再取得操作と簡潔な status 表示は維持する。再取得処理そのもの、custom extractor の実行、foreground Activity が必要な WebView 取得を待機する仕組みは変更しない。

### Web 蔵書設定は取得ルール管理に集中する

Web 蔵書設定には、サイト別のタイトル・サムネイル取得ルールの追加・編集・削除を残す。

取得ルールの編集可否は設定画面自身のルール読み書き処理中かどうかだけで決める。設定画面から再取得を開始しなくなるため、設定画面のルール管理 UI が `refreshState.running` に依存する必要はない。

### transient な再取得状態は一覧表示のため維持する

`WebLibraryRefreshUiState` と item status は、通常一覧で再取得の進行・結果を簡潔に示すため引き続き route/composition 内の transient state として扱う。

ADR-0176 が決定した custom extractor execution の transient diagnostic data や WebView timeout 時の execution snapshot の伝播は変更しない。ただし ADR-0176 のうち「設定画面に直近結果を独立表示する」「初期 10 件を表示して展開する」「詳細診断は設定画面だけに表示する」という UI 決定は本 ADR で置き換える。

ADR-0179 の browser-compatible thumbnail loader と foreground gate は通常一覧および metadata 再取得処理の共通境界として維持する。ただし ADR-0179 のうち、設定画面の再取得対象カードに thumbnail 診断 preview を表示すること、および設定画面から一括再取得を開始することを前提とした UI 決定は本 ADR で置き換える。

## Consequences

- 蔵書一覧と設定画面に重複していた metadata 再取得操作がなくなる。
- Web 蔵書設定は custom metadata extractor のルール管理に集中する。
- 一括再取得と直近結果の詳細一覧は設定画面から利用できなくなる。
- 個別再取得の進行状態と簡潔な結果は通常一覧で引き続き確認できる。
- metadata 取得 pipeline、custom extractor contract、WebView security boundary、foreground gate、thumbnail request policy は変更しない。
- database、backup schema、telemetry、永続 job state への変更はない。

## Verification

- Web 蔵書設定に `metadata 再取得`、一括再取得、再取得対象一覧、`直近の再取得結果` が表示されないことを確認する。
- Web 蔵書設定から取得ルールの追加・編集・削除を引き続き行えることを確認する。
- 通常の蔵書一覧で Web 蔵書の `metadataを再取得` 操作が残っていることを確認する。
- 一覧用 refresh status の unit test を継続する。
- 設定画面専用だった直近結果の折りたたみロジックと test を削除する。
- feature/library/ui の unit test とコンパイルを CI で確認する。
- PR 前に public repository、既存 architecture、test scope、documentation の独立レビューを行う。
