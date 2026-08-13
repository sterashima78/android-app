# ADR-0033: Kindle ownership は観測した resource / rights スキーマを優先して解釈する

- Status: Accepted
- Date: 2026-08-13
- Refines: ADR-0026, ADR-0031

## Context

ADR-0026 と ADR-0031 では `Digital.Content.Ownership*.json` を Kindle 蔵書の正規入力として扱い、ZIP 全階層を探索する方針を定めた。

その後、実際の Amazon エクスポートから取得した ownership JSON の一例を確認したところ、これまでテストで仮定していた `title` / `contentType` / `Grant`・`Revoke` 中心の構造とは異なっていた。

観測した構造では、書籍情報は `resource` オブジェクトにあり、次のキーが存在した。

- `resource.resourceType`: `KindleEBook`
- `resource.ASIN`: Amazonの商品識別子
- `resource.Product Name`: 表示名

権利情報は `rights` 配列にあり、次のキーが存在した。

- `rightType`: 例 `Download`
- `rightStatus`: 例 `Active`
- `acquiredDate`: 権利取得日時
- `origin.originType`: 権利の由来

ルートには `lastUpdatedDate` が存在した。

従来実装は `Product Name` をタイトル候補に含めず、`resourceType` をコンテンツ種別として解釈せず、`rightStatus=Active` も所有状態として扱っていなかった。そのため ASIN を認識できても候補を破棄していた。また JSON 配列内に複数のオブジェクトがある場合、一部のネストしたプリミティブ値を収集できない実装になっていた。

実ユーザーの ownership JSON 自体はパブリックリポジトリへ保存しない。

## Decision

Kindle ownership の解析では、従来の互換キーに加えて、観測した実スキーマを明示的に解釈する。

- `Product Name` をタイトル候補として扱う
- `resourceType` を Kindle / 非書籍コンテンツ判定に利用する
- `rightStatus` を現在の権利状態として `rightType` より優先して扱う
- `rightStatus=Active` 相当が1件でもあれば現在有効な権利として扱う
- Active 相当がなく、Inactive / Revoked / Expired 等だけなら失効扱いにする
- `acquiredDate` と `lastUpdatedDate` を権利イベント日時候補に加える
- `rights` などの配列では要素数にかかわらず JSONObject / JSONArray を再帰的に走査する
- `resourceType` が music / video / Audible 等を明示する場合は Kindle 蔵書から除外する

`rightStatus` が存在しない旧形式については、ADR-0026 で定めた Grant / Revoke 等の action 解釈をフォールバックとして維持する。

`originType` は今回の例で確認できたが、蔵書から除外する根拠としては使用しない。辞書、サンプル、購入、貸出などの区別に使える可能性はあるものの、1例だけでは分類規則を確定できないためである。

テストでは実ASIN、実タイトル、実日時などをコピーせず、同じキー構造を持つ人工データを用いる。

## Consequences

### Positive

- 観測済みの Amazon ownership JSON をそのまま解析できる
- `rights` が複数件あるファイルでも権利状態を取りこぼしにくくなる
- Amazon Music 等の同名 ownership データを `resourceType` で除外しやすくなる
- 実ユーザーデータを公開リポジトリへ追加せずに回帰テストできる

### Negative

- Amazon が `rightStatus` の値体系を変更した場合は追従が必要になる
- Active と Inactive が混在する権利の詳細な意味は、現時点では「Active が1件でもあれば有効」という保守的な規則で扱う
- `originType` による辞書・サンプル等の除外は、追加の実例が得られるまで行わない

## Relationship to existing ADRs

- ADR-0026 の ownership JSON を正規入力とする方針を維持する
- ADR-0031 の ZIP 再帰探索方針を維持する
- 本 ADR は JSON 内部スキーマの解釈を、推測中心から観測済み構造中心へ更新する
