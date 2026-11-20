# World Cup Scoreboard

A small in-memory Java library that keeps a Live Football World Cup Scoreboard for several
simultaneous matches.

## Operations

1. Start a new match
2. Update the score
3. Finish a match
4. Get a summary of matches in progress, ordered by total score (descending), ties broken by the most
   recently started match first
5. Additional feature: finished match results with automatic cleanup (see below)

## Additional feature: finished match results

`getFinishedMatches()` returns the final results of finished matches, oldest first. `finishMatch` moves the match
from the matches in progress to the results in one step, so a reader never sees it in both or in neither.

Old results are cleaned up automatically: the scoreboard keeps at most `resultsLimit` results and drops the oldest
when a match finishes beyond it. The limit is set once, `new Scoreboard(store, resultsLimit)`, and defaults to 104,
the number of matches in the 48-team 2026 World Cup, so a whole tournament fits without tuning.

```java
Scoreboard board = new Scoreboard(new InMemoryMatchStore(), 10);   // keep the last 10 results
Match match = board.startMatch("Spain", "Brazil");
board.updateScore(match.id(), 2, 1);
board.finishMatch(match.id());
board.getFinishedMatches();                                        // [Spain 2 - Brazil 1]
```

Why this feature: the required operations throw a match away the moment it finishes, so the scoreboard loses the
result exactly when it becomes final. Keeping results is the next thing a results page or group table needs, and
it uses the storage interface beyond the bare minimum (`appendFinished`, `findFinished`, `trimFinished`).

Why cleanup by a limit and not a `clearFinishedMatches()` operation: the assignment allows exactly one additional
operation, and a limit cannot be forgotten by the caller. The cost is that results cannot be dropped on demand.

## Build and test

Requires JDK 25 and Maven 3.9+.

```bash
mvn verify                    # tests, coverage gate (90% lines), format check
bash scripts/build.sh         # same, with a short summary instead of the full Maven output
```

On an older JDK, add `-Djava.version=<version>` locally (e.g. `bash scripts/build.sh verify -Djava.version=21`).

Library code lives in `com.sportradar.scoreboard`. Contributor and agent rules: `AGENTS.md`.

### Project layout

A parent POM at the root and one module, `scoreboard/`, with the library (artifact
`com.sportradar:world-cup-scoreboard`, unchanged by the split). The parent holds everything shared: Java version,
dependency and plugin versions, the test dependencies (JUnit, AssertJ) and the configuration of the compiler,
Surefire, JaCoCo, Spotless and flatten plugins. A module POM only adds what is specific to it, here the jar's
`Automatic-Module-Name`. Further modules (for example a persistent `MatchStore` or a demo app) get the same build
without copying it. Build from the root; the JaCoCo gate applies per module.

### Versioning

Maven CI-friendly versions: the version is `${revision}${sha1}${changelist}`, defined once in the parent
(`1.0` + `-SNAPSHOT`) and inherited by every module. CI or a release overrides it without editing a POM:

```bash
mvn verify -Drevision=1.2.0 -Dchangelist=          # release 1.2.0
mvn verify -Dsha1=-$(git rev-parse --short HEAD)   # 1.0-abc1234-SNAPSHOT, traceable snapshot
```

`flatten-maven-plugin` (mode `resolveCiFriendliesOnly`) writes the resolved version into the installed and
deployed POMs, so consumers never see `${revision}`. The generated `.flattened-pom.xml` files are git-ignored.

### CI

GitHub Actions runs `mvn verify` with a check-only Spotless on every push to `main` and every pull request. The
workflow token is read-only (`permissions: contents: read`), every action is pinned to a full commit SHA with its
version in a comment, and checkouts do not keep the token in `.git/config` (`persist-credentials: false`).
Dependabot (`.github/dependabot.yml`) proposes weekly updates to the pinned actions. A Snyk
dependency scan (`snyk test`, all modules, fails on high severity or above) runs only when the repository has a
`SNYK_TOKEN` secret; without it the job shows as skipped and the build stays green. Pull requests from forks get
no secrets, so they skip it too.

## Design

Implementation plan and API specification: [`docs/implementation-plan.md`](docs/implementation-plan.md).
In short: `Scoreboard` is the thread-safe entry point and owns every business rule; storage is pluggable through
the `MatchStore` interface, with `InMemoryMatchStore` as the default.

## Usage

```java
Scoreboard board = new Scoreboard();                    // in-memory; or new Scoreboard(myStore)
Match match = board.startMatch("Mexico", "Canada");     // 0 - 0, returns the match with its id
board.updateScore(match.id(), 0, 5);                    // absolute score
board.getSummary();                                     // [Mexico 0 - Canada 5]
board.finishMatch(match.id());                          // returns Mexico 0 - Canada 5
```

### Custom storage

Implement `com.sportradar.scoreboard.store.MatchStore` and pass it to the constructor. A store only keeps and
returns matches; the scoreboard enforces every rule, so a store cannot change behaviour. The scoreboard calls its
store under its own lock, so a store used by one scoreboard need not be thread-safe. The abstract
`MatchStoreContractTest` in the test sources lists the rules a store must follow; `InMemoryMatchStoreTest` shows
how to run it against an implementation.

