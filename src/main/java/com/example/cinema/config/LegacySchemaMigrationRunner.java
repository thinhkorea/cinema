package com.example.cinema.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
}
