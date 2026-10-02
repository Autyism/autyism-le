package com.autyism.ale;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autyism's Litematica Enhancement：Litematica / MaLiLib 系列的增强功能合集（独立于打印机模组）。
 */
public class AleMod implements ClientModInitializer {
    public static final String MOD_ID = "autyism-le";
    public static final String MOD_NAME = "Autyism's Litematica Enhancement";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        InitializationHandler.getInstance().registerInitializationHandler(AleConfigs::init);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> com.autyism.ale.verifier.ContainerVerifier.tick());
    }
}
