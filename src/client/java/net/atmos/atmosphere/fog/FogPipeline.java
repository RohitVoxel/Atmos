package net.atmos.atmosphere.fog;

import net.atmos.atmosphere.EnvironmentalState;
import java.util.List;

public final class FogPipeline {

    private final List<FogModifier> modifiers;

    public FogPipeline(List<FogModifier> modifiers) {
        this.modifiers = List.copyOf(modifiers);
    }

    public FogState run(FogState initial, FogContext context, EnvironmentalState env) {
        FogState state = initial;
        for (FogModifier modifier : modifiers) {
            state = modifier.apply(state, context, env);
        }
        return state;
    }
}