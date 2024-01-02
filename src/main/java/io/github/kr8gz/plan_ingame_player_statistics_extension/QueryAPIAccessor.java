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
import java.util.function.Function;
import java.util.stream.Collectors;

public class QueryAPIAccessor {
    private static final String INGAME_STATISTICS_TABLE_NAME = "plan_ingame_player_statistics";

    private record TableColumn(String name, String type) {
        @Override
        public String toString() {
            return "%s %s".formatted(name, type);
        }
    }

    private static final TableColumn PLAYER_UUID_COLUMN = new TableColumn("player_uuid", "char(36)");
    private static final TableColumn STAT_NAME_COLUMN = new TableColumn("stat_name", "varchar(255)");
    private static final TableColumn VALUE_COLUMN = new TableColumn("value", "int");

    private final QueryService queryService;

    public QueryAPIAccessor(MinecraftServer server) throws IOException {
        this.queryService = QueryService.getInstance();
        createTableIfNeeded();
        populatePlayerStats(server);
    }

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + INGAME_STATISTICS_TABLE_NAME + " (" +
                    PLAYER_UUID_COLUMN + ", " +
                    STAT_NAME_COLUMN + ", " +
                    VALUE_COLUMN + ", " +
                    "PRIMARY KEY(" + PLAYER_UUID_COLUMN.name + ", " + STAT_NAME_COLUMN.name + ")" +
            ")";

    private void createTableIfNeeded() {
        queryService.execute(CREATE_TABLE_SQL, PreparedStatement::execute);
    }

    private static final Function<Stat<?>, String> GET_PLAYER_STATS_SQL = stat ->
            "SELECT " + PLAYER_UUID_COLUMN.name + ", " + VALUE_COLUMN.name +
            " FROM " + INGAME_STATISTICS_TABLE_NAME +
            " WHERE " + STAT_NAME_COLUMN.name + " = '" + stat.getName() + "'";

    public Object2IntMap<String> getStatForAllPlayers(Stat<?> stat) {
        return queryService.query(GET_PLAYER_STATS_SQL.apply(stat), statement -> {
            try (var resultSet = statement.executeQuery()) {
                var playerStats = new Object2IntOpenHashMap<String>();
                while (resultSet.next()) {
                    playerStats.put(resultSet.getString(PLAYER_UUID_COLUMN.name), resultSet.getInt(VALUE_COLUMN.name));
                }
                return playerStats;
            }
        });
    }

    private static final Function<ServerStatHandler, String> UPDATE_PLAYER_STATS_SQL = statHandler -> {
        var playerUUID = FilenameUtils.getBaseName(statHandler.file.toString());
        var values = statHandler.statMap.object2IntEntrySet().stream()
                .map(entry -> "('%s','%s',%s)".formatted(playerUUID, entry.getKey().getName(), entry.getIntValue()))
                .collect(Collectors.joining(","));

        return "REPLACE INTO " + INGAME_STATISTICS_TABLE_NAME +
                " (" + PLAYER_UUID_COLUMN.name + ", " + STAT_NAME_COLUMN.name + ", " + VALUE_COLUMN.name + ") " +
                " VALUES " + values;
    };

    private void populatePlayerStats(MinecraftServer server) throws IOException {
        try (var playerStatsPathStream = Files.list(server.getSavePath(WorldSavePath.STATS))) {
            playerStatsPathStream
                    .map(path -> new ServerStatHandler(server, path.toFile()))
                    .forEach(this::updatePlayerStats);
        }
    }

    public void updatePlayerStats(ServerStatHandler statHandler) {
        queryService.execute(UPDATE_PLAYER_STATS_SQL.apply(statHandler), PreparedStatement::execute);
    }
}
