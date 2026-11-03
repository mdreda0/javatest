package org.randomgen;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class SimpleProbabilisticRandomGen implements ProbabilisticRandomGen {
    private final int[] numbers;
    private final float[] cumulativeProbabilities;
    private final static float PRECISION = 1e-5f;

    public SimpleProbabilisticRandomGen(List<NumAndProbability> distribution) {
        Objects.requireNonNull(distribution, "distribution");
        if (distribution.isEmpty()) {
            throw new IllegalArgumentException("distribution cannot be empty");
        }

        List<NumAndProbability> cp = distribution.stream().map(element -> {
            Objects.requireNonNull(element, "element");
            if (element.getProbabilityOfSample() < 0f) {
                throw new IllegalArgumentException("elements cannot be negative " + element.getNumber());
            }
            return element;
        }).toList();
        float sum = 0f;
        for (NumAndProbability element : cp) {
            sum += element.getProbabilityOfSample();
        }
        if (Math.abs(sum - 1f) > PRECISION) {
            throw new IllegalArgumentException("Probabilities must sum up to 1");
        }

        this.numbers = new int[cp.size()];
        this.cumulativeProbabilities = new float[cp.size()];

        float cumuSum = 0f;
        for (int i= 0; i < cp.size(); i++) {
            NumAndProbability current = cp.get(i);
            numbers[i] = current.getNumber();
            cumuSum += current.getProbabilityOfSample();
            cumulativeProbabilities[i] = cumuSum;
        }
    }

    @Override
    public int nextFromSample() {
        float randy = ThreadLocalRandom.current().nextFloat();
        int low = 0;
        int high = cumulativeProbabilities.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (cumulativeProbabilities[mid] <= randy) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return numbers[low];
    }
}
