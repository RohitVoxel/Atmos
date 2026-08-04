package net.atmos.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Root of the /atmos command tree. New systems register their own
 * subcommands here without touching any other command class.
 */
public final class AtmosCommandRegistry {

    private AtmosCommandRegistry() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(AtmosCommandRegistry::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandBuildContext context
    ) {
        var root = ClientCommandManager.literal("atmos");

        SeasonCommands.register(root);
        OverlayCommands.register(root);
        DebugCommands.register(root);

        dispatcher.register(root);
    }
}
