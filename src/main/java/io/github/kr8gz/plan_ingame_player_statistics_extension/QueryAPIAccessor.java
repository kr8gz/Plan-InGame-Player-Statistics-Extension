package io.github.kr8gz.plan_ingame_player_statistics_extension;

import com.djrapitops.plan.query.QueryService;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class QueryAPIAccessor {
    private record TableColumn(String name, String type) {
        @Override
        public String toString() {
            return name;
        }

        public String withType() {
            return "%s %s".formatted(name, type);
        }
    }

    private static final String INGAME_STATS_TABLE = "plan_ingame_player_statistics";
    private static final TableColumn PLAYER_UUID_COLUMN = new TableColumn("player_uuid", "char(36)");
    private static final TableColumn STAT_NAME_COLUMN = new TableColumn("stat_name", "varchar(255)");
    private static final TableColumn VALUE_COLUMN = new TableColumn("value", "int");

    private final QueryService queryService;

    public QueryAPIAccessor(MinecraftServer server) throws IOException {
        this.queryService = QueryService.getInstance();
        initializeDatabase(server);
    }

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE " + INGAME_STATS_TABLE + "(" +
                    PLAYER_UUID_COLUMN.withType() + ", " +
                    STAT_NAME_COLUMN.withType() + ", " +
                    VALUE_COLUMN.withType() + ", " +
                    "PRIMARY KEY(" + PLAYER_UUID_COLUMN + ", " + STAT_NAME_COLUMN + ")" +
            ")";

    private void initializeDatabase(MinecraftServer server) throws IOException {
        if (!queryService.getCommonQueries().doesDBHaveTable(INGAME_STATS_TABLE)) {
            queryService.execute(CREATE_TABLE_SQL, PreparedStatement::executeUpdate);
        }

        try (var playerStatsFiles = Files.list(server.getSavePath(WorldSavePath.STATS))) {
            var existingUUIDs = getExistingUUIDs();

            var updatePlayerStatsTasks = playerStatsFiles
                    .filter(path -> {
                        var playerUUID = FilenameUtils.getBaseName(path.toString());
                        return !existingUUIDs.contains(playerUUID);
                    })
                    .map(path -> new ServerStatHandler(server, path.toFile()))
                    .map(this::updatePlayerStats)
                    .toList();

            if (!updatePlayerStatsTasks.isEmpty()) {
                logInitializationProgress(updatePlayerStatsTasks);
            }
        }
    }

    private static void logInitializationProgress(List<? extends Future<?>> updatePlayerStatsTasks) {
        var progressUpdateScheduler = Executors.newScheduledThreadPool(1);
        progressUpdateScheduler.scheduleAtFixedRate(() -> {
            var completedCount = updatePlayerStatsTasks.stream().filter(Future::isDone).count();
            var completedPercentage = (double) completedCount / updatePlayerStatsTasks.size() * 100;

            PlanInGamePlayerStatisticsExtension.LOGGER.info("Initializing database: {}/{} ({}%)", completedCount, updatePlayerStatsTasks.size(), (int) completedPercentage);

            if (completedCount == updatePlayerStatsTasks.size()) {
                progressUpdateScheduler.shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private static final String GET_EXISTING_UUIDS_SQL =
            "SELECT DISTINCT " + PLAYER_UUID_COLUMN + " FROM " + INGAME_STATS_TABLE;

    private List<String> getExistingUUIDs() {
        return queryService.query(GET_EXISTING_UUIDS_SQL, statement -> {
            try (var resultSet = statement.executeQuery()) {
                var existingUUIDs = new ArrayList<String>();
                while (resultSet.next()) {
                    existingUUIDs.add(resultSet.getString(PLAYER_UUID_COLUMN.name));
                }
                return existingUUIDs;
            }
        });
    }

    private static final String UPDATE_PLAYER_STATS_SQL =
            "REPLACE INTO " + INGAME_STATS_TABLE +
            " (" + PLAYER_UUID_COLUMN + ", " + STAT_NAME_COLUMN + ", " + VALUE_COLUMN + ")" +
            " VALUES (?, ?, ?)";

    public Future<?> updatePlayerStats(ServerStatHandler statHandler) {
        return queryService.execute(UPDATE_PLAYER_STATS_SQL, statement -> {
            var playerUUID = FilenameUtils.getBaseName(statHandler.file.toString());
            statement.setString(1, playerUUID);

            for (var statEntry : statHandler.statMap.object2IntEntrySet()) {
                statement.setString(2, statEntry.getKey().getName());
                statement.setInt(3, statEntry.getIntValue());
                statement.addBatch();
            }
            statement.executeBatch();
        });
    }

    private static final String GET_STAT_VALUES_SQL =
            "SELECT " + PLAYER_UUID_COLUMN + ", " + VALUE_COLUMN +
            " FROM " + INGAME_STATS_TABLE +
            " WHERE " + STAT_NAME_COLUMN + " = ?";

    public Object2IntMap<String> getStatForAllPlayers(Stat<?> stat) {
        return queryService.query(GET_STAT_VALUES_SQL, statement -> {
            statement.setString(1, stat.getName());
            try (var resultSet = statement.executeQuery()) {
                var playerStatValues = new Object2IntOpenHashMap<String>();
                while (resultSet.next()) {
                    playerStatValues.put(resultSet.getString(PLAYER_UUID_COLUMN.name), resultSet.getInt(VALUE_COLUMN.name));
                }
                return playerStatValues;
            }
        });
    }

    private static final String RANKED_STATISTICS_CTE = "ranked_statistics";
    private static final String CTE_RANK = "rank";
    private static final String GET_PLAYER_TOP_STATS_SQL =
            "WITH " + RANKED_STATISTICS_CTE + " AS (" +
                    "SELECT *, RANK() OVER (PARTITION BY " + STAT_NAME_COLUMN + " ORDER BY " + VALUE_COLUMN + " DESC) AS " + CTE_RANK +
                    " FROM " + INGAME_STATS_TABLE +
            ")" +
            " SELECT * FROM " + RANKED_STATISTICS_CTE +
            " WHERE " + PLAYER_UUID_COLUMN + " = ?" +
            " ORDER BY " + CTE_RANK + " ASC, " + VALUE_COLUMN + " DESC";

    public record RankedStatistic(String statName, int statValue, int rank) {}

    public List<RankedStatistic> getPlayerTopStats(UUID playerUUID) {
        return queryService.query(GET_PLAYER_TOP_STATS_SQL, statement -> {
            statement.setString(1, playerUUID.toString());
            try (var resultSet = statement.executeQuery()) {
                var playerTopStats = new ArrayList<RankedStatistic>();
                while (resultSet.next()) {
                    var statName = resultSet.getString(STAT_NAME_COLUMN.name);
                    var statValue = resultSet.getInt(VALUE_COLUMN.name);
                    var rank = resultSet.getInt(CTE_RANK);
                    playerTopStats.add(new RankedStatistic(statName, statValue, rank));
                }
                return playerTopStats;
            }
        });
    }
}
