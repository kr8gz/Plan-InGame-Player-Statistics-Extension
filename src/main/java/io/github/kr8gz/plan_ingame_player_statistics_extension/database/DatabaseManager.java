package io.github.kr8gz.plan_ingame_player_statistics_extension.database;

import com.djrapitops.plan.query.QueryService;
import io.github.kr8gz.plan_ingame_player_statistics_extension.PlanInGamePlayerStatisticsExtension;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Manages the database responsible for storing in-game statistics of players,
 * and provides access to the database through methods for updating
 * player statistics, as well as retrieving data using predefined queries.
 */
public final class DatabaseManager {
    /**
     * Represents a table column in the database with a name and an SQL data type.
     *
     * @param name the name of the table column
     * @param type the SQL data type of the table column
     */
    private record TableColumn(@NotBlank String name, @NotBlank String type) {
        /**
         * Returns the name of this table column, for convenient usage in string concatenations.
         *
         * @return the name of this table column
         */
        @Override
        public String toString() {
            return name;
        }

        /**
         * Returns a formatted string containing the name and SQL data type of this table column, separated by a space.
         * This method is useful for generating SQL statements such as those for creating tables,
         * where both the column name and data type need to be specified.
         *
         * @return the name and SQL data type of this table column separated by space
         */
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
     * where the tables used in this extension are stored. Used by the {@code DatabaseManager}
     * to execute all necessary operations on the database, as well as for registering listeners
     * for Plan database events to stay in sync with Plan's database.
     *
     * @see <a href="https://github.com/plan-player-analytics/Plan/wiki/APIv5-Query-API" target="_blank">Plan Query API</a>
     */
    private final @NotNull QueryService queryService;

    /**
     * The {@link MinecraftServer} instance on which the extension is running.
     * The database is initialized using this server's player statistics files.
     */
    private final @NotNull MinecraftServer server;

    /**
     * Creates a new {@code DatabaseManager} instance, setting up the necessary database tables
     * and populating them with existing player statistics from the {@link MinecraftServer} instance.
     * <p>
     * Also registers listeners for Plan database events through the {@link QueryService} instance
     * to stay in sync with Plan's database.
     *
     * @param server the Minecraft server on which the extension is running
     * @throws IllegalStateException if the {@code QueryService} instance is not available yet because Plan is not enabled
     * @throws DatabaseInitializationException if an exception occurs during database initialization
     *
     * @see <a href="https://github.com/plan-player-analytics/Plan/wiki/Query-API-Getting-started" target=_"blank">Plan Query API – Getting started</a>
     */
    public DatabaseManager(@NotNull final MinecraftServer server) throws DatabaseInitializationException {
        this.queryService = QueryService.getInstance();
        this.server = server;

        initializeDatabase();

        queryService.subscribeDataClearEvent(this::clearData);
        queryService.subscribeToPlayerRemoveEvent(this::removePlayer);
    }

