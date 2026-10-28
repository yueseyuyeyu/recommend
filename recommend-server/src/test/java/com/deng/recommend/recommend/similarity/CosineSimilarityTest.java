package com.deng.recommend.recommend.similarity;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CosineSimilarityTest {

    @Test
    void compute_sameVector_returnsOne() {
        Map<Long, Double> vector = Map.of(1L, 2.0, 2L, 3.0);

        assertThat(CosineSimilarity.compute(vector, vector)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void compute_noCommonKey_returnsZero() {
        assertThat(CosineSimilarity.compute(Map.of(1L, 1.0), Map.of(2L, 1.0))).isZero();
    }

    @Test
    void compute_emptyOrNullVector_returnsZero() {
        assertThat(CosineSimilarity.compute(Collections.emptyMap(), Map.of(1L, 1.0))).isZero();
        assertThat(CosineSimilarity.compute(Map.of(1L, 1.0), Collections.emptyMap())).isZero();
        assertThat(CosineSimilarity.compute(null, Map.of(1L, 1.0))).isZero();
        assertThat(CosineSimilarity.compute(Map.of(1L, 1.0), null)).isZero();
        assertThat(CosineSimilarity.compute(null, null)).isZero();
    }

    @Test
    void compute_zeroNorm_returnsZero() {
        Map<Long, Double> zeroVector = new HashMap<>();
        zeroVector.put(1L, 0.0);

        assertThat(CosineSimilarity.compute(zeroVector, zeroVector)).isZero();
        assertThat(CosineSimilarity.norm(zeroVector)).isZero();
        assertThat(CosineSimilarity.norm(null)).isZero();
    }

    @Test
    void compute_oppositeVector_clampedToZero() {
        assertThat(CosineSimilarity.compute(Map.of(1L, 1.0), Map.of(1L, -1.0))).isZero();
    }

    @Test
    void compute_resultAlwaysInUnitRange() {
        Map<Long, Double> a = Map.of(1L, 1.0, 2L, 2.0, 3L, 0.5);
        Map<Long, Double> b = Map.of(1L, 3.0, 2L, 1.0, 4L, 9.0);

        assertThat(CosineSimilarity.compute(a, b)).isBetween(0.0, 1.0);
        assertThat(CosineSimilarity.computeWithPenalty(a, b, 0.5)).isBetween(0.0, 1.0);
    }

    @Test
    void computeWithPenalty_alphaZero_equalsCosine() {
        Map<Long, Double> a = Map.of(1L, 2.0, 2L, 3.0);
        Map<Long, Double> b = Map.of(1L, 4.0, 2L, 1.0);

        assertThat(CosineSimilarity.computeWithPenalty(a, b, 0.0))
                .isEqualTo(CosineSimilarity.compute(a, b));
    }

    @Test
    void computeWithPenalty_positiveAlpha_reducesSimilarity() {
        Map<Long, Double> a = Map.of(1L, 1.0, 2L, 1.0);
        Map<Long, Double> b = Map.of(1L, 1.0, 2L, 1.0, 3L, 1.0);

        double plain = CosineSimilarity.compute(a, b);
        double penalized = CosineSimilarity.computeWithPenalty(a, b, 0.5);

        assertThat(penalized).isLessThan(plain);
        assertThat(penalized).isCloseTo(plain / Math.sqrt(3), within(1e-9));
    }
}
