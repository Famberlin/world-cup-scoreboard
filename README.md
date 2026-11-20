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

## Assumptions

_To be written as the implementation lands. The proposed answers to the open questions are in
[`docs/implementation-plan.md`](docs/implementation-plan.md#4-decisions-on-the-open-questions)._

## Reasoning

_To be written._

## Trade-offs

_To be written._
