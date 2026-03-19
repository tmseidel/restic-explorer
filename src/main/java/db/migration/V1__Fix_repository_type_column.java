package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Drops any H2-generated CHECK constraint on restic_repositories.type that
 * only listed the original 'S3' value. This constraint was created automatically
 * by H2 when Hibernate built the schema with a single-value enum column.
 *
 * On PostgreSQL no such constraint exists, so this migration is a no-op there.
 * On fresh databases the table does not exist yet when this runs (Flyway executes
 * before Hibernate DDL), so the query simply returns no rows and nothing is dropped.
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

        try (Statement queryStmt = connection.createStatement();
             ResultSet rs = queryStmt.executeQuery(
                     "SELECT tc.CONSTRAINT_NAME " +
                     "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                     "JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu " +
                     "  ON tc.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME " +
                     "WHERE tc.TABLE_NAME = 'RESTIC_REPOSITORIES' " +
                     "  AND tc.CONSTRAINT_TYPE = 'CHECK' " +
                     "  AND ccu.COLUMN_NAME = 'TYPE'")) {

            while (rs.next()) {
                String constraintName = rs.getString("CONSTRAINT_NAME");
                try (Statement dropStmt = connection.createStatement()) {
                    dropStmt.execute("ALTER TABLE RESTIC_REPOSITORIES DROP CONSTRAINT IF EXISTS \""
                            + constraintName + "\"");
                }
            }
        }
    }
}
