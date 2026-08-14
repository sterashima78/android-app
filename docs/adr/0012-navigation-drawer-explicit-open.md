# ADR-0012: サイドメニューは明示的な操作でのみ開く

- Status: Accepted
- Date: 2026-08-09

## Context

アプリでは RSS、Reddit、ブックマーク、メールなどの一覧で、左右のスワイプを既読化、保存、スター、アーカイブなどの状態変更に利用している。

一方、アプリ全体のナビゲーションには `ModalNavigationDrawer` を使用しており、従来は Material 3 の標準ジェスチャーによって画面左端から右方向へスワイプすると drawer を開けた。

この drawer gesture は一覧項目の横スワイプと同じ方向・同じ入力領域を利用するため、ユーザーが項目を操作しようとした際にサイドメニューが開くなど、操作の競合が発生し得る。X viewer のように内部コンテンツ自身がジェスチャーを扱う画面でも、アプリ側の drawer opening gesture を重ねる必要はない。

## Decision

アプリ全体を包む `ModalNavigationDrawer` では、drawer が閉じている間は `gesturesEnabled` を `false` にする。これにより、画面左端を含む横スワイプから drawer を開くことはできない。

サイドメニューを開く経路は、画面上に明示的に表示されるハンバーガーメニューアイコンのタップに限定する。通常の TopAppBar と、独自 TopAppBar を持たない X viewer に配置しているメニューアイコンは、従来どおり `DrawerState.open()` を呼び出す。

一方、drawer が開いている間は `gesturesEnabled` を `true` にする。Material 3 の `gesturesEnabled = false` は opening drag だけでなく scrim による dismiss も抑止するため、開いた後は gesture を有効化し、scrim タップや閉じる方向のドラッグ、システム Back、項目選択による閉じ方を維持する。

## Consequences

### Positive

- drawer が閉じている通常状態では、一覧項目の左右スワイプとサイドメニューを開くジェスチャーが競合しない
- 各 feature 側で drawer との競合回避処理を個別に実装する必要がない
- X viewer を含め、アプリ内コンテンツの横方向ジェスチャーを優先できる
- サイドメニューを開く操作が視覚的なハンバーガーメニューに統一される
- drawer を開いた後の scrim タップや閉じる方向のドラッグは維持される

### Negative

- 画面左端からのスワイプでサイドメニューを開く従来操作は利用できなくなる
- サイドメニューの発見可能性はハンバーガーメニューアイコンに依存する
- `gesturesEnabled` は drawer の開閉状態に応じて切り替える必要がある

## Relationship to existing ADRs

- ADR-0001 の UI 層の責務として、アプリ共通 navigation gesture の競合をルート UI で解決する
- ADR-0003 / ADR-0004 の feature 分離を維持し、各 feature に drawer gesture 回避のための例外処理を持ち込まない
- ADR-0008 mail triage workflow など、各 feature が定義する横スワイプの状態変更操作自体には変更を加えない
- ADR-0047 x-webview-css-customization の X viewer でも、WebView 上のジェスチャーと drawer opening gesture の競合を避けるため同じ方針を適用する
