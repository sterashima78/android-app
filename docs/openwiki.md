# リポジトリの OpenWiki

Mosaic のソースコードとテストを調べるための日本語 Wiki を [`../openwiki/`](../openwiki/) に保存する。ユーザー仕様・設計・作業手順の正本は既存の `docs/` と `AGENTS.md` とし、Wiki は根拠へたどるための説明と案内に使う。

## Codex の準備

Node.js 22.22.0 以上で、このリポジトリの連携と同じ OpenWiki バージョンをインストールする。

```sh
npm install -g openwiki@0.7.1
openwiki integrations list --project
```

リポジトリには公式の `.agents/skills/openwiki/` と `.codex/config.toml` を含める。Codex をこのリポジトリで再起動し、`openwiki` MCP が読み込まれたことを確認する。プロジェクト設定の読み込みには Codex の信頼済みプロジェクト設定が必要な場合がある。

Codex 連携はホストのモデルセッションを使うため、OpenWiki 用の別のモデル API キーは不要。単独 CLI の `openwiki --init` / `--update` はモデルプロバイダーの設定を使う別の実行方法である。

## 初期化と更新

Codex へ次のように依頼する。

```text
このリポジトリの OpenWiki を現在のソースとテストから初期化してください。
言語は ja とし、openwiki/INSTRUCTIONS.md の編集方針に従ってください。
```

初期化は既存の生成 Wiki を置き換える。初期化済みの Wiki は次の依頼で更新する。

```text
前回成功した生成からの変更に合わせて、このリポジトリの OpenWiki を更新してください。
既存の正本と現在のソース・テストを確認してください。
```

ページと根拠の Claim は OpenWiki の生成手順で保存する。`.claims/`、索引、生成履歴、`.last-update.json`、生成されたセットアップブロックは手で編集しない。人間が維持する生成方針は [`INSTRUCTIONS.md`](../openwiki/INSTRUCTIONS.md) に記載する。

## 閲覧

[`Wiki の索引`](../openwiki/index.md) から Markdown を読むか、ローカルのグラフ表示を使う。

```sh
openwiki visualize
```

グラフ表示のライブラリは公開 CDN から読み込むため、ネットワーク接続が必要。Codex に具体的な質問をして Wiki を検索・参照させることもできる。重要な判断はリンク先の現在のソースと正本で確認する。

## Git で管理する範囲

Wiki の Markdown、Claim、生成メタデータ、編集方針、Codex 連携を管理する。実行途中の状態は OpenWiki が作る ignore 設定に従う。モデルの認証情報や個人の OpenWiki 設定をリポジトリへ追加しない。

この Wiki は開発用であり、アプリ内の Knowledge 機能がユーザーの資料から生成する Wiki とは別である。
