package com.ontotext.ehri.ead.tests;

import com.ontotext.ehri.ead.GoogleSheets;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class GoogleSheetsTests {

    @Test
    public void test() throws IOException {
        String id = "1H8bgPSWTvvfICZ6znvFpf4iDCib39KZ0jfgTYHmv5e0";

        assertEquals("ead",
                GoogleSheets.toString(GoogleSheets.getValues(id, "B2"), "\n", "\t"));
        assertEquals("target-path\ttarget-node\tsource-node\tvalue",
                GoogleSheets.toString(GoogleSheets.getValues(id, "A1:D1"), "\n", "\t"));
        assertEquals("/\tead\n/ead/\t@xsi:schemaLocation\n/ead/\teadheader",
                GoogleSheets.toString(GoogleSheets.getValues(id, "A2:B4"), "\n", "\t"));
    }
}
