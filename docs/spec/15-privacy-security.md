# 15. Privacy / security

- 公開リポジトリへcredential、token、OAuth secret、実ユーザーのメールアドレス、健康データ、バックアップ、SMB接続情報等を保存しない。
- fixtureとtest dataには人工データを利用する。
- backup対象のSharedPreferencesはallowlist方式とし、将来追加される値を暗黙に外部backupへ含めない。
- Health Connect由来のread dataをBackup、AI task、外部APIへ流さない。
- AI処理は端末内runtimeを基本とし、任意のアプリ内データアクセス権限をモデルへ与えない。
<!-- formal-requirement
id: RSS-RECOMMENDATION-CLOUD-EGRESS-001
models:
  - spec-models/alloy/rss_recommendation_cloud_egress.als
-->
- RSS推薦は既定で端末内AIを利用する。利用者がクラウドAIを明示選択した場合だけ、RSS推薦に必要な除外条件・学習条件・記事タイトル・除外参考タイトルと直前評価をクラウド推論先へ送信する。記事URL、feed本文、リンク先本文、保存済み要約はRSS推薦の入力へ追加しない。
<!-- /formal-requirement -->
- custom Video Provider codeには他Contextのcredentialやdatabase accessを公開せず、外部通信はboundedなHTTPS request capabilityに限定する。function code自体をcredential保存場所として扱わない。
<!-- formal-requirement
id: CRASH-DIAGNOSTIC-PRIVACY-001
models:
  - spec-models/alloy/crash_diagnostic_privacy.als
-->
- ユーザーが表示・コピーできるcrash / process-exit reportは、raw診断値をそのまま保存せず、最終report全体をsanitizerへ通してからapp-private stateへ保存する。
- HTTP(S) URLのauthority / path / query / fragment、その他URIのquery、メールアドレス、credential-like assignment value、Bearer token、Android private pathは共有reportへraw値のまま残さずredactする。
- version、commit、SDK、device、process name / pid、exit reason、PSS / RSS等の高レベル診断値は共有可能なdiagnosticとして保持できる。sanitizerはdefense-in-depthであり、raw user contentやcredentialを新しい診断sectionへ意図的に追加してよい根拠にはしない。
<!-- /formal-requirement -->
- process終了診断には形式versionと完了markerを含め、low-memory理由と以前のsystem subreasonが併存する場合はその文脈を区別して表示する。これらの追加情報にもユーザーコンテンツやcredentialを含めない。


## 形式モデル

- [Alloy: `crash_diagnostic_privacy.als`](../../spec-models/alloy/crash_diagnostic_privacy.als) — 共有可能なcrash / process-exit reportで、機密token classをredactし、高レベル診断値だけをraw表現のまま保持できる構造を検査する。
- [Alloy: `rss_recommendation_cloud_egress.als`](../../spec-models/alloy/rss_recommendation_cloud_egress.als) — 推薦の既定local実行、cloud選択時の送信allowlist、URL・本文・保存済み要約のcloud非送信を検査する。
