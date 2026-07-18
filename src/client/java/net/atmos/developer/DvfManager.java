package net.atmos.developer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.atmos.core.CameraManager;
import net.atmos.developer.overlays.DvfOverlay;
import net.atmos.developer.overlays.OverlayCapability;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public final class DvfManager {

    private static DvfMode currentMode = DvfMode.OFF;
    private static KeyMapping toggleKey;
    private static String atmosVersion = "Unknown";

    private static final Map<DvfMode, DvfOverlay> OVERLAYS = DvfOverlayRegistry.createDefaultRegistry();

    private DvfManager() {}

    public static void init() {
        atmosVersion = FabricLoader.getInstance().getModContainer("atmos")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("Unknown-Debug");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.atmos.dvf_toggle",
                GLFW.GLFW_KEY_F8,
                "category.atmos.developer"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                currentMode = currentMode.next();
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (currentMode == DvfMode.OFF || Minecraft.getInstance().getDebugOverlay().showDebugScreen()) return;

            long frameId = CameraManager.get() != null ? CameraManager.get().frameSequence() : -1;
            DvfDataAccess.ensureSnapshot(frameId);

            int y = 10;

            graphics.drawString(Minecraft.getInstance().font, "§aAtmos DVF §f| §7Version " + atmosVersion, 10, y, 0xFFFFFF, true);
            y += 15;

            graphics.drawString(Minecraft.getInstance().font, "§bMode: §e" + currentMode.getDisplayName(), 10, y, 0xFFFFFF, true);
            y += 15;

            DvfOverlay overlay = OVERLAYS.get(currentMode);
            if (overlay != null) {
                if (overlay.capabilities().contains(OverlayCapability.HUD)) {
                    overlay.renderHud(graphics, y);
                }
            } else {
                graphics.drawString(Minecraft.getInstance().font, "§cUnavailable: Data is transient/uncached in pipeline.", 10, y, 0xFFFFFF, true);
            }
        });

        WorldRenderEvents.LAST.register(context -> {
            if (currentMode == DvfMode.OFF) return;
            if (context.consumers() == null || context.matrixStack() == null) return;

            // ... rest of the rendering logic

            long frameId = CameraManager.get() != null ? CameraManager.get().frameSequence() : -1;
            DvfDataAccess.ensureSnapshot(frameId);

            DvfOverlay overlay = OVERLAYS.get(currentMode);
            if (overlay != null && overlay.capabilities().contains(OverlayCapability.WORLD)) {
                PoseStack poseStack = context.matrixStack();
                Vec3 cameraPos = context.camera().getPosition();

                poseStack.pushPose();
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                overlay.renderWorld(poseStack, context);

                poseStack.popPose();
            }
        });
    }

    public static DvfMode getMode() {
        return currentMode;
    }
}