# ADR-0019: feature 内の主要表示は画面下部タブで切り替える

- Status: Accepted
- Date: 2026-08-11

## Context

アプリには、1つの feature の中で複数の主要表示を切り替える画面が増えている。

蔵書では ADR-0018 により「全体・シリーズ・設定」を Material 3 の下部 `NavigationBar` で切り替え、ワークアウトでも「ワークアウト・タイマー・履歴・設定」を同じ操作モデルで切り替えている。

一方、YouTube は「未読・あとで見る・保存済み・購読管理」を画面上部の横並びボタンで切り替えており、同じアプリ内で主要表示のナビゲーション位置が一致していなかった。

feature ごとに主要表示の切り替え位置が異なると、ユーザーが画面ごとに操作方法を学び直す必要がある。また、画面上部に主要ナビゲーションを置くと、更新、追加、すべて既読などの文脈依存 action と競合しやすい。

## Decision

同一 feature 内に複数の相互排他的な主要表示があり、それらを直接切り替える必要がある場合は、原則として Material 3 の画面下部 `NavigationBar` / `NavigationBarItem` を利用する。

### 1. 下部タブを主要表示の切り替えに使う

2〜5個程度の主要表示を持つ feature では、各表示を下部タブから直接選択できるようにする。

各タブは次を持つ。

- 現在の表示を表す selected state
- 表示内容を短く表す icon
- 1行で読める簡潔な label

タブ選択状態は feature の UI state として扱い、アプリ全体の navigation drawer や top-level destination とは分離する。

### 2. 画面上部は文脈依存 action に使う

更新、追加、検索、フィルタ、すべて既読など、現在選択している表示に対する action は下部タブへ混在させず、画面上部または表示内容の近くに配置する。

主要表示ではない一時的な filter、sort、表示密度などは chip、menu、dialog 等を利用できる。

### 3. nested Scaffold の system bar inset を重複させない

feature 画面はアプリ全体の Scaffold 内に配置されるため、feature 側で下部 `NavigationBar` を持つ場合は、既存の蔵書・ワークアウトと同様に nested `Scaffold` と `NavigationBar` の `WindowInsets` を 0 とし、system bar inset の二重適用を避ける。

### 4. Material 3 の標準 component を直接利用する

現時点では feature ごとにタブ数、label、icon、state ownership が異なるため、下部タブ専用の独自 wrapper は `:core:designsystem` に追加しない。

Material 3 の `NavigationBar` / `NavigationBarItem` を各 feature から直接利用し、配置と役割を本 ADR で統一する。標準 component では表現できない共通 interaction や visual behavior が将来必要になった場合に限り、ADR-0015 の方針に従って design system primitive 化を検討する。

### 5. YouTube に適用する

YouTube feature の次の4表示を画面下部タブへ移す。

- 未読
- あとで見る
- 保存済み
- 購読管理

「すべて既読」「チャンネル追加」「更新」は現在の表示に対する action のため画面上部に残す。

## Exceptions

次は本 ADR の下部タブを必須としない。

- 記事詳細や動画詳細など、単一コンテンツに集中する detail screen
- wizard や onboarding のように順序を持つ flow
- dialog / bottom sheet 内の局所的な切り替え
- 主要表示が多く、5項目程度の下部タブでは適切に表現できない場合

主要表示を持つ通常 feature screen で別のナビゲーション方式を採用する場合は、その理由を設計判断として明示する。

## Consequences

### Positive

- feature をまたいで主要表示の切り替え位置が画面下部に揃う
- 片手操作時に主要表示へ到達しやすくなる
- 画面上部を文脈依存 action に使いやすくなる
- Material 3 標準 component を使うため独自 navigation API を保守する必要がない
- feature 固有 state と top-level navigation の責務分離を維持できる

### Negative

- 各 feature が `NavigationBar` の label / icon mapping を個別に定義する
- feature 内にさらに nested navigation が増えた場合、下部タブだけでは階層を表現できない
- 4〜5タブでは label を簡潔に保つ必要がある

## Relationship to existing ADRs

- ADR-0010 が定義する YouTube の「未読・あとで見る・保存済み・購読管理」という表示分離は維持し、本 ADR がその切り替え位置を定める
- ADR-0018 の蔵書下部タブは本方針の既存適用例として維持する。本 ADR はその操作モデルを特定 feature からアプリ全体へ一般化する
- ADR-0015 の shared UI interaction primitive 方針は維持し、Material 3 標準 component で足りる間は不要な wrapper を追加しない
