# Architecture Decision Log

`docs/adr/` は、このアプリで行った設計判断の履歴を保存する。

現在のアーキテクチャを理解したい場合は、ADR を番号順にすべて読むのではなく、まず [`../architecture/README.md`](../architecture/README.md) から現在形を確認し、理由・代替案・移行経緯が必要な箇所だけ根拠 ADR へ遡る。

## ADR and current architecture docs

```text
architecture/*.md
  「現在どう設計するか」
       |
       | Sources
       v
adr/*.md
  「なぜその判断をしたか」
```

ADR を後から現在形へ書き換えることは避け、後続判断で変更する場合は新しい ADR の `Supersedes` / `Amends` / `Refines` 等で関係を示す。現在形の集約は `docs/architecture/` を更新する。

## Core architecture source set

### Layer / module / ownership

- [ADR-0001: UI・Domain・Data レイヤの責務を分離する](0001-layered-architecture.md)
- [ADR-0002: 関数・class・interface の境界](0002-function-class-interface-boundaries.md)
- [ADR-0003: Feature-first のマルチモジュール構成](0003-multi-module-architecture.md)
- [ADR-0004: 安定した共有概念の concept-oriented ownership](0004-concept-oriented-modules.md)
- [ADR-0046: アーキテクチャ制約を CI で自動検証する](0046-automated-architecture-verification.md)
- [ADR-0063: feature UI ownership cleanup](0063-feature-ui-ownership-cleanup.md)
- [ADR-0101: feature route と background runtime ownership](0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0116: route-owned root ViewModel wiring](0116-route-owned-root-viewmodel-wiring.md)
- [ADR-0120: Bookmark application service / framework provider boundary](0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0125: Application Service と capability interface を責務境界として使う](0125-application-service-and-capability-segregation.md)
- [ADR-0127: Health Connect を読み取り専用 Health Context の外部データソースとする](0127-health-connect-read-only.md)
- [ADR-0128: Calendar を複数 Context と Android Calendar Provider の read model として扱う](0128-calendar-read-model-and-android-calendar-provider.md)
- [ADR-0131: Workout を source of truth として Health Connect へ一方向 export する](0131-workout-health-connect-export.md)
- [ADR-0139: app entry point と Worker runtime ownership](0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0142: app Route の presentation ownership と Task widget 更新境界を整理する](0142-app-route-and-task-widget-ownership-cleanup.md)
- [ADR-0144: composition runtime group と module map 機械検証を導入する](0144-composition-runtime-groups-and-module-map-verification.md)
- [ADR-0146: WorkManager Worker の依存解決を WorkerFactory constructor injection へ移す](0146-workmanager-worker-factory-injection.md)
- [ADR-0147: app composition は選択中タブに必要な ViewModel だけ起動する](0147-active-tab-viewmodel-activation.md)
- [ADR-0150: app shell navigation state を app UI ownership へ収束する](0150-app-shell-navigation-ui-ownership.md)
- [ADR-0152: Library Route と route runtime ownership を整理する](0152-library-route-and-route-runtime-ownership-cleanup.md)
- [ADR-0155: HTTP transport を application scope で共有する](0155-application-scope-http-transport.md)
- [ADR-0156: active tab の message capability policy を navigation spec に集約する](0156-active-tab-message-capability-policy.md)
- [ADR-0160: Worker runtime ownership と Android 17 baseline を現行実装へ収束させる](0160-worker-runtime-and-android-17-baseline-cleanup.md)
- [ADR-0161: Android 17 の main-process memory limit を実行元と相関できる診断にする](0161-android17-main-process-memory-diagnostics.md)
- [ADR-0162: current architecture cleanup の残存境界を guardrail 化する](0162-current-architecture-cleanup-guardrails.md)
- [ADR-0164: owner boundary と main quality gate の残存 P1 を収束する](0164-p1-owner-boundary-and-main-quality-gate.md)
- [ADR-0197: PR quality checks と main APK build を分離する](0197-split-pr-checks-and-main-apk-build.md)
- [ADR-0165: 単発テキスト推論を provider 非依存 capability として分離する](0165-provider-neutral-text-inference-contract.md)
- [ADR-0166: LAN Web と Route composition の責務を分割する](0166-lan-web-and-route-composition-responsibility-split.md)
- [ADR-0167: 共通 dependency version を Gradle version catalog へ集約する](0167-gradle-version-catalog-baseline.md)
- [ADR-0168: ChatGPT OAuth と Codex Responses を隔離した cloud debug adapter として導入する](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171: Summary の Local / ChatGPT routing と URL 起点の cloud Web 取得を分離する](0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172: AI provider 設定・task routing・Local / Cloud runtime control を分離する](0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175: Knowledge Wiki の Local / ChatGPT 実行先を明示選択する](0175-knowledge-local-chatgpt-routing.md)

