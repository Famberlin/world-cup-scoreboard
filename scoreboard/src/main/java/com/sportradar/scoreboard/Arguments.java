package com.sportradar.scoreboard;

/** Argument checks shared by the public API. Every invalid argument, null included, is an IllegalArgumentException. */
final class Arguments {

    private Arguments() {}

    static <T> T requireNotNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    static String requireTeam(String team, String name) {
        String stripped = requireNotNull(team, name).strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }

    static void requireScore(int score, String name) {
        if (score < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + score);
        }
    }
}
