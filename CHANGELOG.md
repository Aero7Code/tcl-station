# Changelog

## [0.1.0-alpha.2] — 2026-10-09

### Added
- In-app, foreground-only live camera preview with front/back switching.
- Tests for camera lifecycle, retired-clip cleanup, and recorder-free APK packaging.

### Removed
- Experimental local recording service, controls, permissions, and recording tests.

### Changed
- On upgrade, attempt to remove only clips matching the retired recorder's exact filename pattern in its app-private directory.
- Update the privacy policy, terms, and README for live-only camera behavior.

The previous alpha (`v0.1.0-alpha.1`) included an experimental recorder; its historical source remains in Git history, but that version is not the current build.
