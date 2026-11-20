package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class ScoreboardConcurrencyTest {

    private static final int THREADS = 16;

    private final Scoreboard scoreboard = new Scoreboard();

    @Test
    void parallelStartsWithTheSameTeamCreateExactlyOneMatch() throws Exception {
        AtomicInteger rejected = new AtomicInteger();

        runInParallel(i -> {
            try {
                scoreboard.startMatch("Spain", "Opponent " + i);
            } catch (TeamAlreadyPlayingException e) {
                rejected.incrementAndGet();
            }
        });

        assertThat(scoreboard.getSummary()).hasSize(1);
        assertThat(rejected).hasValue(THREADS - 1);
    }

    @Test
    void summariesReadDuringUpdatesAreAlwaysOrdered() throws Exception {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            matches.add(scoreboard.startMatch("Home " + i, "Away " + i));
        }
        AtomicInteger unordered = new AtomicInteger();

        runInParallel(i -> {
            for (int round = 0; round < 200; round++) {
                if (i % 2 == 0) {
                    scoreboard.updateScore(matches.get(i).id(), round % 7, round % 5);
                } else if (!isOrdered(scoreboard.getSummary())) {
                    unordered.incrementAndGet();
                }
            }
        });

        assertThat(unordered).hasValue(0);
        assertThat(scoreboard.getSummary()).hasSize(THREADS);
    }

    private static boolean isOrdered(List<Match> summary) {
        for (int i = 1; i < summary.size(); i++) {
            if (Scoreboard.SUMMARY_ORDER.compare(summary.get(i - 1), summary.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }

    private static void runInParallel(IntConsumer task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    task.accept(index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
    }
}
