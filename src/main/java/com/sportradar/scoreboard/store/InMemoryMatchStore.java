package com.sportradar.scoreboard.store;

import com.sportradar.scoreboard.Match;
import com.sportradar.scoreboard.MatchId;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link MatchStore} that keeps matches in memory. Not thread-safe on its own: it relies on the scoreboard's
 * lock, as described in {@link MatchStore}.
 */
public final class InMemoryMatchStore implements MatchStore {

    private final Map<MatchId, Match> matches = new HashMap<>();
    private final Deque<Match> finished = new ArrayDeque<>();
    private long sequence;

    /** Creates an empty store. */
    public InMemoryMatchStore() {}

    @Override
    public long nextSequence() {
        return ++sequence;
    }

    @Override
    public void save(Match match) {
        matches.put(match.id(), match);
    }

    @Override
    public Optional<Match> findById(MatchId id) {
        return Optional.ofNullable(matches.get(id));
    }

    @Override
    public boolean delete(MatchId id) {
        return matches.remove(id) != null;
    }

    @Override
    public Collection<Match> findAll() {
        return List.copyOf(matches.values());
    }

    @Override
    public void appendFinished(Match match) {
        finished.addLast(match);
    }

    @Override
    public List<Match> findFinished() {
        return List.copyOf(finished);
    }

    @Override
    public void trimFinished(int maxResults) {
        while (finished.size() > maxResults) {
            finished.removeFirst();
        }
    }
}
