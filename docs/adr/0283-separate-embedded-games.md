# ADR-0283: アプリ内ゲーム機能を独立アプリへ分離する

- Status: Accepted
- Date: 2026-10-11
- Supersedes: [ADR-0080](0080-game-section-and-sudoku.md), [ADR-0238](0238-promote-godot-sudoku.md), [ADR-0251](0251-godot-klondike.md), [ADR-0262](0262-restore-current-game-runtime-baseline.md)

## Context

ゲームは情報収集・整理アプリとは異なる利用目的と実行基盤を持つ。ゲームエンジンの Android 統合、native library、画面向き、専用 process、および専用ビルド・テスト設定を本体が保守する必要があった。ゲームは独立アプリで管理する。

## Decision

- アプリ内のゲーム一覧、すべてのゲーム画面・ルール・状態管理とアプリからのナビゲーションを削除する。
- `:feature:game:domain`、`:feature:game:ui`、埋め込みゲームエンジン依存、project assets、専用 Android component、consumer ProGuard rules、専用テスト・開発スクリプトを削除する。
- ゲーム専用の画面向き・全画面制御とサブプロセスの診断例外を削除する。
- 現行仕様と architecture map からゲーム capability を除く。過去の ADR は履歴として残し、廃止した判断であることを明示する。
- 情報収集、蔵書、AI、動画、その他のアプリ機能は変更しない。

## Compatibility / Migration

- ゲーム進行状態は永続化していないため、データベース schema、backup 形式、migration は変更しない。
- 既存のゲーム画面へのナビゲーションは廃止する。ゲーム起動は独立アプリの責務とし、本アプリに暫定ランチャーや互換ルートを残さない。
- ゲーム専用依存と assets は APK に同梱しない。他機能の runtime と診断機能は維持する。

## Verification

- Gradle module graph、画面遷移・セクション、専用依存、Android manifest / tools の参照漏れを確認する。
- 公開リポジトリ検査、architecture verification、unit tests、lint、release build で検証する。
