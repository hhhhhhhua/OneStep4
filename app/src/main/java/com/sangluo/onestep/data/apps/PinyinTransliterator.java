package com.sangluo.onestep.data.apps;

import android.icu.text.Transliterator;
import android.util.Log;

/**
 * Latin transliteration for app labels, so a Chinese label can be found by pinyin
 * ("weixin") or by initials ("wx"). Uses the platform ICU transliterator, which keeps
 * the module free of a bundled pinyin dictionary.
 */
public final class PinyinTransliterator {
    private static final String TAG = "OneStep40_Pinyin";
    private static final String TRANSLITERATOR_ID = "Han-Latin; Latin-ASCII";

    private static Transliterator transliterator;
    private static boolean unavailable;

    private PinyinTransliterator() {
    }

    /** Space separated pinyin of {@code text}, or an empty string when unavailable. */
    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Transliterator instance = transliterator();
        if (instance == null) {
            return "";
        }
        try {
            String converted = instance.transliterate(text);
            return converted == null ? "" : converted.trim();
        } catch (RuntimeException e) {
            Log.w(TAG, "Transliteration failed: " + e.getClass().getSimpleName());
            return "";
        }
    }

    /** First letter of every pinyin syllable, e.g. "微信" -> "wx". */
    public static String initials(String text) {
        String pinyin = toPinyin(text);
        if (pinyin.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(pinyin.length());
        boolean atSyllableStart = true;
        for (int index = 0; index < pinyin.length(); index++) {
            char character = pinyin.charAt(index);
            if (character == '\'' || Character.isWhitespace(character)) {
                atSyllableStart = true;
                continue;
            }
            if (atSyllableStart && Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
                atSyllableStart = false;
            }
        }
        return builder.toString();
    }

    private static Transliterator transliterator() {
        if (unavailable) {
            return null;
        }
        if (transliterator == null) {
            try {
                transliterator = Transliterator.getInstance(TRANSLITERATOR_ID);
            } catch (RuntimeException e) {
                unavailable = true;
                Log.w(TAG, "ICU transliterator unavailable: "
                        + e.getClass().getSimpleName());
                return null;
            }
        }
        return transliterator;
    }
}
