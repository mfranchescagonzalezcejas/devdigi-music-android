# Tasks: Navidrome Recent Albums

## WU1 — Domain and protocol contract

- [x] 1.1 Add account-scoped recent-album domain model.
- [x] 1.2 Add repository/result contract with distinct authentication,
      network, malformed-response and server-error outcomes.
- [x] 1.3 Add RED domain ownership tests.
- [x] 1.4 Add RED OpenSubsonic recent-albums parser tests.
- [x] 1.5 Implement strict bounded parser preserving server order.
- [x] 1.6 Run focused tests and complete unit-test suite.
- [x] 1.7 Pass Spotless, lint and debug assembly.

## WU2 — Authenticated Navidrome data boundary

- [x] 2.1 RED tests for authenticated `getAlbumList2` requests.
- [x] 2.2 Implement account-bound secure-credential lookup.
- [x] 2.3 Implement OkHttp recent-albums request boundary.
- [ ] 2.4 Enforce redirect, timeout, response-size and secret-leak protections.
- [ ] 2.5 Map transport/protocol failures to domain outcomes.
- [ ] 2.6 Verify focused and complete test suites.

## WU3 — Presentation and wiring

- [ ] 3.1 Add recent-albums ViewModel/state tests.
- [ ] 3.2 Implement account-aware loading and stale-result rejection.
- [ ] 3.3 Implement adaptive recent-albums Compose surface.
- [ ] 3.4 Wire the feature from the authenticated session in the composition root.
- [ ] 3.5 Preserve loading, empty, authentication, network and malformed states.
- [ ] 3.6 Run full local verification and real-instance validation for #2.
