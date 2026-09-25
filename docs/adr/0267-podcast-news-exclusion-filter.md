# ADR-0267: ニュースポッドキャストで番組単位の除外条件を適用する

- Status: Accepted
- Date: 2026-09-25
- Amends: [ADR-0264](0264-database-v38-compatibility-baseline.md)
- Refines: [ADR-0249](0249-news-podcast-context.md), [ADR-0259](0259-podcast-news-clustering.md), [ADR-0263](0263-podcast-structured-clustering-output.md)

## Context

ニュースポッドキャストは複数のRSS / Atom sourceから未消費entryを集め、同一ニュースをクラスタリングして音声原稿を生成する。現在は番組ごとにsource、生成provider、最大ニュース数、定刻を設定できるが、利用者が聞く必要のない内容をニュース候補から除外する設定はない。

source単位で購読を外すと、同じsourceに含まれる必要なニュースまで失われる。カテゴリ名の固定リストをアプリへ埋め込む方式では、利用者ごとの条件や将来の分類軸を表現できない。

一方、同一ニュース分類ではすでにprovider-neutralな構造化推論を使い、tool schemaとfeature側validationによってモデル出力を検証している。この境界を再利用すれば、provider固有protocolや第二のAI runtimeを増やさずに候補判定を追加できる。

## Decision

### 番組に自由文の除外条件を保存する

Podcast-owned `podcast_programs` に、番組単位の任意の除外条件を保存する。空文字の場合は除外判定を行わず、従来どおり全未消費候補をクラスタリングする。

設定UIでは完全なAI system promptではなく「聞かなくてよいニュースの条件」を自然文で入力する。アプリ側が固定のsystem instructionと構造化tool schemaを所有し、保存された文字列は分類条件としてだけ挿入する。

### 除外判定はクラスタリング前に行う

新規episode生成は次の順序とする。

1. Podcast sourceからfeed entryを取得する。
2. 番組で生成済みまたは除外済みのentry identityを候補から除く。
3. 除外条件が設定されていれば、番組で選択された生成providerと同じprovider-neutral structured inferenceで各候補を含める / 除外する。
4. 判定で残った候補だけを既存の同一ニュースクラスタリングへ渡す。
5. episode予約と原稿生成を従来どおり行う。

判定入力にはfeed-carried title、source title、feed bodyを使う。entry URLやリンク先本文は利用しない。feed本文は判定対象データであり、その中の命令文へ従わないことを固定instructionで明示する。

### 除外判定失敗時は候補を残す

structured inference、tool call、arguments decode、validationのいずれかに失敗した場合、候補記事を失わないことを優先し、その回は除外を行わず全候補を既存クラスタリングへ渡す。coroutine cancellationだけは通常どおり伝播する。

### 除外済みentryを番組単位で記録する

除外されたentryは `podcast_excluded_articles` に番組ID、entry identity、除外時刻を保存する。本文や除外条件のsnapshotは保存しない。

一度除外されたentryは同じ番組の後続生成で再判定しない。番組の除外条件を後から変更しても過去の除外済みentryは自動復活させず、変更後に新しく取得した未処理entryから新条件を適用する。

生成済みentryを保持する `podcast_consumed_articles` はepisode参照を必須とするため、episodeを持たない除外履歴へ流用しない。

### database versionを39へ進める

version 38を更新元baselineとして維持し、version 38 -> 39 migrationで次を追加する。

- `podcast_programs.exclusion_prompt TEXT NOT NULL DEFAULT ''`
- `podcast_excluded_articles(program_id, article_id, excluded_at)`

fresh schemaはversion 39の形を直接作る。Podcast-owned durable stateとして通常のdatabase snapshot backupへ含める。

## Consequences

- sourceを外さずに、番組単位で不要な内容だけを候補から除外できる。
- 利用者固有の条件を固定カテゴリ列挙へ閉じ込めない。
- 同じ除外entryを生成のたびに再推論しないため、推論時間と消費資源を抑えられる。
- 条件変更前に除外されたentryは自動復活しない。過去entryを再評価する機能が必要になった場合は別の明示操作として設計する。
- 除外判定が失敗してもニュースを欠落させず、従来生成へ安全にfallbackする。
- durable tableとversion 38 -> 39 migrationが1つ増えるが、ownershipはPodcast Context内に閉じる。

## Verification

- Domain testで、条件なしでは判定を省略すること、除外後にクラスタリングすること、全件除外時はepisodeを作らないことを確認する。
- structured inference adapter testで、有効なtool call、出力不正、推論失敗fallback、本文を含むprompt budgetを確認する。
- repository / migration testで、除外条件と除外履歴の保存、version 38 -> 39 migration、再取得時の除外を確認する。
- 番組編集UIの入力・保存配線はcompile / lintで確認し、保存値のround-tripはrepository testで確認する。
- architecture verification、unit test、lint、public repository verificationを実行する。
