package io.github.ummamute.driver;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric entrypoint. It is Java so the mod loads without Fabric Language Kotlin; the Kotlin runtime ships
 * inside this jar.
 */
public class DriverMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DriverBootstrap.INSTANCE.start();
    }
}
