package io.github.kr8gz.plan_ingame_player_statistics_extension;

import com.djrapitops.plan.query.QueryService;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

public class QueryAPIAccessor {
    private static final String INGAME_STATISTICS_TABLE_NAME = "ingame_player_statistics";

    record TableColumn(String name, String type) {
        @Override
        public String toString() {
            return "%s %s".formatted(name, type);
        }
    }

    private static final TableColumn PLAYER_UUID_COLUMN = new TableColumn("player_uuid", "char(36)");
    private static final TableColumn STAT_NAME_COLUMN = new TableColumn("stat_name", "varchar(255)");
    private static final TableColumn VALUE_COLUMN = new TableColumn("value", "int");

    private final QueryService queryService;

    public QueryAPIAccessor(MinecraftServer server) {
        this.queryService = QueryService.getInstance();
        createTableIfNeeded();
        populatePlayerStats(server);
    }

    private void createTableIfNeeded() {
        var createTableSql = "CREATE TABLE IF NOT EXISTS " + INGAME_STATISTICS_TABLE_NAME + " (" +
                PLAYER_UUID_COLUMN + ", " +
                STAT_NAME_COLUMN + ", " +
                VALUE_COLUMN + ", " +
                "PRIMARY KEY(" + PLAYER_UUID_COLUMN.name + ", " + STAT_NAME_COLUMN.name + ")" +
                ")";
        queryService.execute(createTableSql, PreparedStatement::execute);
    }

    private void populatePlayerStats(MinecraftServer server) {
        var existingUUIDs = new ArrayList<String>();
        var selectExistingUUIDsSql = "SELECT DISTINCT " + PLAYER_UUID_COLUMN.name + " FROM " + INGAME_STATISTICS_TABLE_NAME;

        queryService.execute(selectExistingUUIDsSql, statement -> {
            var resultSet = statement.executeQuery();
            while (resultSet.next()) {
                existingUUIDs.add(resultSet.getString(PLAYER_UUID_COLUMN.name));
            }
        });

        try (var playerStatsPathStream = Files.list(server.getSavePath(WorldSavePath.STATS))) {
            var statHandlers = playerStatsPathStream
                    .filter(path -> {
                        var uuid = FilenameUtils.getBaseName(path.toString());
                        return !existingUUIDs.contains(uuid);
                    })
                    .map(path -> new ServerStatHandler(server, path.toFile()))
                    .toList();

            var insertPlayerStatsSql = "INSERT INTO " + INGAME_STATISTICS_TABLE_NAME +
                    " (" + PLAYER_UUID_COLUMN.name + ", " + STAT_NAME_COLUMN.name + ", " + VALUE_COLUMN.name + ") " +
                    "VALUES (?, ?, ?)";

            executePlayerStatsBatchUpdate(insertPlayerStatsSql, statHandlers, (statement, statHandler, statEntry) -> {
                statement.setString(1, getPlayerUUIDAsString(statHandler));
                statement.setString(2, statEntry.getKey().getName());
                statement.setInt(3, statEntry.getIntValue());
            });
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updatePlayerStats(Collection<ServerStatHandler> statHandlers) {
        var updatePlayerStatsSql = "UPDATE " + INGAME_STATISTICS_TABLE_NAME +
                " SET " + VALUE_COLUMN.name + " = ? " +
                " WHERE " + PLAYER_UUID_COLUMN.name + " = ? AND " + STAT_NAME_COLUMN.name + " = ?";

        executePlayerStatsBatchUpdate(updatePlayerStatsSql, statHandlers, (statement, statHandler, statEntry) -> {
            statement.setInt(1, statEntry.getIntValue());
            statement.setString(2, getPlayerUUIDAsString(statHandler));
            statement.setString(3, statEntry.getKey().getName());
        });
    }

    @FunctionalInterface
    private interface StatementParameterSetter {
        void setUsing(PreparedStatement statement, ServerStatHandler statHandler, Object2IntMap.Entry<Stat<?>> statEntry) throws SQLException;
    }

    private void executePlayerStatsBatchUpdate(String sql, Collection<ServerStatHandler> statHandlers, StatementParameterSetter statementParameterSetter) {
        if (statHandlers.isEmpty()) return;
        queryService.execute(sql, statement -> {
            for (var statHandler : statHandlers) {
                for (var statEntry : statHandler.statMap.object2IntEntrySet()) {
                    statementParameterSetter.setUsing(statement, statHandler, statEntry);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        });
    }

    private static String getPlayerUUIDAsString(ServerStatHandler statHandler) {
        return FilenameUtils.getBaseName(statHandler.file.toString());
    }
}
