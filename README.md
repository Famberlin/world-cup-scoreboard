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
- Team names are trimmed and otherwise kept as given. They are compared ignoring case, so "Spain" and "spain" are
  the same team.
- A team plays at most one match at a time. Starting a match for a team already in a match in progress throws
  `TeamAlreadyPlayingException`; this also catches starting the same match twice. A team may play again once its
  match has finished.
- Scores are absolute (`updateScore(id, 2, 1)`), never negative, and may go down, to allow corrections such as a
  disallowed goal.
- Updating or finishing an unknown or already finished match throws `MatchNotFoundException`; finishing twice is a
  caller error, not a no-op.
- Null arguments throw `NullPointerException`; blank names, the same team on both sides and negative scores throw
  `IllegalArgumentException`.
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
- **No persistence, events or multi-process consistency.** Out of scope for an in-memory library; the storage
  interface is the extension point if they are needed.
