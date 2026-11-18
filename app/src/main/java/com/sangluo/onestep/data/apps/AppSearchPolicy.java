package com.sangluo.onestep.data.apps;

/**
 * Scoring rules for the "add app" picker's fuzzy search.
 *
 * <p>Each candidate is described by four pre-computed keys: the visible label, its
 * pinyin, the pinyin initials, and the package name. Higher scores rank first; a
 * negative score means the candidate does not match at all.</p>
 */
public final class AppSearchPolicy {
    public static final int NO_MATCH = -1;

    private static final int LABEL_PREFIX = 1200;
    private static final int LABEL_CONTAINS = 1000;
    private static final int PACKAGE_PREFIX = 900;
    private static final int PACKAGE_CONTAINS = 800;
    private static final int PINYIN_PREFIX = 700;
    private static final int PINYIN_CONTAINS = 600;
    private static final int INITIALS_PREFIX = 500;
    private static final int INITIALS_CONTAINS = 450;
    private static final int LABEL_SUBSEQUENCE = 300;
    private static final int PINYIN_SUBSEQUENCE = 200;
    private static final int INITIALS_SUBSEQUENCE = 150;

    private AppSearchPolicy() {
    }

    /**
     * @return {@link #NO_MATCH} when the query does not match, otherwise a rank where
     * larger values are better matches. An empty query matches everything.
     */
    public static int score(String query, String label, String pinyin, String initials,
                            String packageName) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return 0;
        }
        String normalizedLabel = normalize(label);
        String normalizedPinyin = normalize(pinyin);
        String normalizedInitials = normalize(initials);
        String normalizedPackage = normalize(packageName);

        int best = NO_MATCH;
        best = Math.max(best, matchScore(normalizedLabel, normalizedQuery,
                LABEL_PREFIX, LABEL_CONTAINS));
        best = Math.max(best, matchScore(normalizedPackage, normalizedQuery,
                PACKAGE_PREFIX, PACKAGE_CONTAINS));
        best = Math.max(best, matchScore(normalizedPinyin, normalizedQuery,
                PINYIN_PREFIX, PINYIN_CONTAINS));
        best = Math.max(best, matchScore(normalizedInitials, normalizedQuery,
                INITIALS_PREFIX, INITIALS_CONTAINS));
        if (best != NO_MATCH) {
            return best;
        }
        if (isSubsequence(normalizedQuery, normalizedLabel)) {
            return LABEL_SUBSEQUENCE;
        }
        if (isSubsequence(normalizedQuery, normalizedPinyin)) {
            return PINYIN_SUBSEQUENCE;
        }
        if (isSubsequence(normalizedQuery, normalizedInitials)) {
            return INITIALS_SUBSEQUENCE;
        }
        return NO_MATCH;
    }

    /** True when every query character appears in {@code target} in order. */
    public static boolean isSubsequence(String query, String target) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        if (target == null || target.isEmpty()) {
            return false;
        }
        int queryIndex = 0;
        for (int index = 0; index < target.length() && queryIndex < query.length(); index++) {
            if (target.charAt(index) == query.charAt(queryIndex)) {
                queryIndex++;
            }
        }
        return queryIndex == query.length();
    }

    /** Lower-cased with all whitespace removed, so "wei xin" and "WeiXin" compare equal. */
    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character)) {
                continue;
            }
            builder.append(Character.toLowerCase(character));
        }
        return builder.toString();
    }

    private static int matchScore(String target, String query, int prefixScore,
                                  int containsScore) {
        if (target.isEmpty()) {
            return NO_MATCH;
        }
        if (target.startsWith(query)) {
            return prefixScore;
        }
        int index = target.indexOf(query);
        if (index < 0) {
            return NO_MATCH;
        }
        // Earlier occurrences rank higher, but never above a prefix match.
        return Math.max(1, containsScore - index);
    }
}