    /**
     * Ensures that the necessary database tables are created, and inserts player statistics from the save files
     * of this {@code DatabaseManager}'s {@link #server} for players that have no existing entries in the database.
     *
     * @throws DatabaseInitializationException if an exception occurs during database initialization
     */
    private void initializeDatabase() throws DatabaseInitializationException {
        queryService.execute(CREATE_TABLE_SQL, PreparedStatement::executeUpdate);

        try (var playerStatsFiles = Files.list(server.getSavePath(WorldSavePath.STATS))) {
            var existingUUIDs = getExistingUUIDs();

            var statHandlers = playerStatsFiles
                    .filter(path -> {
                        var playerUUID = FilenameUtils.getBaseName(path.toString());
                        return !existingUUIDs.contains(playerUUID);
                    })
                    .map(path -> new ServerStatHandler(server, path.toFile()))
                    .toList();

            var future = updatePlayerStats(statHandlers).orElse(null);
            if (future == null) return;

            try {
                future.get();
                PlanInGamePlayerStatisticsExtension.LOGGER.info("Successfully loaded {} player statistics files from server into database", statHandlers.size());
            } catch (InterruptedException | ExecutionException e) {
                throw new DatabaseInitializationException("Exception occurred while writing player statistics to database", e);
            }
        } catch (IOException e) {
            throw new DatabaseInitializationException("I/O exception occurred while getting player statistics files", e);
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
     * Deletes the database tables entirely.
     */
    private void clearData() {
        queryService.execute(DROP_TABLE_SQL, PreparedStatement::executeUpdate);
    }

    private static final String DROP_TABLE_SQL =
            "DROP TABLE IF EXISTS " + INGAME_STATS_TABLE;

    /**
     * Removes all entries for the specified player UUID from the database.
     *
     * @param playerUUID the UUID of the player to be removed
     */
    private void removePlayer(UUID playerUUID) {
        queryService.execute(REMOVE_PLAYER_ENTRIES_SQL, statement -> {
            statement.setString(1, playerUUID.toString());
            statement.executeUpdate();
        });
    }

    private static final String REMOVE_PLAYER_ENTRIES_SQL =
            "DELETE FROM " + INGAME_STATS_TABLE +
            " WHERE " + PLAYER_UUID_COLUMN + " = ?";

    /**
     * Fetches a list of player UUIDs that are currently stored in the database.
     * UUIDs are intentionally kept as {@code String}s for internal processing.
     *
     * @return a list containing all stored player UUIDs as {@code String}s
     */
    @NotNull
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
     * Inserts player statistics provided by the {@code statHandlers} into the database.
     * Existing entries for player UUID and statistic names are replaced.
     * <p>
     * This method returns an optional {@code Future} that can be used to block the thread with {@link Future#get()}
     * until the SQL statement has executed, or an empty optional if {@code statHandlers} is empty.
     *
     * @param statHandlers a {@code Collection} of {@link ServerStatHandler}s containing the player statistics to be updated
     * @return an optional {@code Future} for tracking the execution of the SQL statement,
     *         or an empty optional if {@code statHandlers} is empty
     */
    @NotNull
    public Optional<Future<?>> updatePlayerStats(@NotNull final Collection<ServerStatHandler> statHandlers) {
        if (statHandlers.isEmpty()) return Optional.empty();

        Future<?> future = queryService.execute(UPDATE_PLAYER_STATS_SQL, statement -> {
            for (ServerStatHandler statHandler : statHandlers) {
                var playerUUID = FilenameUtils.getBaseName(statHandler.file.toString());
                statement.setString(1, playerUUID);

                for (var statEntry : statHandler.statMap.object2IntEntrySet()) {
                    statement.setString(2, statEntry.getKey().getName());
                    statement.setInt(3, statEntry.getIntValue());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        });

        return Optional.of(future);
    }

    private static final String UPDATE_PLAYER_STATS_SQL =
            "REPLACE INTO " + INGAME_STATS_TABLE +
            " (" + PLAYER_UUID_COLUMN + ", " + STAT_NAME_COLUMN + ", " + VALUE_COLUMN + ")" +
            " VALUES (?, ?, ?)";

    /**
     * Returns a map containing player UUIDs and their associated values for the specified statistic from the database.
     *
     * @param stat the {@link Stat} for which to retrieve all players' values
     * @return an {@link Object2IntMap} with player UUIDs as keys and their corresponding statistic values
     */
    @NotNull
    public Object2IntMap<UUID> getStatForAllPlayers(@NotNull final Stat<?> stat) {
        return queryService.query(GET_STAT_VALUES_SQL, statement -> {
            statement.setString(1, stat.getName());
            try (var resultSet = statement.executeQuery()) {
                var playerStatValues = new Object2IntOpenHashMap<UUID>();
                while (resultSet.next()) {
                    var playerUUID = UUID.fromString(resultSet.getString(PLAYER_UUID_COLUMN.name));
                    playerStatValues.put(playerUUID, resultSet.getInt(VALUE_COLUMN.name));
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
    public record RankedStatistic(@NotBlank String statName, int statValue, int rank) {}

    /**
     * Retrieves a list of {@code RankedStatistic}s for a player.
     * The returned list is ordered by the player's rank in ascending order,
     * followed by the statistic values in descending order.
     *
     * @param playerUUID the UUID of the player to get the statistics for
     * @return a list of the player's statistics as {@code RankedStatistic} objects
     */
    @NotNull
    public List<RankedStatistic> getPlayerTopStats(@NotNull final UUID playerUUID) {
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
}
