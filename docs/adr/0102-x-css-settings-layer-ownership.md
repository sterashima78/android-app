# ADR-0102: X の CSS 設定を UI・Domain・Data に分離する

- Status: Accepted
- Date: 2026-08-19
- Amends: ADR-0047
- Amended by: ADR-0107

## Context

ADR-0047 では X feature が小規模だったため、カスタム CSS の状態、SharedPreferences 永続化、asset 読み込みを `:feature:x:ui` 内に置き、domain/data module を作らない判断をした。

その後、CSS の有効/無効、3セットの切り替え、セット間コピー、デフォルト復元、WebView 上の要素選択からの CSS 追記が追加され、`XViewerCssSettings.kt` が次の異なる変更理由を同時に持つようになった。

- Compose による設定 UI
- CSS セットの状態遷移と制約
- SharedPreferences の保存形式
- asset からのデフォルト CSS 読み込み

この状態は ADR-0001 の UI / Domain / Data の責務分離、および ADR-0003 の feature-first module 境界と整合しなくなっている。

## Decision

X feature を次の3 module に分ける。

```text
:feature:x:ui
    ↓
:feature:x:domain
    ↑
:feature:x:data
```

### Domain

`:feature:x:domain` は以下を所有する。

- `XViewerCssSettings`
- CSS セット数とセット切り替え・コピーの制約
- `cssForInjection()`
- `XViewerCssRepository` contract
- application-level composition から repository を提供する `XViewerCssRepositoryProvider`

Domain は Android framework に依存しない。

### Data

`:feature:x:data` は以下を所有する。

- `SharedPreferencesXViewerCssRepository`
- SharedPreferences key と保存形式
- デフォルト CSS asset の読み込み

既存端末の保存データをそのまま利用するため、以下の永続化形式は変更しない。

- preferences name: `x_viewer_preferences`
- `custom_css_enabled`
- `active_css_set`
- `custom_css_set_1` 〜 `custom_css_set_3`

デフォルト CSS asset `x_viewer.css` は data module へ移す。

### UI

`:feature:x:ui` は以下に限定する。

- WebView 表示
- Compose 設定 UI
- DOM 要素選択 UI と CSS rule 生成

UI は SharedPreferences や asset stream を直接扱わず `XViewerCssRepository` を通して設定を取得・保存する。

既存の X WebView は Android `Context` を既に必要とするため、段階的移行として application が実装する `XViewerCssRepositoryProvider` から domain contract を解決する薄い adapter を UI module に置く。この adapter は永続化処理や保存形式を所有しない。

### Composition

`:app` の `YomitoriApplication` が `XViewerCssRepositoryProvider` を実装し、`SharedPreferencesXViewerCssRepository` を組み立てる。

## Consequences

### Positive

- CSS の状態遷移を Android 非依存でテストできる
- SharedPreferences の形式変更が Compose UI に波及しない
- UI module から concrete Data 実装への依存を作らずに済む
- X feature が ADR-0001 / ADR-0003 の責務・依存方向に戻る
- 既存の保存 key を維持するため利用者の CSS 設定を失わない

### Negative

- 小規模な X feature に domain/data module が追加される
- WebView からの repository 解決に application-level provider を使う薄い adapter が残る

## Follow-up

ADR-0107 で provider lookup を廃止し、`XViewerCssRepository` を app-level route から明示注入する形へ移行した。
