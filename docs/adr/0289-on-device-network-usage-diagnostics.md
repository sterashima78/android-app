# ADR-0289: 通信量の機能別・回線別診断

- Status: Accepted
- Date: 2026-10-21
- Refines: [ADR-0155](0155-application-scope-http-transport.md)

## Context
Androidのアプリ全体の通信量からは、記事更新・AI生成・動画・モデル取得のどの処理がデータを使ったか区別できない。原因不明の転送を分析するため、通信内容や利用者情報を収集せずに機能別の転送量を集計する。

## Decision
- 共有HTTPクライアントにベストエフォートの転送量通知と、機能ラベルをコルーチン経由で伝播できる能力を追加する。JVMとAndroidの境界は維持する。
- 動画はMedia3のネットワーク転送listenerから、AIモデル直接取得はダウンロード処理から、転送された受信byteを計測する。
- Android compositionが通信の回線判定、90日の日次集計、OSの自身のUIDの通信量参照を所有する。
- 回線はWi-Fi、モバイル、不明に分類する。VPNや複数transportを一意に決められない場合は不明とする。
- アプリ内のSharedPreferencesに日付・機能・回線・送受信量だけを保持し、backup allowlistへは追加しない。URL、host、path、HTTP本文、認証情報、AI promptは記録しない。
- OS統計は取得できない端末では表示不可とする。取得できてもHTTPペイロード量とIPレイヤーの通信量は一致しないため、差分は参考情報とする。
- 計測のために新しいアクセス権限、background worker、外部通信は追加しない。

## Consequences
- 一部の直接HTTPやWebViewは分類済みの計測から漏れる可能性がある。OS集計との差を表示することでその可能性を確認できる。
- 転送中の回線切替・VPNや圧縮/ヘッダ差により、区分とbyte数に誤差が生じる。
- 新規の診断集計はユーザーコンテンツのsource of truthではない。古い版へロールバックしても他のuser stateに影響しない。

## Verification
- 診断失敗時の通信分離・コルーチンタグ伝播・byte数の扱いのunit test。
- CIのarchitecture / compile / lint / tests。
- 実機でWi-Fi・モバイル・通信エラー・動画・cloud推論・OS差分を確認する。

Current architecture: [traffic-diagnostics.md](../architecture/traffic-diagnostics.md)
