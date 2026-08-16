# ADR-0084: アプリブランドを Mosaic に変更する

- Status: Accepted
- Date: 2026-08-16

## Context

このアプリは RSS 管理アプリとして開始したが、現在は RSS、ブックマーク、蔵書、YouTube、AI 要約、ナレッジ、タスク、ゲームなど複数の機能を統合する個人向けアプリへ発展している。

表示名 `Yomitori RSS` と記事リストを中心にした既存ランチャーアイコンは、現在の責務を RSS に限定して見せてしまう。また、既存アイコンは単一 Vector Drawable であり、Android の Adaptive Icon が提供する foreground / background の分離やテーマアイコン向けの monochrome レイヤーを持っていない。

## Decision

- ユーザーに表示するアプリ名を `Mosaic` に変更する。
- 異なる情報や機能が一つの場所へ組み合わさる性格を表すため、複数の非対称タイルからなるロゴを採用する。
- Android 8.0 以降では foreground / background を分離した Adaptive Icon を使用する。
- テーマアイコン向けに同じタイル形状の monochrome レイヤーを提供する。
- フォールバック用の通常 Vector Drawable も同じ Mosaic タイル形状へ変更する。
- `applicationId`、package、namespace は変更しない。既存インストールを同一アプリとして更新できることを優先する。
- 内部識別子に残る `yomitorirss` は今回のブランド変更では改名しない。内部識別子の全面変更はデータ互換性や外部連携への影響を伴うため、必要性が生じた場合に別の設計判断として扱う。

## Consequences

### Positive

- RSS に限定されない現在の統合アプリとしての性格を名称とロゴで表現できる。
- `applicationId` を維持するため、既存ユーザーは同一アプリとして更新できる。
- Adaptive Icon によりランチャーのマスクや視覚効果へ適応できる。
- monochrome レイヤーにより Android のテーマアイコン表示へ適応できる。
- 将来さらに機能が増えても、特定機能を直接描いたロゴより陳腐化しにくい。

### Negative

- 既存ユーザーには `Yomitori RSS` から `Mosaic` への名称変更を認識してもらう必要がある。
- package 名や namespace には旧名称由来の `yomitorirss` が残る。
- 既存の説明文やスクリーンショットに旧ブランド名が含まれる場合は別途更新が必要になる。

## Relationship

ADR-0055 の番号管理方針に従い、現在の最大番号より大きい ADR-0084 を割り当てる。本 ADR はユーザー向けブランドと Android ランチャーアイコンの設計判断のみを扱い、既存機能の責務やデータ形式は変更しない。
