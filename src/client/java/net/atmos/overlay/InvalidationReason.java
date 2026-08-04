package net.atmos.overlay;

/** Why a surface or mesh bucket was invalidated — diagnostic context only, never branched on for correctness. */
public enum InvalidationReason {
    BLOCK_CHANGED,
    CHUNK_LOADED,
    LEVEL_CROSSING,
    CONFIG_RELOAD,
    MANUAL
}