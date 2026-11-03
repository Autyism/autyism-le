package com.autyism.ale.locate;

import com.autyism.ale.diff.DiffSession;
import fi.dy.masa.malilib.event.RenderEventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Registers the wrong block guide and the schematic diff (ticks, HUD, chunk events) */
public final class ToolsInit {
    private static boolean done;

    private ToolsInit() {
    }

    public static void init() {
        if (done) return;
        done = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WrongBlockGuide.tick(client);
            DiffSession.tick(client);
        });
        // a chunk of the client world (re)loaded or went away: what the guide knows about it is out of date
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (LocateIndex.active) LocateIndex.markColumnChanged(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (LocateIndex.active) LocateIndex.markColumnChanged(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
        });
        //? if >=26.1 {
        /*RenderEventHandler.getInstance().registerInGameGuiRenderer(ToolsHud.INSTANCE);
        *///?} else
        RenderEventHandler.getInstance().registerGameOverlayRenderer(ToolsHud.INSTANCE);
    }
}
