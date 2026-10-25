# ADR-0288: ニュースポッドキャストで直近アーカイブの同一ニュースを再報しない

## Status

Accepted

## Context

Podcast は同じ entry identity の再利用を防ぐ一方、別 URL・別配信元から届いた同一出来事の再報を、直近のアーカイブ済み episode と照合できない。feed 取得時に正規化タイトルで記事を捨てると、同じニュースを報じる異なる情報源の記事が episode snapshot に残らない。

## Decision

通常の新規生成に限り、対象番組で作成日時が生成開始前48時間以内、かつ現在 ARCHIVED の episode に保存された記事を比較参照として読む。既存 schema にはアーカイブ操作日時がないため、判定期間の基準は episode の created_at とする。deleted episode は記事 snapshot を保持しないため比較対象にしない。

未消費・未除外 entry に利用者の除外条件を適用した後、直近のアーカイブ記事と新規候補を同一出来事の分類器へ渡す。同じ具体的な出来事としてアーカイブ記事と結び付いた候補は「既報」として今回の生成から外す。単に同じ主体・話題であることや、新たな決定・進展を伝える続報であることのみを理由に除外しない。比較処理が失敗または不正な partition を返したときには、候補を一切捨てず通常の分類へ進む。

新規候補同士の重複は従来のニュースクラスタとして集約する。出来事分類モードでは比較時の正常なクラスタを再利用し、関連トピックモードでは既報除外後の新規記事に従来の埋め込み分類を適用する。ニュース cluster 単位のエピソード分割と番組の maxArticlesPerEpisode 上限は維持し、可能な限り多くの異なる新規ニュースを先頭 episode に収める。

RSS source adapter はタイトル一致による事前除去をせず、異なる entry identity と source metadata を維持して既存の分類・episode snapshot へ渡す。生成済み・アーカイブ済み episode、consumed / excluded identity、retry / comparison / rebuild の snapshot は書き換えない。既報として除外した候補を durable excluded identity に登録しないため、後の新たな進展まで不可逆に遮断しない。

同じ Podcast Context の既存 repository・同一出来事分類器を利用する。新しい AI provider、外部通信経路、DB schema、永続状態は追加しない。

## Consequences

- 直近のアーカイブ済み episode と同一出来事の記事を再び読み上げる頻度を減らせる。
- 同一出来事の別配信元は、新規 episode では同じ chapter に出典を保持する。
- モデル判定の誤認識による既報除外の可能性は残る。推論失敗時は新規記事を維持する。
- 48時間は episode 作成時刻基準であり、アーカイブした日時基準ではない。厳密なアーカイブ操作日時が必要になった場合は別の durable state / migration 判断を行う。

## 2026-10-21 refinement: bounded archive comparison

直近アーカイブと新規候補の合計が24件以内なら、従来どおり単一の出来事分類を実施し、出来事モードの分類結果を再利用する。24件を超える場合は、アーカイブ・候補それぞれを最大12件のchunkに分け、全組み合わせを最大8回の推論で照合する。各推論の入力は最大24件であり、各候補が比較範囲のいずれかのアーカイブ記事と同じ出来事なら既報として除外する。chunkをまたぐ候補同士の分類結果を結合して完全partitionとみなさず、既報除外後の候補を通常の分類へ渡す。

必要な比較が8回を超える場合、推論の一部が失敗した場合、または完全partitionを検証できない場合には、その回の既報除外を実施せず候補をすべて保持する。候補を部分的に除外する結果は確定しない。これは入力・推論回数の予算を超えて記事を欠落させないためのfail-openであり、アーカイブを古い順から恣意的に間引く方式を採らない。将来の件数上限見直しは推論品質・コストの実測で決め、現行DBや所有境界を変更しない。

## Relations

- Refines: ADR-0259, ADR-0282

## 2026-10-23 refinement: separate previously covered episodes

Archive comparison no longer removes matched incoming articles from generation. A valid comparison partitions incoming articles into new and previously covered groups. New groups are reserved first up to the existing per-episode chapter limit, followed by previously covered groups in their own episodes. Each previously covered episode has a distinct visible title marked `既報ニュース`; the title is stored at reservation, so queued generation and interruption recovery retain the distinction without introducing schema or a new provider.

The bounded comparison budget and fail-open policy remain unchanged: uncertain matches stay in ordinary new episodes. Previously covered articles are consumed on reservation just like new ones; archive reference articles are never copied into new episode snapshots. Each segment is clustered independently using the selected mode, and the same chapter/episode checkpoint and queue lifecycle applies. The category ordering and maximum number of news clusters per episode remain unchanged.

Existing archived episodes, chapter snapshots, rebuilds, and comparison episodes are not repartitioned retroactively. The previous discard policy described above is superseded by this refinement.

## 2026-10-25 refinement: 72-hour archive window and visible diagnostic outcomes

The comparison looks at archived episodes created in the preceding 72 hours rather than 48 hours. The limit continues to use episode creation time, not the moment an episode was archived. The bounded batch comparison and fail-open behavior remain unchanged.

Archive-comparison failures and comparison-budget exhaustion previously produced ordinary-news episodes without a visible indication. A bounded, app-private generation diagnostic history now records fixed-form stage, count, and outcome messages for each program. The screen exposes recent warnings and a per-program history. This is observational cache, not a new generation source of truth: it does not store source article text, URLs, raw inference output, or raw exception messages; it is excluded from the app's logical backup allowlist. Log persistence failure does not abort generation. Historic ADR paragraphs above retain the original decisions at their respective dates.
