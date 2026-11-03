package org.randomgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RandomGenTest {

    @Test
    void rejectsNullOrEmpty() {
        assertThrows(NullPointerException.class, () -> new SimpleProbabilisticRandomGen(null));
        assertThrows(IllegalArgumentException.class, () -> new SimpleProbabilisticRandomGen(List.of()));
    }

    @Test
    void rejectsNegativeProbability() {
        var samples = List.of(new ProbabilisticRandomGen.NumAndProbability(1, -0.1f));
        assertThrows(IllegalArgumentException.class, () -> new SimpleProbabilisticRandomGen(samples));
    }

    @Test
    void rejectsSumNotOne() {
        var samples = List.of(
                new ProbabilisticRandomGen.NumAndProbability(1, 0.3f),
                new ProbabilisticRandomGen.NumAndProbability(2, 0.3f)
        );
        assertThrows(IllegalArgumentException.class, () -> new SimpleProbabilisticRandomGen(samples));
    }

    @Test
    void singleElementAlwaysReturnsThatElement() {
        var gen = new SimpleProbabilisticRandomGen(
                List.of(new ProbabilisticRandomGen.NumAndProbability(42, 1.0f)));
        for (int i = 0; i < 100; i++) {
            assertEquals(42, gen.nextFromSample());
        }
    }

    @RepeatedTest(5)
    void empiricalDistributionMatchesExpected() {
        var samples = List.of(
                new ProbabilisticRandomGen.NumAndProbability(1, 0.1f),
                new ProbabilisticRandomGen.NumAndProbability(2, 0.3f),
                new ProbabilisticRandomGen.NumAndProbability(3, 0.6f)
        );
        var gen = new SimpleProbabilisticRandomGen(samples);

        int trials = 100_000;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < trials; i++) {
            counts.merge(gen.nextFromSample(), 1, Integer::sum);
        }

        // Allow 2% absolute tolerance (generous for 100k trials)
        assertEquals(0.1, counts.getOrDefault(1, 0) / (double) trials, 0.02);
        assertEquals(0.3, counts.getOrDefault(2, 0) / (double) trials, 0.02);
        assertEquals(0.6, counts.getOrDefault(3, 0) / (double) trials, 0.02);
    }

    @Test
    void zeroProbabilityIsNeverReturned() {
        var samples = List.of(
                new ProbabilisticRandomGen.NumAndProbability(1, 0.0f),
                new ProbabilisticRandomGen.NumAndProbability(2, 1.0f)
        );
        var gen = new SimpleProbabilisticRandomGen(samples);
        for (int i = 0; i < 1000; i++) {
            assertEquals(2, gen.nextFromSample());
        }
    }
}