package com.yche.springmind.qa.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiHopRerankTest {
    @Test void previewMatchesUnicodeCodePointBoundary() {
        String text = "😀".repeat(4097);
        String preview = MultiHopRerank.preview(text);
        assertEquals(4096, preview.codePointCount(0, preview.length()));
        assertEquals(8192, preview.length());
    }
    @Test void providerCannotDropDuplicateOrInventCandidates() {
        var input = input();
        for (var order : List.of(List.of("a"), List.of("a", "a"), List.of("a", "x")))
            assertThrows(IllegalArgumentException.class, () -> MultiHopRerank.validate(input,
                    new MultiHopRerank.Outcome("q", "RRF", "hash", "APPLIED", 10, order, Map.of("a", .2, "b", .8))));
    }
    @Test void scoreOrderAndFallbackAreAuditable() {
        MultiHopRerank.validate(input(), new MultiHopRerank.Outcome("q", "RRF", "hash", "APPLIED", 10, List.of("b", "a"), Map.of("a", .2, "b", .8)));
        MultiHopRerank.validate(input(), new MultiHopRerank.Outcome("q", "RRF", "hash", "FALLBACK", 10000, List.of("a", "b"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MultiHopRerank.validate(input(),
                new MultiHopRerank.Outcome("q", "RRF", "hash", "FALLBACK", 10, List.of("b", "a"), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> MultiHopRerank.validate(input(),
                new MultiHopRerank.Outcome("q", "RRF", "hash", "APPLIED", 10, List.of("a", "b"), Map.of("a", .2, "b", .8))));
    }
    static MultiHopRerank.Input input() {
        return new MultiHopRerank.Input("q", "RRF", "hash", List.of(
                new MultiHopRerank.Candidate("a", 1, List.of(1L), "x", "x", false),
                new MultiHopRerank.Candidate("b", 2, List.of(2L), "y", "y", false)), 10);
    }
}
