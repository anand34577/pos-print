# Changelog

All notable changes to this project are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.1] - 2026-11-07

### Changed
- Tagged releases now build a signed APK in CI and attach it to the GitHub release.

### Fixed
- CI failed on Linux because the Gradle wrapper was not executable.

## [0.1.0] - 2026-11-05

First build, not yet tested on a physical printer.

### Added
- Bluetooth Classic and USB host connections, with auto-reconnect and plain-language error messages.
- Background print queue with retry, cancel, and reprint from history.
- ESC/POS receipt printing: text styles, tables, images, QR codes, 1D barcodes, PDF417, cash drawer, cut, beep.
- Label printing in TSPL, ZPL, EPL and CPCL, with a drag-and-resize designer and CSV or .xlsx batch import.
- Template editor with live preview, `{{variables}}`, filters and conditionals; JSON import and export.
- A4, A5 and Letter printing through the Android Print Framework, plus PDF export.
- Quick Print, Mini POS, share-sheet printing, camera scan-to-print, backup and restore.
- 41 unit tests covering the receipt, label and template engines.
