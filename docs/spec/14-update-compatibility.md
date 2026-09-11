# 14. 更新互換性

- 現在配布中の最新版を次版への更新互換性baselineとする。
- 移行完了が確認された一時的migrationや旧形式fallbackは恒久的に保持しない。
- databaseとアプリ独自backupは、現在利用中の最新版へ収束した状態を基準に互換性範囲を定める。
- 現在のユーザーデータを失う可能性がある形式変更では、現行形式へ安全に収束してから旧処理を削除する。
- frameworkがclass name等を永続化する場合は、必要な期間だけ明示的compatibilityを維持する。
- application idと内部database file名は既存インストールの継続性のため維持する。
