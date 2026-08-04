package net.atmos.cloud;

import java.util.List;

/**
 * Immutable render state published by {@link CloudManager}. Contains only
 * the five {@link CloudLayer} instances required to render — no simulation
 * values. CloudRenderer consumes this and never mutates it.
 */
public record CloudRenderState(List<CloudLayer> layers) {
    public CloudRenderState {
        if (layers == null) {
            throw new IllegalArgumentException("layers must not be null — use List.of() for none");
        }
        layers = List.copyOf(layers);
    }

    public static CloudRenderState empty() {
        return new CloudRenderState(List.of());
    }
}
