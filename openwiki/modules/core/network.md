---
type: module
title: HTTP transport と応答容量制限
description: feature の通信を共通 HTTP 契約へ集約し、共有接続と有限の応答読み込みを提供する。
tags: [network, http, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-8a56b3d0b7418c9267bccacc
    resource: repo://core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt
  - id: openwiki-source-9f91e31786fe5974995b535d
    resource: repo://core/network/src/test/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClientTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# HTTP transport と応答容量制限

## 共通通信の境界

`:core:network` は Android library ではなく Kotlin/JVM module で、各 Data adapter が provider の内容を解釈する前の HTTP transport を所有する。入口の HttpClient.execute は [HttpRequest](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/HttpRequest.kt) を受け、status、最終 URL、header、ByteArray 本文を返す。成功判定は 2xx、header 取得は大文字小文字を区別しない。JSON、RSS、認証の業務解釈は caller に残る。

[OkHttpHttpClient](../../../core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt) は IO dispatcher 上で同期 call を実行し、response と stream を use で閉じる。factory は process 内の OkHttp transport を共有し、User-Agent ごとの wrapper を再利用する。app composition はバージョン入り User-Agent を渡すため、adapter ごとに接続 pool を増やす必要がない。接続・読み込み・call の timeout と redirect 方針もここに集約される。

## 容量と失敗

request は成功応答と失敗応答に別々の容量上限を指定でき、未指定でも有限である。Content-Length が上限を超えれば本文を読み始める前に拒否し、不明な場合も累積読み込み量を測って拒否する。ResponseTooLargeException は IOException ではなく、単なる一時通信失敗として再試行する判断と分けられる。timeout、DNS、接続失敗などの IOException は日本語の原因分類を付けて再送出される。

## 検証と拡張

[OkHttpHttpClientTest](../../../core/network/src/test/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClientTest.kt) は local socket を使い、Content-Length 超過、chunked 超過、境界ちょうど、途中切断を検証する。応答上限を変える場合は用途側 request とメモリ使用量の両方を確認する。新 provider もこの transport を注入し、provider retry と認証は adapter に実装する。[クラウド AI](ai-cloud-openai.md) はその利用例である。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)。
