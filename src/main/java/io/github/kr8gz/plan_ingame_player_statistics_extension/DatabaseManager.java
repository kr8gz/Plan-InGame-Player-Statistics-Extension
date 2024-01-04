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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Manages the database responsible for storing in-game statistics of players.
 * This class provides access to the database through methods for updating
 * player statistics, as well as retrieving data using predefined queries.
 */
public class DatabaseManager {
    /**
     * Represents a table column in the database with a name and an SQL data type.
     *
     * @param name The name of the table column.
     * @param type The SQL data type of the table column.
     */
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

    /**
     * The {@link QueryService} instance for accessing and interacting with Plan's database,
     * where the tables used in this extension are stored. This service is employed by the
     * {@code DatabaseManager} to execute all necessary operations on the database.
     *
     * @see com.djrapitops.plan.query.QueryService
     * @see <a href="https://www.spigotmc.org/resources/plan-player-analytics.32536/" target="_blank">Plan Plugin</a>
     */
    private final QueryService queryService;

    /**
     * Creates a new {@code DatabaseManager} instance, setting up the necessary database tables
     * and populating them with existing player statistics from the {@link MinecraftServer} instance.
     *
     * @param server the server on which the extension is running
     * @throws IOException if an I/O error occurs during the database initialization process
     */
    public DatabaseManager(MinecraftServer server) throws IOException {
        this.queryService = QueryService.getInstance();
        initializeDatabase(server);
    }

    /**
     * TODO
     *
     * @param server
     * @throws IOException
     */
    private void initializeDatabase(MinecraftServer server) throws IOException {
        queryService.execute(CREATE_TABLE_SQL, PreparedStatement::executeUpdate);

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

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + INGAME_STATS_TABLE + "(" +
                    PLAYER_UUID_COLUMN.withType() + ", " +
                    STAT_NAME_COLUMN.withType() + ", " +
                    VALUE_COLUMN.withType() + ", " +
                    "PRIMARY KEY(" + PLAYER_UUID_COLUMN + ", " + STAT_NAME_COLUMN + ")" +
            ")";

    /**
     * TODO
     *
     * @param updatePlayerStatsTasks
     */
    private static void logInitializationProgress(List<? extends Future<?>> updatePlayerStatsTasks) {
        var progressUpdateScheduler = Executors.newScheduledThreadPool(1);
        progressUpdateScheduler.scheduleAtFixedRate(() -> {
            var completedCount = updatePlayerStatsTasks.stream().filter(Future::isDone).count();
            var completedPercentage = (double) completedCount / updatePlayerStatsTasks.size() * 100;

            PlanInGamePlayerStatisticsExtension.LOGGER.info("Database initialization in progress: {}/{} players processed ({}%)", completedCount, updatePlayerStatsTasks.size(), (int) completedPercentage);

            if (completedCount == updatePlayerStatsTasks.size()) {
                progressUpdateScheduler.shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Fetches a list of player UUIDs that are currently stored in the database.
     *
     * @return a list containing all stored player UUIDs as {@link String}s
     */
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

    private static final String GET_EXISTING_UUIDS_SQL =
            "SELECT DISTINCT " + PLAYER_UUID_COLUMN + " FROM " + INGAME_STATS_TABLE;

    /**
     * Inserts player statistics provided by the {@code statHandler} into the database.
     * Existing entries for the specified player UUID and statistic names are replaced.
     * <p>
     * The {@link Future} returned by this method can be used to block the thread with {@link Future#get()}
     * until the SQL statement has executed.
     *
     * @param statHandler the {@link ServerStatHandler} instance containing player statistics to be updated
     * @return a {@link Future} for tracking the asynchronous execution of the SQL statement
     */
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

    private static final String UPDATE_PLAYER_STATS_SQL =
            "REPLACE INTO " + INGAME_STATS_TABLE +
            " (" + PLAYER_UUID_COLUMN + ", " + STAT_NAME_COLUMN + ", " + VALUE_COLUMN + ")" +
            " VALUES (?, ?, ?)";

    /**
     * Returns a map containing player UUIDs and their associated values for the specified statistic from the database.
     *
     * @param stat the {@link Stat} for which player statistics are to be retrieved
     * @return an {@link Object2IntMap} with player UUIDs as keys and their corresponding statistic values
     */
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

    private static final String GET_STAT_VALUES_SQL =
            "SELECT " + PLAYER_UUID_COLUMN + ", " + VALUE_COLUMN +
            " FROM " + INGAME_STATS_TABLE +
            " WHERE " + STAT_NAME_COLUMN + " = ?";

    /**
     * Represents a ranked statistic for a player, including the statistic name, value,
     * and the player's position on the leaderboard for the specified statistic.
     *
     * @param statName the name of the statistic
     * @param statValue the value of the statistic
     * @param rank the player's position on the leaderboard for the specified statistic
     */
    public record RankedStatistic(String statName, int statValue, int rank) {}

    /**
     * Retrieves a list of {@link RankedStatistic}s for a player.
     * The returned list is ordered by the player's rank in ascending order,
     * followed by the statistic values in descending order.
     *
     * @param playerUUID the UUID of the player to get the statistics for
     * @return a list of the player's statistics as {@link RankedStatistic} objects
     */
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

    /**
     * Fetches a random statistic name from the database for which an entry exists.
     *
     * @return an optional {@link String} with the selected statistic name,
     *         or an empty optional if no entries exist
     */
    public Optional<String> getRandomStat() {
        return queryService.query(GET_RANDOM_STAT_SQL, statement -> {
            try (var resultSet = statement.executeQuery()) {
                var statNames = new ArrayList<String>();
                while (resultSet.next()) {
                    statNames.add(resultSet.getString(STAT_NAME_COLUMN.name));
                }
                if (statNames.isEmpty()) {
                    return Optional.empty();
                }
                var randomIndex = new Random().nextInt(statNames.size());
                return Optional.of(statNames.get(randomIndex));
            }
        });
    }

    private static final String GET_RANDOM_STAT_SQL =
            "SELECT DISTINCT " + STAT_NAME_COLUMN + " FROM " + INGAME_STATS_TABLE;
}