### Domain / Context / persistence

- [ADR-0047: Feature-owned database schema contribution](0047-feature-owned-database-schema-contributions.md)
- [ADR-0098: durable user data を単一 DB へ統合する](0098-unified-user-database.md)
- [ADR-0106: Domain context・Aggregate・persistence ownership](0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117: cross-context persistence boundary phase 1](0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119: Content Classification・Retention・table ownership enforcement](0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0123: Content / Curation 永続化境界の第二段階を完了する](0123-content-curation-persistence-phase2.md)
- [ADR-0128: Calendar を複数 Context と Android Calendar Provider の read model として扱う](0128-calendar-read-model-and-android-calendar-provider.md)
- [ADR-0131: Workout を source of truth として Health Connect へ一方向 export する](0131-workout-health-connect-export.md)
- [ADR-0138: database version 27 を更新・バックアップ互換性の基準とする](0138-database-v27-compatibility-baseline.md)

### Documentation / repository governance

- [ADR-0055: ADR 番号を一意な単調増加番号として管理する](0055-adr-numbering-policy.md)
- [ADR-0122: ADR を根拠とする current architecture documentation を維持する](0122-current-architecture-documentation.md)
- [ADR-0136: 公開リポジトリの高確度な秘密情報を CI で検査する](0136-public-repository-content-verification.md)
- [ADR-0144: composition runtime group と module map 機械検証を導入する](0144-composition-runtime-groups-and-module-map-verification.md)
- [ADR-0149: 共有可能なクラッシュ診断を保存前にサニタイズする](0149-sanitize-shareable-crash-diagnostics.md)
- [ADR-0151: current architecture の互換 redirect は参照移行後に廃止する](0151-retire-current-architecture-compatibility-redirects.md)
- [ADR-0157: Mosaic の外部識別子と互換識別子を区別する](0157-mosaic-external-and-compatibility-identifiers.md)
- [ADR-0162: current architecture cleanup の残存境界を guardrail 化する](0162-current-architecture-cleanup-guardrails.md)
- [ADR-0164: owner boundary と main quality gate の残存 P1 を収束する](0164-p1-owner-boundary-and-main-quality-gate.md)
- [ADR-0166: LAN Web と Route composition の責務を分割する](0166-lan-web-and-route-composition-responsibility-split.md)
- [ADR-0167: 共通 dependency version を Gradle version catalog へ集約する](0167-gradle-version-catalog-baseline.md)
- [ADR-0168: ChatGPT OAuth と Codex Responses を隔離した cloud debug adapter として導入する](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171: Summary の Local / ChatGPT routing と URL 起点の cloud Web 取得を分離する](0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172: AI provider 設定・task routing・Local / Cloud runtime control を分離する](0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175: Knowledge Wiki の Local / ChatGPT 実行先を明示選択する](0175-knowledge-local-chatgpt-routing.md)

## Supporting architecture areas

### Platform / Android runtime

- [ADR-0126: Android 14 を最小プラットフォーム基準とする](0126-android-platform-baseline.md)
- [ADR-0139: app entry point と Worker runtime ownership](0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0145: Android 画像推論の Engine 寿命をメモリ安全性のため制限する](0145-bound-vision-inference-memory-lifetime.md)
- [ADR-0146: WorkManager Worker の依存解決を WorkerFactory constructor injection へ移す](0146-workmanager-worker-factory-injection.md)
- [ADR-0149: 共有可能なクラッシュ診断を保存前にサニタイズする](0149-sanitize-shareable-crash-diagnostics.md)
- [ADR-0153: タスクウィジェット起動時はタスクタブを開く](0153-task-widget-open-task-tab.md)
- [ADR-0155: HTTP transport を application scope で共有する](0155-application-scope-http-transport.md)
- [ADR-0157: Mosaic の外部識別子と互換識別子を区別する](0157-mosaic-external-and-compatibility-identifiers.md)
- [ADR-0159: SMB 画像推論を短寿命の専用プロセスへ隔離する](0159-isolate-smb-vision-inference-process.md)
- [ADR-0160: Worker runtime ownership と Android 17 baseline を現行実装へ収束させる](0160-worker-runtime-and-android-17-baseline-cleanup.md)
- [ADR-0161: Android 17 の main-process memory limit を実行元と相関できる診断にする](0161-android17-main-process-memory-diagnostics.md)
- [ADR-0163: WebView renderer 終了を app process 障害と分離して復旧する](0163-webview-renderer-exit-recovery.md)
- [ADR-0166: LAN Web と Route composition の責務を分割する](0166-lan-web-and-route-composition-responsibility-split.md)

### Health

