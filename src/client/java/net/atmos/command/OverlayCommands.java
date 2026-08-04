package net.atmos.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.atmos.core.AtmosClient;
import net.atmos.overlay.OverlaySource;
import net.atmos.overlay.OverlayType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Commands only write a manual contribution into OverlayManager — they
 * never render and never bypass the manager's own combination logic.
 */
public final class OverlayCommands {

    private OverlayCommands() {}

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var overlay = literal("overlay");

        overlay.then(overlayLiteral("frost", OverlayType.FROST));
        overlay.then(overlayLiteral("snow", OverlayType.SNOW));
        overlay.then(overlayLiteral("wet", OverlayType.WET));
        overlay.then(overlayLiteral("autumn", OverlayType.AUTUMN));
        overlay.then(overlayLiteral("dust", OverlayType.DUST));
        overlay.then(overlayLiteral("pollen", OverlayType.POLLEN));

        overlay.then(literal("reset").executes(ctx -> {
            AtmosClient.getOverlayManager().reset();
            ctx.getSource().sendFeedback(Component.literal("Atmos: overlay values reset."));
            return 1;
        }));

        root.then(overlay);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> overlayLiteral(String name, OverlayType type) {
        return literal(name)
                .then(argument("value", FloatArgumentType.floatArg(0f, 1f))
                        .executes(ctx -> {
                            float value = FloatArgumentType.getFloat(ctx, "value");
                            AtmosClient.getOverlayManager().setContribution(type, OverlaySource.MANUAL, value);
                            ctx.getSource().sendFeedback(Component.literal(
                                    "Atmos: " + name + " overlay target set to " + value));
                            return 1;
                        }));
    }
}
