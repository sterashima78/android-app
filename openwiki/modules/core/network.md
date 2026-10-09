---
type: module
title: HTTP transport と応答容量制限
description: feature の通信を共通 HTTP 契約へ集約し、共有接続と有限の応答読み込みを提供する。
tags:
  - network
  - http
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-d62ac7d985204cb3acec963b
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt
  - id: openwiki-source-8a56b3d0b7418c9267bccacc
    resource: repo://core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt
  - id: openwiki-source-9f91e31786fe5974995b535d
    resource: repo://core/network/src/test/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClientTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# HTTP transport と応答容量制限

## 共通通信の境界

`:core:network` は Android library ではなく Kotlin/JVM module で、各 Data adapter が provider の内容を解釈する前の HTTP transport を所有する。入口の HttpClient.execute は [HttpRequest](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/HttpRequest.kt) を受け、status、最終 URL、header、ByteArray 本文を返す。成功判定は 2xx、header 取得は大文字小文字を区別しない。JSON、RSS、認証の業務解釈は caller に残る。

[OkHttpHttpClient](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt) は IO dispatcher 上で同期 call を実行し、response と stream を use で閉じる。factory は process 内の OkHttp transport を共有し、User-Agent ごとの wrapper を再利用する。app composition はバージョン入り User-Agent を渡すため、adapter ごとに接続 pool を増やす必要がない。接続・読み込み・call の timeout と redirect 方針もここに集約される。

## 主要な構成要素と API

| 宣言・種類 | 責務と主要 API | 関係・根拠 |
| --- | --- | --- |
| `HttpClient` interface | `execute(HttpRequest)` で応答を取得。`create(userAgent)` は共有 transport の wrapper を返す。 | [契約](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/HttpClient.kt)を `OkHttpHttpClient` が実装する。 |
| `HttpMethod` enum / `HttpRequest` data class | HTTP method、URL、headers、任意の本文・contentType、成功/失敗時の本文上限を表現する。上限は正かつ ByteArray に収まる値に限定する。 | [要求モデル](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/HttpRequest.kt)。 |
| `HttpResponse` data class | status、理由、redirect 後 URL、複数値 headers、本文を保持。`isSuccessful` と `header(name)` を提供する。 | [応答モデル](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/HttpResponse.kt)。 |
| `OkHttpHttpClient` internal class / `OkHttpHttpClientFactory` internal object | request を OkHttp へ変換し IO 上で実行。factory は接続 pool と User-Agent 別 wrapper を共有する。 | [実装](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt)。 |
| `ResponseTooLargeException` class | 容量超過を最大量と宣言済み Content-Length と共に表す。 | 同じ実装ファイルで定義し、通信 IOException と区別する。 |

## 注入と代表的な処理フロー

[AppContainer](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt) が版付き User-Agent の `HttpClient` を生成し、feature Data と [AI core の接続](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt)へ渡す。provider の認証や再試行は利用側に残る。

1. 利用側が `HttpRequest` を作り `execute` を呼ぶ。GET は本文なし、他 method は未指定なら空本文へ変換する。
2. wrapper の User-Agent と caller headers を設定して同期 call を IO dispatcher 上で実行する。caller の同名 header は上書きできる。
3. status に応じた容量上限を選び、宣言長と stream の累積量を検査して `HttpResponse` を返す。
4. `use` が応答と stream を閉じる。容量超過は専用例外、ネットワーク障害は原因を保持する IOException として caller へ戻る。

factory の再利用と異なる User-Agent の wrapper 分離は [HttpClientTest](../../../core/network/src/test/kotlin/dev/terashima/yomitorirss/core/network/HttpClientTest.kt)で確認できる。
## 容量と失敗

request は成功応答と失敗応答に別々の容量上限を指定でき、未指定でも有限である。Content-Length が上限を超えれば本文を読み始める前に拒否し、不明な場合も累積読み込み量を測って拒否する。ResponseTooLargeException は IOException ではなく、単なる一時通信失敗として再試行する判断と分けられる。timeout、DNS、接続失敗などの IOException は日本語の原因分類を付けて再送出される。

## 検証と拡張

[OkHttpHttpClientTest](../../../core/network/src/test/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClientTest.kt) は local socket を使い、Content-Length 超過、chunked 超過、境界ちょうど、途中切断を検証する。応答上限を変える場合は用途側 request とメモリ使用量の両方を確認する。新 provider もこの transport を注入し、provider retry と認証は adapter に実装する。[クラウド AI](ai-cloud-openai.md) はその利用例である。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)。
