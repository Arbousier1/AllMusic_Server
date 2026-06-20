# AllMusic refactor rules

## Execution policy
- Do not run local Gradle builds, local compile tasks, or local test builds on the user's computer unless the user explicitly asks.
- After each phase, commit and push to GitHub, then rely on GitHub Actions for build verification.
- If local verification is needed, only use lightweight checks such as `git status`, `git diff`, `git diff --check`, file searches, and source inspection.
- Do not include `.claude/`, local build outputs, Gradle caches, Kotlin caches, or unrelated dirty submodule changes in commits.
- Keep each phase small. Do not rewrite large core files in one step.
- Preserve command behavior, playback behavior, list behavior, vote behavior, search behavior, provider behavior, and client protocol data unless the user explicitly asks for behavior changes.

## Completed phases
- Phase 0: fixed CI compile errors.
- Phase 1: extracted `MusicApiRegistry`.
- Phase 2: scanned platform cross references; no code changes needed.
- Phase 3: extracted pure plugin-message protocol/state bridge while keeping platform-specific scheduling/sending in Paper, Spigot, and Folia.

## Current recommended sequence
1. Phase 4.1: extract `PlaybackQueue` from `PlayMusic`.
2. Phase 4.2: extract `VoteService` from `PlayMusic`.
3. Phase 4.3: extract `PushService` from `PlayMusic`.
4. Phase 4.4: extract `PlaybackState` from `PlayMusic`.
5. Phase 5: refactor search service.
6. Phase 6: isolate music providers.
7. Phase 7: unify HTTP, Cookie, and Auth services.
8. Phase 8: introduce `AllMusicContext` gradually.
9. Phase 9: split Gradle modules last.

## Phase 4 playback split
### Current problem
`PlayMusic.java` is too large and currently owns queue management, playlist state, current playback state, voting, push/insert logic, idle playback, song checks, message sending, playback control, and thread loops.

### Target package
`core/music/playback`

### Target classes
- `PlaybackService`
- `PlaybackQueue`
- `PlaybackState`
- `VoteService`
- `PushService`
- `IdlePlaylistService`

### Phase 4.1 PlaybackQueue scope
Move only these responsibilities first:
- `playList`
- `PLAY_LIST_LOCK`
- `haveMusic`
- `getList`
- `getListSize`
- `getAllList`
- `findMusicIndex`
- `findPlayerMusic`

`PlayMusic` must continue to expose the same public/static API and delegate to `PlaybackQueue` for compatibility.

### Phase 4.2 VoteService scope
Move:
- `votePlayer`
- `voteTime`
- `voteSender`
- `addVote`
- `startVote`
- `clearVote`
- `containVote`

### Phase 4.3 PushService scope
Move:
- `pushPlayer`
- `pushTime`
- `pushSender`
- `push`

### Phase 4.4 PlaybackState scope
Move:
- `nowPlayMusic`
- `lyric`
- `url`
- `musicAllTime`
- `musicLessTime`
- `musicNowTime`
- `error`

### Phase 4 acceptance
- CI passes on GitHub Actions.
- Command behavior unchanged.
- Playback list behavior unchanged.
- Vote behavior unchanged.
- Client-received data unchanged.

## Phase 5 search service
### Target package
`core/music/search`

### Target classes
- `SearchService`
- `SearchSessionStore`
- `SearchAggregator`
- `SearchPresenter`

### Acceptance
- `/music search` behavior unchanged.
- `/music select` behavior unchanged.
- `all` search behavior unchanged.
- API label display unchanged.
- CI passes on GitHub Actions.

## Phase 6 provider isolation
### Target package direction
- `core/music/provider`
- provider-specific implementation packages for Netease, QQ, Kugou, Kuwo, and Baidu.

### Direction
- Keep `IMusicApi` short-term if needed.
- Providers should return data, not send messages or save playlists directly.
- Provider internals should reduce direct dependencies on `AllMusic.getMessage()`, `AllMusic.log`, `MusicListSave`, and command argument objects.

### Acceptance
- Netease, QQ, Kugou, Kuwo, and Baidu behavior unchanged.
- Cookie login unchanged.
- Playlist import unchanged.
- CI passes on GitHub Actions.

## Phase 7 HTTP / Cookie / Auth
### Target packages
- `core/http`
- `core/auth`

### Target services
- `HttpClientService`
- `HttpRequest`
- `HttpResponse`
- `CookieStoreService`
- `CookieImportService`
- `QrLoginService`
- `BrowserCookieImporter`
- `NeteaseQrLoginProvider`

### Acceptance
- Cookie import unchanged.
- QR login unchanged.
- All provider requests unchanged.
- CI passes on GitHub Actions.

## Phase 8 AllMusicContext
Introduce `AllMusicContext` gradually. Keep old `AllMusic` static compatibility methods at first, but internally delegate to context where safe.

Target dependencies include:
- config
- message
- registry
- playback
- search
- platform bridge
- logger
- cookie

Acceptance:
- reload lifecycle is cleaner.
- stop lifecycle shuts down threads correctly.
- external API remains compatible.
- CI passes on GitHub Actions.

## Phase 9 Gradle module split
Do this last only after code structure is stable.

Potential target modules:
- `server-api`
- `server-core`
- `server-provider`
- `server-platform-common`
- `server-bukkit-common`
- `server-paper`
- `server-spigot`

Acceptance:
- all platform jars build correctly in GitHub Actions.
- shadow contents are correct.
- client/codec references are correct.
- release artifacts upload correctly.
