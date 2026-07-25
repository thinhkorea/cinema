package com.example.cinema.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LegacySchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // Align legacy database schema with current entities.
        dropColumnIfExists("users", "username");
        dropColumnIfExists("customers", "email");
        dropColumnIfExists("customers", "phone");
        widenColumnToTextIfExists("movies", "description");
        restoreActiveFlagsIfAllInactive("rooms");
        restoreActiveFlagsIfAllInactive("seats");
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        String existsSql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """;

        Integer count = jdbcTemplate.queryForObject(existsSql, Integer.class, tableName, columnName);
        if (count != null && count > 0) {
            String alterSql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
            jdbcTemplate.execute(alterSql);
            log.info("Dropped legacy column {}.{}", tableName, columnName);
        }
    }

    private void restoreActiveFlagsIfAllInactive(String tableName) {
        if (!columnExists(tableName, "active")) {
            return;
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        if (total == null || total == 0) {
            return;
        }

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE active = 1",
                Integer.class);

        if (activeCount != null && activeCount == 0) {
            int restored = jdbcTemplate.update(
                    "UPDATE " + tableName + " SET active = 1 WHERE active = 0 OR active IS NULL");
            log.info("Restored {} legacy rows in {} to active=true", restored, tableName);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        String existsSql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """;

        Integer count = jdbcTemplate.queryForObject(existsSql, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void widenColumnToTextIfExists(String tableName, String columnName) {
        String typeSql = """
                SELECT DATA_TYPE
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """;

        List<String> dataTypes = jdbcTemplate.queryForList(typeSql, String.class, tableName, columnName);
        if (dataTypes.isEmpty()) {
            return;
        }

        String dataType = dataTypes.get(0);
        if ("text".equalsIgnoreCase(dataType)) {
            return;
        }

        String alterSql = "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " TEXT";
        jdbcTemplate.execute(alterSql);
        log.info("Widened legacy column {}.{} from {} to TEXT", tableName, columnName, dataType);
    }
}
