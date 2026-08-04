package net.atmos.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.atmos.seasonal.Season;
import net.atmos.seasonal.SeasonalFeelingSnapshot;
import net.atmos.seasonal.SeasonalFeelingStateManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Commands only modify SeasonDebugState (a debug time override consumed at
 * the AtmosClient call site) or read the already-published
 * SeasonalFeelingSnapshot. Never renders. Never touches frozen Seasonal
 * Feeling System math directly.
 */
public final class SeasonCommands {

    private static final long YEAR_LENGTH_TICKS = 24_000L * 365L;
    private static final long QUARTER_TICKS = YEAR_LENGTH_TICKS / 4L;

    private SeasonCommands() {}

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var season = literal("season");
        var set = literal("set");

        set.then(seasonLiteral("spring", Season.SPRING, 0.0f));
        set.then(seasonLiteral("early-spring", Season.SPRING, 0.05f));
        set.then(seasonLiteral("mid-spring", Season.SPRING, 0.5f));
        set.then(seasonLiteral("late-spring", Season.SPRING, 0.95f));
        set.then(seasonLiteral("summer", Season.SUMMER, 0.0f));
        set.then(seasonLiteral("autumn", Season.AUTUMN, 0.0f));
        set.then(seasonLiteral("winter", Season.WINTER, 0.0f));
        season.then(set);

        season.then(literal("transition")
                .then(argument("season", StringArgumentType.word())
                        .then(argument("days", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    String seasonName = StringArgumentType.getString(ctx, "season");
                                    int days = IntegerArgumentType.getInteger(ctx, "days");
                                    Season target = Season.valueOf(seasonName.toUpperCase());
                                    SeasonDebugState.setOverride(seasonWorldTime(target, 0f));
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Atmos: seeking toward " + seasonName
                                                    + " (" + days + " in-game day transition target)."));
                                    return 1;
                                }))));

        season.then(literal("progress")
                .then(argument("value", FloatArgumentType.floatArg(0f, 1f))
                        .executes(ctx -> {
                            float progress = FloatArgumentType.getFloat(ctx, "value");
                            SeasonalFeelingSnapshot snapshot = SeasonalFeelingStateManager.get();
                            Season current = snapshot.calendar().currentSeason();
                            SeasonDebugState.setOverride(seasonWorldTime(current, progress));
                            ctx.getSource().sendFeedback(Component.literal(
                                    "Atmos: season progress set to " + progress));
                            return 1;
                        })));

        season.then(literal("pause").executes(ctx -> {
            SeasonalFeelingSnapshot snapshot = SeasonalFeelingStateManager.get();
            long effectiveTime = seasonWorldTime(snapshot.calendar().currentSeason(), snapshot.calendar().seasonProgress());
            SeasonDebugState.pause(effectiveTime);
            ctx.getSource().sendFeedback(Component.literal("Atmos: season cycle paused."));
            return 1;
        }));

        season.then(literal("resume").executes(ctx -> {
            SeasonDebugState.clearOverride();
            SeasonDebugState.resume();
            ctx.getSource().sendFeedback(Component.literal("Atmos: season cycle resumed."));
            return 1;
        }));

        root.then(season);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> seasonLiteral(String name, Season season, float progress) {
        return literal(name).executes(ctx -> {
            SeasonDebugState.setOverride(seasonWorldTime(season, progress));
            ctx.getSource().sendFeedback(Component.literal("Atmos: season set to " + name));
            return 1;
        });
    }

    private static long seasonWorldTime(Season season, float progressWithinSeason) {
        int index = season.ordinal();
        return (long) (index * QUARTER_TICKS + progressWithinSeason * QUARTER_TICKS);
    }
}
