package com.sangluo.onestep.data.apps;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppSearchPolicyTest {
    @Test
    public void emptyQueryMatchesEverything() {
        assertEquals(0, AppSearchPolicy.score("", "微信", "wei xin", "wx", "com.tencent.mm"));
        assertEquals(0, AppSearchPolicy.score("   ", "微信", "wei xin", "wx",
                "com.tencent.mm"));
    }

    @Test
    public void labelMatchesRankAboveEveryOtherField() {
        int label = AppSearchPolicy.score("微信", "微信", "wei xin", "wx", "com.tencent.mm");
        int pinyin = AppSearchPolicy.score("weixin", "微信", "wei xin", "wx",
                "com.tencent.mm");
        int initials = AppSearchPolicy.score("wx", "微信", "wei xin", "wx",
                "com.tencent.mm");

        assertTrue(label > pinyin);
        assertTrue(pinyin > initials);
        assertTrue(initials > 0);
    }

    @Test
    public void findsChineseLabelsByPinyinAndInitials() {
        assertTrue(AppSearchPolicy.score("weixin", "微信", "wei xin", "wx",
                "com.tencent.mm") > 0);
        assertTrue(AppSearchPolicy.score("wx", "微信", "wei xin", "wx",
                "com.tencent.mm") > 0);
        assertTrue(AppSearchPolicy.score("weix", "微信", "wei xin", "wx",
                "com.tencent.mm") > 0);
    }

    @Test
    public void ignoresCaseAndWhitespaceInTheQuery() {
        assertEquals(
                AppSearchPolicy.score("weixin", "微信", "wei xin", "wx", "com.tencent.mm"),
                AppSearchPolicy.score("  Wei Xin ", "微信", "wei xin", "wx",
                        "com.tencent.mm"));
    }

    @Test
    public void matchesPackageNames() {
        assertTrue(AppSearchPolicy.score("tencent", "微信", "wei xin", "wx",
                "com.tencent.mm") > 0);
        assertTrue(AppSearchPolicy.score("com.tencent", "微信", "wei xin", "wx",
                "com.tencent.mm") > 0);
    }

    @Test
    public void fallsBackToSubsequenceMatching() {
        int labelSubsequence = AppSearchPolicy.score("chm", "Chrome", "", "",
                "com.android.chrome");
        assertTrue(labelSubsequence > 0);

        int pinyinSubsequence = AppSearchPolicy.score("wxn", "微信", "wei xin", "wx",
                "com.tencent.mm");
        assertTrue(pinyinSubsequence > 0);
    }

    @Test
    public void rejectsUnrelatedQueries() {
        assertEquals(AppSearchPolicy.NO_MATCH,
                AppSearchPolicy.score("支付宝", "微信", "wei xin", "wx", "com.tencent.mm"));
        assertEquals(AppSearchPolicy.NO_MATCH,
                AppSearchPolicy.score("zzz", "微信", "wei xin", "wx", "com.tencent.mm"));
    }

    @Test
    public void earlierOccurrencesScoreHigherThanLaterOnes() {
        int early = AppSearchPolicy.score("a", "abc", "", "", "x");
        int late = AppSearchPolicy.score("a", "xxa", "", "", "x");
        assertTrue(early > late);
    }

    @Test
    public void subsequenceHelperIsOrderSensitive() {
        assertTrue(AppSearchPolicy.isSubsequence("abc", "aXbXc"));
        assertTrue(AppSearchPolicy.isSubsequence("", "anything"));
        assertTrue(!AppSearchPolicy.isSubsequence("cba", "abc"));
        assertTrue(!AppSearchPolicy.isSubsequence("ab", ""));
    }

    @Test
    public void normalizeStripsWhitespaceAndLowerCase() {
        assertEquals("weixin", AppSearchPolicy.normalize(" Wei Xin "));
        assertEquals("", AppSearchPolicy.normalize(null));
    }
}
