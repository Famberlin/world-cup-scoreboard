package com.sportradar.scoreboard;

import java.text.Normalizer;

/** Argument checks shared by the public API. Every invalid argument, null included, is an IllegalArgumentException. */
final class Arguments {

    private Arguments() {}

    static <T> T requireNotNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /**
     * Canonical form of a team name: NFC-normalized and stripped, so that names which look the same compare the same.
     * Control and format characters (zero-width, bidi overrides, soft hyphen and the like) are rejected rather than
     * removed, because a name that hides characters is a caller error, not a spelling variant.
     */
    static String requireTeam(String team, String name) {
        String normalized = Normalizer.normalize(requireNotNull(team, name), Normalizer.Form.NFC)
                .strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        int length = normalized.codePointCount(0, normalized.length());
        if (length > Match.MAX_TEAM_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    name + " must not be longer than " + Match.MAX_TEAM_NAME_LENGTH + " characters, was " + length);
        }
        normalized.codePoints().filter(Arguments::isHidden).findFirst().ifPresent(codePoint -> {
            throw new IllegalArgumentException(
                    name + " must not contain control or invisible characters, found " + unicodeName(codePoint));
        });
        return normalized;
    }

    static void requireScore(int score, String name) {
        if (score < 0 || score > Match.MAX_SCORE) {
            throw new IllegalArgumentException(name + " must be between 0 and " + Match.MAX_SCORE + ", was " + score);
        }
    }

    private static boolean isHidden(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    private static String unicodeName(int codePoint) {
        return String.format("U+%04X", codePoint);
    }
}
