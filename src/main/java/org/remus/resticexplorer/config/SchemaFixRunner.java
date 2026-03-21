package org.remus.resticexplorer.config;

import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reconciles Hibernate-generated check constraints for enum columns on PostgreSQL.
 * <p>
 * Hibernate 7.x creates check constraints for {@code @Enumerated(EnumType.STRING)} columns
 * that list the enum values known at schema-creation time. When new enum values are added
 * (e.g. a new {@code RepositoryType}), {@code ddl-auto=update} does NOT update the existing
 * constraint, causing inserts to fail. This runner replaces stale constraints with ones
 * that match the current enum values, preserving data integrity.
 * <p>
 * <b>Removal notice:</b> This migration fix will be removed in version 1.0.
 * Users upgrading from &lt;0.4 to &gt;=1.0 must first run any version &gt;=0.4 and &lt;1.0
 * so that this fix is applied before it is removed.
 */
@Component
@Slf4j
public class SchemaFixRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public SchemaFixRunner(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        log.info("Running schema migration fix (will be removed in 1.0 — "
                + "ensure you run a version >=0.4 and <1.0 before upgrading to >=1.0)");

        reconcileCheckConstraint(
                "restic_repositories",
                "restic_repositories_type_check",
                "type",
                Arrays.stream(RepositoryType.values()).map(Enum::name).toList());

        reconcileCheckConstraint(
                "repository_properties",
                "repository_properties_property_key_check",
                "property_key",
                Arrays.stream(RepositoryPropertyKey.values()).map(Enum::name).toList());
    }

    private boolean isPostgres() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception e) {
            log.warn("Could not determine database type, skipping schema fix", e);
            return false;
        }
    }

    /**
     * Drops an existing check constraint and re-creates it with the given allowed values.
     * If the constraint does not exist yet (fresh install), it is left for Hibernate to create.
     */
    private void reconcileCheckConstraint(String table, String constraint, String column,
                                          List<String> allowedValues) {
        try {
            var exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                    + "WHERE table_name = ? AND constraint_name = ? AND constraint_type = 'CHECK'",
                    Integer.class, table, constraint);

            if (exists == null || exists == 0) {
                return; // nothing to fix — fresh install, Hibernate will create it
            }

            String valueList = allowedValues.stream()
                    .map(v -> "'" + v + "'")
                    .collect(Collectors.joining(","));

            jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + constraint);
            jdbc.execute("ALTER TABLE " + table
                    + " ADD CONSTRAINT " + constraint
                    + " CHECK (" + column + " IN (" + valueList + "))");

            log.info("Reconciled check constraint '{}' on {}.{} with values: {}",
                    constraint, table, column, allowedValues);
        } catch (Exception e) {
            log.warn("Failed to reconcile check constraint '{}' on table '{}': {}",
                    constraint, table, e.getMessage());
        }
    }
}
