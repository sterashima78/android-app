# ADR-0157: Mosaic の外部識別子と互換識別子を区別する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0084](0084-mosaic-application-branding.md)
- Related: [ADR-0138](0138-database-v27-compatibility-baseline.md)

## Context

ADR-0084 でユーザー向けブランドを Mosaic へ変更した後も、旧名称由来の identifier が複数残っている。これらを一律に rename すると、既存 install、database、内部 protocol、package / namespace といった互換性へ不要な影響を与える。一方、HTTP User-Agent のような外部へ新たに提示する識別子まで旧ブランドを維持する理由はない。

## Decision

旧名称由来の identifier を次の3種類として扱う。

### 1. 外部へ現在のブランドとして提示する identifier

新しい通信や配布物など、互換性 key ではない外部表示は `Mosaic` に揃える。

- Android の表示名
- APK / CI artifact 名
- application HTTP User-Agent

今回、共通 HTTP User-Agent を `Mosaic/0.2 (Android)` へ更新する。

### 2. 既存 install / data / protocol の互換識別子

既存データや呼び出し元が参照し得る identifier は、rename の具体的利益と migration plan がない限り維持する。

- application id
- package / namespace
- database file 名
- 既存の app-internal URL scheme など、保存済み値や既存呼び出しとの互換に関係する identifier

### 3. build / source 内部の harmless identifier

Gradle root project name、class 名、package path 等のうち外部 brand 表示ではなく、互換性や大規模 rename cost に対して実益が小さいものは現状維持してよい。

旧文字列が存在すること自体を cleanup 目標にはせず、「外部 brand mismatch か」「互換 key か」「内部 implementation detail か」で判断する。

## Consequences

### Positive

- Mosaic ブランドとして外部へ見える識別子の不整合を解消できる。
- package / application id / database / internal scheme の一括 rename による更新互換性リスクを避けられる。
- 将来の cleanup で旧名称検索結果を機械的に削除せず、用途を基準に判断できる。

### Negative

- source tree には旧名称由来の identifier が継続して残る。
- external / compatibility / internal の区分を理解せずに文字列置換すると破壊的変更になり得るため、レビュー時の文脈確認が必要になる。

## Verification

- `docs/spec.md` の更新互換性にこの区分を反映する。
- application HTTP User-Agent が Mosaic を使用する。
- application id と database file 名は本 cleanup では変更しない。
- PR review で旧名称の残存を用途別に確認する。

## Public repository review

識別子の分類と公開 User-Agent のみを扱う。private identifier、credential、実ユーザー URL、メールアドレス、端末固有値は記録しない。
