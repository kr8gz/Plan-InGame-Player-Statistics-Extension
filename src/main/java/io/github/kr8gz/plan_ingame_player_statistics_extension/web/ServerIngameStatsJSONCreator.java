package io.github.kr8gz.plan_ingame_player_statistics_extension.web;

import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.kr8gz.plan_ingame_player_statistics_extension.common.PlanHook;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

import java.net.HttpURLConnection;
import java.util.*;

public sealed abstract class ServerIngameStatsJSONCreator {
    public final String key;
    private final Stat<?>[] stats;

    private ServerIngameStatsJSONCreator(List<String> path, Stat<?>... stats) {
        this.key = String.join(".", path);
        this.stats = stats;
    }

    private static <T> List<String> extendPath(String parent, Registry<T> registry, T registryObject) {
        Identifier id = Objects.requireNonNull(registry.getId(registryObject),
                () -> "Object %s not found in registry %s".formatted(registryObject, registry.getKey()));
        return extendPath(parent, id.getPath());
    }

    private static List<String> extendPath(String parent, String path) {
        return extendPath(parent, new ArrayList<>(Collections.singleton(path)));
    }

    private static List<String> extendPath(String parent, List<String> path) {
        path.add(0, parent);
        return path;
    }

    public final Response getJSONResponse() {
        var databaseManager = PlanHook.getDatabaseManager().orElseThrow(() -> new BadRequestException("In-game player statistics database is not yet initialized."));

        var statsArray = new JsonArray();
        for (Stat<?> stat : stats) {
            var playerStatMap = databaseManager.getStatForAllPlayers(stat);

            var statValuesObject = new JsonObject();
            playerStatMap.forEach((uuid, value) -> statValuesObject.addProperty(uuid.toString(), value));

            var statInfoObject = new JsonObject();
            statInfoObject.addProperty("stat", stat.getName());
            statInfoObject.add("values", statValuesObject);

            statsArray.add(statInfoObject);
        }

        var jsonData = new JsonObject();
        jsonData.addProperty("key", key);
        jsonData.add("stats", statsArray);

        return Response.builder()
                .setJSONContent(jsonData.toString())
                .setStatus(HttpURLConnection.HTTP_OK)
                .build();
    }

    public static final class GeneralCategory extends ServerIngameStatsJSONCreator {
        private static final String CATEGORY_KEY = "general";

