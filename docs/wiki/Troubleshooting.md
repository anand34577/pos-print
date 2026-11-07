# Troubleshooting

**No Bluetooth devices show up on Android 11 or older.** Grant the Location permission and turn Location on. Android needs it for scanning.

**Bluetooth connection fails.** Another phone may be holding the printer. Switch the printer off and on. If in-app pairing fails, pair it in the system Bluetooth settings first.

**USB: nothing is listed.** Use a data cable, not a charge-only one. Tap Allow on the prompt, or replug the cable.

**The ruler wraps or is cut off on the test page.** Switch the paper width between 58 and 80 mm, or set characters per line in the printer profile.

**Garbled symbols.** Pick the matching code page in the profile. Text outside that code page prints as an image automatically.

**Print is faint or too dark.** Turn on "Set print density" in the profile. Some clones ignore it.

**Labels are misaligned or skip.** Match the media type (gap, black mark, continuous) and gap height to your roll, then run the printer's own calibration.

**Label image looks inverted.** Bitmap polarity follows each language: TSPL and EPL use 0 for black, ZPL and CPCL use 1. Report your printer model in an issue.

**No paper-out or cover-open warnings.** Many clones never answer status queries, so the app assumes everything is fine.

**Still stuck.** Open Settings, then Developer tools, and send raw hex or read the command log. Attach what you find to an issue.
