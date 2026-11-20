# World Cup Scoreboard

A small in-memory Java library that keeps a Live Football World Cup Scoreboard for several
simultaneous matches.

## Operations

1. Start a new match
2. Update the score
3. Finish a match
4. Get a summary of matches in progress, ordered by total score (descending), ties broken by the most
   recently started match first
5. Additional feature: _to be decided_

## Build and test

Requires JDK 25 and Maven 3.9+.

```bash
mvn verify                    # tests, coverage gate (90% lines), format check
bash scripts/build.sh         # same, with a short summary instead of the full Maven output
```

On an older JDK, add `-Djava.version=<version>` locally (e.g. `bash scripts/build.sh verify -Djava.version=21`).

Library code lives in `com.sportradar.scoreboard`. Contributor and agent rules: `AGENTS.md`.

## Assumptions

_To be written._

## Reasoning

_To be written._

## Trade-offs

_To be written._