        private GeneralCategory(String key, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, key), stats);
        }

        public static final ServerIngameStatsJSONCreator DAMAGE_DEALT = new GeneralCategory("damage_dealt",
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_DEALT),
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_DEALT_ABSORBED),
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_DEALT_RESISTED)
        );
        public static final ServerIngameStatsJSONCreator DAMAGE_TAKEN = new GeneralCategory("damage_taken",
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_TAKEN),
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_BLOCKED_BY_SHIELD),
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_ABSORBED),
                Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_RESISTED)
        );
        public static final ServerIngameStatsJSONCreator PLAYTIME = new GeneralCategory("playtime",
                Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME),
                Stats.CUSTOM.getOrCreateStat(Stats.LEAVE_GAME),
                Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_DEATH),
                Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST)
        );
        public static final ServerIngameStatsJSONCreator KILLS_AND_DEATHS = new GeneralCategory("kills_deaths",
                Stats.CUSTOM.getOrCreateStat(Stats.DEATHS),
                Stats.CUSTOM.getOrCreateStat(Stats.PLAYER_KILLS)
        );
    }

    public static final class MovementCategory extends ServerIngameStatsJSONCreator {
        private static final String CATEGORY_KEY = "movement";

        private MovementCategory(String key, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, key), stats);
        }

        public static final ServerIngameStatsJSONCreator AIR = new MovementCategory("air",
                Stats.CUSTOM.getOrCreateStat(Stats.AVIATE_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.FLY_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.CLIMB_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.FALL_ONE_CM)
        );
        public static final ServerIngameStatsJSONCreator BY_VEHICLE = new MovementCategory("vehicle",
                Stats.CUSTOM.getOrCreateStat(Stats.BOAT_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.MINECART_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.HORSE_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.PIG_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.STRIDER_ONE_CM)
        );
        public static final ServerIngameStatsJSONCreator GROUND = new MovementCategory("ground",
                Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.SPRINT_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.CROUCH_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.SNEAK_TIME),
                Stats.CUSTOM.getOrCreateStat(Stats.JUMP)
        );
        public static final ServerIngameStatsJSONCreator WATER = new MovementCategory("water",
                Stats.CUSTOM.getOrCreateStat(Stats.SWIM_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.WALK_ON_WATER_ONE_CM),
                Stats.CUSTOM.getOrCreateStat(Stats.WALK_UNDER_WATER_ONE_CM)
        );
    }

    public static final class BlockInteractions extends ServerIngameStatsJSONCreator {
        private static final String CATEGORY_KEY = "interactions";

        private BlockInteractions(String path, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, path), stats);
        }

        public static final ServerIngameStatsJSONCreator STORAGE_BLOCKS = new BlockInteractions("storage",
                Stats.CUSTOM.getOrCreateStat(Stats.OPEN_CHEST),
                Stats.CUSTOM.getOrCreateStat(Stats.OPEN_ENDERCHEST),
                Stats.CUSTOM.getOrCreateStat(Stats.OPEN_SHULKER_BOX),
                Stats.CUSTOM.getOrCreateStat(Stats.OPEN_BARREL)
        );
        public static final ServerIngameStatsJSONCreator CRAFTING_BLOCKS = new BlockInteractions("crafting",
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_CRAFTING_TABLE),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_SMITHING_TABLE),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_STONECUTTER),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_CARTOGRAPHY_TABLE),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_LOOM)
        );
        public static final ServerIngameStatsJSONCreator SMELTING_BLOCKS = new BlockInteractions("smelting",
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_FURNACE),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_SMOKER),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_BLAST_FURNACE),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_CAMPFIRE)
        );
        public static final ServerIngameStatsJSONCreator REDSTONE_BLOCKS = new BlockInteractions("redstone",
                Stats.CUSTOM.getOrCreateStat(Stats.INSPECT_DISPENSER),
                Stats.CUSTOM.getOrCreateStat(Stats.INSPECT_DROPPER),
                Stats.CUSTOM.getOrCreateStat(Stats.INSPECT_HOPPER),
                Stats.CUSTOM.getOrCreateStat(Stats.TARGET_HIT),
                Stats.CUSTOM.getOrCreateStat(Stats.TRIGGER_TRAPPED_CHEST)
        );
        public static final ServerIngameStatsJSONCreator MUSIC_BLOCKS = new BlockInteractions("music",
                Stats.CUSTOM.getOrCreateStat(Stats.PLAY_RECORD),
                Stats.CUSTOM.getOrCreateStat(Stats.PLAY_NOTEBLOCK),
                Stats.CUSTOM.getOrCreateStat(Stats.TUNE_NOTEBLOCK)
        );
        public static final ServerIngameStatsJSONCreator UTILITY_BLOCKS = new BlockInteractions("utility",
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_ANVIL),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_BEACON),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_BREWINGSTAND),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_GRINDSTONE),
                Stats.CUSTOM.getOrCreateStat(Stats.INTERACT_WITH_LECTERN)
        );
        public static final ServerIngameStatsJSONCreator CAULDRON = new BlockInteractions("cauldron",
                Stats.CUSTOM.getOrCreateStat(Stats.FILL_CAULDRON),
                Stats.CUSTOM.getOrCreateStat(Stats.USE_CAULDRON),
                Stats.CUSTOM.getOrCreateStat(Stats.CLEAN_ARMOR),
                Stats.CUSTOM.getOrCreateStat(Stats.CLEAN_BANNER),
                Stats.CUSTOM.getOrCreateStat(Stats.CLEAN_SHULKER_BOX)
        );
        public static final ServerIngameStatsJSONCreator MISCELLANEOUS = new BlockInteractions("misc",
                Stats.CUSTOM.getOrCreateStat(Stats.SLEEP_IN_BED),
                Stats.CUSTOM.getOrCreateStat(Stats.BELL_RING),
                Stats.CUSTOM.getOrCreateStat(Stats.EAT_CAKE_SLICE),
                Stats.CUSTOM.getOrCreateStat(Stats.POT_FLOWER)
        );
    }

    public static sealed class ItemsCategory extends ServerIngameStatsJSONCreator {
        private static final String CATEGORY_KEY = "items";

        private ItemsCategory(String path, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, path), stats);
        }

        private ItemsCategory(List<String> path, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, path), stats);
        }

        public static final ServerIngameStatsJSONCreator OVERVIEW = new ItemsCategory("overview",
                Stats.CUSTOM.getOrCreateStat(Stats.DROP),
                Stats.CUSTOM.getOrCreateStat(Stats.ENCHANT_ITEM),
                Stats.CUSTOM.getOrCreateStat(Stats.FISH_CAUGHT)
        );

        public static final class SpecificItem extends ItemsCategory {
            private SpecificItem(Item item) {
                super(extendPath("specific_item", Registries.ITEM, item), getApplicableStats(item));
            }

            private static Stat<?>[] getApplicableStats(Item item) {
                var stats = new ArrayList<Stat<?>>(List.of(
                        Stats.CRAFTED.getOrCreateStat(item),
                        Stats.USED.getOrCreateStat(item),
                        Stats.PICKED_UP.getOrCreateStat(item),
                        Stats.DROPPED.getOrCreateStat(item),
                        Stats.BROKEN.getOrCreateStat(item)
                ));
                if (item instanceof BlockItem blockItem) {
                    stats.add(Stats.MINED.getOrCreateStat(blockItem.getBlock()));
                }
                return stats.toArray(Stat<?>[]::new);
            }

            public static final List<? extends ServerIngameStatsJSONCreator> ITEMS = Registries.ITEM.stream().map(SpecificItem::new).toList();
        }
    }

    public static sealed class MobsCategory extends ServerIngameStatsJSONCreator {
        private static final String CATEGORY_KEY = "mobs";

        private MobsCategory(String path, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, path), stats);
        }

        private MobsCategory(List<String> path, Stat<?>... stats) {
            super(extendPath(CATEGORY_KEY, path), stats);
        }

        public static final ServerIngameStatsJSONCreator OVERVIEW = new MobsCategory("overview",
                Stats.CUSTOM.getOrCreateStat(Stats.MOB_KILLS),
                Stats.CUSTOM.getOrCreateStat(Stats.ANIMALS_BRED)
        );
        public static final ServerIngameStatsJSONCreator RAIDS = new MobsCategory("raids",
                Stats.CUSTOM.getOrCreateStat(Stats.RAID_TRIGGER),
                Stats.CUSTOM.getOrCreateStat(Stats.RAID_WIN)
        );
        public static final ServerIngameStatsJSONCreator VILLAGERS = new MobsCategory("villagers",
                Stats.CUSTOM.getOrCreateStat(Stats.TALKED_TO_VILLAGER),
                Stats.CUSTOM.getOrCreateStat(Stats.TRADED_WITH_VILLAGER)
        );

        public static final class SpecificMob extends MobsCategory {
            private SpecificMob(EntityType<?> entityType) {
                super(extendPath("specific_mob", Registries.ENTITY_TYPE, entityType),
                        Stats.KILLED.getOrCreateStat(entityType),
                        Stats.KILLED_BY.getOrCreateStat(entityType)
                );
            }

            // TODO how to get only LivingEntities
            public static final List<? extends ServerIngameStatsJSONCreator> MOBS = Registries.ENTITY_TYPE.stream().map(MobsCategory.SpecificMob::new).toList();
        }
    }

    private static final List<ServerIngameStatsJSONCreator> ALL_VIEWS = new ArrayList<>();

    public static List<ServerIngameStatsJSONCreator> getAll() {
        if (ALL_VIEWS.isEmpty()) {
            ALL_VIEWS.add(GeneralCategory.PLAYTIME);
            ALL_VIEWS.add(GeneralCategory.DAMAGE_DEALT);
            ALL_VIEWS.add(GeneralCategory.DAMAGE_TAKEN);
            ALL_VIEWS.add(GeneralCategory.KILLS_AND_DEATHS);

            ALL_VIEWS.add(MovementCategory.GROUND);
            ALL_VIEWS.add(MovementCategory.AIR);
            ALL_VIEWS.add(MovementCategory.WATER);
            ALL_VIEWS.add(MovementCategory.BY_VEHICLE);

            ALL_VIEWS.add(BlockInteractions.STORAGE_BLOCKS);
            ALL_VIEWS.add(BlockInteractions.CRAFTING_BLOCKS);
            ALL_VIEWS.add(BlockInteractions.SMELTING_BLOCKS);
            ALL_VIEWS.add(BlockInteractions.REDSTONE_BLOCKS);
            ALL_VIEWS.add(BlockInteractions.MUSIC_BLOCKS);
            ALL_VIEWS.add(BlockInteractions.UTILITY_BLOCKS);
            ALL_VIEWS.add(BlockInteractions.CAULDRON);
            ALL_VIEWS.add(BlockInteractions.MISCELLANEOUS);

            ALL_VIEWS.add(ItemsCategory.OVERVIEW);
            ALL_VIEWS.addAll(ItemsCategory.SpecificItem.ITEMS);

            ALL_VIEWS.add(MobsCategory.OVERVIEW);
            ALL_VIEWS.add(MobsCategory.RAIDS);
            ALL_VIEWS.add(MobsCategory.VILLAGERS);
            ALL_VIEWS.addAll(MobsCategory.SpecificMob.MOBS);
        }
        return ALL_VIEWS;
    }

    public static Optional<ServerIngameStatsJSONCreator> getByKey(String key) {
        return getAll().stream()
                .filter(serverIngameStatsJSONCreator -> serverIngameStatsJSONCreator.key.equals(key))
                .findAny();
    }
}
