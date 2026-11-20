package com.sportradar.scoreboard;

import com.sportradar.scoreboard.store.InMemoryMatchStore;
import com.sportradar.scoreboard.store.MatchStore;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Live Football World Cup scoreboard: keeps the matches in progress and their scores.
 *
 * <p>All business rules live here: input validation, one match in progress per team, and the summary order. The
 * matches themselves are kept in a {@link MatchStore}.
 *
 * <p>Finished matches are kept as results (see {@link #getFinishedMatches()}); the oldest are cleaned up once there
 * are more than the results limit.
 *
 * <p>This class is thread-safe. Operations that change the scoreboard run one at a time; summaries can be read in
 * parallel. Every returned {@link Match} and list is an immutable snapshot.
 */
public final class Scoreboard {

    /** Total score descending, then most recently started first. */
    static final Comparator<Match> SUMMARY_ORDER = Comparator.comparingInt(Match::totalScore)
            .thenComparingLong(Match::startSequence)
            .reversed();

    /** Results kept by default: the number of matches in the 48-team 2026 World Cup. */
    public static final int DEFAULT_RESULTS_LIMIT = 104;

    private final MatchStore store;
    private final int resultsLimit;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Creates a scoreboard backed by a new {@link InMemoryMatchStore}. */
    public Scoreboard() {
        this(new InMemoryMatchStore());
    }

    /**
     * Creates a scoreboard backed by the given store. The scoreboard becomes the store's only writer.
     *
     * @param store where matches in progress are kept
     * @throws IllegalArgumentException if {@code store} is null
     */
    public Scoreboard(MatchStore store) {
        this(store, DEFAULT_RESULTS_LIMIT);
    }

    /**
     * Creates a scoreboard backed by the given store that keeps at most {@code resultsLimit} finished match results.
     * When a match finishes and the limit is exceeded, the oldest results are removed.
     *
     * @param store where matches in progress and results are kept
     * @param resultsLimit maximum number of finished match results to keep
     * @throws IllegalArgumentException if {@code store} is null or {@code resultsLimit} is less than 1
     */
    public Scoreboard(MatchStore store, int resultsLimit) {
        if (resultsLimit < 1) {
            throw new IllegalArgumentException("resultsLimit must be at least 1, was " + resultsLimit);
        }
        this.store = Arguments.requireNotNull(store, "store");
        this.resultsLimit = resultsLimit;
    }

    /**
     * Starts a match with score 0 - 0 and adds it to the scoreboard. Team names are stripped of surrounding
     * whitespace and compared ignoring case.
     *
     * @param homeTeam home team name
     * @param awayTeam away team name
     * @return snapshot of the new match, carrying the id used by the other operations
     * @throws IllegalArgumentException if a team name is null, blank, longer than {@link Match#MAX_TEAM_NAME_LENGTH} or
     *     contains control or invisible format characters, or both names are the same team
     * @throws TeamAlreadyPlayingException if either team is already in a match in progress
     */
    public Match startMatch(String homeTeam, String awayTeam) {
        String home = Arguments.requireTeam(homeTeam, "homeTeam");
        String away = Arguments.requireTeam(awayTeam, "awayTeam");
        if (home.equalsIgnoreCase(away)) {
            throw new IllegalArgumentException("A team cannot play against itself: " + home);
        }
        return write(() -> {
            for (String team : List.of(home, away)) {
                if (store.isTeamPlaying(team)) {
                    throw new TeamAlreadyPlayingException(team);
                }
            }
            Match match = new Match(MatchId.random(), home, away, 0, 0, store.nextSequence());
            store.save(match);
            return match;
        });
    }

    /**
     * Replaces the score of a match in progress with the given absolute score. A lower score than before is
     * allowed, to support corrections.
     *
     * @param id id of the match
     * @param homeScore new home score
     * @param awayScore new away score
     * @return snapshot of the updated match
     * @throws IllegalArgumentException if {@code id} is null or a score is negative or above {@link Match#MAX_SCORE}
     * @throws MatchNotFoundException if no match in progress has this id
     */
    public Match updateScore(MatchId id, int homeScore, int awayScore) {
        Arguments.requireNotNull(id, "id");
        Arguments.requireScore(homeScore, "homeScore");
        Arguments.requireScore(awayScore, "awayScore");
        return write(() -> {
            Match updated = find(id).withScore(homeScore, awayScore);
            store.save(updated);
            return updated;
        });
    }

    /**
     * Finishes a match in progress, removes it from the scoreboard and keeps its final snapshot as a result. If
     * there are then more results than the results limit, the oldest are removed.
     *
     * @param id id of the match
     * @return the final snapshot of the match
     * @throws IllegalArgumentException if {@code id} is null
     * @throws MatchNotFoundException if no match in progress has this id, including one already finished
     */
    public Match finishMatch(MatchId id) {
        Arguments.requireNotNull(id, "id");
        return write(() -> {
            Match match = find(id);
            store.delete(id);
            store.appendFinished(match);
            store.trimFinished(resultsLimit);
            return match;
        });
    }

    /**
     * Matches in progress, ordered by total score descending, ties broken by the most recently started match first.
     *
     * @return an unmodifiable snapshot; later changes to the scoreboard do not affect it
     */
    public List<Match> getSummary() {
        return read(() -> store.findAll().stream().sorted(SUMMARY_ORDER).toList());
    }

    /**
     * Final results of finished matches, oldest first. Holds at most the results limit; older results have been
     * cleaned up.
     *
     * @return an unmodifiable snapshot; later changes to the scoreboard do not affect it
     */
    public List<Match> getFinishedMatches() {
        return read(() -> List.copyOf(store.findFinished()));
    }

    private Match find(MatchId id) {
        return store.findById(id).orElseThrow(() -> new MatchNotFoundException(id));
    }

    private <T> T write(Supplier<T> action) {
        return locked(lock.writeLock(), action);
    }

    private <T> T read(Supplier<T> action) {
        return locked(lock.readLock(), action);
    }

    private static <T> T locked(Lock lock, Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
