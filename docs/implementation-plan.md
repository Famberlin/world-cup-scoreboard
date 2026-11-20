# Implementation plan and API specification

Status: implemented. This document is the contract the implementation was built and reviewed against.
One deviation: a null team name or id throws `NullPointerException` (the JDK convention), not
`IllegalArgumentException`; blank names and negative scores still throw `IllegalArgumentException`.
Decisions summarised here are carried into `README.md` (assumptions, reasoning, trade-offs) as they are implemented.

## 1. Goals and scope

- A plain Java library (no frameworks, no runtime dependencies) that keeps a live scoreboard of several
  simultaneous matches.
- Required operations: start a match, update its score, finish it, and get a summary of matches in progress
  ordered by total score (descending), ties broken by the most recently started match first.
- Exactly one additional operation, introduced in its own commit: **finished match results** (section 7).
- Storage is pluggable: the library defines a storage interface (SPI) that users implement to keep matches
  wherever they want. The library ships a default in-memory implementation, used when no store is given.

Out of scope: persistence beyond the in-memory store, networking, a UI, multi-process consistency of a shared
store, tournament structure (groups, rounds), extra time and penalties.

## 2. Package layout

```
com.sportradar.scoreboard              public API
  Scoreboard                           entry point, enforces all business rules
  Match                                immutable snapshot of a match in progress (record)
  MatchId                              opaque match identifier (record)
  ScoreboardException                  base unchecked exception
  MatchNotFoundException               unknown or already finished match
  TeamAlreadyPlayingException          a team is already in a match in progress

com.sportradar.scoreboard.store        storage SPI
  MatchStore                           interface users implement for custom storage
  InMemoryMatchStore                   default implementation
```

Anything not listed stays package-private.

## 3. API specification

### 3.1 `Scoreboard`

```java
public final class Scoreboard {

    /** Scoreboard backed by a new {@link InMemoryMatchStore}. */
    public Scoreboard();

    /** Scoreboard backed by the given store. The scoreboard becomes the store's only writer. */
    public Scoreboard(MatchStore store);

    /**
     * Extra feature. Like {@link #Scoreboard(MatchStore)}, keeping at most {@code resultsLimit}
     * finished results; the oldest are removed first. Default limit: 104.
     *
     * @throws IllegalArgumentException  resultsLimit is less than 1
     */
    public Scoreboard(MatchStore store, int resultsLimit);

    /**
     * Starts a match with score 0 - 0 and adds it to the scoreboard.
     *
     * @return snapshot of the new match, carrying the id used by the other operations
     * @throws NullPointerException         a team name is null
     * @throws IllegalArgumentException     a team name is blank, or both names are the same team
     * @throws TeamAlreadyPlayingException  either team is already in a match in progress
     */
    public Match startMatch(String homeTeam, String awayTeam);

    /**
     * Replaces the score of a match in progress with the given absolute score.
     *
     * @return snapshot of the updated match
     * @throws IllegalArgumentException  a score is negative, or id is null
     * @throws MatchNotFoundException    no match in progress has this id
     */
    public Match updateScore(MatchId id, int homeScore, int awayScore);

    /**
     * Finishes a match in progress and removes it from the scoreboard. Once the extra feature
     * lands, the final snapshot is also kept as a result (see getFinishedMatches).
     *
     * @return the final snapshot of the match
     * @throws MatchNotFoundException  no match in progress has this id (including one already finished)
     */
    public Match finishMatch(MatchId id);

    /**
     * Matches in progress, ordered by total score descending, ties broken by the most recently
     * started match first.
     *
     * @return an unmodifiable snapshot; later changes to the scoreboard do not affect it
     */
    public List<Match> getSummary();

    /**
     * Extra feature. Final results of finished matches, in the order they were finished
     * (oldest first). Holds at most the results limit; older results have been cleaned up.
     *
     * @return an unmodifiable snapshot
     */
    public List<Match> getFinishedMatches();
}
```

### 3.2 `Match` and `MatchId`

```java
/** Immutable snapshot of a match in progress. */
public record Match(
        MatchId id,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        long startSequence) {

    // compact constructor: non-null id and teams, non-blank teams, non-negative scores

    public int totalScore();                                   // homeScore + awayScore
    public Match withScore(int homeScore, int awayScore);      // new snapshot, same id and sequence
    @Override public String toString();                        // "Uruguay 6 - Italy 6"
}

/** Opaque identifier of a match. Equality by value. */
public record MatchId(UUID value) {
    static MatchId random();   // package-private factory used by Scoreboard
}
```

