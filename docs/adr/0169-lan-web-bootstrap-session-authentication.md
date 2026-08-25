# ADR-0169: LAN Web 認証を起動単位の bootstrap / session token にする

- Status: Accepted
- Date: 2026-08-25
- Amends: [ADR-0166](0166-lan-web-and-route-composition-responsibility-split.md)

## Context

ADR-0166 では LAN Web の責務分割時に既存の認証 contract を変更しないことを決定した。その時点の実装は、端末に永続化した access token を query parameter と Cookie の双方で継続利用していた。

LAN Web は同一 LAN 内の read-only UI だが HTTP は暗号化されない。永続 token を URL に含め続けると、ブラウザ履歴、URL 共有、LAN 上の観測などを通じて token が長期間再利用される余地がある。認証情報を端末移行や platform backup に含める必要もない。

一方、初回 token を単純に一度だけ消費すると、認証後に端末の LAN IPv4 address が変化した場合、Cookie の origin が変わるため新しい address からアクセスできなくなる。また bootstrap 成功と session token 取得を別同期処理にすると、停止処理との競合で成功済み session token を失う可能性がある。

## Decision

LAN Web の認証を次の contract に変更する。

- service 起動ごとに cryptographically secure な bootstrap token をメモリ上で生成し、永続化しない。
- 初回 URL は `http://<lan-address>:8765/?token=<bootstrap>` とする。
- bootstrap token は照合に成功した1回の request で消費し、その同じ同期処理で新しい session token を生成して認証結果として返す。
- server は session token を `HttpOnly; SameSite=Strict` Cookie に設定し、token query を除去した同一 path へ `303 See Other` で redirect する。
- query に token が残る request は bootstrap 専用とし、消費済み token や Cookie との併用による再利用を拒否する。
- LAN IPv4 address が変化した場合は新しい bootstrap token を生成して server と表示 URL を同時に更新し、旧 bootstrap token と既存 session token を失効する。
- server 停止時は bootstrap token と session token の双方を失効する。再起動後に旧 token を受け入れない。
- HTTP 自体は暗号化しないため、UI では信頼できる LAN に限定することと初回 URL を共有しないことを明示する。

ADR-0166 の transport / read model / renderer の責務分割と read-only contract は維持する。本 ADR は同 ADR の「認証方式を変更しない」という移行時制約だけを更新する。

## Consequences

### Positive

- 長寿命の永続 access token を SharedPreferences と URL から排除できる。
- 初回 URL が履歴に残っても bootstrap token は成功後に再利用できない。
- 認証後の通常 URL から query token を除去できる。
- LAN address 変更後も新しい origin 用 bootstrap URL から再認証でき、旧 origin の session も失効する。
- bootstrap 成功結果が session token を値として保持するため、server 停止との競合で `sessionToken` 参照が失敗しない。

### Negative

- server 起動中でも LAN address が変わると既存 session が失効し、再認証が必要になる。
- HTTP は平文のままであり、同一 LAN 上の盗聴耐性は提供しない。
- session Cookie は address origin ごとに分離されるため、address change ごとに新しい bootstrap が必要になる。

## Verification

- bootstrap token は一度だけ成功すること。
- bootstrap 成功結果が生成済み session token を保持し、直後に invalidate されても成功 response を構築できること。
- bootstrap token の差し替え後は旧 bootstrap / session token を拒否し、新 token を受け入れること。
- stop / restart 後は旧 bootstrap / session token を拒否すること。
- redirect URL から token query だけを除去すること。
- PR CI の unit test、architecture verification、lint、public repository verification を継続する。

## Public repository review

実 token、LAN address、閲覧データ、credential は source / test / documentation に保存しない。テストでは固定のダミー token だけを使用する。
