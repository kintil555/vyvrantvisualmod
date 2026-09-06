package com.bloom.client;

import com.bloom.BloomMod;
import com.bloom.client.config.BloomConfig;
import com.bloom.client.config.BloomConfigScreen;
import com.bloom.client.config.BloomMaskConfig;
import com.bloom.client.render.BloomEntityMaskTextures;
import com.bloom.client.render.BloomEntityTextureCatalog;
import com.bloom.client.render.BloomMaskAtlas;
import com.bloom.client.render.BloomPostProcessor;
import com.bloom.client.render.LevelRendererRebuilds;
import com.bloom.client.render.ShineRenderBackend;
import com.bloom.client.render.TransientMeshArena;
import com.bloom.client.experimental.config.ShoreFoamConfigManager;
import com.bloom.client.experimental.render.ExperimentalShoreFoamConfigScreen;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Minimal client entrypoint: bloom post-processing + shore foam only.
 * The original Shine codebase wires in ~30 other experimental features
 * (particles, weather, rim light, grass blades, etc.) here; those are
 * intentionally not included in this trimmed-down build.
 */
public class BloomClient implements ClientModInitializer {
   private static final KeyMapping.Category MAIN_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("vybrantvisual", "main"));
   private static final KeyMapping TOGGLE_BLOOM_KEY;
   private static final KeyMapping OPEN_EDITOR_KEY;

   public void onInitializeClient() {
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientLifecycleEvents.ClientStarted)(client) ->
         BloomMod.LOGGER.info("Vybrant Visual selected graphics backend: {}.", ShineRenderBackend.current()));

      BloomConfig.load();
      BloomMaskConfig.load();
      ShoreFoamConfigManager.load();

      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(client) -> {
         BloomPostProcessor.shutdown();
         TransientMeshArena.close();
      });

      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
         public Identifier getFabricId() {
            return Identifier.fromNamespaceAndPath("vybrantvisual", "mask_reload");
         }

         public void onResourceManagerReload(ResourceManager manager) {
            TransientMeshArena.close();
            BloomMaskAtlas.markDirty();
            BloomEntityMaskTextures.markDirty();
            BloomEntityTextureCatalog.markDirty();
            BloomPostProcessor.onConfigSaved();
            LevelRendererRebuilds.requestChunkGeometryRebuild();
         }
      });

      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         while (TOGGLE_BLOOM_KEY.consumeClick()) {
            boolean enabled = BloomPostProcessor.toggleFromKeybind();
            BloomMod.LOGGER.info("Vybrant Visual post-processing {}", enabled ? "enabled" : "disabled");
         }

         while (OPEN_EDITOR_KEY.consumeClick()) {
            if (client.gui.screen() == null) {
               client.gui.setScreen(BloomConfigScreen.create((Screen)null));
            }
         }
      });
   }

   /**
    * Opens the shore foam config screen, editing a copy of the current settings.
    */
   public static void openShoreFoamScreen(Screen parent) {
      Minecraft.getInstance().gui.setScreen(ExperimentalShoreFoamConfigScreen.create(parent, ShoreFoamConfigManager.get().copy(), ShoreFoamConfigManager.defaults()));
   }

   static {
      TOGGLE_BLOOM_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vybrantvisual.toggle", Type.KEYSYM, 66, MAIN_CATEGORY));
      OPEN_EDITOR_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vybrantvisual.editor", Type.KEYSYM, 75, MAIN_CATEGORY));
   }
}
