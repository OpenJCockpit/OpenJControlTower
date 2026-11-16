package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Each OpenJCockpit project has exactly one repository. Duplicates are never resolved silently: the migration
 * fails and lists them, so someone can edit or delete the extra rows by hand.
 */
public class V5__UniqueCockpitProject extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        List<String> duplicates=new ArrayList<>();
        try (Statement statement=context.getConnection().createStatement();
             var rows=statement.executeQuery("SELECT cockpit_project_id, COUNT(*) FROM projects GROUP BY cockpit_project_id HAVING COUNT(*) > 1 ORDER BY cockpit_project_id")) {
            while (rows.next()) duplicates.add(rows.getString(1)+" ("+rows.getInt(2)+" repositories)");
        }
        if (!duplicates.isEmpty())
            throw new IllegalStateException("Cannot make cockpit_project_id unique: these OpenJCockpit projects have several repositories: "
                +String.join(", ",duplicates)+". Edit or delete the extra rows in the projects table, then start again.");
        try (Statement statement=context.getConnection().createStatement()) {
            statement.execute("CREATE UNIQUE INDEX ux_projects_cockpit_project_id ON projects (cockpit_project_id)");
        }
    }
}
