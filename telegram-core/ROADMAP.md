# Telegram Core Roadmap

The branch is implementation-first. A milestone is only complete when its code path is buildable and covered by a deterministic test or a real TDLib integration check.

## Phase A — contract

1. Public API boundary
2. Transport seam
3. Stable authorization state model
4. Deterministic API contract tests

## Phase B — real TDLib runtime

5. Pin an exact TDLib source commit
6. Reproducible Android native build for supported ABIs
7. Package TDLib JSON/JNI runtime without exposing generated classes to the host
8. Initialize TDLib parameters and encrypted local database
9. Continuous update receiver with request correlation

## Phase C — account lifecycle

10. Phone authorization
11. Email authorization when requested by TDLib
12. Password / 2-step verification
13. Registration and terms-of-service handling
14. QR authentication
15. Correct logout versus process shutdown semantics
16. Explicit handling for every authorization state; never treat unknown as READY

## Phase D — messenger core

17. Chat cache driven by updateNewChat/updateChat* events
18. Chat-list pagination backed by TDLib loadChats/getChats semantics
19. Message history pagination with exact TDLib ordering semantics
20. Text sending and send-result/update reconciliation
21. Photo sending
22. Video sending
23. Document sending
24. Upload progress and failed-send state
25. Network recovery and retry policy

## Phase E — host integration

26. Android storage/session adapter
27. Android lifecycle-safe client owner
28. Camera/gallery media adapter
29. GPS/EXIF integration stays outside Telegram Core
30. Integration demo: camera -> media file -> Telegram chat -> sent message

## Phase F — release gate

31. Unit tests
32. TDLib integration tests with a disposable test account/session
33. Android ABI matrix build
34. Clean CI from a fresh checkout
35. Security review: no credentials, sessions or raw auth payloads in source/logs
36. Versioned release artifact and migration notes

## Non-negotiable completion criteria

- No Bot API dependency.
- No Telegram transport classes in `telegram.core.api`.
- No fake success paths.
- No hard-coded API credentials.
- No claim of production readiness until the real TDLib runtime passes the integration gate.