## Assumptions

- A match is identified by the `MatchId` returned from `startMatch`, not by its team names.
- Team names are stripped, normalized to Unicode NFC and otherwise kept as given. They are compared ignoring case,
  so "Spain" and "spain" are the same team, and NFC makes "C\u00F4te d'Ivoire" (precomposed o with
  circumflex) and "Co\u0302te d'Ivoire" (o plus a combining circumflex) the same team too.
- A team name is at most `Match.MAX_TEAM_NAME_LENGTH` (64) code points after stripping and normalization; the
  longest FIFA member name has about 30. Control characters and invisible format characters (zero-width space and
  joiners, soft hyphen, bidi overrides, byte order mark: Unicode categories Cc and Cf) are rejected, not removed.
  Otherwise "Spa\u200Bin" would look like "Spain" on screen but count as another team, and slip past the same-team
  and already-playing checks.
- NFC is canonical, not compatibility, normalization: fullwidth or styled letters and lookalikes from other scripts
  (a Cyrillic `U+0430` "a" in "Spain") are still different teams. Catching those needs a confusables table, which is out of
  scope for a scoreboard that receives names from its own operator.
- A team plays at most one match at a time. Starting a match for a team already in a match in progress throws
  `TeamAlreadyPlayingException`; this also catches starting the same match twice. A team may play again once its
  match has finished.
- Scores are absolute (`updateScore(id, 2, 1)`), never negative, and may go down, to allow corrections such as a
  disallowed goal.
- A score is at most `Match.MAX_SCORE` (999). That is far above any real football score and keeps `totalScore()`
  of two maximum scores well inside `int`, so the summary ordering can never be broken by an overflow.
- Updating or finishing an unknown or already finished match throws `MatchNotFoundException`; finishing twice is a
  caller error, not a no-op.
- Every invalid argument throws `IllegalArgumentException`: null arguments, blank, overlong or hidden-character
  names, the same team on both sides, and scores below 0 or above the maximum. Callers handle one exception type for bad input; the library never throws
  `NullPointerException` on purpose.
- "Most recently started" means the order of `startMatch` calls, not a clock.

## Reasoning

- **One class owns the rules.** `Scoreboard` validates input, enforces one match per team and sorts the summary.
  Keeping this out of the store means a custom store is a few lines of plumbing and cannot break the ordering.
- **Start order is a sequence, not a timestamp.** Two matches started in the same millisecond would tie on a clock.
  The store hands out a strictly increasing sequence, so ties are impossible and tests need no clock. The store owns
  the counter so a persistent store keeps the order across restarts.
- **Immutable snapshots.** `Match` and `MatchId` are records and every change produces a new `Match`. Lists returned
  by the scoreboard are unmodifiable copies, so callers cannot change the scoreboard from outside and can share
  results between threads.
- **Thread safety with one read/write lock.** `startMatch` checks that a team is free and then saves the match;
  that check-then-act must be atomic. Writes take the write lock, summaries take the read lock and run in parallel.
- **Unchecked exceptions with a common base.** `ScoreboardException` lets callers catch scoreboard conflicts in one
  place without checked exceptions on every call.
- **Tests first.** Each step started with a failing test. The assignment's example is an acceptance test in
  `ScoreboardTest#summaryMatchesAssignmentExample`, and `ScoreboardConcurrencyTest` covers parallel use.

## Trade-offs

- **`MatchId` over team-pair lookup.** Callers keep one extra value, in exchange for a key that does not depend on
  name spelling and is cheap to look up.
- **A single lock over lock-free structures.** Writes are serialised. That is simpler to reason about and more than
  fast enough for the handful of matches a scoreboard holds at once.
- **Sorting on every `getSummary`.** O(n log n) per call instead of maintaining a sorted structure on every write.
  With a few dozen matches this costs nothing and keeps the store trivial.
- **Store holds no rules.** Custom stores are easy to write but cannot push the ordering down (for example
  `ORDER BY` in SQL). An optional sorted query could be added later.
- **Results cleanup by count, not age or explicit clear.** See the additional feature above. The in-memory store
  keeps results in an `ArrayDeque`, so removing the oldest is O(1).
- **The storage interface grew with the feature.** Adding `appendFinished`, `findFinished` and `trimFinished` would
  break custom stores written against the first version. Accepted because nothing is released yet; after a release
  this would use default methods or a separate interface.
- **Multi-module build for a single library.** One module does not need a parent POM yet. Splitting now keeps
  shared build setup in one place, so a new module (a persistent store, a demo) is a short POM, at the cost of one
  extra POM and a `scoreboard/` directory level.
- **Snyk as an optional job.** Skipped without a token instead of failing, so forks and clones build without a
  Snyk account. The cost is that a missing secret silently skips the scan; the skipped job is visible in the checks.
- **No persistence, events or multi-process consistency.** Out of scope for an in-memory library; the storage
  interface is the extension point if they are needed.
