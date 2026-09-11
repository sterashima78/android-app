# 15. Privacy / security

- 公開リポジトリへcredential、token、OAuth secret、実ユーザーのメールアドレス、健康データ、バックアップ、SMB接続情報等を保存しない。
- fixtureとtest dataには人工データを利用する。
- backup対象のSharedPreferencesはallowlist方式とし、将来追加される値を暗黙に外部backupへ含めない。
- Health Connect由来のread dataをBackup、AI task、外部APIへ流さない。
- AI処理は端末内runtimeを基本とし、任意のアプリ内データアクセス権限をモデルへ与えない。
- custom Video Provider codeには他Contextのcredentialやdatabase accessを公開せず、外部通信はboundedなHTTPS request capabilityに限定する。function code自体をcredential保存場所として扱わない。
- ユーザーがコピーして共有できるクラッシュ診断は保存前にサニタイズし、URL の path/query、メールアドレス、credential-like 値、端末内 private path を伏せる。