- [ADR-0127: Health Connect を読み取り専用 Health Context の外部データソースとする](0127-health-connect-read-only.md)
- [ADR-0131: Workout を source of truth として Health Connect へ一方向 export する](0131-workout-health-connect-export.md)
- [ADR-0140: Health の期間別表示と運動時間帯の活動量表示を整理する](0140-health-presentation-and-session-activity.md)

### Calendar

- [ADR-0128: Calendar を複数 Context と Android Calendar Provider の read model として扱う](0128-calendar-read-model-and-android-calendar-provider.md)

### Library

- [ADR-0065: SMB 蔵書と組み込み Book Reader を分離して提供する](0065-smb-library-and-built-in-book-reader.md)
- [ADR-0108: 蔵書整理を Library 所有の metadata と AI suggestion に分離する](0108-library-organization-and-ai-suggestions.md)
- [ADR-0133: SMB 蔵書の表紙取得を永続バックグラウンドキューへ分離する](0133-smb-cover-prefetch-queue.md)
- [ADR-0134: SMB 書籍の書誌・ファイル名正規化をマルチモーダル候補レビューとして扱う](0134-smb-multimodal-metadata-normalization.md)
- [ADR-0135: SMB 表紙キャッシュをバックアップ復元後に再関連付けする](0135-smb-cover-cache-backup-restore.md)
- [ADR-0141: SMB 書籍の読書開始を明示的な application callback で接続する](0141-explicit-smb-book-open-routing.md)
- [ADR-0143: Web URL を Library source として扱い Bookmark と安全に移動する](0143-web-library-source-and-bookmark-transfer.md)
- [ADR-0145: Android 画像推論の Engine 寿命をメモリ安全性のため制限する](0145-bound-vision-inference-memory-lifetime.md)
- [ADR-0152: Library Route と route runtime ownership を整理する](0152-library-route-and-route-runtime-ownership-cleanup.md)
- [ADR-0154: Web Library metadata は静的 HTTP を優先し WebView を不足時の fallback とする](0154-web-library-rendered-metadata-fallback.md)
- [ADR-0158: Book Reader の page geometry metadata cache を上限付きにする](0158-bounded-book-page-geometry-cache.md)
- [ADR-0159: SMB 画像推論を短寿命の専用プロセスへ隔離する](0159-isolate-smb-vision-inference-process.md)
- [ADR-0160: Worker runtime ownership と Android 17 baseline を現行実装へ収束させる](0160-worker-runtime-and-android-17-baseline-cleanup.md)
- [ADR-0163: WebView renderer 終了を app process 障害と分離して復旧する](0163-webview-renderer-exit-recovery.md)

### Background / AI runtime

- [ADR-0006: durable background sync](0006-durable-background-sync.md)
- [ADR-0020: local AI runtime options](0020-local-ai-runtime-options.md)
- [ADR-0056: feature-owned local AI policies](0056-feature-owned-local-ai-policies.md)
- [ADR-0069: unified AI model settings and task queue](0069-unified-ai-model-settings-and-task-queue.md)
- [ADR-0071: prioritized background AI task scheduling](0071-prioritized-background-ai-task-scheduling.md)
- [ADR-0079: process-wide local AI inference sessions](0079-process-wide-local-ai-inference-sessions.md)
- [ADR-0104: AI task queue feature ownership](0104-ai-task-queue-feature-ownership.md)
- [ADR-0133: SMB 蔵書の表紙取得を永続バックグラウンドキューへ分離する](0133-smb-cover-prefetch-queue.md)
- [ADR-0134: SMB 書籍の書誌・ファイル名正規化をマルチモーダル候補レビューとして扱う](0134-smb-multimodal-metadata-normalization.md)
- [ADR-0137: 旧 Summary 実行設定の一度限り migration を終了する](0137-retire-summary-execution-preference-migration.md)
- [ADR-0139: app entry point と Worker runtime ownership](0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0145: Android 画像推論の Engine 寿命をメモリ安全性のため制限する](0145-bound-vision-inference-memory-lifetime.md)
- [ADR-0146: WorkManager Worker の依存解決を WorkerFactory constructor injection へ移す](0146-workmanager-worker-factory-injection.md)
- [ADR-0148: local model revision marker の互換 migration を終了する](0148-retire-local-model-revision-marker-migration.md)
- [ADR-0155: HTTP transport を application scope で共有する](0155-application-scope-http-transport.md)
- [ADR-0159: SMB 画像推論を短寿命の専用プロセスへ隔離する](0159-isolate-smb-vision-inference-process.md)
- [ADR-0160: Worker runtime ownership と Android 17 baseline を現行実装へ収束させる](0160-worker-runtime-and-android-17-baseline-cleanup.md)
- [ADR-0161: Android 17 の main-process memory limit を実行元と相関できる診断にする](0161-android17-main-process-memory-diagnostics.md)
- [ADR-0164: owner boundary と main quality gate の残存 P1 を収束する](0164-p1-owner-boundary-and-main-quality-gate.md)
- [ADR-0165: 単発テキスト推論を provider 非依存 capability として分離する](0165-provider-neutral-text-inference-contract.md)
- [ADR-0168: ChatGPT OAuth と Codex Responses を隔離した cloud debug adapter として導入する](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171: Summary の Local / ChatGPT routing と URL 起点の cloud Web 取得を分離する](0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172: AI provider 設定・task routing・Local / Cloud runtime control を分離する](0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175: Knowledge Wiki の Local / ChatGPT 実行先を明示選択する](0175-knowledge-local-chatgpt-routing.md)

