package com.bloom.client;

import com.bloom.BloomMod;
import com.bloom.client.coloredlight.ColoredLightConfigManager;
import com.bloom.client.coloredlight.ColoredLightRenderer;
import com.bloom.client.command.ShineClientCommands;
import com.bloom.client.compat.ShineCompatibilityManager;
import com.bloom.client.compat.ShineInteropManager;
import com.bloom.client.config.BloomConfig;
import com.bloom.client.config.BloomMaskConfig;
import com.bloom.client.config.ShineConfigScreen;
import com.bloom.client.config.ShineFpsDiagnosticsScreen;
import com.bloom.client.config.ShineInterfaceConfig;
import com.bloom.client.config.ShineOnboardingConfig;
import com.bloom.client.config.ShinePresetStateManager;
import com.bloom.client.customparticles.CustomParticleManager;
import com.bloom.client.customparticles.CustomParticleRuntime;
import com.bloom.client.diagnostics.ShineFpsDiagnostics;
import com.bloom.client.experimental.algae.SurfaceAlgaeFlecksRenderer;
import com.bloom.client.experimental.amethyst.AmethystSparkleParticle;
import com.bloom.client.experimental.amethyst.AmethystSparkleSpawner;
import com.bloom.client.experimental.camera.CameraShakeController;
import com.bloom.client.experimental.cavedust.CaveDustParticle;
import com.bloom.client.experimental.cavedust.CaveDustSpawner;
import com.bloom.client.experimental.chestbubbles.UnderwaterChestBubbleParticle;
import com.bloom.client.experimental.config.ExperimentalConfigManager;
import com.bloom.client.experimental.desertdust.DesertDustParticle;
import com.bloom.client.experimental.desertdust.DesertDustSpawner;
import com.bloom.client.experimental.duckweedlitter.DuckweedLitterParticle;
import com.bloom.client.experimental.duckweedlitter.DuckweedLitterSpawner;
import com.bloom.client.experimental.enddust.EndDustRenderer;
import com.bloom.client.experimental.endportal.EndPortalEffectsRenderer;
import com.bloom.client.experimental.endportal.EndPortalEyePlacementParticle;
import com.bloom.client.experimental.endportal.EndPortalEyePlacementRenderer;
import com.bloom.client.experimental.endportal.EndPortalGlowDustRenderer;
import com.bloom.client.experimental.endportal.EndPortalLightRayRenderer;
import com.bloom.client.experimental.endportal.EndPortalRisingBurstRenderer;
import com.bloom.client.experimental.flowerlitter.FlowerLitterParticle;
import com.bloom.client.experimental.flowerlitter.FlowerLitterSpawner;
import com.bloom.client.experimental.fogfx.FogFxParticle;
import com.bloom.client.experimental.fogfx.FogFxSpawner;
import com.bloom.client.experimental.foliagesway.FoliageSwayController;
import com.bloom.client.experimental.grassblades.GrassBladeInteractionController;
import com.bloom.client.experimental.grassblades.GrassBladesModel;
import com.bloom.client.experimental.groundlitter.InteractiveGroundLitterParticle;
import com.bloom.client.experimental.groundlitter.InteractiveGroundLitterSpawner;
import com.bloom.client.experimental.groundmist.GroundMistParticle;
import com.bloom.client.experimental.groundmist.GroundMistSpawner;
import com.bloom.client.experimental.index.ClientSectionFeatureIndex;
import com.bloom.client.experimental.jellyfish.JellyfishParticle;
import com.bloom.client.experimental.jellyfish.JellyfishSpawner;
import com.bloom.client.experimental.lanterntorch.LanternTorchParticle;
import com.bloom.client.experimental.lanterntorch.LanternTorchSmokeParticle;
import com.bloom.client.experimental.lavaparticles.GlowingAshParticle;
import com.bloom.client.experimental.lavaparticles.LavaDropletSplashParticle;
import com.bloom.client.experimental.lavaparticles.LavaEmberParticle;
import com.bloom.client.experimental.lavaparticles.LavaParticlesSpawner;
import com.bloom.client.experimental.lavaparticles.LavaPopParticle;
import com.bloom.client.experimental.lavaparticles.LavaSprayFlashParticle;
import com.bloom.client.experimental.lavaparticles.LavaSprayParticle;
import com.bloom.client.experimental.lavaplate.LavaPlateParticle;
import com.bloom.client.experimental.lavaplate.LavaPlateSpawner;
import com.bloom.client.experimental.lavasteam.LavaSteamParticle;
import com.bloom.client.experimental.lavasteam.LavaSteamSpawner;
import com.bloom.client.experimental.leaflitter.LeafLitterParticle;
import com.bloom.client.experimental.leaflitter.LeafLitterSoundController;
import com.bloom.client.experimental.leaflitter.LeafLitterSpawner;
import com.bloom.client.experimental.lilypadlitter.LilyPadLitterParticle;
import com.bloom.client.experimental.lilypadlitter.LilyPadLitterSpawner;
import com.bloom.client.experimental.mist.MistParticle;
import com.bloom.client.experimental.mist.MistSpawner;
import com.bloom.client.experimental.netherlavadust.NetherLavaDustRenderer;
import com.bloom.client.experimental.netherrays.NetherRaysParticle;
import com.bloom.client.experimental.netherrays.NetherRaysSpawner;
import com.bloom.client.experimental.palegardendust.PaleGardenDustRenderer;
import com.bloom.client.experimental.palegardeneyes.PaleGardenEyesRenderer;
import com.bloom.client.experimental.plantshadows.PlantContactShadowModel;
import com.bloom.client.experimental.rainbow.RainbowRenderer;
import com.bloom.client.experimental.reactivewater.BoatSideWaveTrailRenderer;
import com.bloom.client.experimental.reactivewater.BoatSplashParticle;
import com.bloom.client.experimental.reactivewater.BoatSplashSpawner;
import com.bloom.client.experimental.reactivewater.BoatTrailSplashParticle;
import com.bloom.client.experimental.reactivewater.BoatTrailSplashSpawner;
import com.bloom.client.experimental.reactivewater.ReactiveWaterRenderer;
import com.bloom.client.experimental.reactivewater.TrailerSplashBandParticle;
import com.bloom.client.experimental.reactivewater.TrailerSplashParticle;
import com.bloom.client.experimental.reactivewater.WaterSplashDropletParticle;
import com.bloom.client.experimental.render.ExperimentalPostProcessor;
import com.bloom.client.experimental.render.FoliagePixelWindRenderer;
import com.bloom.client.experimental.render.ShineEnvironmentPipeline;
import com.bloom.client.experimental.render.ShineWorldHazeMesh;
import com.bloom.client.experimental.render.TerrainWaterUniformBuffer;
import com.bloom.client.experimental.render.WaterReflectionRenderer;
import com.bloom.client.experimental.render.sodium.SodiumVulkanTerrainUniforms;
import com.bloom.client.experimental.sculkdust.SculkDustRenderer;
import com.bloom.client.experimental.sound.CicadaAmbientSoundHandler;
import com.bloom.client.experimental.sound.DeepDarkAmbientSoundController;
import com.bloom.client.experimental.sound.SurfaceMovementSoundMixer;
import com.bloom.client.experimental.surfaceimprints.SurfaceImprintRenderer;
import com.bloom.client.experimental.surfaceimprints.SurfaceImprintTracker;
import com.bloom.client.experimental.tumbleweed.TumbleweedParticle;
import com.bloom.client.experimental.tumbleweed.TumbleweedSpawner;
import com.bloom.client.experimental.watercascade.WaterCascadeParticle;
import com.bloom.client.experimental.watercascade.WaterCascadeSpawner;
import com.bloom.client.experimental.weather.BlockSideRainParticle;
import com.bloom.client.experimental.weather.BlockSideRainSpawner;
import com.bloom.client.experimental.weather.CustomRainParticle;
import com.bloom.client.experimental.weather.CustomRainSpawner;
import com.bloom.client.experimental.weather.CustomRainSplashParticle;
import com.bloom.client.experimental.weather.DesertWindSoundController;
import com.bloom.client.experimental.weather.LightRainBounceArcRenderer;
import com.bloom.client.experimental.weather.LightRainController;
import com.bloom.client.experimental.weather.LightRainSoundController;
import com.bloom.client.experimental.weather.NetherStormController;
import com.bloom.client.experimental.weather.NetherStormFireDustRenderer;
import com.bloom.client.experimental.weather.NetherStormSoundController;
import com.bloom.client.experimental.weather.RainPuddleParticle;
import com.bloom.client.experimental.weather.RainPuddleSpawner;
import com.bloom.client.experimental.weather.WeatherParticle;
import com.bloom.client.experimental.weather.WeatherParticleSpawner;
import com.bloom.client.experimental.worldambience.ButterflyHingedWingRenderer;
import com.bloom.client.experimental.worldambience.VultureRenderer;
import com.bloom.client.experimental.worldambience.WorldAmbienceEffectsSpawner;
import com.bloom.client.experimental.worldambience.WorldAmbienceParticle;
import com.bloom.client.experimental.xptrails.XpGroundTrailRenderer;
import com.bloom.client.menu.ShineMenuPanorama;
import com.bloom.client.menu.ShineOptionsScreenButton;
import com.bloom.client.menu.ShineTitleScreenButton;
import com.bloom.client.migration.ShineUpgradeMigration;
import com.bloom.client.migration.ShineUpgradeNotice;
import com.bloom.client.migration.ShineVanillaDefaults;
import com.bloom.client.migration.ShineVulkanOnboarding;
import com.bloom.client.particle.ShineParticleAtlases;
import com.bloom.client.render.BloomEntityMaskTextures;
import com.bloom.client.render.BloomEntityTextureCatalog;
import com.bloom.client.render.BloomMaskAtlas;
import com.bloom.client.render.BloomPostProcessor;
import com.bloom.client.render.LevelRendererRebuilds;
import com.bloom.client.render.ShineRenderBackend;
import com.bloom.client.render.TransientMeshArena;
import com.bloom.client.resource.ShineResourceDefaults;
import com.bloom.client.rimlight.config.RimLightConfigManager;
import com.bloom.client.rimlight.render.RimLightMaskRenderer;
import com.bloom.client.rimlight.render.RimLightRenderer;
import com.bloom.client.shading.ShadingConfigManager;
import com.bloom.client.shinepack.ShinePackManager;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.Objects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class BloomClient implements ClientModInitializer {
   private static final KeyMapping.Category MAIN_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("vybrantvisual", "main"));
   private static final KeyMapping TOGGLE_BLOOM_KEY;
   private static final KeyMapping TOGGLE_SHINE_KEY;
   private static final KeyMapping OPEN_EDITOR_KEY;
   private static final KeyMapping OPEN_LAST_EDITOR_PAGE_KEY;
   private static final KeyMapping OPEN_DIAGNOSTICS_KEY;

   public void onInitializeClient() {
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientLifecycleEvents.ClientStarted)(client) -> {
         BloomMod.LOGGER.info("Shine selected graphics backend: {}.", ShineRenderBackend.current());
         ShineVanillaDefaults.applyOnce(client);
      });
      ShineUpgradeMigration.MigrationPlan upgradePlan = ShineUpgradeMigration.prepare();
      BloomConfig.load();
      BloomMaskConfig.load();
      ShineInterfaceConfig.init();
      ShineOnboardingConfig.init();
      ShineMasterToggle.init();
      ColoredLightConfigManager.init();
      ExperimentalConfigManager.init();
      CustomParticleManager.init();
      RimLightConfigManager.init();
      ShadingConfigManager.init();
      ShineCompatibilityManager.init();
      ShineFpsDiagnostics.init();
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(client) -> {
         BloomPostProcessor.shutdown();
         WaterReflectionRenderer.shutdown();
         TerrainWaterUniformBuffer.shutdown();
         SodiumVulkanTerrainUniforms.shutdown();
         ClientSectionFeatureIndex.clear();
         ShineWorldHazeMesh.close();
         TransientMeshArena.close();
         ShineMenuPanorama.close();
      });
      ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((ClientLevelEvents.AfterClientLevelChange)(client, world) -> ClientSectionFeatureIndex.clear());
      ShineClientCommands.register();
      ShineUpgradeMigration.applyAfterConfigLoad(upgradePlan);
      ShinePresetStateManager.initAfterConfigLoad();
      FoliageSwayController.init();
      SurfaceImprintRenderer.init();
      GrassBladesModel.init();
      PlantContactShadowModel.init();
      RimLightRenderer.init();
      ExperimentalPostProcessor.init();
      ShineParticleAtlases.init();
      ShinePackManager.init();
      FogFxParticle.registerFactory();
      MistParticle.registerFactory();
      GroundMistParticle.registerFactory();
      DesertDustParticle.registerFactory();
      NetherRaysParticle.registerFactory();
      LavaSteamParticle.registerFactory();
      LavaEmberParticle.registerFactory();
      LavaPopParticle.registerFactories();
      LavaSprayParticle.registerFactory();
      LavaSprayFlashParticle.registerFactory();
      LavaDropletSplashParticle.registerFactory();
      GlowingAshParticle.registerFactory();
      LeafLitterParticle.registerFactory();
      FlowerLitterParticle.registerFactory();
      InteractiveGroundLitterParticle.registerFactories();
      LilyPadLitterParticle.registerFactory();
      LavaPlateParticle.registerFactory();
      DuckweedLitterParticle.registerFactory();
      CustomRainParticle.registerFactory();
      BlockSideRainParticle.registerFactory();
      CustomRainSplashParticle.registerFactory();
      RainPuddleParticle.registerFactory();
      BoatSplashParticle.registerFactory();
      BoatTrailSplashParticle.registerFactories();
      UnderwaterChestBubbleParticle.registerFactories();
      TrailerSplashBandParticle.registerFactory();
      WaterSplashDropletParticle.registerFactory();
      TrailerSplashParticle.registerFactories();
      WaterCascadeParticle.registerFactory();
      WeatherParticle.registerFactory();
      CaveDustParticle.registerFactory();
      AmethystSparkleParticle.registerFactory();
      LanternTorchParticle.registerFactories();
      LanternTorchSmokeParticle.registerFactory();
      JellyfishParticle.registerFactory();
      EndPortalEyePlacementParticle.registerFactory();
      WorldAmbienceParticle.registerFactories();
      TumbleweedParticle.registerFactory();
      BloomMaskAtlas.markDirty();
      FoliagePixelWindRenderer.markDirty();
      GrassBladeInteractionController.markDirty();
      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
         {
            Objects.requireNonNull(BloomClient.this);
         }

         public Identifier getFabricId() {
            return Identifier.fromNamespaceAndPath("vybrantvisual", "mask_reload");
         }

         public void onResourceManagerReload(ResourceManager manager) {
            ShineInteropManager.reloadResources(manager);
            WorldAmbienceParticle.requestResourceReloadReset();
            FlowerLitterParticle.requestResourceReloadReset();
            DesertDustParticle.requestResourceReloadReset();
            ShineWorldHazeMesh.close();
            TransientMeshArena.close();
            GrassBladesModel.onResourceReload();
            ShineResourceDefaults.reload(manager);
            ShinePresetStateManager.reloadResourcePresets(manager);
            ColoredLightConfigManager.onResourceDefaultsChanged();
            BloomMaskAtlas.markDirty();
            FoliagePixelWindRenderer.markDirty();
            GrassBladeInteractionController.markDirty();
            BloomEntityMaskTextures.markDirty();
            BloomEntityTextureCatalog.markDirty();
            BloomPostProcessor.onConfigSaved();
            WaterReflectionRenderer.retryAfterResourceReload();
            BloomClient.rebuildChunksForResourceDefaults();
         }
      });
      ScreenEvents.AFTER_INIT.register((ScreenEvents.AfterInit)(client, screen, scaledWidth, scaledHeight) -> {
         if (screen instanceof TitleScreen) {
            ShineTitleScreenButton.add(client, screen, scaledWidth, scaledHeight);
            ScreenMouseEvents.allowMouseClick(screen).register((ScreenMouseEvents.AllowMouseClick)(clickedScreen, event) -> !ShineUpgradeNotice.mouseClicked(clickedScreen, event));
            ShineVulkanOnboarding.maybeShow(client, screen);
         } else if (screen instanceof OptionsScreen) {
            ShineOptionsScreenButton.add(client, screen, scaledWidth, scaledHeight);
         }

      });
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         ShineInteropManager.clientTick();
         WorldAmbienceParticle.applyPendingResourceReloadReset();
         FlowerLitterParticle.applyPendingResourceReloadReset();
         DesertDustParticle.applyPendingResourceReloadReset();
         ShineFpsDiagnostics.clientTick(client);

         while(OPEN_DIAGNOSTICS_KEY.consumeClick()) {
            if (ShineFpsDiagnostics.isActive()) {
               ShineFpsDiagnostics.stop();
            } else if (client.gui.screen() == null) {
               client.gui.setScreen(ShineFpsDiagnosticsScreen.create((Screen)null));
            }
         }

         while(TOGGLE_SHINE_KEY.consumeClick()) {
            boolean enabled = ShineMasterToggle.toggle();
            onMasterToggleChanged(client);
            BloomMod.LOGGER.info("Shine master {}", enabled ? "enabled" : "disabled");
         }

         while(TOGGLE_BLOOM_KEY.consumeClick()) {
            boolean enabled = BloomPostProcessor.toggleFromKeybind();
            BloomMod.LOGGER.info("Shine post-processing {}", enabled ? "enabled" : "disabled");
         }

         while(OPEN_EDITOR_KEY.consumeClick()) {
            if (client.gui.screen() == null) {
               client.gui.setScreen(ShineConfigScreen.create((Screen)null));
            }
         }

         while(OPEN_LAST_EDITOR_PAGE_KEY.consumeClick()) {
            if (client.gui.screen() == null) {
               client.gui.setScreen(ShineConfigScreen.createLastVisited((Screen)null));
            }
         }

         long diagnosticStart = ShineFpsDiagnostics.beginCpu("Foliage sway tick");
         FoliageSwayController.tick(client);
         ShineFpsDiagnostics.endCpu("Foliage sway tick", diagnosticStart);
         diagnosticStart = ShineFpsDiagnostics.beginCpu("Grass blades settings");
         GrassBladesModel.tick(client);
         ShineFpsDiagnostics.endCpu("Grass blades settings", diagnosticStart);
         diagnosticStart = ShineFpsDiagnostics.beginCpu("Grass blade interaction");
         GrassBladeInteractionController.tick(client);
         ShineFpsDiagnostics.endCpu("Grass blade interaction", diagnosticStart);
         DeepDarkAmbientSoundController.tick(client);
         CameraShakeController.tick(client);
         if (!ShineMasterToggle.enabled()) {
            CicadaAmbientSoundHandler.reset();
            LightRainController.tick(client);
            LightRainBounceArcRenderer.tick(client);
            LightRainSoundController.tick(client);
            NetherStormController.tick(client);
            LeafLitterSoundController.tick(client);
            SurfaceMovementSoundMixer.tick(client);
            DesertWindSoundController.tick(client);
            NetherStormSoundController.tick(client);
         } else {
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Nether storm tick");
            NetherStormController.tick(client);
            ShineFpsDiagnostics.endCpu("Nether storm tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Light rain tick");
            LightRainController.tick(client);
            ShineFpsDiagnostics.endCpu("Light rain tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Environment pipeline tick");
            ShineEnvironmentPipeline.update(client);
            ShineFpsDiagnostics.endCpu("Environment pipeline tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Fog FX tick");
            FogFxSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Fog FX tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Mist tick");
            MistSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Mist tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Ground mist tick");
            GroundMistSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Ground mist tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Desert dust tick");
            DesertDustSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Desert dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Nether rays tick");
            NetherRaysSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Nether rays tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Lava steam tick");
            LavaSteamSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Lava steam tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Lava particles tick");
            LavaParticlesSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Lava particles tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Leaf litter tick");
            LeafLitterSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Leaf litter tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Flower litter tick");
            FlowerLitterSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Flower litter tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Interactive ground litter tick");
            InteractiveGroundLitterSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Interactive ground litter tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Lily pad litter tick");
            LilyPadLitterSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Lily pad litter tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Lava plate tick");
            LavaPlateSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Lava plate tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Duckweed tick");
            DuckweedLitterSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Duckweed tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Surface algae tick");
            SurfaceAlgaeFlecksRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Surface algae tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Plant contact shadow settings");
            PlantContactShadowModel.tick(client);
            ShineFpsDiagnostics.endCpu("Plant contact shadow settings", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Reactive water tick");
            ReactiveWaterRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Reactive water tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Boat splash tick");
            BoatSplashSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Boat splash tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Boat trail splash tick");
            BoatTrailSplashSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Boat trail splash tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Boat side wave trails tick");
            BoatSideWaveTrailRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Boat side wave trails tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Water cascade tick");
            WaterCascadeSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Water cascade tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Surface imprint tracking");
            SurfaceImprintTracker.tick(client);
            ShineFpsDiagnostics.endCpu("Surface imprint tracking", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Surface imprint tick");
            SurfaceImprintRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Surface imprint tick", diagnosticStart);
            LeafLitterSoundController.tick(client);
            SurfaceMovementSoundMixer.tick(client);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Custom rain tick");
            CustomRainSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Custom rain tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Block-side rain tick");
            BlockSideRainSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Block-side rain tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Rain puddle tick");
            RainPuddleSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Rain puddle tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Weather particles tick");
            WeatherParticleSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Weather particles tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Nether storm fire dust tick");
            NetherStormFireDustRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Nether storm fire dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Custom particles tick");
            CustomParticleRuntime.tick(client);
            ShineFpsDiagnostics.endCpu("Custom particles tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Cave dust tick");
            CaveDustSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Cave dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Amethyst sparkle tick");
            AmethystSparkleSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Amethyst sparkle tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Jellyfish tick");
            JellyfishSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Jellyfish tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("XP ground trails tick");
            XpGroundTrailRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("XP ground trails tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Light Rain bounce arcs tick");
            LightRainBounceArcRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Light Rain bounce arcs tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("End dust tick");
            EndDustRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("End dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Nether lava dust tick");
            NetherLavaDustRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Nether lava dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Sculk dust tick");
            SculkDustRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Sculk dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Pale Garden dust tick");
            PaleGardenDustRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Pale Garden dust tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Pale Garden eyes tick");
            PaleGardenEyesRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Pale Garden eyes tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("End portal effects tick");
            EndPortalEyePlacementRenderer.tick(client);
            EndPortalGlowDustRenderer.tick(client);
            EndPortalLightRayRenderer.tick(client);
            EndPortalRisingBurstRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("End portal effects tick", diagnosticStart);
            DesertWindSoundController.tick(client);
            LightRainSoundController.tick(client);
            NetherStormSoundController.tick(client);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Cicada ambience tick");
            CicadaAmbientSoundHandler.tick(client);
            ShineFpsDiagnostics.endCpu("Cicada ambience tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Ambient nature effects tick");
            WorldAmbienceEffectsSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Ambient nature effects tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Vulture tick");
            VultureRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Vulture tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Tumbleweed tick");
            TumbleweedSpawner.tick(client);
            ShineFpsDiagnostics.endCpu("Tumbleweed tick", diagnosticStart);
            diagnosticStart = ShineFpsDiagnostics.beginCpu("Rainbow tick");
            RainbowRenderer.tick(client);
            ShineFpsDiagnostics.endCpu("Rainbow tick", diagnosticStart);
         }
      });
      LevelRenderEvents.START_MAIN.register((LevelRenderEvents.StartMain)(context) -> TransientMeshArena.beginFrame());
      LevelRenderEvents.START_MAIN.register(BloomPostProcessor::prepareSourceIfEnabled);
      LevelRenderEvents.START_MAIN.register((LevelRenderEvents.StartMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Colored light update");
         ColoredLightRenderer.updateVisibleLights(context);
         ShineFpsDiagnostics.endCpu("Colored light update", started);
      });
      LevelRenderEvents.START_MAIN.register((LevelRenderEvents.StartMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Rim light mask preparation");
         RimLightMaskRenderer.prepareIfEnabled();
         ShineFpsDiagnostics.endCpu("Rim light mask preparation", started);
      });
      ClientChunkEvents.CHUNK_LOAD.register((ClientChunkEvents.Load)(world, chunk) -> {
         ColoredLightRenderer.onChunkLoaded();
         ClientSectionFeatureIndex.onChunkLoaded(world, chunk);
         GrassBladesModel.onChunkTopologyChanged();
         LeafLitterParticle.onChunkChanged(world, chunk.getPos().x(), chunk.getPos().z());
      });
      ClientChunkEvents.CHUNK_UNLOAD.register((ClientChunkEvents.Unload)(world, chunk) -> {
         ClientSectionFeatureIndex.onChunkUnloaded(world, chunk);
         GrassBladesModel.onChunkUnloaded(chunk.getPos().x(), chunk.getPos().z());
         GrassBladesModel.onChunkTopologyChanged();
         LeafLitterParticle.onChunkChanged(world, chunk.getPos().x(), chunk.getPos().z());
      });
      LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(BloomPostProcessor::captureTerrainDepthIfEnabled);
      LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(BloomPostProcessor::captureOccluderDepthIfEnabled);
      LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(WaterReflectionRenderer::render);
      LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(InteractiveGroundLitterParticle::renderSubmergedBeforeWater);
      LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register((LevelRenderEvents.AfterTranslucentTerrain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Reactive water render");
         ReactiveWaterRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Reactive water render", started);
      });
      LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register((LevelRenderEvents.AfterTranslucentTerrain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Boat side wave trails render");
         BoatSideWaveTrailRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Boat side wave trails render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Custom particles render");
         CustomParticleRuntime.render(context);
         ShineFpsDiagnostics.endCpu("Custom particles render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Vulture render");
         VultureRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Vulture render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Hinged-wing butterflies render");
         ButterflyHingedWingRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Hinged-wing butterflies render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("End portal effects render");
         EndPortalEffectsRenderer.render(context);
         ShineFpsDiagnostics.endCpu("End portal effects render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Nether storm fire dust render");
         NetherStormFireDustRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Nether storm fire dust render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("End dust render");
         EndDustRenderer.render(context);
         ShineFpsDiagnostics.endCpu("End dust render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("XP ground trails render");
         XpGroundTrailRenderer.render(context);
         ShineFpsDiagnostics.endCpu("XP ground trails render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Light Rain bounce arcs render");
         LightRainBounceArcRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Light Rain bounce arcs render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Nether lava dust render");
         NetherLavaDustRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Nether lava dust render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Sculk dust render");
         SculkDustRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Sculk dust render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Pale Garden dust render");
         PaleGardenDustRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Pale Garden dust render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Pale Garden eyes render");
         PaleGardenEyesRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Pale Garden eyes render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Surface algae render");
         SurfaceAlgaeFlecksRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Surface algae render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Rainbow render");
         RainbowRenderer.render(context);
         ShineFpsDiagnostics.endCpu("Rainbow render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         long started = ShineFpsDiagnostics.beginCpu("Rim light render");
         RimLightRenderer.renderIfEnabled();
         ShineFpsDiagnostics.endCpu("Rim light render", started);
      });
      LevelRenderEvents.END_MAIN.register((LevelRenderEvents.EndMain)(context) -> {
         TerrainWaterUniformBuffer.endFrame();
         SodiumVulkanTerrainUniforms.endFrame();
      });
   }

   private static void rebuildChunksForResourceDefaults() {
      LevelRendererRebuilds.requestChunkGeometryRebuild();
   }

   private static void onMasterToggleChanged(Minecraft client) {
      BloomPostProcessor.onConfigSaved();
      ExperimentalPostProcessor.onConfigSaved();
      RimLightRenderer.onConfigSaved();
      ColoredLightRenderer.onConfigChanged();
      ShadingConfigManager.onMasterToggleChanged();
      FoliageSwayController.onConfigPreviewChanged();
      LevelRendererRebuilds.requestChunkGeometryRebuild();
   }

   static {
      TOGGLE_BLOOM_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shine.toggle", Type.KEYSYM, 66, MAIN_CATEGORY));
      TOGGLE_SHINE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shine.master_toggle", Type.KEYSYM, -1, MAIN_CATEGORY));
      OPEN_EDITOR_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shine.editor", Type.KEYSYM, 75, MAIN_CATEGORY));
      OPEN_LAST_EDITOR_PAGE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shine.last_editor_page", Type.KEYSYM, -1, MAIN_CATEGORY));
      OPEN_DIAGNOSTICS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shine.fps_diagnostics", Type.KEYSYM, 297, MAIN_CATEGORY));
   }
}
