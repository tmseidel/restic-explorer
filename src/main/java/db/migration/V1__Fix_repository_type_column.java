package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Drops any H2-generated CHECK constraints on enum columns that were created
 * automatically by H2 when Hibernate built the schema with limited enum values.
 *
 * Specifically drops CHECK constraints on:
 * - restic_repositories.type (originally only allowed 'S3')
 * - repository_properties.property_key (originally only allowed S3 property keys)
 *
 * On PostgreSQL no such constraints exist, so this migration is a no-op there.
 * On fresh databases the tables do not exist yet when this runs (Flyway executes
 * before Hibernate DDL), so the queries simply return no rows and nothing is dropped.
 */
public class V1__Fix_repository_type_column extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String url = connection.getMetaData().getURL();

        // Only H2 creates inline CHECK constraints for @Enumerated(EnumType.STRING) columns.
        if (!url.startsWith("jdbc:h2:")) {
            return;
        }

        dropCheckConstraints(connection, "RESTIC_REPOSITORIES", "TYPE");
        dropCheckConstraints(connection, "REPOSITORY_PROPERTIES", "PROPERTY_KEY");
    }

    private void dropCheckConstraints(Connection connection, String tableName, String columnName) throws Exception {
        try (Statement queryStmt = connection.createStatement();
             ResultSet rs = queryStmt.executeQuery(
                     "SELECT tc.CONSTRAINT_NAME " +
                     "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                     "JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu " +
                     "  ON tc.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME " +
                     "WHERE tc.TABLE_NAME = '" + tableName + "' " +
                     "  AND tc.CONSTRAINT_TYPE = 'CHECK' " +
                     "  AND ccu.COLUMN_NAME = '" + columnName + "'")) {

            while (rs.next()) {
                String constraintName = rs.getString("CONSTRAINT_NAME");
                try (Statement dropStmt = connection.createStatement()) {
                    dropStmt.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT IF EXISTS \""
                            + constraintName + "\"");
                }
            }
        }
    }
}
