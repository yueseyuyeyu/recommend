package com.deng.recommend.recommend.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserItemMatrixTest {

    private static BehaviorRecord record(long userId, long productId, Double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    @Test
    void build_null_returnsEmptyMatrix() {
        UserItemMatrix matrix = UserItemMatrix.build(null);

        assertThat(matrix).isNotNull();
        assertThat(matrix.isEmpty()).isTrue();
        assertThat(matrix.userCount()).isZero();
        assertThat(matrix.itemCount()).isZero();
    }

    @Test
    void build_emptyList_returnsEmptyMatrix() {
        UserItemMatrix matrix = UserItemMatrix.build(Collections.emptyList());

        assertThat(matrix.isEmpty()).isTrue();
        assertThat(matrix.allUserIds()).isEmpty();
        assertThat(matrix.allItemIds()).isEmpty();
    }

    @Test
    void build_duplicateBehavior_sumsWeight() {
        UserItemMatrix matrix = UserItemMatrix.build(List.of(
                record(1L, 10L, 1.0),
                record(1L, 10L, 2.0),
                record(1L, 10L, 3.0)));

        assertThat(matrix.getUserItems(1L)).containsEntry(10L, 6.0);
        assertThat(matrix.getItemUsers(10L)).containsEntry(1L, 6.0);
        assertThat(matrix.getPopularity(10L)).isEqualTo(6.0);
    }

    @Test
    void build_nonPositiveOrNullWeight_skipped() {
        UserItemMatrix matrix = UserItemMatrix.build(List.of(
                record(1L, 10L, null),
                record(1L, 11L, 0.0),
                record(1L, 12L, -1.0)));

        assertThat(matrix.isEmpty()).isTrue();
        assertThat(matrix.allItemIds()).isEmpty();
    }

    @Test
    void build_nullUserIdOrProductId_skipped() {
        UserItemMatrix matrix = UserItemMatrix.build(List.of(
                new BehaviorRecord(null, 10L, 1.0),
                new BehaviorRecord(1L, null, 1.0)));

        assertThat(matrix.isEmpty()).isTrue();
    }

    @Test
    void build_populatesInvertedIndexAndPopularity() {
        UserItemMatrix matrix = UserItemMatrix.build(List.of(
                record(1L, 10L, 2.0),
                record(2L, 10L, 3.0),
                record(1L, 20L, 1.5)));

        assertThat(matrix.userCount()).isEqualTo(2);
        assertThat(matrix.itemCount()).isEqualTo(2);
        assertThat(matrix.getItemUsers(10L)).containsEntry(1L, 2.0).containsEntry(2L, 3.0);
        assertThat(matrix.getPopularity(10L)).isEqualTo(5.0);
        assertThat(matrix.getPopularity(20L)).isEqualTo(1.5);
        assertThat(matrix.getPopularity(99L)).isZero();
        assertThat(matrix.hasInteraction(2L, 10L)).isTrue();
        assertThat(matrix.hasInteraction(2L, 20L)).isFalse();
        assertThat(matrix.getUserItems(99L)).isEmpty();
        assertThat(matrix.getItemUsers(99L)).isEmpty();
    }
}
