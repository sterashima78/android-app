# ADR-0026: Kindle / Audible は実エクスポートの蔵書ファイルを明示選択して取り込む

- Status: Superseded
- Date: 2026-08-12
- Amended: 2026-08-14
- Superseded by: ADR-0057（Kindle）、ADR-0058（Audible）

## Context

この ADR は Amazon Request Your Data の実エクスポートを調査し、汎用的な CSV / ZIP 探索ではなく source ごとの正規ファイルだけを読み込む方針を定めた。

当時は次を正規入力としていた。

- Kindle: `Digital.Content.Ownership*.json` またはそれを含む ZIP
- Audible: `Library.csv` またはそれを含む ZIP

この判断により、Kindle の行動ログや Audible の Listening History / Wishlist など、タイトルを含む別データを蔵書として誤取り込みする問題を避けていた。また大きな Kindle ZIP をストリーミング走査するための安全境界を定めていた。

## Superseded decision

旧実装では source ごとに Amazon Request Your Data のファイル形式を解析し、次の制約を持っていた。

- Kindle ownership JSON の Grant / Revoke を解決して現在所有する書籍を抽出する
- Audible は `Library.csv` だけを蔵書として扱う
- UI で Kindle は JSON / ZIP、Audible は CSV / ZIP を選択可能にする
- 実ユーザーのエクスポート内容や認証情報を保存・ログ・fixture に含めない

## Current decision

2026-08-14 に Kindle / Audible とも Web Library から必要な蔵書メタデータを取得できる方式へ移行した。

Kindle は ADR-0057 に従い、Kindle Web Library から生成する JSON のみを正規入力とする。旧 ownership JSON / ZIP は受け付けない。

Audible は ADR-0058 に従い、Audible Web Library と Catalog API から生成する JSON のみを正規入力とする。旧 `Library.csv` / ZIP は受け付けない。

両方式とも、アプリ自身は Amazon / Audible の Cookie、パスワード、セッショントークンを受け取らない。ブラウザの既存ログイン状態で JSON を生成し、アプリは生成された JSON のみを読み込む。

旧 Amazon Request Your Data importer とその互換コードは後方互換を維持せず削除する。

## Relationship to existing ADRs

- Kindle の現行入力形式・安全境界は ADR-0057 を参照する
- Audible の現行入力形式・安全境界・表紙取得方針は ADR-0058 を参照する
- サービス非依存 `LibraryBook` と source ごとの置換方針は維持する
- 実ユーザーデータを公開リポジトリへ追加しない方針は維持する
