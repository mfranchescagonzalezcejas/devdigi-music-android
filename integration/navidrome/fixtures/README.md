# Synthetic Navidrome fixtures

These files are original synthetic test media generated from mathematical
sine waves. They contain no personal or copyrighted source audio.

Canonical metadata:

- Artist: `DevDigi Synthetic Artist`
- Album: `DevDigi Synthetic Album`
- Track 1: `Synthetic Track A`, 440 Hz, one second
- Track 2: `Synthetic Track B`, 660 Hz, one second
- Audio: mono, 44.1 kHz, signed 16-bit source, FLAC compression level 8

Canonical SHA-256:

- `01-synthetic-a.flac`
  `cd06e0ff28d4d162a703d083d15e2d240b2863f91bed2b6b05bfb3ba37891ac7`
- `02-synthetic-b.flac`
  `82cdb3c79e6880649c7a45e2f8adf95ac10b4cd1ba74682317b322f8f3c39114`

The canonical committed bytes are the integration-test source of truth.
Runtime and CI execution do not require FFmpeg.

The files were generated from FFmpeg `lavfi` sine sources with existing
metadata removed before the deterministic metadata above was added.

Navidrome-generated album and track identifiers are deliberately not
recorded or assumed by the integration suite.
