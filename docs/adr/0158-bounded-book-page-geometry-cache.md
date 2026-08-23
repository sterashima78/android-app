# ADR-0158: Book Reader の page geometry metadata cache を上限付きにする

- Status: Accepted
- Date: 2026-08-23
- Related: [ADR-0065](0065-smb-library-and-built-in-book-reader.md)

## Context

`BookPageMemoryCache` は画像 byte cache を 48 MiB / 16 entries で制限している一方、各 page の aspect ratio は無制限の `Map` に保持していた。画像が eviction された後も geometry を保持することで reader layout の安定性を高めていたが、ページ数の多い書籍を順に読むと metadata 件数だけは増え続ける。

1件あたりの値は小さいものの、cache policy として image data だけが bounded で metadata が unbounded なのは意図が不明確であり、長時間利用時の不要な process memory retention を避ける必要がある。

## Decision

- page image cache と page geometry cache は別の上限を持つ。
- geometry は access-order の LRU とし、既定上限を 256 page とする。
- image が byte/entry limit で eviction されても、その geometry は geometry cache の上限に達するまでは保持する。
- geometry cache から page 固有値が eviction された場合は、従来どおり直近に学習した `fallbackAspectRatio` を使用する。
- cache は Book Reader UI process memory のみであり、durable persistence は追加しない。

## Consequences

### Positive

- page count に比例した unbounded metadata retention を防げる。
- 最近参照した page geometry は画像本体より長く保持でき、既存 layout behavior を維持できる。
- geometry が eviction された場合も fallback により未知 page と同じ安定した表示ができる。

### Negative

- 256 page を超えて過去の page へ大きく戻ると、個別 geometry が失われ fallback を利用する場合がある。
- image cache と geometry cache の2つの LRU policy を理解する必要がある。

## Verification

- image eviction 後に geometry が残る既存 unit test を維持する。
- geometry cache が least-recently-used metadata を eviction する unit test を追加する。
- full unit test / lint を PR CI で実行する。

## Public repository review

memory cache policy のみを扱い、書籍名、ファイル path、SMB 接続情報、実ユーザーデータは追加しない。
