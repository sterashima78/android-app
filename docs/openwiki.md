# リポジトリの OpenWiki

現在のソースコードとテストを調べるための日本語 Wiki を [`../openwiki/`](../openwiki/) に保存する。ユーザー仕様・設計・作業手順の正本は既存の `docs/` と `AGENTS.md` とし、Wiki は根拠へたどるための説明と案内に使う。

## CLI の準備

Node.js 22.22.0 以上と Python 3 が必要。OpenWiki はスタンドアロン CLI として利用し、MCP サーバー、エージェント向け連携・スキルはインストールしない。

```sh
npm install -g openwiki@0.7.1
```

CLI の初回実行時にモデルプロバイダーを選択し、認証する。ホストエージェントの認証を引き継ぐ MCP 連携とは異なり、OpenWiki CLI 自身のプロバイダー設定が必要である。設定と認証情報はリポジトリへコミットしない。

## Wiki の生成と更新

通常の更新では、リポジトリルートから次のコマンドを使う。

```sh
python3 scripts/openwiki_cli.py update
```

初回導入や Wiki 全体を作り直す必要がある場合に限り、次を使う。再初期化は既存の生成ページと Claim を作り直すので、通常は使用しない。

```sh
python3 scripts/openwiki_cli.py init
```

ラッパーはそれぞれ `openwiki code --update --print` / `openwiki code --init --print --language ja` を実行する。根拠追跡、Claim の再検証、変更されたページの選定、Markdown とメタデータの更新は OpenWiki 内蔵エージェントが行い、外部の MCP ツールは使わない。

OpenWiki CLI は毎回 `AGENTS.md` に MCP 向けの管理ブロックを追加・更新する。これをそのまま残すと、このリポジトリの CLI 専用方針と矛盾する。そのためラッパーは実行終了時（失敗時も含む）に、`<!-- OPENWIKI:START -->` と `<!-- OPENWIKI:END -->` で囲まれた自動生成ブロックのみを除去する。人手で保守する指示には触れない。

また、初期化時に CLI が新規作成する `.github/workflows/openwiki-update.yml` はラッパーで削除する。既存の同名ファイルを勝手に削除しない。Wiki 専用の定期更新 workflow は採用しない。CLI を直接呼び出すと管理ブロックや workflow が再生成され得るため、通常の生成・更新にはラッパーを使う。

生成後は `openwiki/INSTRUCTIONS.md` の編集方針と現在の正本に合っているか確認する。ページと根拠の Claim は OpenWiki の生成手順で保存する。`.claims/`、索引、生成履歴、`.last-update.json` は手で編集しない。通常の変更後は初期化ではなく更新を行う。

## 閲覧

[`Wiki の索引`](../openwiki/index.md) から Markdown を読み、必要な関連ページだけを開く。Wiki を利用するために MCP 検索ツールは必要ない。

[`モジュール別の説明`](../openwiki/modules/index.md) は `settings.gradle.kts` に登録された各 Gradle module を対象とする。feature の Domain / Data / UI も個別に説明し、責務・処理の流れ・状態・依存境界と関連する実装・テストへ案内する。全体構成や機能をまたぐ処理は既存の architecture / workflow ページから確認する。

```sh
openwiki visualize
```

グラフ表示のライブラリは公開 CDN から読み込むため、ネットワーク接続が必要。重要な判断はリンク先の現在のソースと正本で確認する。

## Git で管理する範囲

Wiki の Markdown、Claim、生成メタデータ、編集方針、CLI 用ラッパーとテストを管理する。MCP のリポジトリ設定や連携スキルは管理しない。

`openwiki/.run.json` はリポジトリの `.gitignore` で除外する。モデルの認証情報や個人の OpenWiki 設定をリポジトリへ追加しない。

この Wiki は開発用であり、アプリ内の Knowledge 機能がユーザーの資料から生成する Wiki とは別である。
