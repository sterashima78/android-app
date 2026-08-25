# ADR-0170: HTTP response body を用途別の有限上限で読み込む

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0155](0155-application-scope-http-transport.md)

## Context

ADR-0155 により HTTP transport は application scope で共有するようになったが、`HttpClient` の response body 読み込み量は transport contract として制限されていなかった。各 adapter が `response.body` を受け取った後でサイズを検査する方式では、過大 response をすでにメモリへ読み込んでから拒否するため、memory safety の境界にならない。

また RSS、HTML、Gmail、Google Books、ChatGPT/Codex では妥当な response size が異なる。全用途へ同じ大きな上限を適用すると小さい JSON API の防御が弱くなり、逆に小さい共通上限では記事 HTML や mail payload の正常系を壊す。

サイズ上限超過は同じ request を再実行しても成功する可能性が低い policy violation であり、timeout、DNS failure、connection failure のような一時的 `IOException` と同じ retry classification にすると WorkManager 等で無意味な再試行が継続する。

## Decision

`HttpRequest` に成功 response と error response の最大 byte 数を持たせ、共有 `OkHttpHttpClient` が body を materialize する前に上限を強制する。

- `maxResponseBytes` は成功 response の上限とする。
- `maxErrorResponseBytes` は失敗 response の上限とし、未指定時は `maxResponseBytes` と同じ値を使う。
- どちらも `1..Int.MAX_VALUE` の有限値だけを許可する。
- adapter が上限を指定しない場合も transport default として 1 MiB を使用し、無制限読み込みを許可しない。
- `Content-Length` が既知で上限を超える場合は body を読み込まず拒否する。
- `Content-Length` が不明、または不正確でも、stream 読み込み中に累積 byte 数が上限を超えた時点で停止する。
- RSS、記事 HTML、Web Library、Google Books、Gmail、YouTube、ChatGPT/Codex 等は用途に応じた上限を request ごとに明示する。
- ChatGPT/Codex のように成功 body と error body の想定サイズが異なる adapter は両上限を個別に指定する。

上限超過は `ResponseTooLargeException` として返す。この例外は `IOException` を継承しない。timeout、DNS failure、connection failure、途中切断などの transport `IOException` は従来どおり一時的 network failure として扱えるが、response-size policy violation は generic `IOException` retry path に入れない。

## Consequences

### Positive

- external response を無制限に heap へ読み込まない。
- `Content-Length` がなくても chunked response を上限内に制限できる。
- feature ごとの正常な payload size に合わせた上限を選べる。
- OAuth/error response など小さい payload には小さい上限を設定できる。
- 過大 response が WorkManager の一時的 network retry と誤分類されることを防げる。

### Negative

- 新しい HTTP adapter は用途に応じた response size を検討する必要がある。
- upstream の正常 response が想定より大きくなった場合は、adapter の上限調整が必要になる。
- default 1 MiB に依存すると大きな payload を扱う新規 adapter が失敗するため、意図的な大容量用途では明示上限が必要になる。

## Verification

- `Content-Length` が上限を超える response を body 読み込み前に拒否する。
- `Content-Length` のない chunked response も累積 byte 数で拒否する。
- 上限ちょうどの response は成功する。
- 途中切断は `IOException` として扱う。
- `ResponseTooLargeException` が `IOException` を継承しないことを固定する。
- PR CI の unit test、architecture verification、lint、public repository verification を継続する。

## Public repository review

本変更は response size policy と transport error classification のみを扱う。実 response body、credential、token、private endpoint、実ユーザー URL やメール内容を source / test / documentation に追加しない。test server は固定のダミー payload だけを返す。
