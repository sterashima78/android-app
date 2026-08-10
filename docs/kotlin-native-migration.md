# Kotlinネイティブ移行設計

## 目的

Expo / React Nativeの描画・ジェスチャー処理を廃止し、記事一覧のスワイプ、リスト更新、永続化、ローカルLLMをAndroidネイティブの実装へ統合する。

## 採用構成

| 領域 | 実装 |
| --- | --- |
| UI | Jetpack Compose |
| 状態管理 | ViewModel + StateFlow |
| 一覧 | LazyColumn + `animateItem` |
| スワイプ | Compose pointer input + 描画オフセット |
| 永続化 | SQLiteOpenHelper、既存スキーマ互換 |
| HTTP | OkHttp |
| HTML / XML | jsoup |
| 要約 | MediaPipe Tasks GenAI / LiteRT-LM |
| バックアップ | Storage Access Framework + JSON |

Expo、React Native、JavaScript、Metro、EASに依存するファイルとパッケージは削除する。

## スワイプの状態遷移

1. 指を動かしている間は記事カードの横方向オフセットだけを更新する。
2. 閾値未満で指を離した場合、スプリングアニメーションで原点へ戻す。
3. 閾値を超えた場合、カードを画面外へ移動させる。
4. 記事IDを一時的な非表示集合へ追加し、一覧から即座に除外する。
5. IOスレッドでSQLiteを更新する。
6. 成功時はデータを再読込し、一時状態を破棄する。
7. 失敗時は非表示集合から記事IDを外し、`animateItem`で一覧へ戻す。

SQLite処理の完了を待ってからアニメーションを始めない。連続操作は記事ID単位で独立して処理する。

## データ互換性

従来版と同じアプリID `dev.terashima.yomitorirss`、署名証明書、データベース名 `yomitori-rss.db` を使用する。

署名にはExpoのbare minimumテンプレートとReact Native Communityテンプレートに含まれる同一の公開デバッグ鍵を使用する。これは既存の直接配布APKへ上書き更新するための互換措置であり、本番ストア配布には使用しない。

ネイティブ版の初回起動時に次を実行する。

1. Android標準DB保存先にファイルがある場合は何もしない。
2. `files/SQLite/yomitori-rss.db` がある場合、標準DB保存先へコピーする。
3. `-wal` と `-shm` が存在する場合は併せてコピーする。
4. 既存テーブルを維持したまま、要約キャッシュ用 `article_summaries` を追加する。

## 要約ランタイム

- Qwen2.5の `.task` モデルはMediaPipe Tasks GenAIで実行する。
- Gemma 4の `.litertlm` モデルはLiteRT-LMのKotlin APIで実行する。
- モデル読込と推論はIOスレッドで実行する。
- モデルのダウンロード、選択、削除、メモリ警告、進捗表示をKotlin側へ統合する。
- 生成結果は記事ID、モデルID、生成日時とともにSQLiteへ保存する。

## 残す互換仕様

- Android 10以降
- 縦向き、ダークテーマのみ
- 外部ブラウザーで記事を開く
- RSS 2.0、Atom 1.0、RSS 1.0
- ETag / Last-Modified
- 保存、あとで読む、履歴、タグ
- JSONバックアップと置換復元
- 端末内モデルの選択、ダウンロード、削除、要約進捗

## 検証項目

- 通常閾値未満のスワイプが確定しないこと
- 左右の通常スワイプが正しい操作へ対応すること
- 大きな右スワイプが「あとで読む」を優先すること
- RSS 1.0の名前空間付き日付を解析できること
- Atomの相対URLを絶対URLへ変換できること
- 既存Expo版APKへ上書きインストールできること
- 初回起動時に購読フィード、記事、保存状態、タグを引き継げること
- 連続スワイプ中に一覧操作が停止しないこと
- SQLite更新失敗時に対象記事だけが一覧へ戻ること
