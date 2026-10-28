package com.deng.recommend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourcePasswordValidatorTest {

    private static DataSourcePasswordValidator validatorWithPassword(String password) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setPassword(password);
        return new DataSourcePasswordValidator(properties);
    }

    @Test
    void afterPropertiesSet_passwordMissing_throwsWithEnvHint() {
        assertThatThrownBy(() -> validatorWithPassword(null).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void afterPropertiesSet_blankPassword_throwsWithEnvHint() {
        assertThatThrownBy(() -> validatorWithPassword("   ").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void afterPropertiesSet_unresolvedPlaceholder_throwsWithEnvHint() {
        assertThatThrownBy(() -> validatorWithPassword("${DB_PASSWORD}").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void afterPropertiesSet_passwordPresent_passes() {
        assertThatCode(() -> validatorWithPassword("s3cret").afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
