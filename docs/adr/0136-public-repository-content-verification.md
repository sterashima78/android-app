# ADR-0136: 公開リポジトリの高確度な秘密情報を CI で検査する

- Status: Accepted
- Date: 2026-08-22

## Context

この repository は public であり、ADR・architecture docs では credential、token、OAuth secret、実ユーザーの URL・メールアドレス・個人データ等を追加しないことを既に規則としている。

一方、この規則は主にレビューへ依存していた。release signing secret は GitHub Secrets から runner の一時領域へ復元しており source tree には保存していないが、誤って credential file や高確度な secret literal を commit した場合、通常の architecture / test / lint では検出できない。

完全な PII 判定や任意形式の secret 検出を regex だけで行うと誤検知が増え、検査を形骸化させる。自動検査と意味的レビューの境界を分ける必要がある。

## Decision

### 1. tracked file を対象に public repository verification を行う

`scripts/verify_public_repository.py` を repository-local verifier とし、`git ls-files` で得られる tracked file を検査する。

高確度に private artifact と判断できる次の種類を CI failure とする。

- private key / keystore / PKCS#12 等の秘密ファイル
- `.env` 系の実設定ファイル（example / sample / template は除く）
- OAuth client secret / Google services credential file
- tracked SQLite database file
- backup / export と明示された ZIP archive
- Google、GitHub、AWS、Slack、OpenAI、Stripe 等の高確度な credential literal

検出時の CI log には path と分類だけを出し、matching value 自体は出力しない。

### 2. PR と main push の両方で検査する

pull request では独立した `Public repository` job として verifier の unit test と repository scan を実行し、既存 `quality` job の必須集約対象にする。

`main` push の release build でも tracked content を検査する。release keystore を GitHub Secrets から復元する前に実行し、source tree の検査と runner 上の一時 secret を混同しない。

### 3. 自動検査は独立レビューを置き換えない

実ユーザーのメールアドレス、URL、書籍名、健康データ、識別子等は文字列形式だけでは private かどうかを確実に判定できない。

PR 作成前の独立レビューでは引き続き次を意味的に確認する。

- 実ユーザーまたは家庭内データを fixture / docs へ固定していないこと
- 公開を意図しない endpoint / identifier を追加していないこと
- credential を一般的な文字列形式へ変形して scanner を回避していないこと

自動化可能な高確度パターンが新たに判明した場合は verifier と test を同じ変更で追加する。

## Consequences

### Positive

- credential file や代表的 secret token の誤 commit を PR 時点で検出できる。
- main への直接 push でも release build より前に検出できる。
- 外部 secret-scanning service に repository policy を依存しない。
- CI log が secret value を再出力しない。
- 自動判定と意味的な privacy review の責務が明確になる。

### Negative

- 未知の credential 形式や意味的な個人情報は自動検査だけでは検出できない。
- synthetic database fixture 等を将来 tracked file として必要とする場合は、public-safe である根拠と検査方針の見直しが必要になる。
- credential provider の形式変更に合わせて pattern を保守する必要がある。

## Relationship to existing decisions

- ADR-0046 の「機械判定できる再発パターンは CI で検査する」という方針を、architecture ownership ではなく public repository safety に適用する。
- ADR-0122 の current architecture documentation と public repository rule は維持する。本 ADR はその機械的 guardrail を追加する。
