package com.sangluo.onestep.feature.screenshot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SurfaceFlingerDisplayParserTest {
    private static final String DUMP =
            "Display 4630946756802996883 (HWC display 0): port=147 pnpId=QCM "
                    + "screenPartStatus=UNSUPPORTED displayName=\"\"\n"
                    + "Display 11529215046970049598 (Virtual display): "
                    + "displayName=\"StarCore Extended Desktop\"\n"
                    + "Display 11529215046945199102 (Virtual display): "
                    + "displayName=\"OneStepSlot-0/P19723/G1\"\n";

    @Test
    public void findsVirtualDisplayTokenByName() {
        assertEquals("11529215046970049598",
                SurfaceFlingerDisplayParser.tokenFor(DUMP, "StarCore Extended Desktop"));
        assertEquals("11529215046945199102",
                SurfaceFlingerDisplayParser.tokenFor(DUMP, "OneStepSlot-0/P19723/G1"));
    }

    @Test
    public void returnsNullForUnknownOrMissingInput() {
        assertNull(SurfaceFlingerDisplayParser.tokenFor(DUMP, "Missing display"));
        assertNull(SurfaceFlingerDisplayParser.tokenFor(DUMP, ""));
        assertNull(SurfaceFlingerDisplayParser.tokenFor(null, "StarCore Extended Desktop"));
    }

    @Test
    public void ignoresLinesThatAreNotDisplayEntries() {
        String dump = "  displayName=\"Fake\" inside another line\n"
                + "Display 42 (Virtual display): displayName=\"Fake\"\n";

        assertEquals("42", SurfaceFlingerDisplayParser.tokenFor(dump, "Fake"));
    }
}