`Match` has a public canonical constructor so custom stores can rebuild matches from their own representation
(database rows, JSON, and so on).

### 3.3 Storage SPI: `MatchStore`

The store is deliberately simple: it keeps and returns matches. It holds no business rules (validation,
uniqueness, ordering); those live in `Scoreboard`, so a custom store cannot change how the scoreboard behaves.

```java
public interface MatchStore {

    /**
     * Next value of a strictly increasing sequence, used to order matches by start time.
     * It must keep increasing for the lifetime of the stored data (a persistent store must
     * persist it as well).
     */
    long nextSequence();

    /** Inserts the match, or replaces the stored match with the same id. */
    void save(Match match);

    /** The match in progress with this id, if any. */
    Optional<Match> findById(MatchId id);

    /** Removes the match with this id. Returns whether a match was removed. */
    boolean delete(MatchId id);

    /** All matches in progress, in no particular order. */
    Collection<Match> findAll();

    /**
     * Whether the team takes part in a match in progress. The default scans {@link #findAll()};
     * stores with an index should override it.
     */
    default boolean isTeamPlaying(String team) { ... }

    // Added by the extra feature (section 7):

    /** Appends the final snapshot of a finished match to the results log. */
    void appendFinished(Match match);

    /** Finished matches in the order they were appended. */
    List<Match> findFinished();

    /** Removes the oldest finished matches until at most {@code maxResults} remain. */
    void trimFinished(int maxResults);
}
```

Matches in progress and finished results are kept apart: `findAll` and `isTeamPlaying` only ever see matches in
progress, so a team can start a new match after its previous one finished.

Thread-safety contract: `Scoreboard` calls the store only while holding its own lock, so a store used by a single
`Scoreboard` does not need to be thread-safe. A store shared by several `Scoreboard` instances or processes is
responsible for its own consistency; this is documented and out of scope.

`InMemoryMatchStore`: a `HashMap<MatchId, Match>`, a `long` counter and, with the extra feature, an
`ArrayDeque<Match>` of results (cheap removal of the oldest). Not thread-safe on its own, by the
contract above.

A reusable abstract contract test (`MatchStoreContractTest`) checks the SPI rules. The in-memory store's test
extends it, and it is the template a custom store would follow.

### 3.4 Usage

```java
Scoreboard board = new Scoreboard();                   // or new Scoreboard(myStore)
Match match = board.startMatch("Mexico", "Canada");
board.updateScore(match.id(), 0, 5);
board.getSummary();                                    // [Mexico 0 - Canada 5]
board.finishMatch(match.id());
```

## 4. Decisions on the open questions

| Question | Decision | Why |
|---|---|---|
| How is a match identified? | By the `MatchId` returned from `startMatch`. | Stable even if names are corrected, cheap to look up, and does not depend on name spelling or case. Team-pair lookup could be added later on top. |
| Team name validation | `null` or blank is rejected with `IllegalArgumentException`. Names are stripped of surrounding whitespace and stored as given otherwise. | Fail fast on input that can never be valid. |
| Same team on both sides | Rejected (`IllegalArgumentException`), compared case-insensitively. | "Spain - spain" is not a match. |
| Team already playing | Rejected with `TeamAlreadyPlayingException`, compared case-insensitively. | A team cannot play two matches at the same time; this also catches duplicate starts of the same match. |
| Score updates | Absolute scores (`updateScore(id, 2, 1)`), not increments. Negative values rejected. Lower scores than before are allowed. | Absolute values are idempotent and match how feeds usually deliver scores; allowing decreases supports corrections such as a disallowed goal. |
| Unknown or finished match | `MatchNotFoundException` for update and finish. Finishing twice is an error, not a no-op. | Silently ignoring it would hide bugs in the caller. |
| "Most recently started" with equal timestamps | Ordering uses a strictly increasing `startSequence` from the store, not a clock. | Two matches started in the same millisecond still have a defined order, and tests need no clock. The store owns the counter so a persistent store keeps the order across restarts. |
| Concurrency | `Scoreboard` is thread-safe. One `ReentrantReadWriteLock`: start, update and finish take the write lock (they check and then change state), `getSummary` takes the read lock. | Check-then-act steps (team already playing, then save) must be atomic. A single lock is simple and enough for the handful of live matches a scoreboard holds. |
| Immutability | `Match` and `MatchId` are records; `getSummary` returns `List.copyOf(...)`. Every change produces a new `Match`. | Callers can share snapshots between threads and cannot change the scoreboard from outside. |
| Where does sorting happen? | In `Scoreboard`, after `findAll()`. | The ordering rule stays in one place and cannot be broken by a custom store. Sorting a few dozen matches per call costs nothing. |
| Exceptions | Unchecked. `IllegalArgumentException` for bad arguments; `ScoreboardException` subclasses for state conflicts. | Callers can catch scoreboard problems specifically without checked exceptions on every call. |

