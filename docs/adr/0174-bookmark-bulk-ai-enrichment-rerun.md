# ADR-0174: ブックマークの要約・タグ付けを再実行できるようにする

- Status: Accepted
- Date: 2026-08-25
- Amends: ADR-0030, ADR-0064, ADR-0092, ADR-0125

## Context

ブックマーク追加時の自動AI補完は、Summary が所有する既存の永続キューを使って要約とブックマークメタデータを生成している。過去に保存したブックマークについて、モデルや要約プロンプト、分類品質が改善した後に、要約とタグ付けをまとめてやり直す手段がなかった。

既存の `BackfillBookmarkAutoEnrichmentUseCase` は未処理項目だけを補完する用途であり、保存済み要約または既存タスクがある記事をスキップする。そのため再生成用途には利用できない。

また ADR-0030 では通常の自動AI処理が既存タグを削除しない方針を採用している。再実行では古い分類結果を新しい推論結果へ置き換えられる必要がある一方、既存の単記事「要約を再生成」操作だけで暗黙にタグを置き換えるべきではない。

## Decision

### 1. ブックマーク一覧から一括再実行を明示的に要求する

ブックマーク一覧に「要約・タグを一括再実行」操作を追加し、実行前に確認ダイアログを表示する。

対象は既存の `shouldRequestBookmarkEnrichment` policy が自動AI処理対象と判定する保存済みブックマークとする。YouTube、Reddit、漫画など既存policyで対象外となるContentは一括再実行からも除外する。

### 2. 既存Summaryキューへ再生成モードを保持して投入する

新しい永続キューやtableは追加しない。`ReprocessBookmarkAutoEnrichmentUseCase` が対象article idを収集し、Summary のbatch capabilityへ渡す。

既存の `force_refresh` INTEGER は従来 `0` / `1` の値だけを使用していたため、更新互換性を維持したまま次のrefresh modeとして解釈する。

- `0`: 通常生成
- `1`: 要約再生成
- `2`: 要約・タグ再生成

既存DBの `0` / `1` は従来と同じ意味を維持し、schema version変更は行わない。data層では `forceRefresh` と `replaceBookmarkTags` の別フラグとして復元し、タグ置換をgenericなforce refreshから推測しない。

queued / running の記事は既存の排他制御により重複投入しない。終了済みtaskは新しい再生成taskへ置き換えられる。

### 3. タグ置換は明示的に要求された再実行だけで行う

通常の自動AI補完と通常の単記事再生成では既存タグ保持を維持する。一括再実行、または単記事再生成でユーザーがタグ再生成を明示的に選択した場合だけ `replaceBookmarkTags=true` を設定する。

メタデータ推論が正常に完了した後、既存の `article_tags` 関連を削除し、生成されたタグを同一transaction内で追加する。タグ削除を推論前に行わないため、要約生成またはメタデータ生成が失敗した場合は既存タグを保持する。

現在のデータモデルは手動タグとAIタグのprovenanceを区別しないため、タグ再生成を明示した場合は手動追加を含む既存タグも置換対象になる。この動作は一括確認ダイアログと単記事オプションの説明で明示する。

### 4. 単記事の要約再生成ではタグ再生成をオプションにする

既存のSummary dialogの「再生成」に「ブックマークのタグも再生成」オプションを追加し、初期値はOFFとする。

- OFF: 要約だけをforce refreshし、既存タグは保持する。
- ON: 要約をforce refreshし、メタデータ生成成功後に既存タグを生成タグへ置き換える。

非ブックマーク記事ではBookmark enrichment contextが存在しないため、ONでもタグ更新は発生しない。

### 5. capabilityとapplication serviceの境界を維持する

Bookmark UI は Summary repository やAI runtimeへ直接依存しない。`:app` のroute compositionから `suspend () -> Int` の再実行callbackを渡す。

対象判定と複数Repositoryをまたぐorchestrationは Summary domain の `ReprocessBookmarkAutoEnrichmentUseCase` が所有する。Summary dataはbatch queueingとrefresh modeを所有し、Bookmark dataはタグ関連の置換を所有する。

単記事再生成では既存の `SummaryRequester` 経路に明示的な `replaceBookmarkTags` optionを追加し、UIからdurable taskまで意図を保持する。

`AppContainer` はこれらの実装を組み立てて公開するだけとし、source判定やqueue policyを持たない。

## Consequences

- 過去のブックマークへ現在の要約モデル・プロンプト・タグ分類をまとめて適用できる。
- 単記事では要約だけの再生成と、要約・タグ両方の再生成を選択できる。
- 既存のSummaryキュー、優先度、一時停止、provider routing、cloud retry、foreground実行、失敗管理を再利用できる。
- source別の自動AI対象policyを通常追加・backfill・一括再実行で共有できる。
- タグ置換は推論成功後に行うため、AI失敗によって既存タグだけが失われることを避けられる。
- 手動タグとAIタグを区別していないため、タグ再生成を選択すると手動タグも置き換わる。
- 一括再実行では対象件数分の推論が発生し、端末負荷またはcloud利用量が増える。

## Verification

- Summary domain testで一括再実行が既存の自動AI対象policyを再利用し、対象外Contentを除外することを確認する。
- Summary persistence testで通常の要約再生成と要約・タグ再生成が別のtask flagとして復元されることを確認する。
- Bookmark data testで通常enrichmentは既存タグを保持し、タグ置換指定時だけ既存タグ関連を生成成功後に置換することを確認する。
- CIでunit test、lint、architecture verification、public repository verificationを実行する。

## Relationship to existing ADRs

- ADR-0030の通常自動enrichmentにおける既存タグ保持は維持し、明示的なタグ再生成だけ置換する例外を追加する。
- ADR-0064のYouTube / Reddit自動AI対象外policyを一括再実行にも適用する。
- ADR-0092の要約後にメタデータを生成する処理順を維持する。
- ADR-0125のApplication Service / narrow capability / composition root方針に従う。
