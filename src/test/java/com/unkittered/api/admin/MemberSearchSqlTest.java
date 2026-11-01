package com.unkittered.api.admin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberSearchSqlTest {

    @Test
    void everyFilterButtonBuildsAWellFormedQuery() {
        for (String filter : List.of("", "reported", "suspended", "verified", "paying", "incomplete", "nonsense")) {
            String sql = AdminInsightsService.membersSql(filter);
            String condition = AdminInsightsService.memberFilter(filter);
            // The condition must stand apart from ORDER BY, not run into it.
            assertThat(sql).doesNotContainPattern("[A-Za-z0-9_')]ORDER BY");
            assertThat(sql).contains(condition);
            assertThat(sql.trim()).endsWith("LIMIT ?");
            // Six placeholders: the search term five times, then the page size.
            assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(6);
        }
    }

    @Test
    void theButtonsTheDashboardShowsAllHaveAFilter() {
        for (String filter : List.of("reported", "suspended", "verified", "paying", "incomplete")) {
            assertThat(AdminInsightsService.memberFilter(filter)).startsWith("AND ");
        }
        assertThat(AdminInsightsService.memberFilter("")).isEmpty();
    }
}
