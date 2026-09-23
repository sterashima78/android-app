# ADR-0266: ローカル単発推論の subprocess 待機を有界化する

- Status: Accepted
- Date: 2026-09-24
- Refines: [ADR-0190](0190-isolate-local-text-inference-process.md), [ADR-0219](0219-local-ai-subprocess-exit-diagnostics-and-recovery.md)

## Context

ローカル単発推論は短寿命 subprocess へ隔離され、呼び出し元 cancellation や Binder death では active session を破棄できる。一方で subprocess 自体が生存したまま Service 接続 callback または生成 response を返さない場合、main process の待機には上限がなかった。

この状態では durable task owner が失敗を確定できず、Podcast のチャプター checkpoint などが生成中のまま残り得る。feature ごとに個別 timeout を追加すると、同じ subprocess failure mode と lifecycle cleanup を重複実装することになる。

既存 execution snapshot には model ごとの PREPARING_MODEL / GENERATING_RESPONSE 実測時間が含まれているため、runtime 自身が通常の処理時間に余裕を持った watchdog を決定できる。

## Decision

### 1. Service 接続待機を30秒で打ち切る

新しい subprocess への bind 自体は30秒以内に Service 接続 callback を返す必要がある。超過時は接続を解除し、通常のローカル推論失敗として呼び出し元へ返す。

### 2. 生成 response 待機に実測ベースの watchdog を設ける

生成 response の watchdog は次の規則で決める。

- 実測 stage duration がない初回等は10分を利用する。
- 実測値がある場合は PREPARING_MODEL と GENERATING_RESPONSE の合計に4倍の余裕と1分の grace を加える。
- 最小5分、最大20分に制限する。

短い通常処理へ過度に厳しい期限を与えず、設定変更や端末差で過去実測より遅くなる場合にも余裕を残しつつ、無期限待機は許可しない。

### 3. watchdog 超過時は subprocess を廃棄し通常失敗へ変換する

response watchdog が期限を超えた場合は active Service を unbind して subprocess を終了し、session を再利用しない。

watchdog は runtime 内部の異常終了であり、呼び出し元 cancellation として扱わない。通常の推論失敗へ変換することで、durable task owner は既存の失敗状態、再試行、checkpoint 契約をそのまま利用できる。

呼び出し元 coroutine 自体が cancellation された場合は従来どおり cancellation を伝播し、active subprocess を終了する。中断再開を所有する feature の semantics は変更しない。

### 4. prompt / output を timeout diagnostics へ追加しない

watchdog 判定に利用するのは接続時間と既存 stage-duration metadata だけとする。prompt、生成結果、記事名等を log、diagnostics、durable runtime metadata へ保存しない。

## Consequences

### Positive

- subprocess が生存したまま停止しても durable task が生成中で無期限に残らない。
- timeout cleanup を各 feature に複製せず、provider-neutral Local text inference boundary で一貫して扱える。
- 既存の stage-duration snapshot を再利用するため、新しい永続 state や schema を追加しない。
- 実際の caller cancellation と runtime watchdog を区別し、既存の中断再開 semantics を維持できる。

### Negative

- 正常だが極端に遅い generation は最大20分で失敗する。
- 過去の stage-duration が現在条件を完全には表さない場合があるため、十分な倍率と上下限を保守的に設定する必要がある。

## Verification

- ProcessIsolatedLocalAiTextInferenceTest
  - 実測値なしで既定 watchdog を利用すること
  - 実測値から余裕を加え、最小値 / 最大値へ収めること
  - Service 接続 timeout が生成 watchdog より短いこと
- existing Local text inference tests
- repository unit tests / lint
- architecture verification
- public repository verification

## Public repository review

watchdog の実装、テスト、診断には実ユーザーの prompt、生成結果、URL、account、credential、private endpoint を含めない。
