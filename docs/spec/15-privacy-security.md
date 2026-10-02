# 15. Privacy / security

- 公開リポジトリへcredential、token、OAuth secret、実ユーザーのメールアドレス、健康データ、バックアップ、SMB接続情報等を保存しない。
- fixtureとtest dataには人工データを利用する。
- backup対象のSharedPreferencesはallowlist方式とし、将来追加される値を暗黙に外部backupへ含めない。
- Health Connect由来のread dataをBackup、AI task、外部APIへ流さない。
- AI処理は端末内runtimeを基本とし、任意のアプリ内データアクセス権限をモデルへ与えない。
- RSS推薦は既定で端末内AIを利用する。利用者がクラウドAIを明示選択した場合だけ、RSS推薦に必要な除外条件・学習条件・記事タイトル・除外参考タイトルと直前評価をクラウド推論先へ送信する。記事URL、feed本文、リンク先本文、保存済み要約はRSS推薦の入力へ追加しない。
- custom Video Provider codeには他Contextのcredentialやdatabase accessを公開せず、外部通信はboundedなHTTPS request capabilityに限定する。function code自体をcredential保存場所として扱わない。
- アプリ全体ロックは既定で無効とし、永続化するのは有効フラグだけとする。認証済みsession状態とCustom Tabs遷移markerはprocess内の一時状態として扱う。
<!-- formal-requirement
id: APP-LOCK-SESSION-001
models:
  - spec-models/quint/app_lock_session_lifecycle.qnt
-->
- ロック有効時に未認証ならfeature content、起動時診断、共有・widget等のincoming Intent処理を認証成功まで公開・処理しない。認証成功後は同じsessionを解除状態にする。
- ロック有効時にActivityが通常のbackgroundへ移行した場合は次回表示前に再認証が必要なlocked状態へ戻す。configuration changeと認証prompt自身によるlifecycle遷移では不要な再ロックを行わない。
- アプリ自身がCustom Tabs起動を開始した直後は、10秒以内の最初のonStopだけを再ロック対象外にできる。このmarkerはone-shotとし、起動失敗、期限切れ、または一度消費した後のonStopは通常どおり再ロックする。
<!-- /formal-requirement -->
- ユーザーがコピーして共有できるクラッシュ診断は保存前にサニタイズし、URL の path/query、メールアドレス、credential-like 値、端末内 private path を伏せる。
- process終了診断には形式versionと完了markerを含め、low-memory理由と以前のsystem subreasonが併存する場合はその文脈を区別して表示する。これらの追加情報にもユーザーコンテンツやcredentialを含めない。


## 形式モデル

- [Quint: `app_lock_session_lifecycle.qnt`](../../spec-models/quint/app_lock_session_lifecycle.qnt) — app-wide lockのsession状態、通常background再ロック、lifecycle例外、Custom Tabsの10秒one-shot遷移を検査する。
