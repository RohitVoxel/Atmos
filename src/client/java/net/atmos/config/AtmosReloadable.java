package net.atmos.config;

/** Registered with AtmosReloadManager; invoked once per successful config reload. */
@FunctionalInterface
public interface AtmosReloadable {
    void onConfigReload();
}