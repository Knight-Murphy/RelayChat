# RelayChat Project Instructions

## APK Build Workflow

- After every code or resource change, build and update only the test APK first:
  `.\build-apk.ps1 -TestBuild`
- The test artifact is `dist\RelayChat-test.apk` with application ID
  `com.relaychat.app.debug`.
- Do not build, replace, or distribute the final/normal APK as part of the initial
  change.
- Build the final/normal APK only after the user explicitly approves the test APK
  for that round of changes. Then run `.\build-apk.ps1` without `-TestBuild`, which
  writes `dist\RelayChat-debug.apk`.
- Approval applies only to the current round of changes. Later changes require a
  new test APK and a new explicit approval before the final APK is built.
