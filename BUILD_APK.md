# APKの作り方

## GitHub Actions
1. このプロジェクトをGitHubリポジトリへ置きます。
2. GitHubの **Actions** → **Build Android APK** → **Run workflow** を実行します。
3. 完了後、Artifacts の **CombiniDiet-debug-apk** から APK を取得します。

ワークフローは JDK 17 / Gradle 9.6.0 で `assembleDebug` を実行します。
