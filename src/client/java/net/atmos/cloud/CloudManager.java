package net.atmos.cloud;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Owns the cloud system lifecycle: loads the texture registry, builds the
 * five fixed Batch 1 layers, and publishes {@link CloudRenderState}.
 *
 * Texture assignment happens once during {@link #initialize()} and never
 * changes afterward — Batch 1 rendering must remain deterministic after
 * startup. Never renders, never calculates simulation, never queries
 * seasons/weather/air.
 */
public final class CloudManager {

    private record LayerBlueprint(
            CloudLayerType type, float height, float scale, float opacity, List<String> familyPreference
    ) {}

    // Placeholder heights/scales/opacities only — no behavioural difference
    // between layers yet beyond these static values. familyPreference lists
    // candidate families in priority order; the first family with any
    // discovered textures is used.
    private static final List<LayerBlueprint> BLUEPRINTS = List.of(
            new LayerBlueprint(CloudLayerType.HIGH_WISPY, 320f, 1400f, 0.35f, List.of("cirrus", "wispy")),
            new LayerBlueprint(CloudLayerType.UPPER, 256f, 1000f, 0.45f, List.of("small", "broken")),
            new LayerBlueprint(CloudLayerType.MAIN_DECK, 192f, 900f, 0.65f, List.of("large", "dense", "medium")),
            new LayerBlueprint(CloudLayerType.LOWER, 140f, 700f, 0.50f, List.of("puffy", "medium")),
            new LayerBlueprint(CloudLayerType.HORIZON, 100f, 1600f, 0.30f, List.of("overcast", "fogsheet"))
    );

    private final CloudTextureRegistry registry = new CloudTextureRegistry();

    private volatile CloudRenderState renderState = CloudRenderState.empty();
    private boolean initialized = false;

    /** Discovers textures, assigns each layer a texture once, and publishes the render state. */
    public void initialize() {
        registry.initialize();

        Random random = new Random();
        List<CloudLayer> layers = new ArrayList<>(BLUEPRINTS.size());
        for (int i = 0; i < BLUEPRINTS.size(); i++) {
            layers.add(buildLayer(i, BLUEPRINTS.get(i), random));
        }

        renderState = new CloudRenderState(layers);
        initialized = true;
    }

    private CloudLayer buildLayer(int index, LayerBlueprint blueprint, Random random) {
        String resolvedFamily = blueprint.familyPreference().get(0);
        List<ResourceLocation> candidates = List.of();

        for (String family : blueprint.familyPreference()) {
            List<ResourceLocation> textures = registry.texturesFor(family);
            if (!textures.isEmpty()) {
                resolvedFamily = family;
                candidates = textures;
                break;
            }
        }

        ResourceLocation texture = candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));

        return new CloudLayer(
                index, blueprint.type(), blueprint.height(), blueprint.scale(), blueprint.opacity(),
                resolvedFamily, texture, texture != null
        );
    }

    public CloudRenderState getRenderState() {
        return renderState;
    }

    public CloudTextureRegistry getRegistry() {
        return registry;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void reset() {
        registry.reset();
        renderState = CloudRenderState.empty();
        initialized = false;
    }
}
