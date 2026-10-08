# ファイル

- [ChatGPT / Codex 認証とクラウド推論 adapter](ai-cloud-openai.md) - device login、暗号化 credential、モデル選択、自由文・構造化生成と provider failure の正規化を扱う。
- [provider 共通の背景 AI 推論契約](ai-inference.md) - ローカル・クラウド共通の単発推論、モデル情報、構造化 tool 出力と Worker 実行境界を定義する。
- [端末内モデルと推論 subprocess](ai-runtime.md) - LiteRT-LM の端末内モデル管理、生成 session、token 計数と分離 process の lifetime を扱う。
- [背景取得ポリシーとローカル AI の実行調停](background.md) - 機能を横断する通信制約、AI 一時停止設定、優先度付き直列実行を管理する。
- [SQLite 接続・スキーマと永続変更通知](database.md) - 共有 SQLite の接続、機能別スキーマの合成、commit 後通知、整合したスナップショットを提供する。
- [共有 Compose 表現とジェスチャー](designsystem.md) - 一覧 swipe、pull-to-refresh、Markdown と chat 表現を業務状態から分離して共有する。
- [HTTP transport と応答容量制限](network.md) - feature の通信を共通 HTTP 契約へ集約し、共有接続と有限の応答読み込みを提供する。
- [許可 origin に限定した Web 収集 dialog](web-collector.md) - ログインを伴う Web 収集の WebView、message bridge、容量制限と renderer 終了を共通化する。
