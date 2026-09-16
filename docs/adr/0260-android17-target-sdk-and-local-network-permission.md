# ADR-0260: Android 17 / API 37 を target SDK baseline とする

- Status: Accepted
- Date: 2026-09-16
- Refines: [ADR-0065](0065-smb-library-and-built-in-book-reader.md), [ADR-0160](0160-worker-runtime-and-android-17-baseline-cleanup.md), [ADR-0221](0221-android15-minimum-platform-baseline.md), [ADR-0258](0258-android17-compile-sdk-baseline.md)

## Context

ADR-0258で `compileSdk = 37` を先行採用し、`targetSdk = 36` を一時的に維持していた。targetSdk 37ではローカルネットワーク通信がruntime permissionの対象になるため、SMBとLAN Web Serverを持つ本アプリでは単純なversion bumpとして扱えない。

また、大画面ではorientation / resizability / aspect-ratio制約を前提にできないため、portrait指定やfullscreen orientation要求はUIの成立条件にしない必要がある。

## Decision

- executable appの `targetSdk` をAPI 37へ更新する。
- `compileSdk = 37`、`minSdk = 35` を維持する。
- manifestへ `android.permission.ACCESS_LOCAL_NETWORK` を宣言する。
- local-network permissionはapp presentation boundaryで扱い、feature Domain / DataへAndroid permission APIを持ち込まない。
- LAN Web Serverはユーザーがserverを起動するときにlocal-network permissionを要求し、拒否時は起動しない。notification permissionも必要な場合はlocal-network permissionの後に要求する。
- SMBはLAN通信を行うユーザー操作の直前にlocal-network permissionを要求する。許可後は同じOS permissionを同期、reader、cover prefetch、metadata normalization、file operation等のLAN通信で利用する。
- permissionを拒否した場合、永続設定やcredentialを削除しない。次のLAN操作で再度許可を取得できる状態を維持する。
- `MainActivity`等のportrait指定やVideo/Gameのorientation要求はsmall-screen向けpresentation hintとして扱い、大画面でsystemが無視しても機能が成立することをcontractとする。
- API 37 target adoptionによってContext ownership、durable schema、backup formatは変更しない。

## Consequences

### Positive

- Android 17 target-specific behaviorへ正式に移行できる。
- LAN accessがOS permission boundaryとして明示され、意図しないLAN通信をsystem permissionで制御できる。
- permission orchestrationをapp presentationへ置くことでfeature ownershipを維持できる。
- compile/targetの一時的な差異を解消できる。

### Negative

- SMBやLAN Web Serverの利用開始時に追加のpermission UXが発生する。
- permissionを拒否した端末ではbackground SMB workもLANへ接続できないため、ユーザー操作で許可を得るまで待つ必要がある。
- 大画面ではorientation指定を表示保証として利用できない。

## Verification

- `app/build.gradle.kts` が `targetSdk = 37` であることをsource-level testで固定する。
- manifestが `ACCESS_LOCAL_NETWORK` を宣言することをsource-level testで固定する。
- LAN Web Serverはpermission未付与時にserverを起動せず、許可後に既存start flowへ進むことを確認する。
- SMBのLAN操作はpermission未付与時に要求し、許可後に保留していた操作を1回だけ実行することを確認する。
- permission拒否後にSMB設定・credentialが保持されることを確認する。
- phone portrait、phone landscape、sw600dp以上のresizable windowで主要navigation、Library、Video、Gameが操作可能であることを確認する。
- Architecture / Test / Lint / R8 / public repository verificationを通す。

## Follow-up

- local-network permissionがない状態で実行される既存background SMB workerは、permission failureを無限再試行せず失敗状態へ収束することを維持する。
- 大画面でorientation hintが無視されることによる具体的なUI不具合が見つかった場合は、orientation強制を戻すのではなくadaptive layout側で修正する。
