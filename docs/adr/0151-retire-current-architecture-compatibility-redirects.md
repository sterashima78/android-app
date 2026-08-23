# ADR-0151: current architecture の互換 redirect は参照移行後に廃止する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0122](0122-current-architecture-documentation.md)

## Context

ADR-0122 では current architecture document を `docs/architecture/` に集約する際、既存リンクを壊さないため旧 top-level Context Map を compatibility entry として残した。

その後、repository 内の current architecture 参照は [`docs/architecture/context-map.md`](../architecture/context-map.md) へ移行した。旧入口は内容を持たない redirect-only document となり、repository search では canonical document と compatibility entry の両方が見えるため、どちらが現在の正本かを判断する余分な分岐になる。

互換入口を永続的に残すより、参照がなくなった時点で削除し canonical path だけを維持する方が ADR-0122 の「current architecture を一箇所へ投影する」という目的に合う。

## Decision

### 1. repository 内参照は canonical architecture path を直接使う

current architecture へのリンクは `docs/architecture/` 配下の正本へ直接向ける。redirect-only document を repository 内の案内先として利用しない。

Context Map の canonical document は [`docs/architecture/context-map.md`](../architecture/context-map.md) とする。

### 2. compatibility redirect は参照がゼロになったら削除する

旧 top-level Context Map compatibility entry は、repository 内の参照を canonical document へ移行したことを確認したうえで削除する。

今後も current architecture document を移動する場合、互換 redirect は移行期間の手段としてのみ利用し、参照が解消した後に恒久的な第二入口として残さない。

### 3. ADR は historical decision と current path を区別する

過去 ADR が旧 path を設計判断の文脈として説明している場合でも、現在参照すべき文書へのリンクは canonical path に向ける。旧 path を現在有効な入口として扱う表現は後続 ADR relationship と current architecture document に合わせて整理する。

## Consequences

### Positive

- current architecture の入口が `docs/architecture/` に一意化される。
- redirect-only document の検索ノイズと stale link maintenance を削減できる。
- document relocation の互換措置に終了条件ができる。

### Negative

- repository 外部から旧 URL を直接参照しているリンクは 404 になる可能性がある。
- path 移動時には削除前に repository 内参照を確認する手順が必要になる。

## Verification

- repository search で旧 Context Map path への参照を確認し、canonical path へ移行する。
- `docs/architecture/README.md` が Context Map の canonical entry を直接列挙していることを確認する。
- ADR integrity で新規 ADR と更新した ADR relationship / local link を検証する。
- public repository verifier で公開不可情報の追加がないことを確認する。

## Documentation

- ADR-0122 を本 ADRで refine し、current path への参照だけを残す。
- `docs/adr/README.md` の documentation governance source set に本 ADR を追加する。

## Public repository review

変更対象は公開済み architecture document の path / link policy だけである。credential、token、OAuth secret、実ユーザー URL・メールアドレス・個人データ、database / backup / private artifact は追加しない。
