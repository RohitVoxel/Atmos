package net.atmos.atmosphere.fog;

import net.atmos.atmosphere.EnvironmentalState;

@FunctionalInterface
public interface FogModifier {
    FogState apply(FogState fog, FogContext context, EnvironmentalState env);
}