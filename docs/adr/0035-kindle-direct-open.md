# ADR-0035: Kindle 蔵書の直接起動

- Status: Accepted
- Date: 2026-08-13

Kindle ownership の ASIN を使い、書籍タップ時の起動先を Kindle 書籍 URI として解決する。

形式は `kindle://book/?action=open&asin={ASIN}` とする。これは Amazon の公開 API 契約ではなく、2025年に Amazon サポートフォーラムで実際の「Open With Kindle」に利用されていることが確認された形式である。将来 Kindle 側で変更された場合は本 ADR を更新する。

DB とインポート形式は変更せず、既存データにそのまま適用する。実ユーザーの書籍情報や認証情報はリポジトリへ追加しない。

ADR-0028 のサービス識別子から起動先を解決する方針と、ADR-0033 の Kindle ASIN 解釈を引き継ぐ。