### Content / summary / knowledge

- [ADR-0078: content type inheritance](0078-content-type-inheritance-for-rss-articles.md)
- [ADR-0092: Summary と Bookmark metadata generation の分離](0092-separate-summary-and-bookmark-metadata-generation.md)
- [ADR-0105: summary content preparation pipeline](0105-summary-content-preparation-pipeline.md)
- [ADR-0109: generated Knowledge wiki](0109-generated-knowledge-wiki.md)
- [ADR-0113: Knowledge page lifecycle management](0113-knowledge-page-lifecycle-management.md)
- [ADR-0125: Application Service と capability interface を責務境界として使う](0125-application-service-and-capability-segregation.md)
- [ADR-0164: owner boundary と main quality gate の残存 P1 を収束する](0164-p1-owner-boundary-and-main-quality-gate.md)
- [ADR-0165: 単発テキスト推論を provider 非依存 capability として分離する](0165-provider-neutral-text-inference-contract.md)
- [ADR-0171: Summary の Local / ChatGPT routing と URL 起点の cloud Web 取得を分離する](0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172: AI provider 設定・task routing・Local / Cloud runtime control を分離する](0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175: Knowledge Wiki の Local / ChatGPT 実行先を明示選択する](0175-knowledge-local-chatgpt-routing.md)

この索引は「現在の architecture source set」を優先した案内であり、全 ADR の機能別目録ではない。特定 feature の設計履歴は `docs/adr/` の番号順ファイルまたは repository search から辿る。

## Numbering and integrity

ADR の番号は4桁の一意な単調増加番号とする。新しい ADR は現在存在する最大番号より大きい番号を使う。

ローカル検査:

```bash
python3 -m unittest scripts.test_verify_adr_integrity
python3 scripts/verify_adr_integrity.py
```

検査は filename/header の番号一致、番号重複、存在しない ADR 参照、壊れた ADR link に加え、`docs/architecture/*.md` と `docs/spec.md` からの ADR reference / link target も検出する。意味的に正しい ADR を参照しているかはレビューで確認する。

## Public repository rule

ADR には設計判断に必要な情報だけを記録し、credential、token、OAuth secret、実ユーザー URL、メールアドレス、個人データ、公開を意図しない endpoint 等を含めない。

高確度な credential / private artifact は `scripts/verify_public_repository.py` でも検査するが、意味的な個人情報レビューは引き続き必須とする。ProfilingManager trace や heap dump 等の診断 artifact も repository へ追加しない。

## Sources

- [ADR-0055](0055-adr-numbering-policy.md)
- [ADR-0122](0122-current-architecture-documentation.md)
- [ADR-0136](0136-public-repository-content-verification.md)
- [ADR-0150](0150-app-shell-navigation-ui-ownership.md)
- [ADR-0151](0151-retire-current-architecture-compatibility-redirects.md)
- [ADR-0152](0152-library-route-and-route-runtime-ownership-cleanup.md)
- [ADR-0155](0155-application-scope-http-transport.md)
- [ADR-0156](0156-active-tab-message-capability-policy.md)
- [ADR-0157](0157-mosaic-external-and-compatibility-identifiers.md)
- [ADR-0158](0158-bounded-book-page-geometry-cache.md)
- [ADR-0159](0159-isolate-smb-vision-inference-process.md)
- [ADR-0160](0160-worker-runtime-and-android-17-baseline-cleanup.md)
- [ADR-0161](0161-android17-main-process-memory-diagnostics.md)
- [ADR-0162](0162-current-architecture-cleanup-guardrails.md)
- [ADR-0163](0163-webview-renderer-exit-recovery.md)
- [ADR-0164](0164-p1-owner-boundary-and-main-quality-gate.md)
- [ADR-0165](0165-provider-neutral-text-inference-contract.md)
- [ADR-0166](0166-lan-web-and-route-composition-responsibility-split.md)
- [ADR-0167](0167-gradle-version-catalog-baseline.md)
- [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175](0175-knowledge-local-chatgpt-routing.md)
