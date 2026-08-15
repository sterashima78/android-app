# ADR-0065: 蔵書の整理情報をユーザー状態として管理しAIは候補生成に限定する

- Status: Accepted
- Date: 2026-08-15

## Context

蔵書は Google Play Books、Kindle、Audible、ファイルサーバなど複数の取得元を横断して表示できるようになった。一方、冊数が増えると取得元とシリーズだけでは後から本を探しにくく、ユーザー自身の分類軸としてコレクション、タグ、読書状態が必要になる。

ADR-0013 は `library_items` を外部サービスから再構築可能な同期キャッシュとして扱う。ADR-0017 は手動シリーズ設定をこの同期キャッシュから分離し、再同期でユーザー調整を失わない方針を採用している。蔵書整理も同じ性質を持ち、外部サービス由来の書誌情報とは別のユーザー状態として扱う必要がある。

また、既存のローカルAIを分類作業へ利用できるが、AIが蔵書全体を自動的に書き換えると誤分類の修正が難しくなる。既存のBookmark AI enrichmentでは既存タグを候補として提示し、似た分類語の増殖を抑える方針を採用している。

## Decision

### 1. 整理情報を同期キャッシュから分離する

蔵書整理は `library_items` の列として保存せず、library feature が所有する次のテーブルへ保存する。

```text
library_organization_tags
- tag_id
- name
- normalized_name UNIQUE
- created_at

library_organization_collections
- collection_id
- name
- normalized_name UNIQUE
- created_at

library_item_organization_tags
- source
- source_id
- tag_id
- created_at
- PRIMARY KEY(source, source_id, tag_id)

library_item_organization_collections
- source
- source_id
- collection_id
- created_at
- PRIMARY KEY(source, source_id, collection_id)

library_item_reading_status
- source
- source_id
- status
- updated_at
- PRIMARY KEY(source, source_id)
```

書籍の識別には既存の `LibrarySource` と source-specific ID の組を使う。整理情報から `library_items` への外部キーは設けない。同期処理が `library_items` を置換した場合や、一時的に取得元から項目が消えた場合でもユーザーの整理状態を保持するためである。

タグ・コレクションの関連先から分類マスタへの外部キーは設定し、分類マスタを削除した場合は関連も削除する。

### 2. コレクションとタグを異なる整理軸として扱う

コレクションは「技術」「仕事」「育児」など比較的広い本棚相当の分類とし、一冊を複数コレクションへ所属可能にする。タグは「Android」「LLM」「マネジメント」など横断的な主題分類とし、こちらも複数付与できる。

初期実装ではコレクションをフラットに扱う。階層化は必要性が確認できてから別の設計判断として追加する。

タグ名・コレクション名は空白を正規化したcase-insensitiveな `normalized_name` で重複排除する。ユーザーが一度作成した分類は、現在の関連が0件になっても分類候補として保持する。将来の整理やAI候補生成で既存体系を再利用できるようにするためである。

### 3. 読書状態を分類とは独立した属性にする

読書状態は次を持つ。

- 未読
- 読書中
- 読了
- 中断中
- 中止
- 未設定

未設定はDB行が存在しない状態として表す。AIは読書状態を推測しない。読書状態はユーザーの実際の行動を表し、書誌情報から正しく推論できないためである。

### 4. スマート条件は保存せず導出する

整理画面では、保存された整理状態から次のような条件を動的に導出する。

- 未整理: タグもコレクションもない
- 状態未設定
- 未読
- 読書中
- 読了

これらを独立した永続レコードとして保存しない。条件の意味が変わっても既存データを移行せず再計算できるようにする。

### 5. AIは候補生成だけを行い、自動適用しない

library feature は `LibraryOrganizationSuggester` をdomain contractとして所有し、data layerの `LocalLibraryOrganizationSuggester` が `core:ai-runtime` の汎用 `LocalModelManager.generate` を利用する。

AIへの入力は書名、著者、出版社、出版日、説明、シリーズ、取得元、および既存のタグ・コレクション名に限定する。既存分類は最大100件提示し、意味が同じ既存分類を優先して再利用するよう指示する。新しいタグは最大5件、新しいコレクションは最大2件とする。

書誌データや既存分類名はユーザーデータとして扱い、その中の文字列をAIへの指示として解釈しないようsystem相当のprompt policyへ明記する。

AIの返却値はタグ、コレクション、短い理由を持つJSONとして解析する。候補は編集フォームへ反映するだけで、ユーザーが「保存」を実行するまで永続化しない。AI候補生成の失敗も既存の整理状態へ影響しない。

### 6. 整理UIを既存蔵書表示から独立させる

既存の「全体」「シリーズ」「非表示」「設定」タブと書籍を開く操作を変更せず、蔵書画面から全画面の整理ワークスペースを開く。

整理ワークスペースはスマート条件で対象を絞り込み、各書籍のコレクション、タグ、読書状態を編集できる。AI候補生成も同じ編集画面から実行する。

この分離により、通常の読書開始操作と大量の蔵書整理操作を混在させない。

### 7. Database versionを16へ上げる

新しい整理テーブルを既存インストールへ作成するため、app-level database versionを15から16へ上げる。テーブル作成はADR-0047に従って `feature:library:data` の `DatabaseSchemaContribution` が所有する。

### 8. 公開リポジトリに実ユーザーデータを残さない

このリポジトリはpublicであるため、AI整理機能のテスト、ログ、fixture、ADR、PR説明に実際の蔵書名一覧、ASIN、Personal Document ID、ユーザーの分類体系を含めない。

AI整理は端末上の既存ローカルモデルで実行し、整理対象の書誌情報や分類体系を外部AIサービスへ送信しない。

## Consequences

### Positive

- Kindle/Audible/Google Books/SMBの再同期後もユーザーの整理状態を維持できる。
- 一冊を複数のコレクションとタグで横断的に整理できる。
- 未整理や読書状態を自動条件で探せるため、整理漏れを継続的に減らせる。
- AIが既存分類体系を参照するため、似たタグやコレクションの増殖を抑えられる。
- AIの誤分類が自動で永続化されず、ユーザーが確認してから適用できる。
- Library固有の分類policyを `core:ai-runtime` へ流出させない。

### Negative

- source-specific IDが変化した場合は既存の手動シリーズ設定と同様に整理状態の移行が必要になる。
- 初期実装ではコレクション階層、分類名の一括rename/merge、AIによる一括整理は提供しない。
- AI候補生成にはローカルモデルのダウンロードと選択が必要になる。
- 未使用のタグ・コレクションも候補として保持されるため、将来は分類体系の整理UIが必要になる可能性がある。

## Relationship to existing ADRs

- ADR-0013 の `library_items` を再構築可能な同期キャッシュとする判断を維持する。
- ADR-0017 の「ユーザー編集状態を同期キャッシュから分離する」方針を蔵書整理へ拡張する。
- ADR-0030 の既存分類をAIへ提示して分類語の増殖を抑える考え方をlibrary向けに適用する。
- ADR-0047 のfeature-owned database schema方針に従う。
- ADR-0054 のlibrary runtime/UI ownershipを維持する。
- ADR-0056 に従い、AI runtimeは汎用推論だけを提供し、蔵書分類policyはlibrary featureが所有する。
