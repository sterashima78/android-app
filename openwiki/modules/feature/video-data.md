---
type: module
title: 動画 Data：カタログと再生先解決
description: Web/SMB動画保存、公開SMB能力の利用と再生request境界を説明する。
tags: [video, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-db2f77ea69c72039f9d185a4
    resource: repo://feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolver.kt
  - id: openwiki-source-5336c3b072d030176c88d507
    resource: repo://feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 動画 Data：カタログと再生先解決

`:feature:video:data` は次の責務を持ちます。

## 取り込みと保存

DefaultVideoRepositoryは動画カタログ、保存フォルダ、再生位置、SMB同期場所とWeb抽出規則をSQLiteへ保存します。Web追加はURLを正規化し、静的metadataと一致する抽出規則の結果からタイトルとthumbnailを決めます。抽出が失敗した場合は静的metadata等へ戻り、sourceとURLから安定IDを作って保存します。削除は動画に付随する保存状態と再生状態も同じtransactionで削除します。

SMB同期ではLibrary Domainの接続プロファイルとSmbMediaFileAccessを利用します。動画独自のshare/rootは保持しますが、Library Dataや接続credentialの具体実装へ直接依存しません。再生用のoffset readも公開SMB能力へ委譲し、ファイル全体を取得してからでなければ再生できない経路に固定しません。

## 再生先と通信情報

DefaultVideoPlaybackResolverはWeb抽出規則でstreamが得られた場合だけ直接再生先を返し、それ以外は元ページ表示へ戻ります。Refererは既定でorigin相当へ制限し、path共有を明示した場合でもuserinfo、query、fragmentを除きます。cookieは再生要求先に応じて供給する契約を通して扱います。変更時はURL validation、Referer設定、cookie転送、SMB設定root内のreadを関連テストで確認してください。metadata取得成功と再生成功は異なるため、両方の境界を分けて調査します。

## 調査と変更の入口

[DefaultVideoRepository.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepository.kt)、[DefaultVideoPlaybackResolver.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolver.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [DefaultVideoPlaybackResolverTest.kt](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolverTest.kt)、[DefaultVideoRepositoryTest.kt](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepositoryTest.kt) です。

[video domain](video-domain.md)、[video ui](video-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。
