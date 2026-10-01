# Tasks: Navidrome Album Details

## WU1A — Domain contract

- [x] 1.1 Define account-scoped album-details and track models.
- [x] 1.2 Define repository/result contract with distinct authentication,
      network, malformed-response and server-error outcomes.
- [x] 1.3 Add RED domain ownership and transport-separation tests.
- [x] 1.4 Run focused tests and complete unit-test suite.
- [x] 1.5 Pass Spotless, lint and debug assembly.

## WU1B — Protocol parser

- [ ] 1.6 Add RED OpenSubsonic `getAlbum` parser tests.
- [ ] 1.7 Implement strict bounded parser preserving song order.
- [ ] 1.8 Cover empty tracks, optional metadata and protocol failures.
- [ ] 1.9 Verify focused and complete test suites.

## WU2 — Authenticated album data boundary

- [ ] 2.1 Add RED authenticated `getAlbum` request tests.
- [ ] 2.2 Implement exact-account secure-credential lookup.
- [ ] 2.3 Implement authenticated OkHttp album request boundary.
- [ ] 2.4 Enforce redirects, timeout, response-size and secret protections.
- [ ] 2.5 Map transport/protocol failures to domain outcomes.
- [ ] 2.6 Verify focused and complete test suites.

## WU3 — Presentation and wiring

- [ ] 3.1 Add account-aware album-details presentation state.
- [ ] 3.2 Reject stale results after album/account changes.
- [ ] 3.3 Implement responsive album-details Compose surface.
- [ ] 3.4 Render ordered selectable track list and safe metadata fallbacks.
- [ ] 3.5 Wire recent-album selection into album details.
- [ ] 3.6 Run full local and real-instance validation.