## 5. Trade-offs to record in the README

- `MatchId` over team-pair lookup: one extra value for the caller to keep, in exchange for a stable key.
- Single lock over lock-free structures: simpler to reason about, at the cost of serialising writes. Fine for the
  expected load.
- Sorting on every `getSummary` call over keeping a sorted structure: O(n log n) per call, but n is tiny and the
  store stays trivial to implement.
- Store holds no rules: custom stores are easy to write, but cannot optimise ordering (for example
  `ORDER BY` in SQL). An optional sorted query could be added later without breaking the SPI.
- Cleanup of finished results is automatic, by a count limit, not an explicit `clear` operation. An extra public
  `clearFinishedMatches()` would be a second additional operation, which the assignment rules out; a limit also
  cannot be forgotten by the caller. Cost: a caller cannot drop results on demand, and the limit counts matches,
  not age.
- Default limit 104, the number of matches in the 48-team 2026 World Cup, so a whole tournament fits without
  tuning while memory stays bounded for a long-running service.
- The results log is append-only and ordered by the store (insertion order). That is the one ordering a store
  owns, because it is the natural behaviour of any log or table with an auto-increment key.
- Adding `appendFinished`, `findFinished` and `trimFinished` to `MatchStore` in the feature commit breaks custom stores written
  against the earlier SPI. Accepted: the library has no released version yet. After a release this would be done
  with default methods or a separate `ResultStore` interface.

## 6. Implementation steps

Test first in every step. Each step is one or two small commits on its own `feature/...` branch and pull request.

1. **Domain types**: `MatchId`, `Match` (validation, `totalScore`, `withScore`, `toString`), exceptions.
2. **Storage SPI**: `MatchStore`, `InMemoryMatchStore`, abstract `MatchStoreContractTest` with the in-memory
   test extending it.
3. **Start and finish**: `Scoreboard` constructors, `startMatch` with all validation rules, `finishMatch`.
4. **Update score**: `updateScore` with validation and the not-found case.
5. **Summary**: `getSummary` ordering, including the assignment's example scenario as an acceptance test and an
   equal-total tie test.
6. **Concurrency**: test that parallel `startMatch` calls with the same team result in exactly one match, and
   that summaries taken during updates are consistent snapshots.
7. **Extra feature, finished match results with cleanup**: one distinct commit adding `appendFinished`,
   `findFinished` and `trimFinished` to the SPI and the in-memory store (with contract tests), the `resultsLimit`
   constructor, `finishMatch` appending the final snapshot and trimming, and `getFinishedMatches()`. Tests: a
   finished match appears in results with its final score and leaves the summary; results keep finish order; a team
   can start again after finishing; with limit 2, finishing a third match drops the oldest result; a limit below 1
   is rejected; the returned list is unmodifiable. README
   section explaining the feature and why it was chosen.
8. **Documentation**: README assumptions, reasoning, trade-offs; AI.md prompt history updated along the way.

`bash scripts/build.sh` (tests, 90% line coverage gate, Spotless) must pass before each pull request.

## 7. Extra feature: finished match results, with cleanup

Chosen by Daniel from four candidates (score change listener, goal scored, finished match results, find a team's
match).

**Operation**: `List<Match> getFinishedMatches()` returns the final results of finished matches, in the order the
matches finished. Old results are cleaned up automatically so storage cannot grow without bound.

**Why**: the required operations throw a match away when it finishes, so the scoreboard loses the result the moment
the match ends. Keeping final results is what a results page or a group table would need next, and it exercises the
storage SPI beyond the bare minimum.

**Behaviour**:

- `finishMatch` removes the match from the matches in progress and appends its final snapshot to the results, as
  one step under the write lock, so a reader never sees the match in both or in neither.
- `getFinishedMatches` takes the read lock and returns `List.copyOf(store.findFinished())`.
- Cleanup: right after appending, `finishMatch` calls `store.trimFinished(resultsLimit)`, still under the same
  lock. The oldest results go first. The limit is set once in the constructor (default 104, minimum 1).
- Results are never changed by the library, only removed by the cleanup; there is no "un-finish".
- A team in the results can start a new match: only matches in progress count for `TeamAlreadyPlayingException`.
