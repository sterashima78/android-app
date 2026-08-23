# Architecture Documentation

このディレクトリは、現在有効なアーキテクチャを現在形で説明する。

ADR は「なぜその判断をしたか」を残す Decision Log とし、このディレクトリは複数 ADR の結果として「現在どう設計するか」を参照しやすい形へ投影する。

## Documents

- [principles.md](principles.md): 実装時に守るアーキテクチャ原則と禁止事項
- [module-map.md](module-map.md): Gradle module と物理 ownership の現在形
- [context-map.md](context-map.md): Domain Context と Context 間関係
- [persistence.md](persistence.md): schema・table access・migration ownership
- [testing.md](testing.md): テスト責務と architecture verification
- [platform.md](platform.md): Android runtime / SDK の現在の基準
- [glossary.md](glossary.md): ubiquitous language とアーキテクチャ用語
- [../adr/README.md](../adr/README.md): Decision Log の読み方と主要 ADR の索引
- [../spec.md](../spec.md): ユーザーから見たアプリ仕様

## Source of truth

文書の責務を次のように分ける。

```text
ADR
  なぜその設計を選んだか / 何を変更・廃止したか
        |
        v
architecture/*.md
  現在の設計・ルール・概念をまとめて説明する
        |
        v
settings.gradle.kts / config/architecture/* / Gradle verification / production code
  機械的に検査可能な構造と実装
```

矛盾が見つかった場合は次のように扱う。

1. 新しい Accepted ADR が architecture document より新しい判断をしている場合、ADR を基準に architecture document を更新する。
2. architecture document と実装が異なり、設計変更を記録した ADR がない場合、実装 drift として扱う。実装だけを理由に architecture document を書き換えない。
3. module 一覧、table ownership manifest など機械可読な構成値は、それぞれの専用ファイルを列挙の正本とし、architecture document では意味と読み方を説明する。

## Maintenance rule

アーキテクチャ上の意思決定を追加・変更する PR では、次を同時に確認する。

- 新規または更新 ADR が必要か
- `docs/architecture/` の現在形が変わるか
- `settings.gradle.kts`、`config/architecture/*`、`verifyArchitecture` 等の機械的制約を更新すべきか
- 既存 ADR の `Superseded` / `Amended by` / `Refines` 等の関係を更新すべきか

Architecture document の規範的な節には末尾の `Sources` で根拠 ADR を示す。理由・議論・代替案は重複記載せず ADR を参照する。

current architecture document への repository 内リンクは、このディレクトリの canonical path へ直接向ける。文書移動時に compatibility redirect を置く場合は移行期間だけの措置とし、repository 内参照を canonical path へ移したことを確認後に削除する。redirect-only document を恒久的な第二の入口として残さない。

公開リポジトリであるため、architecture document、ADR、manifest、fixture には credential、token、OAuth secret、実ユーザーの URL・メールアドレス・個人データを記載しない。高確度な credential / private artifact は `scripts/verify_public_repository.py` でも検査し、意味的な個人情報判定は独立レビューで補完する。

## Sources

- [ADR-0001](../adr/0001-layered-architecture.md)
- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0055](../adr/0055-adr-numbering-policy.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0122](../adr/0122-current-architecture-documentation.md)
- [ADR-0126](../adr/0126-android-platform-baseline.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
- [ADR-0136](../adr/0136-public-repository-content-verification.md)
- [ADR-0151](../adr/0151-retire-current-architecture-compatibility-redirects.md)
