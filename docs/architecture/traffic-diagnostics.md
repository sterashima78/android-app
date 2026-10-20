# Traffic diagnostics

## Change Impact Brief

目的: RSS更新・動画・生成などで通信量が増えた原因を、機能別・回線別に端末内で分析できるようにする。

Existing capability:
- `core/network/OkHttpHttpClient.kt`: 共通HTTP送受信。
- `feature/video/ui/VideoPlayerMedia.kt`: 動画再生の別通信経路。
- `feature/settings/ui/SettingsScreen.kt`: 設定overlay。

Options:
- OSのUID合計だけ表示: 低コストだが原因を機能別に特定できない。
- ペイロードとメディアの転送量を機能タグ付きで集計: 転送階層の差と一部未計測通信が残るが、機能別原因を絞れる。

選択: 後者。新しい外部送信・権限・Worker・DB schema・feature owner変更なし。追加状態は90日以内の匿名な日次カウンタのみ。保存済みコンテンツや既存user stateの移行不要。通信途中の回線判定、計測漏れ、OS API制約はUIで説明する。

## Current architecture

- `core/network`: 実受信ペイロード・送信bodyの概算量を通知。コルーチンの`TrafficAttribution`でWikiやPodcastなどの生成コンテキストを識別。Android依存を持たない。
- `feature/video/ui`: Media3 `TransferListener`でストリーミング受信量を計測。
- `core/ai-runtime`: モデルartifact取得の受信済みバイト数を計測。失敗・中断時のダウンロード済み量も含める。
- `app/composition`: 端末のアクティブなtransportをWi-Fi / mobile / unknownへ区分し、アプリ専用preferencesへ匿名集計する。独自のネットワーククライアントは追加しない。
- `feature/settings:domain/ui`: read-onlyの診断インターフェースと「通信量の分析」画面。期間7/30/90日、回線別と機能別・日別集計を表示。
- OSの自分自身のUID通信量を`NetworkStatsManager`から取得可能なら比較値として表示。結果が利用不可なら未計測量を断定しない。

## Persistence / privacy

`traffic_diagnostics` SharedPreferencesは日付・機能ラベル・transport・送受信量の集約値だけを格納し、90日を超えた記録は次の通信時に破棄する。バックアップのallowlistに含めない。URL、hostname、headers、body、credential、個別の履歴時刻は収集しない。

## Accuracy

共通HTTPではdecode後のresponse bodyを計測し、ヘッダや下位ネットワークパケットは含めない。動画はMedia3のネットワーク転送byte数、直接ダウンロードは実際に読み込んだbytesを計測する。OS合計は通信階層・WebView等の未計測経路・アプリ更新以前の期間・VPN/通信切替等の事情で差が生じる。差分は未計測の可能性を示す参考値であり、精密な利用量の一致を保証しない。

## Decision
- [ADR-0289](../adr/0289-on-device-network-usage-diagnostics.md)
