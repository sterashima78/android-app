# ADR-0278: ニュースポッドキャストをfeed categoryで連続配置する

## Status

Accepted

## Context

ニュースポッドキャストは複数のRSS / Atom sourceから候補entryを集め、同じ具体的な出来事を1つのnews clusterへまとめてからchapterを生成する。

RSS 2.0 itemやAtom entryはカテゴリ情報を持てるが、sourceごとにtaxonomyや粒度が異なる。1つのentryが広い親カテゴリ、階層カテゴリ、末端カテゴリを同時に持つ場合もあるため、最初のカテゴリや固定taxonomyへ単純変換すると、広すぎるカテゴリへニュースが集中したりsource固有の分類情報を失ったりする。

一方、feed categoryがないsourceでもカテゴリ単位の連続再生は維持したい。カテゴリ分類のために記事本文やリンク先を追加送信すると、Podcastの既存のデータ境界を不必要に広げる。

カテゴリの目的はepisode内のchapter順をまとめることであり、カテゴリ名自体を検索・編集する要件はない。新しいdurable source of truthやdatabase migrationを追加せず、既存のcluster snapshotで最終順序を固定したい。

## Decision

RSS / Atom parserはentry単位のカテゴリを一時metadataとして読み取る。

- RSS 2.0 itemではcategory elementの値を取得する。
- Atom entryではcategory elementのterm attributeを取得する。
- 空値を除外し、entry内の重複を除く。
- RssFeedContentEntryからPodcastFeedEntryまで既存のfeed-content boundaryを通して伝搬する。
- feed categoryはPodcastのdurable tableへ新しいcolumnとして保存しない。

同一ニュースclusterを確定した後、cluster内entryが持つカテゴリを正規化して和集合にする。今回の候補news cluster全体について、各カテゴリが何clusterに現れたかを数える。同一cluster内の複数entryに同じカテゴリがあっても1回として数える。

各clusterの代表カテゴリは、そのclusterが持つカテゴリのうち全体の出現cluster数が最も少ないものとする。これにより広い親カテゴリより、候補全体でより限定的に現れるカテゴリを優先する。

出現数が同じ場合は次の順で決定する。

1. スラッシュ区切りの階層が深いカテゴリ
2. case-insensitiveな文字列順
3. 元文字列順

代表カテゴリが同じclusterは連続配置する。カテゴリgroup自体は元のcluster順で最初に現れた代表カテゴリ順とし、同一カテゴリ内では元のcluster順を維持する。並べ替え後にchapter_positionを0から振り直す。

feed categoryを1つも持たないclusterだけPodcastNewsCategorizerへ渡す。production implementationは番組で選択された生成providerと同じprovider-neutral structured inferenceを使う。

カテゴリ補完へ渡す材料は次に限定する。

- cluster内entryのtitle
- source title
- feed categoryからすでに得られた代表カテゴリ名を再利用候補として提示

feed body、entry URL、linked page本文はカテゴリ補完の入力へ追加しない。カテゴリ補完はニュースごとに1つの非空カテゴリ名を同じ順序で返すstructured tool callとし、不正出力は1回だけ再試行する。推論または最終validationに失敗したclusterは「その他」として扱い、episode生成全体は失敗させない。coroutine cancellationはfallbackせず伝播する。

カテゴリ名そのものはsnapshotしない。episode予約時にはカテゴリgrouping後のcluster順だけを既存chapter_positionへ保存する。このためretry、中断再開、queued episode生成では同じ順序を再利用する。

明示的な作り直しではfeedを再取得せず保存済みarticle snapshotから再クラスタリングする。feed categoryは保存していないため、作り直し時のclusterは保存済みtitle / source titleを使ったPodcastNewsCategorizerでカテゴリ補完して並べ替える。

## Consequences

- sourceが提供するカテゴリを固定taxonomyへ変換せず利用できる。
- 広い親カテゴリを持つfeedでも、候補全体でより少数のカテゴリへ自然に振り分けやすい。
- 同じカテゴリのニュースがepisode内で連続し、音声として話題がまとまる。
- feed categoryがないsourceでも同じ後段のカテゴリgroupingを利用できる。
- カテゴリ補完のためにfeed bodyやURLの新しい外部送信を追加しない。
- カテゴリ分類が失敗しても「その他」に退化し、ニュースを欠落させない。
- 新しいtable / column / migration / backup契約を追加しない。
- 明示的な作り直しでは元feed categoryを再利用できず、保存済みtitle / source titleからカテゴリを補完するため、元episodeとカテゴリgroupingが変わる可能性がある。

## Verification

- RSS 2.0 itemとAtom entryの複数カテゴリをparser testで検証する。
- RSS feed content boundaryからPodcast候補へカテゴリが伝搬することをunit testする。
- 同一cluster内のカテゴリ重複を1ニュースとして数えることをunit testする。
- 全news clusterで出現数が少ないカテゴリを代表として選ぶことをunit testする。
- 同数時に階層が深いカテゴリを選ぶことをunit testする。
- 同じ代表カテゴリのclusterが連続し、カテゴリ内の元順序を維持することをunit testする。
- categoryなしclusterのstructured inference、invalid output fallback、prompt budgetをunit testする。
- architecture verification、unit tests、Android lintをCIで確認する。

## Relations

- Refines: [ADR-0259](0259-podcast-news-clustering.md)
- Related: [ADR-0250](0250-podcast-owned-feed-sources.md), [ADR-0268](0268-podcast-rebuild-regeneration.md)
