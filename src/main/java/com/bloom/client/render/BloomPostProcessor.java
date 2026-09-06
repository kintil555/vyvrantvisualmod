package com.bloom.client.render;

import com.bloom.BloomMod;
import com.bloom.api.ShineCompatibilityApi;
import com.bloom.api.ShineSystem;
import com.bloom.client.ShineMasterToggle;
import com.bloom.client.config.BloomConfig;
import com.bloom.client.diagnostics.ShineFpsDiagnostics;
import com.bloom.client.experimental.config.ExperimentalConfig;
import com.bloom.client.experimental.config.ExperimentalConfigManager;
import com.bloom.client.experimental.desertdust.DesertDustParticle;
import com.bloom.client.experimental.fogfx.FogFxParticle;
import com.bloom.client.experimental.index.ClientSectionFeatureIndex;
import com.bloom.client.experimental.render.SkyLightOcclusionRenderer;
import com.bloom.client.experimental.render.sodium.BloomSodiumVisibility;
import com.bloom.mixin.client.accessor.PostChainAccessor;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class BloomPostProcessor {
   private static final Identifier EXTRACT_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_extract");
   private static final Identifier EXTRACT_DOWNSAMPLE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_extract_downsample");
   private static final Identifier DOWNSAMPLE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_downsample");
   private static final Identifier BLUR_HORIZONTAL_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_blur_horizontal");
   private static final Identifier BLUR_VERTICAL_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_blur_vertical");
   private static final Identifier COMPOSITE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_composite");
   private static final Identifier PROFILE_RESOLVE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_profile_resolve");
   private static final Identifier PROFILE_COMBINE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_profile_combine");
   private static final Identifier RESOLVED_COMPOSITE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "post/bloom_resolved_composite");
   private static final Identifier SCREEN_QUAD_SHADER_ID = Identifier.withDefaultNamespace("core/screenquad");
   private static final Identifier RUNTIME_CHAIN_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "runtime_bloom");
   private static final Set<Identifier> EXTERNAL_TARGETS;
   private static final ProjectionMatrixBuffer PROJECTION_BUFFER;
   private static final Projection ORTHO_PROJECTION;
   private static final int MAX_PYRAMID_BLUR_PASSES = 3;
   private static final int LEGACY_ACTIVE_LEVELS = 7;
   private static final int MAX_ACTIVE_LEVELS = 8;
   private static final float UNIFORM_EPSILON = 1.0E-5F;
   private static final float DEFAULT_NEAR_PLANE = 0.05F;
   private static final float DISTANCE_FADE_RANGE = 2.0F;
   private static final float SOURCE_STRENGTH_SCALE = 5.0F;
   private static final float FIRST_EXTRA_LEVEL_RADIUS = 100.0F;
   private static final float SECOND_EXTRA_LEVEL_RADIUS = 200.0F;
   private static final int REFERENCE_LEVEL0_HEIGHT = 540;
   private static final int MIN_LEVEL0_HEIGHT = 270;
   private static final float MAX_FUSED_SOURCE_SCALE = 4.001F;
   private static final float RADIUS_RESPONSE_EXPONENT = 1.5F;
   private static final double WEIGHT_DISTRIBUTION_DENOMINATOR = 1.15;
   private static final float MIN_COMPOSITE_LEVEL_WEIGHT = 1.0E-5F;
   private static final float MAX_OPTIMIZED_DROPPED_LEVEL0_WEIGHT = 0.01F;
   private static final float[] BASE_LEVEL_FACTORS;
   private static final Identifier[] LEVEL_TARGET_IDS;
   private static final Identifier[] LEVEL_BLUR_TARGET_IDS;
   private static final Identifier[][] COMBINED_LEVEL_TARGET_IDS;
   private static final Identifier[][] COMBINED_LEVEL_BLUR_TARGET_IDS;
   private static final Identifier[] PROFILE_RESOLVED_TARGET_IDS;
   private static final Identifier COMBINED_RESOLVED_TARGET_ID;
   private static boolean warnedChainLoadFailure;
   private static boolean loggedRuntimeChainReady;
   private static boolean loggedProcessChainRun;
   private static boolean compatibilityDisabled;
   private static String compatibilityMessage;
   private static final boolean[] runtimeChainUniformsDirty;
   private static final PostChain[] runtimeChains;
   private static final int[] runtimeChainWidths;
   private static final int[] runtimeChainHeights;
   private static final int[] runtimeChainLevels;
   private static final int[] runtimeChainExtraBlurPasses;
   private static final int[] runtimeChainProfileFilters;
   private static final float[][] lastExtractUniforms;
   private static final float[][] lastCompositeLevelWeights;
   private static final double[] lastCompositeRadius;
   private static final int[] lastCompositeActiveLevels;
   private static final float[] lastCompositeStrength;
   private static final float[] lastCompositeMaxDistance;
   private static final float[] lastCompositeNearPlane;
   private static final float[] lastCompositeFarPlane;
   private static PostChain combinedRuntimeChain;
   private static int combinedRuntimeChainWidth;
   private static int combinedRuntimeChainHeight;
   private static int combinedRuntimeChainProfileMask;
   private static int combinedRuntimeChainExtraBlurPasses;
   private static final int[] combinedRuntimeChainLevels;
   private static boolean combinedRuntimeChainUniformsDirty;
   private static boolean combinedRuntimeChainLoadFailed;
   private static final float[][] lastCombinedExtractUniforms;
   private static final float[][] lastCombinedResolveWeights;
   private static float lastCombinedStrength;
   private static float lastCombinedMaxDistance;
   private static float lastCombinedNearPlane;
   private static float lastCombinedFarPlane;
   private static boolean loggedCombinedRuntimeChainReady;
   private static long radiusProfileScanConfigVersion;
   private static int radiusProfileScanChunkX;
   private static int radiusProfileScanChunkZ;
   private static int radiusProfileScanChunkRadius;
   private static long radiusProfileScanIndexGeneration;
   private static int radiusProfilesNearbyMask;
   private static boolean sodiumVisibilityBridgeFailed;
   private static int diagnosticActiveProfileMask;

   private BloomPostProcessor() {
   }

   public static boolean toggleFromKeybind() {
      BloomConfig.Data config = BloomConfig.get();
      config.enabled = !config.enabled;
      BloomConfig.save();
      return config.enabled;
   }

   public static void onConfigSaved() {
      warnedChainLoadFailure = false;
      combinedRuntimeChainLoadFailed = false;
      BloomDirectPostExecutor.resetFailureState();

      for(int chainIndex = 0; chainIndex < runtimeChainUniformsDirty.length; ++chainIndex) {
         runtimeChainUniformsDirty[chainIndex] = true;
      }

      closeRuntimeChain();
      BloomMaskAtlas.markDirty();
      BloomEntityMaskTextures.markDirty();
      BloomSourceRenderer.reset();
      radiusProfileScanConfigVersion = Long.MIN_VALUE;
      radiusProfilesNearbyMask = 0;
   }

   public static void shutdown() {
      closeRuntimeChain();
      BloomSourceRenderer.reset();
   }

   public static void prepareSourceIfEnabled(LevelTerrainRenderContext context) {
      if (ShineMasterToggle.enabled()) {
         BloomConfig.Data config = BloomConfig.get();
         if (config.enabled && !shouldSkipForCompatibility()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
               BloomSourceRenderer.prepareSource(context);
            }
         }
      }
   }

   public static void captureTerrainDepthIfEnabled(LevelTerrainRenderContext context) {
      BloomSourceRenderer.resetTerrainDepthCapture();
      if (ShineMasterToggle.enabled()) {
         BloomConfig.Data config = BloomConfig.get();
         boolean bloomNeedsDepth = config.enabled && !shouldSkipForCompatibility();
         boolean dustNeedsDepth = DesertDustParticle.needsDepthSoftnessSnapshot();
         boolean fogFxNeedsDepth = FogFxParticle.needsSkyOpacitySnapshot();
         ExperimentalConfig experimentalConfig = ExperimentalConfigManager.fastConfig();
         boolean skyVolumetricNeedsDepth = experimentalConfig != null && SkyLightOcclusionRenderer.isSupported() && experimentalConfig.enabled && experimentalConfig.skyVolumetricRaysEnabled;
         if (bloomNeedsDepth || dustNeedsDepth || fogFxNeedsDepth || skyVolumetricNeedsDepth) {
            if (fogFxNeedsDepth) {
               FogFxParticle.prepareSkyOpacityUniform();
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
               BloomSourceRenderer.captureTerrainDepth(skyVolumetricNeedsDepth);
            }
         }
      }
   }

   public static void captureOccluderDepthIfEnabled(LevelRenderContext context) {
      if (ShineMasterToggle.enabled()) {
         BloomConfig.Data config = BloomConfig.get();
         if (config.enabled && !shouldSkipForCompatibility()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
               BloomSourceRenderer.captureOccluderDepth();
            }
         }
      }
   }

   public static void renderIfEnabled(LevelRenderContext context) {
      renderIfEnabled();
   }

   public static void renderIfEnabled() {
      diagnosticActiveProfileMask = 0;
      if (ShineMasterToggle.enabled()) {
         BloomConfig.Data config = BloomConfig.get();
         if (config.enabled && !shouldSkipForCompatibility()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && !(config.strength <= 1.0E-4)) {
               RenderTarget bloomSourceTarget = BloomSourceRenderer.getSourceTarget();
               if (bloomSourceTarget != null && BloomSourceRenderer.hasPreparedSourceThisFrame()) {
                  RenderTarget terrainDepthTarget = BloomSourceRenderer.getTerrainDepthTarget();
                  if (terrainDepthTarget != null && BloomSourceRenderer.hasCapturedTerrainDepthThisFrame()) {
                     RenderTarget occluderDepthTarget = BloomSourceRenderer.getOccluderDepthTarget();
                     if (occluderDepthTarget == null || !BloomSourceRenderer.hasCapturedOccluderDepthThisFrame()) {
                        occluderDepthTarget = terrainDepthTarget;
                     }

                     if (!ShineFpsDiagnostics.skipBloomPostForArchitecture()) {
                        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
                        int activeProfileMask = activeRadiusProfileMask(minecraft, config);
                        diagnosticActiveProfileMask = activeProfileMask;
                        int extraBlurPasses = getExtraBlurPasses(config.blurPassCount);
                        if (canUseCombinedLowResolutionPath(config, activeProfileMask, mainTarget.width, mainTarget.height)) {
                           int[] activeLevels = activeLevelsForProfiles(config, activeProfileMask);
                           PostChain combinedChain = ensureCombinedRuntimeChain(mainTarget.width, mainTarget.height, activeProfileMask, activeLevels, extraBlurPasses, config);
                           if (combinedChain != null) {
                              for(int profile = 0; profile < 3; ++profile) {
                                 closeRuntimeChain(profile);
                              }

                              applyCombinedConfigUniforms(combinedChain, config, activeProfileMask, activeLevels, extraBlurPasses);
                              processChain(combinedChain, mainTarget, bloomSourceTarget, terrainDepthTarget, occluderDepthTarget);
                              return;
                           }
                        }

                        closeCombinedRuntimeChain();

                        for(int profile = 0; profile < 3; ++profile) {
                           if ((activeProfileMask & 1 << profile) == 0) {
                              closeRuntimeChain(profile);
                           } else {
                              double radius = radiusForProfile(config, profile);
                              int activeLevels = getActiveLevels(radius);
                              PostChain chain = ensureRuntimeChain(profile, mainTarget.width, mainTarget.height, activeLevels, extraBlurPasses, profile);
                              if (chain == null) {
                                 return;
                              }

                              applyConfigUniforms(profile, chain, config, activeLevels, radius, profile);
                              processChain(chain, mainTarget, bloomSourceTarget, terrainDepthTarget, occluderDepthTarget);
                           }
                        }

                     }
                  }
               }
            }
         }
      }
   }

   private static int[] activeLevelsForProfiles(BloomConfig.Data config, int activeProfileMask) {
      int[] activeLevels = new int[3];

      for(int profile = 0; profile < 3; ++profile) {
         if ((activeProfileMask & 1 << profile) != 0) {
            activeLevels[profile] = getActiveLevels(radiusForProfile(config, profile));
         }
      }

      return activeLevels;
   }

   private static boolean canUseCombinedLowResolutionPath(BloomConfig.Data config, int activeProfileMask, int mainWidth, int mainHeight) {
      if (combinedRuntimeChainLoadFailed) {
         return false;
      } else {
         int baseWidth = getLevelWidth(mainWidth, mainHeight, 1);
         int baseHeight = getLevelHeight(mainHeight, 1);
         if (baseWidth < mainWidth && baseHeight < mainHeight) {
            if (!((float)mainWidth / (float)baseWidth > 4.001F) && !((float)mainHeight / (float)baseHeight > 4.001F)) {
               for(int profile = 0; profile < 3; ++profile) {
                  if ((activeProfileMask & 1 << profile) != 0) {
                     double radius = radiusForProfile(config, profile);
                     float[] weights = computeLevelWeights(radius, getActiveLevels(radius));
                     if (Math.abs(weights[0]) > 0.01F) {
                        return false;
                     }
                  }
               }

               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   public static int diagnosticActiveProfileMask() {
      return diagnosticActiveProfileMask;
   }

   private static double radiusForProfile(BloomConfig.Data config, int profile) {
      return profile == 1 ? config.tinyRadius : (profile == 2 ? config.broadRadius : config.radius);
   }

   private static int activeRadiusProfileMask(Minecraft minecraft, BloomConfig.Data config) {
      if (config.sourceRadiusProfiles != null && !config.sourceRadiusProfiles.isEmpty() && minecraft.level != null && minecraft.player != null) {
         ClientLevel level = minecraft.level;
         long configVersion = BloomConfig.version();
         long indexGeneration = ClientSectionFeatureIndex.prepareBloomRadiusProfiles(level, config.sourceRadiusProfiles, configVersion);
         int configuredMask = ClientSectionFeatureIndex.configuredBloomRadiusProfileMask();
         if (configuredMask == 0) {
            radiusProfilesNearbyMask = 0;
            return 1;
         } else {
            if (!sodiumVisibilityBridgeFailed && FabricLoader.getInstance().isModLoaded("sodium")) {
               try {
                  int visibleMask = BloomSodiumVisibility.visibleRadiusProfileMask(level, configuredMask);
                  if (visibleMask >= 0) {
                     radiusProfilesNearbyMask = visibleMask & configuredMask;
                     return 1 | radiusProfilesNearbyMask;
                  }
               } catch (RuntimeException | LinkageError exception) {
                  sodiumVisibilityBridgeFailed = true;
                  BloomMod.LOGGER.debug("Shine Bloom could not read Sodium's visible terrain lists; using the loaded-chunk profile fallback.", exception);
               }
            }

            int chunkX = minecraft.player.blockPosition().getX() >> 4;
            int chunkZ = minecraft.player.blockPosition().getZ() >> 4;
            int bloomChunkRadius = Math.max(1, (int)Math.ceil(config.bloomDistance / (double)16.0F));
            int chunkRadius = Math.min(bloomChunkRadius, Math.max(1, minecraft.options.getEffectiveRenderDistance()));
            if (configVersion == radiusProfileScanConfigVersion && chunkX == radiusProfileScanChunkX && chunkZ == radiusProfileScanChunkZ && chunkRadius == radiusProfileScanChunkRadius && indexGeneration == radiusProfileScanIndexGeneration) {
               return 1 | radiusProfilesNearbyMask;
            } else {
               radiusProfileScanConfigVersion = configVersion;
               radiusProfileScanChunkX = chunkX;
               radiusProfileScanChunkZ = chunkZ;
               radiusProfileScanChunkRadius = chunkRadius;
               radiusProfileScanIndexGeneration = indexGeneration;
               ClientChunkCache chunks = level.getChunkSource();
               int foundMask = 0;

               for(int ring = 0; ring <= chunkRadius; ++ring) {
                  for(int dz = -ring; dz <= ring; ++dz) {
                     for(int dx = -ring; dx <= ring; ++dx) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                           LevelChunk chunk = chunks.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, false);
                           if (chunk != null) {
                              foundMask |= ClientSectionFeatureIndex.chunkBloomRadiusProfileMask(level, chunk);
                              if ((foundMask & configuredMask) == configuredMask) {
                                 radiusProfilesNearbyMask = foundMask & configuredMask;
                                 return 1 | radiusProfilesNearbyMask;
                              }
                           }
                        }
                     }
                  }
               }

               radiusProfilesNearbyMask = foundMask & configuredMask;
               return 1 | radiusProfilesNearbyMask;
            }
         }
      } else {
         radiusProfilesNearbyMask = 0;
         return 1;
      }
   }

   private static boolean shouldSkipForCompatibility() {
      Optional<String> reason = ShineCompatibilityApi.firstBlockReason(ShineSystem.BLOOM);
      if (reason.isEmpty()) {
         if (compatibilityDisabled) {
            BloomMod.LOGGER.info("Shine bloom re-enabled: compatibility blockers cleared.");
            compatibilityDisabled = false;
            compatibilityMessage = null;
         }

         return false;
      } else {
         String message = "Shine bloom disabled: " + (String)reason.get();
         if (!compatibilityDisabled || !Objects.equals(message, compatibilityMessage)) {
            BloomMod.LOGGER.info(message);
            compatibilityDisabled = true;
            compatibilityMessage = message;
            closeRuntimeChain();
            BloomSourceRenderer.reset();
         }

         return true;
      }
   }

   private static PostChain ensureRuntimeChain(int chainIndex, int mainWidth, int mainHeight, int activeLevels, int extraBlurPasses, int profileFilter) {
      if (runtimeChains[chainIndex] != null && runtimeChainWidths[chainIndex] == mainWidth && runtimeChainHeights[chainIndex] == mainHeight && runtimeChainLevels[chainIndex] == activeLevels && runtimeChainExtraBlurPasses[chainIndex] == extraBlurPasses && runtimeChainProfileFilters[chainIndex] == profileFilter) {
         return runtimeChains[chainIndex];
      } else {
         closeRuntimeChain(chainIndex);
         Minecraft minecraft = Minecraft.getInstance();

         try {
            ORTHO_PROJECTION.setupOrtho((float)mainWidth, (float)mainHeight, 0.05F, 1000.0F, false);
            runtimeChains[chainIndex] = PostChain.load(buildRuntimeConfig(mainWidth, mainHeight, activeLevels, extraBlurPasses, profileFilter), minecraft.getTextureManager(), EXTERNAL_TARGETS, RUNTIME_CHAIN_ID.withSuffix("_profile_" + profileFilter + "_" + activeLevels + "_smooth_" + extraBlurPasses), ORTHO_PROJECTION, PROJECTION_BUFFER);
            runtimeChainWidths[chainIndex] = mainWidth;
            runtimeChainHeights[chainIndex] = mainHeight;
            runtimeChainLevels[chainIndex] = activeLevels;
            runtimeChainExtraBlurPasses[chainIndex] = extraBlurPasses;
            runtimeChainProfileFilters[chainIndex] = profileFilter;
            runtimeChainUniformsDirty[chainIndex] = true;
            warnedChainLoadFailure = false;
            if (!loggedRuntimeChainReady) {
               BloomMod.LOGGER.debug("Shine runtime bloom chain built for {}x{} with {} active levels and {} extra smoothing passes.", new Object[]{mainWidth, mainHeight, activeLevels, extraBlurPasses});
               loggedRuntimeChainReady = true;
            }

            return runtimeChains[chainIndex];
         } catch (ShaderManager.CompilationException e) {
            if (!warnedChainLoadFailure) {
               BloomMod.LOGGER.warn("Shine runtime bloom chain could not be built.", e);
               warnedChainLoadFailure = true;
            }

            return null;
         }
      }
   }

   private static PostChainConfig buildRuntimeConfig(int mainWidth, int mainHeight, int activeLevels, int extraBlurPasses, int profileFilter) {
      Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap();

      for(int i = 0; i < activeLevels; ++i) {
         targets.put(LEVEL_TARGET_IDS[i], internalTarget(getLevelWidth(mainWidth, mainHeight, i), getLevelHeight(mainHeight, i)));
      }

      if (extraBlurPasses > 0) {
         for(int i = 0; i < activeLevels; ++i) {
            targets.put(LEVEL_BLUR_TARGET_IDS[i], internalTarget(getLevelWidth(mainWidth, mainHeight, i), getLevelHeight(mainHeight, i)));
         }
      }

      List<PostChainConfig.Pass> passes = new ArrayList();
      passes.add(postPass(EXTRACT_SHADER_ID, List.of(targetInput("TerrainDepth", BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID, true, false), targetInput("OccluderDepth", BloomSourceRenderer.OCCLUDER_DEPTH_TARGET_ID, true, false), targetInput("Source", BloomSourceRenderer.SOURCE_TARGET_ID, false, false)), LEVEL_TARGET_IDS[0], extractUniformDefaults((float)profileFilter, 1 << profileFilter)));
      addExtraBlurPasses(passes, 0, extraBlurPasses);

      for(int i = 1; i < activeLevels; ++i) {
         passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", LEVEL_TARGET_IDS[i - 1], false, true)), LEVEL_TARGET_IDS[i], Map.of()));
         addExtraBlurPasses(passes, i, extraBlurPasses);
      }

      passes.add(postPass(COMPOSITE_SHADER_ID, List.of(targetInput("Main", LevelTargetBundle.MAIN_TARGET_ID, false, false), targetInput("Level0", compositeLevelTarget(0, activeLevels), false, true), targetInput("Level1", compositeLevelTarget(1, activeLevels), false, true), targetInput("Level2", compositeLevelTarget(2, activeLevels), false, true), targetInput("Level3", compositeLevelTarget(3, activeLevels), false, true), targetInput("Level4", compositeLevelTarget(4, activeLevels), false, true), targetInput("Level5", compositeLevelTarget(5, activeLevels), false, true), targetInput("Level6", compositeLevelTarget(6, activeLevels), false, true), targetInput("Level7", compositeLevelTarget(7, activeLevels), false, true), targetInput("Depth", LevelTargetBundle.MAIN_TARGET_ID, true, false), targetInput("TerrainDepth", BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID, true, false)), LevelTargetBundle.MAIN_TARGET_ID, compositeUniformDefaults()));
      return new PostChainConfig(targets, passes);
   }

   private static PostChain ensureCombinedRuntimeChain(int mainWidth, int mainHeight, int activeProfileMask, int[] activeLevels, int extraBlurPasses, BloomConfig.Data config) {
      if (combinedRuntimeChain != null && combinedRuntimeChainWidth == mainWidth && combinedRuntimeChainHeight == mainHeight && combinedRuntimeChainProfileMask == activeProfileMask && combinedRuntimeChainExtraBlurPasses == extraBlurPasses && combinedLevelsMatch(activeLevels)) {
         return combinedRuntimeChain;
      } else {
         closeCombinedRuntimeChain();
         Minecraft minecraft = Minecraft.getInstance();

         try {
            String levelKey = activeLevels[0] + "_" + activeLevels[1] + "_" + activeLevels[2];
            ORTHO_PROJECTION.setupOrtho((float)mainWidth, (float)mainHeight, 0.05F, 1000.0F, false);
            combinedRuntimeChain = PostChain.load(buildCombinedRuntimeConfig(mainWidth, mainHeight, activeProfileMask, activeLevels, extraBlurPasses, config), minecraft.getTextureManager(), EXTERNAL_TARGETS, RUNTIME_CHAIN_ID.withSuffix("_combined_" + activeProfileMask + "_" + levelKey + "_smooth_" + extraBlurPasses), ORTHO_PROJECTION, PROJECTION_BUFFER);
            combinedRuntimeChainWidth = mainWidth;
            combinedRuntimeChainHeight = mainHeight;
            combinedRuntimeChainProfileMask = activeProfileMask;
            combinedRuntimeChainExtraBlurPasses = extraBlurPasses;
            System.arraycopy(activeLevels, 0, combinedRuntimeChainLevels, 0, combinedRuntimeChainLevels.length);
            combinedRuntimeChainUniformsDirty = true;
            combinedRuntimeChainLoadFailed = false;
            warnedChainLoadFailure = false;
            if (!loggedCombinedRuntimeChainReady) {
               BloomMod.LOGGER.info("Shine optimized Bloom graph active at {}x{} for profile mask {} (working height {}).", new Object[]{mainWidth, mainHeight, activeProfileMask, getLevelHeight(mainHeight, 1)});
               loggedCombinedRuntimeChainReady = true;
            }

            return combinedRuntimeChain;
         } catch (ShaderManager.CompilationException e) {
            combinedRuntimeChainLoadFailed = true;
            if (!warnedChainLoadFailure) {
               BloomMod.LOGGER.warn("Shine optimized Bloom graph could not be built; using the legacy Bloom graph.", e);
               warnedChainLoadFailure = true;
            }

            closeCombinedRuntimeChain();
            return null;
         }
      }
   }

   private static boolean combinedLevelsMatch(int[] activeLevels) {
      for(int profile = 0; profile < combinedRuntimeChainLevels.length; ++profile) {
         if (combinedRuntimeChainLevels[profile] != activeLevels[profile]) {
            return false;
         }
      }

      return true;
   }

   private static PostChainConfig buildCombinedRuntimeConfig(int mainWidth, int mainHeight, int activeProfileMask, int[] activeLevels, int extraBlurPasses, BloomConfig.Data config) {
      Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap();
      int baseWidth = getLevelWidth(mainWidth, mainHeight, 1);
      int baseHeight = getLevelHeight(mainHeight, 1);

      for(int profile = 0; profile < 3; ++profile) {
         if ((activeProfileMask & 1 << profile) != 0) {
            for(int level = 1; level < activeLevels[profile]; ++level) {
               targets.put(COMBINED_LEVEL_TARGET_IDS[profile][level], internalTarget(getLevelWidth(mainWidth, mainHeight, level), getLevelHeight(mainHeight, level)));
               if (extraBlurPasses > 0) {
                  targets.put(COMBINED_LEVEL_BLUR_TARGET_IDS[profile][level], internalTarget(getLevelWidth(mainWidth, mainHeight, level), getLevelHeight(mainHeight, level)));
               }
            }

            targets.put(PROFILE_RESOLVED_TARGET_IDS[profile], internalTarget(baseWidth, baseHeight));
         }
      }

      if (Integer.bitCount(activeProfileMask) > 1) {
         targets.put(COMBINED_RESOLVED_TARGET_ID, internalTarget(baseWidth, baseHeight));
      }

      List<PostChainConfig.Pass> passes = new ArrayList();
      float resolvePerceptualEncoding = 1.0F;

      for(int profile = 0; profile < 3; ++profile) {
         if ((activeProfileMask & 1 << profile) != 0) {
            passes.add(postPass(EXTRACT_DOWNSAMPLE_SHADER_ID, List.of(targetInput("TerrainDepth", BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID, true, false), targetInput("OccluderDepth", BloomSourceRenderer.OCCLUDER_DEPTH_TARGET_ID, true, false), targetInput("Source", BloomSourceRenderer.SOURCE_TARGET_ID, false, false)), COMBINED_LEVEL_TARGET_IDS[profile][1], extractUniformDefaults((float)profile, activeProfileMask)));
            addCombinedExtraBlurPasses(passes, profile, 1, extraBlurPasses);

            for(int level = 2; level < activeLevels[profile]; ++level) {
               passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", COMBINED_LEVEL_TARGET_IDS[profile][level - 1], false, true)), COMBINED_LEVEL_TARGET_IDS[profile][level], Map.of()));
               addCombinedExtraBlurPasses(passes, profile, level, extraBlurPasses);
            }

            passes.add(postPass(PROFILE_RESOLVE_SHADER_ID, List.of(targetInput("Level1", combinedProfileLevelTarget(profile, 1, activeLevels[profile]), false, true), targetInput("Level2", combinedProfileLevelTarget(profile, 2, activeLevels[profile]), false, true), targetInput("Level3", combinedProfileLevelTarget(profile, 3, activeLevels[profile]), false, true), targetInput("Level4", combinedProfileLevelTarget(profile, 4, activeLevels[profile]), false, true), targetInput("Level5", combinedProfileLevelTarget(profile, 5, activeLevels[profile]), false, true), targetInput("Level6", combinedProfileLevelTarget(profile, 6, activeLevels[profile]), false, true), targetInput("Level7", combinedProfileLevelTarget(profile, 7, activeLevels[profile]), false, true)), PROFILE_RESOLVED_TARGET_IDS[profile], resolveUniformDefaults(computeLowResolutionResolveWeights(radiusForProfile(config, profile), activeLevels[profile]), resolvePerceptualEncoding)));
         }
      }

      Identifier resolvedTarget;
      if (Integer.bitCount(activeProfileMask) > 1) {
         int fallbackProfile = Integer.numberOfTrailingZeros(activeProfileMask);
         passes.add(postPass(PROFILE_COMBINE_SHADER_ID, List.of(targetInput("Profile0", resolvedProfileTargetOrFallback(0, fallbackProfile, activeProfileMask), false, false), targetInput("Profile1", resolvedProfileTargetOrFallback(1, fallbackProfile, activeProfileMask), false, false), targetInput("Profile2", resolvedProfileTargetOrFallback(2, fallbackProfile, activeProfileMask), false, false)), COMBINED_RESOLVED_TARGET_ID, profileCombineUniformDefaults(activeProfileMask)));
         resolvedTarget = COMBINED_RESOLVED_TARGET_ID;
      } else {
         resolvedTarget = PROFILE_RESOLVED_TARGET_IDS[Integer.numberOfTrailingZeros(activeProfileMask)];
      }

      passes.add(postPass(RESOLVED_COMPOSITE_SHADER_ID, List.of(targetInput("Main", LevelTargetBundle.MAIN_TARGET_ID, false, false), targetInput("Bloom", resolvedTarget, false, true), targetInput("Depth", LevelTargetBundle.MAIN_TARGET_ID, true, false), targetInput("TerrainDepth", BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID, true, false)), LevelTargetBundle.MAIN_TARGET_ID, resolvedCompositeUniformDefaults()));
      return new PostChainConfig(targets, passes);
   }

   private static void processChain(PostChain chain, RenderTarget mainTarget, RenderTarget bloomSourceTarget, RenderTarget terrainDepthTarget, RenderTarget occluderDepthTarget) {
      if (BloomDirectPostExecutor.tryExecute(chain, mainTarget, bloomSourceTarget, terrainDepthTarget, occluderDepthTarget, ORTHO_PROJECTION, PROJECTION_BUFFER)) {
         if (!loggedProcessChainRun) {
            BloomMod.LOGGER.debug("Shine persistent post chain executed with main={}x{} source={}x{}.", new Object[]{mainTarget.width, mainTarget.height, bloomSourceTarget.width, bloomSourceTarget.height});
            loggedProcessChainRun = true;
         }

      } else {
         FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
         BloomTargetBundle targetBundle = new BloomTargetBundle(frameGraphBuilder.importExternal("main", mainTarget), frameGraphBuilder.importExternal("bloom_source", bloomSourceTarget), frameGraphBuilder.importExternal("terrain_depth", terrainDepthTarget), frameGraphBuilder.importExternal("occluder_depth", occluderDepthTarget));
         chain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
         frameGraphBuilder.execute(PostProcessingSupport.frameAllocator());
         if (!loggedProcessChainRun) {
            BloomMod.LOGGER.debug("Shine post chain executed with main={}x{} source={}x{}.", new Object[]{mainTarget.width, mainTarget.height, bloomSourceTarget.width, bloomSourceTarget.height});
            loggedProcessChainRun = true;
         }

      }
   }

   private static void applyConfigUniforms(int chainIndex, PostChain chain, BloomConfig.Data config, int activeLevels, double radius, int profileFilter) {
      List<PostPass> passes = ((PostChainAccessor)chain).bloom$getPasses();
      if (!passes.isEmpty()) {
         boolean uniformsDirty = runtimeChainUniformsDirty[chainIndex];
         float[] extractUniforms = lastExtractUniforms[chainIndex];
         float[] compositeLevelWeights = lastCompositeLevelWeights[chainIndex];
         Minecraft minecraft = Minecraft.getInstance();
         float farPlane = currentFarPlane(minecraft);
         float nearPlane = 0.05F;
         float threshold = (float)config.threshold;
         float highlightClamp = (float)config.highlightClamp;
         float softKnee = (float)config.softKnee;
         float maxDistance = (float)config.bloomDistance;
         if (uniformsDirty || changed(extractUniforms[0], threshold) || changed(extractUniforms[1], highlightClamp) || changed(extractUniforms[2], softKnee) || changed(extractUniforms[3], maxDistance) || changed(extractUniforms[4], nearPlane) || changed(extractUniforms[5], farPlane) || changed(extractUniforms[6], 5.0F) || changed(extractUniforms[7], (float)profileFilter) || changed(extractUniforms[8], (float)(1 << profileFilter))) {
            writeExtractUniforms((PostPass)passes.get(0), threshold, highlightClamp, softKnee, maxDistance, nearPlane, farPlane, 5.0F, (float)profileFilter, 1 << profileFilter);
            extractUniforms[0] = threshold;
            extractUniforms[1] = highlightClamp;
            extractUniforms[2] = softKnee;
            extractUniforms[3] = maxDistance;
            extractUniforms[4] = nearPlane;
            extractUniforms[5] = farPlane;
            extractUniforms[6] = 5.0F;
            extractUniforms[7] = (float)profileFilter;
            extractUniforms[8] = (float)(1 << profileFilter);
         }

         float strength = (float)config.strength;
         PostPass compositePass = (PostPass)passes.get(passes.size() - 1);
         if (uniformsDirty || changed(lastCompositeStrength[chainIndex], strength)) {
            writeCompositeUniforms(compositePass, strength);
            lastCompositeStrength[chainIndex] = strength;
         }

         if (uniformsDirty || activeLevels != lastCompositeActiveLevels[chainIndex] || Math.abs(radius - lastCompositeRadius[chainIndex]) > (double)1.0E-5F) {
            float[] profileWeights = computeSingleProfileWeights(radius, activeLevels);
            if (uniformsDirty || weightsChanged(profileWeights, compositeLevelWeights)) {
               writeCompositeWeights(compositePass, profileWeights);
               System.arraycopy(profileWeights, 0, compositeLevelWeights, 0, compositeLevelWeights.length);
            }

            lastCompositeRadius[chainIndex] = radius;
            lastCompositeActiveLevels[chainIndex] = activeLevels;
         }

         if (uniformsDirty || changed(lastCompositeMaxDistance[chainIndex], maxDistance) || changed(lastCompositeNearPlane[chainIndex], nearPlane) || changed(lastCompositeFarPlane[chainIndex], farPlane)) {
            writeCompositeDistanceUniforms(compositePass, maxDistance, nearPlane, farPlane);
            lastCompositeMaxDistance[chainIndex] = maxDistance;
            lastCompositeNearPlane[chainIndex] = nearPlane;
            lastCompositeFarPlane[chainIndex] = farPlane;
         }

         runtimeChainUniformsDirty[chainIndex] = false;
      }
   }

   private static void applyCombinedConfigUniforms(PostChain chain, BloomConfig.Data config, int activeProfileMask, int[] activeLevels, int extraBlurPasses) {
      List<PostPass> passes = ((PostChainAccessor)chain).bloom$getPasses();
      if (!passes.isEmpty()) {
         boolean uniformsDirty = combinedRuntimeChainUniformsDirty;
         Minecraft minecraft = Minecraft.getInstance();
         float farPlane = currentFarPlane(minecraft);
         float nearPlane = 0.05F;
         float threshold = (float)config.threshold;
         float highlightClamp = (float)config.highlightClamp;
         float softKnee = (float)config.softKnee;
         float maxDistance = (float)config.bloomDistance;
         float resolvePerceptualEncoding = 1.0F;
         int passIndex = 0;

         for(int profile = 0; profile < 3; ++profile) {
            if ((activeProfileMask & 1 << profile) != 0) {
               PostPass extractPass = (PostPass)passes.get(passIndex++);
               float[] extractUniforms = lastCombinedExtractUniforms[profile];
               if (uniformsDirty || changed(extractUniforms[0], threshold) || changed(extractUniforms[1], highlightClamp) || changed(extractUniforms[2], softKnee) || changed(extractUniforms[3], maxDistance) || changed(extractUniforms[4], nearPlane) || changed(extractUniforms[5], farPlane) || changed(extractUniforms[6], 5.0F) || changed(extractUniforms[7], (float)profile) || changed(extractUniforms[8], (float)activeProfileMask)) {
                  writeExtractUniforms(extractPass, threshold, highlightClamp, softKnee, maxDistance, nearPlane, farPlane, 5.0F, (float)profile, activeProfileMask);
                  extractUniforms[0] = threshold;
                  extractUniforms[1] = highlightClamp;
                  extractUniforms[2] = softKnee;
                  extractUniforms[3] = maxDistance;
                  extractUniforms[4] = nearPlane;
                  extractUniforms[5] = farPlane;
                  extractUniforms[6] = 5.0F;
                  extractUniforms[7] = (float)profile;
                  extractUniforms[8] = (float)activeProfileMask;
               }

               passIndex += extraBlurPasses * 2;

               for(int level = 2; level < activeLevels[profile]; ++level) {
                  ++passIndex;
                  passIndex += extraBlurPasses * 2;
               }

               PostPass resolvePass = (PostPass)passes.get(passIndex++);
               float[] weights = computeLowResolutionResolveWeights(radiusForProfile(config, profile), activeLevels[profile]);
               if (uniformsDirty || weightsChanged(weights, lastCombinedResolveWeights[profile])) {
                  writeResolveWeights(resolvePass, weights, resolvePerceptualEncoding);
                  System.arraycopy(weights, 0, lastCombinedResolveWeights[profile], 0, 8);
               }
            }
         }

         PostPass compositePass = (PostPass)passes.get(passes.size() - 1);
         float strength = (float)config.strength;
         if (uniformsDirty || changed(lastCombinedStrength, strength)) {
            writeCompositeUniforms(compositePass, strength);
            lastCombinedStrength = strength;
         }

         if (uniformsDirty || changed(lastCombinedMaxDistance, maxDistance) || changed(lastCombinedNearPlane, nearPlane) || changed(lastCombinedFarPlane, farPlane)) {
            writeCompositeDistanceUniforms(compositePass, maxDistance, nearPlane, farPlane);
            lastCombinedMaxDistance = maxDistance;
            lastCombinedNearPlane = nearPlane;
            lastCombinedFarPlane = farPlane;
         }

         combinedRuntimeChainUniformsDirty = false;
      }
   }

   private static float[] computeSingleProfileWeights(double radius, int activeLevels) {
      float[] packed = new float[28];
      float[] weights = computeLevelWeights(radius, activeLevels);

      for(int profile = 0; profile < 3; ++profile) {
         System.arraycopy(weights, 0, packed, profile * 8, 8);
      }

      float radiusNorm = (float)(radius / (double)700.0F);
      packed[24] = radiusNorm;
      packed[25] = radiusNorm;
      packed[26] = radiusNorm;
      packed[27] = 0.0F;
      return packed;
   }

   private static float[] computeLowResolutionResolveWeights(double radius, int activeLevels) {
      float[] weights = computeLevelWeights(radius, activeLevels);
      weights[0] = 0.0F;
      float retainedTotal = 0.0F;

      for(int level = 1; level < activeLevels; ++level) {
         retainedTotal += Math.abs(weights[level]);
      }

      if (retainedTotal > 1.0E-5F) {
         for(int level = 1; level < activeLevels; ++level) {
            weights[level] /= retainedTotal;
         }
      }

      return weights;
   }

   private static float[] computeLevelWeights(double radius, int activeLevels) {
      float[] weights = new float[8];
      float radiusNorm = clamp((float)(radius / (double)500.0F), 0.0F, 1.0F);
      float radiusResponse = (float)Math.pow((double)radiusNorm, (double)1.5F);
      float oldTargetIndex = radiusResponse * (float)Math.max(5, 0);
      float pyramidBlend = smoothstep(0.04F, 0.18F, radiusNorm);
      float targetIndex = lerp(0.0F, 1.0F + oldTargetIndex, pyramidBlend);
      if (activeLevels > 7) {
         float extraRadiusNorm = clamp((float)((radius - (double)500.0F) / (double)200.0F), 0.0F, 1.0F);
         targetIndex = lerp(targetIndex, (float)activeLevels - 1.0F, smoothstep(0.0F, 1.0F, extraRadiusNorm));
      }

      for(int i = 0; i < activeLevels; ++i) {
         float distance = (float)i - targetIndex;
         weights[i] = (float)((double)BASE_LEVEL_FACTORS[i] * Math.exp((double)(-(distance * distance)) / 1.15));
      }

      float total = 0.0F;

      for(float weight : weights) {
         total += weight;
      }

      if (total > 1.0E-5F) {
         for(int i = 0; i < activeLevels; ++i) {
            weights[i] /= total;
         }
      }

      float retainedTotal = 0.0F;

      for(int i = 0; i < activeLevels; ++i) {
         if (weights[i] < 1.0E-5F) {
            weights[i] = 0.0F;
         } else {
            retainedTotal += weights[i];
         }
      }

      if (retainedTotal > 1.0E-5F) {
         for(int i = 0; i < activeLevels; ++i) {
            weights[i] /= retainedTotal;
         }
      }

      int strongestLevel = -1;
      int secondStrongestLevel = -1;

      for(int i = 0; i < activeLevels; ++i) {
         if (!(weights[i] <= 0.0F)) {
            if (strongestLevel >= 0 && !(weights[i] > weights[strongestLevel])) {
               if (secondStrongestLevel < 0 || weights[i] > weights[secondStrongestLevel]) {
                  secondStrongestLevel = i;
               }
            } else {
               secondStrongestLevel = strongestLevel;
               strongestLevel = i;
            }
         }
      }

      for(int i = 0; i < activeLevels; ++i) {
         if (weights[i] > 0.0F && i != strongestLevel && i != secondStrongestLevel) {
            weights[i] = -weights[i];
         }
      }

      return weights;
   }

   private static void writeExtractUniforms(PostPass pass, float threshold, float highlightClamp, float softKnee, float maxDistance, float nearPlane, float farPlane, float sourceStrengthScale, float profileFilter, int activeProfileMask) {
      writeUniform(pass, "BloomExtractConfig", (builder) -> {
         builder.putFloat(threshold);
         builder.putFloat(highlightClamp);
         builder.putFloat(softKnee);
         builder.putFloat(maxDistance);
         builder.putFloat(nearPlane);
         builder.putFloat(farPlane);
         builder.putFloat(sourceStrengthScale);
         builder.putFloat(2.0F);
         builder.putFloat(profileFilter);
         builder.putFloat((float)activeProfileMask);
      });
   }

   private static void writeCompositeUniforms(PostPass pass, float strength) {
      writeUniform(pass, "BloomCompositeConfig", (builder) -> builder.putFloat(strength));
   }

   private static void writeCompositeWeights(PostPass pass, float[] weights) {
      writeUniform(pass, "BloomCompositeWeights", (builder) -> {
         for(float weight : weights) {
            builder.putFloat(weight);
         }

      });
   }

   private static void writeResolveWeights(PostPass pass, float[] weights, float perceptualEncoding) {
      writeUniform(pass, "BloomResolveWeights", (builder) -> {
         for(int level = 1; level < 8; ++level) {
            builder.putFloat(weights[level]);
         }

         builder.putFloat(perceptualEncoding);
      });
   }

   private static void writeCompositeDistanceUniforms(PostPass pass, float maxDistance, float nearPlane, float farPlane) {
      writeUniform(pass, "BloomCompositeDistanceConfig", (builder) -> {
         builder.putFloat(maxDistance);
         builder.putFloat(nearPlane);
         builder.putFloat(farPlane);
         builder.putFloat(2.0F);
      });
   }

   private static boolean changed(float previous, float current) {
      return Math.abs(previous - current) > 1.0E-5F;
   }

   private static boolean weightsChanged(float[] current, float[] previous) {
      for(int i = 0; i < current.length; ++i) {
         if (changed(previous[i], current[i])) {
            return true;
         }
      }

      return false;
   }

   private static void writeUniform(PostPass pass, String uniformName, UniformWriter writer) {
      Objects.requireNonNull(writer);
      PostProcessingSupport.writeUniform(pass, uniformName, writer::write);
   }

   private static void closeRuntimeChain() {
      for(int i = 0; i < runtimeChains.length; ++i) {
         closeRuntimeChain(i);
      }

      closeCombinedRuntimeChain();
   }

   private static void closeRuntimeChain(int index) {
      if (runtimeChains[index] != null) {
         BloomDirectPostExecutor.close(runtimeChains[index]);
         runtimeChains[index].close();
         runtimeChains[index] = null;
      }

      runtimeChainWidths[index] = -1;
      runtimeChainHeights[index] = -1;
      runtimeChainLevels[index] = -1;
      runtimeChainExtraBlurPasses[index] = -1;
      runtimeChainProfileFilters[index] = Integer.MIN_VALUE;
      runtimeChainUniformsDirty[index] = true;
   }

   private static void closeCombinedRuntimeChain() {
      if (combinedRuntimeChain != null) {
         BloomDirectPostExecutor.close(combinedRuntimeChain);
         combinedRuntimeChain.close();
         combinedRuntimeChain = null;
      }

      combinedRuntimeChainWidth = -1;
      combinedRuntimeChainHeight = -1;
      combinedRuntimeChainProfileMask = -1;
      combinedRuntimeChainExtraBlurPasses = -1;

      for(int profile = 0; profile < combinedRuntimeChainLevels.length; ++profile) {
         combinedRuntimeChainLevels[profile] = -1;
      }

      combinedRuntimeChainUniformsDirty = true;
   }

   private static int getExtraBlurPasses(int blurPassCount) {
      return Math.max(0, Math.min(4, blurPassCount) - 3);
   }

   private static int getActiveLevels(double radius) {
      return radius > (double)500.0F ? 8 : 7;
   }

   private static int getLevelWidth(int mainWidth, int mainHeight, int levelIndex) {
      if (levelIndex == 0) {
         return Math.max(1, mainWidth);
      } else {
         float aspect = mainHeight <= 0 ? 1.0F : (float)mainWidth / (float)mainHeight;
         float levelScale = (float)Math.pow((double)0.5F, (double)(levelIndex - 1));
         return Math.max(1, Math.round((float)getReferenceLevel0Height(mainHeight) * aspect * levelScale));
      }
   }

   private static int getLevelHeight(int mainHeight, int levelIndex) {
      if (levelIndex == 0) {
         return Math.max(1, mainHeight);
      } else {
         float levelScale = (float)Math.pow((double)0.5F, (double)(levelIndex - 1));
         return Math.max(1, Math.round((float)getReferenceLevel0Height(mainHeight) * levelScale));
      }
   }

   private static int getReferenceLevel0Height(int mainHeight) {
      return Math.max(1, Math.min(540, Math.max(270, mainHeight)));
   }

   private static int getScaledDimension(int mainSize, float scale) {
      return Math.max(1, Math.round((float)mainSize * scale));
   }

   private static void addExtraBlurPasses(List<PostChainConfig.Pass> passes, int levelIndex, int extraBlurPasses) {
      for(int i = 0; i < extraBlurPasses; ++i) {
         passes.add(postPass(BLUR_HORIZONTAL_SHADER_ID, List.of(targetInput("In", LEVEL_TARGET_IDS[levelIndex], false, true)), LEVEL_BLUR_TARGET_IDS[levelIndex], Map.of()));
         passes.add(postPass(BLUR_VERTICAL_SHADER_ID, List.of(targetInput("In", LEVEL_BLUR_TARGET_IDS[levelIndex], false, true)), LEVEL_TARGET_IDS[levelIndex], Map.of()));
      }

   }

   private static void addCombinedExtraBlurPasses(List<PostChainConfig.Pass> passes, int profile, int levelIndex, int extraBlurPasses) {
      for(int i = 0; i < extraBlurPasses; ++i) {
         passes.add(postPass(BLUR_HORIZONTAL_SHADER_ID, List.of(targetInput("In", COMBINED_LEVEL_TARGET_IDS[profile][levelIndex], false, true)), COMBINED_LEVEL_BLUR_TARGET_IDS[profile][levelIndex], Map.of()));
         passes.add(postPass(BLUR_VERTICAL_SHADER_ID, List.of(targetInput("In", COMBINED_LEVEL_BLUR_TARGET_IDS[profile][levelIndex], false, true)), COMBINED_LEVEL_TARGET_IDS[profile][levelIndex], Map.of()));
      }

   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float smoothstep(float edge0, float edge1, float x) {
      float t = clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static float currentFarPlane(Minecraft minecraft) {
      return minecraft != null && minecraft.options != null ? Math.max(32.0F, (float)minecraft.options.getEffectiveRenderDistance() * 16.0F) : 1024.0F;
   }

   private static Identifier runtimeTarget(String path) {
      return Identifier.fromNamespaceAndPath("vybrantvisual", path);
   }

   private static Identifier[][] createProfileLevelTargets(String targetKind) {
      Identifier[][] targets = new Identifier[3][8];

      for(int profile = 0; profile < targets.length; ++profile) {
         for(int level = 0; level < targets[profile].length; ++level) {
            targets[profile][level] = runtimeTarget("combined/profile_" + profile + "/" + targetKind + "_" + level);
         }
      }

      return targets;
   }

   private static PostChainConfig.InternalTarget internalTarget(int width, int height) {
      return new PostChainConfig.InternalTarget(Optional.of(width), Optional.of(height), false, 0);
   }

   private static Identifier compositeLevelTarget(int levelIndex, int activeLevels) {
      int resolvedIndex = Math.min(levelIndex, activeLevels - 1);
      if (resolvedIndex >= 0 && resolvedIndex < LEVEL_TARGET_IDS.length) {
         return LEVEL_TARGET_IDS[resolvedIndex];
      } else {
         throw new IllegalArgumentException("Unsupported bloom level index: " + resolvedIndex);
      }
   }

   private static Identifier combinedProfileLevelTarget(int profile, int levelIndex, int activeLevels) {
      int resolvedIndex = Math.max(1, Math.min(levelIndex, activeLevels - 1));
      if (profile >= 0 && profile < COMBINED_LEVEL_TARGET_IDS.length && resolvedIndex < COMBINED_LEVEL_TARGET_IDS[profile].length) {
         return COMBINED_LEVEL_TARGET_IDS[profile][resolvedIndex];
      } else {
         throw new IllegalArgumentException("Unsupported combined bloom profile/level: " + profile + "/" + resolvedIndex);
      }
   }

   private static Identifier resolvedProfileTargetOrFallback(int profile, int fallbackProfile, int activeProfileMask) {
      return PROFILE_RESOLVED_TARGET_IDS[(activeProfileMask & 1 << profile) != 0 ? profile : fallbackProfile];
   }

   private static PostChainConfig.Pass postPass(Identifier fragmentShaderId, List<PostChainConfig.Input> inputs, Identifier outputTarget, Map<String, List<UniformValue>> uniforms) {
      return new PostChainConfig.Pass(SCREEN_QUAD_SHADER_ID, fragmentShaderId, inputs, outputTarget, uniforms);
   }

   private static PostChainConfig.TargetInput targetInput(String samplerName, Identifier targetId, boolean depthBuffer, boolean bilinear) {
      return new PostChainConfig.TargetInput(samplerName, targetId, depthBuffer, bilinear);
   }

   private static Map<String, List<UniformValue>> extractUniformDefaults(float selectedProfile, int activeProfileMask) {
      return Map.of("BloomExtractConfig", List.of(new UniformValue.FloatUniform(0.15F), new UniformValue.FloatUniform(0.3F), new UniformValue.FloatUniform(0.2F), new UniformValue.FloatUniform(75.0F), new UniformValue.FloatUniform(0.05F), new UniformValue.FloatUniform(1024.0F), new UniformValue.FloatUniform(5.0F), new UniformValue.FloatUniform(2.0F), new UniformValue.FloatUniform(selectedProfile), new UniformValue.FloatUniform((float)activeProfileMask)));
   }

   private static Map<String, List<UniformValue>> compositeUniformDefaults() {
      return Map.of("BloomCompositeConfig", List.of(new UniformValue.FloatUniform(3.0F)), "BloomCompositeWeights", compositeWeightDefaults(), "BloomCompositeDistanceConfig", List.of(new UniformValue.FloatUniform(75.0F), new UniformValue.FloatUniform(0.05F), new UniformValue.FloatUniform(1024.0F), new UniformValue.FloatUniform(2.0F)));
   }

   private static Map<String, List<UniformValue>> resolveUniformDefaults(float[] weights, float perceptualEncoding) {
      List<UniformValue> values = new ArrayList(8);

      for(int level = 1; level < 8; ++level) {
         values.add(new UniformValue.FloatUniform(weights[level]));
      }

      values.add(new UniformValue.FloatUniform(perceptualEncoding));
      return Map.of("BloomResolveWeights", List.copyOf(values));
   }

   private static Map<String, List<UniformValue>> profileCombineUniformDefaults(int activeProfileMask) {
      return Map.of("BloomProfileCombineConfig", List.of(new UniformValue.FloatUniform((activeProfileMask & 1) != 0 ? 1.0F : 0.0F), new UniformValue.FloatUniform((activeProfileMask & 2) != 0 ? 1.0F : 0.0F), new UniformValue.FloatUniform((activeProfileMask & 4) != 0 ? 1.0F : 0.0F), new UniformValue.FloatUniform(0.0F)));
   }

   private static Map<String, List<UniformValue>> resolvedCompositeUniformDefaults() {
      return Map.of("BloomCompositeConfig", List.of(new UniformValue.FloatUniform(3.0F)), "BloomCompositeDistanceConfig", List.of(new UniformValue.FloatUniform(75.0F), new UniformValue.FloatUniform(0.05F), new UniformValue.FloatUniform(1024.0F), new UniformValue.FloatUniform(2.0F)));
   }

   private static List<UniformValue> compositeWeightDefaults() {
      List<UniformValue> values = new ArrayList(28);

      for(int profile = 0; profile < 3; ++profile) {
         for(int level = 0; level < 8; ++level) {
            values.add(new UniformValue.FloatUniform(level == 0 ? 1.0F : 0.0F));
         }
      }

      values.add(new UniformValue.FloatUniform(0.0F));
      values.add(new UniformValue.FloatUniform(0.0F));
      values.add(new UniformValue.FloatUniform(0.0F));
      values.add(new UniformValue.FloatUniform(0.0F));
      return List.copyOf(values);
   }

   static {
      EXTERNAL_TARGETS = Set.of(LevelTargetBundle.MAIN_TARGET_ID, BloomSourceRenderer.SOURCE_TARGET_ID, BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID, BloomSourceRenderer.OCCLUDER_DEPTH_TARGET_ID);
      PROJECTION_BUFFER = new ProjectionMatrixBuffer("shine_runtime_post");
      ORTHO_PROJECTION = new Projection();
      BASE_LEVEL_FACTORS = new float[]{1.0F, 1.0F, 0.8F, 0.6F, 0.4F, 0.2F, 0.1F, 0.05F};
      LEVEL_TARGET_IDS = new Identifier[]{runtimeTarget("full_a"), runtimeTarget("half_a"), runtimeTarget("quarter_a"), runtimeTarget("eighth_a"), runtimeTarget("sixteenth_a"), runtimeTarget("thirtysecond_a"), runtimeTarget("sixtyfourth_a"), runtimeTarget("onetwentyeighth_a")};
      LEVEL_BLUR_TARGET_IDS = new Identifier[]{runtimeTarget("full_blur"), runtimeTarget("half_blur"), runtimeTarget("quarter_blur"), runtimeTarget("eighth_blur"), runtimeTarget("sixteenth_blur"), runtimeTarget("thirtysecond_blur"), runtimeTarget("sixtyfourth_blur"), runtimeTarget("onetwentyeighth_blur")};
      COMBINED_LEVEL_TARGET_IDS = createProfileLevelTargets("level");
      COMBINED_LEVEL_BLUR_TARGET_IDS = createProfileLevelTargets("blur");
      PROFILE_RESOLVED_TARGET_IDS = new Identifier[]{runtimeTarget("combined/profile_0/resolved"), runtimeTarget("combined/profile_1/resolved"), runtimeTarget("combined/profile_2/resolved")};
      COMBINED_RESOLVED_TARGET_ID = runtimeTarget("combined/resolved");
      runtimeChainUniformsDirty = new boolean[]{true, true, true};
      runtimeChains = new PostChain[3];
      runtimeChainWidths = new int[]{-1, -1, -1};
      runtimeChainHeights = new int[]{-1, -1, -1};
      runtimeChainLevels = new int[]{-1, -1, -1};
      runtimeChainExtraBlurPasses = new int[]{-1, -1, -1};
      runtimeChainProfileFilters = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
      lastExtractUniforms = new float[3][9];
      lastCompositeLevelWeights = new float[3][28];
      lastCompositeRadius = new double[]{Double.NaN, Double.NaN, Double.NaN};
      lastCompositeActiveLevels = new int[]{-1, -1, -1};
      lastCompositeStrength = new float[3];
      lastCompositeMaxDistance = new float[3];
      lastCompositeNearPlane = new float[3];
      lastCompositeFarPlane = new float[3];
      combinedRuntimeChainWidth = -1;
      combinedRuntimeChainHeight = -1;
      combinedRuntimeChainProfileMask = -1;
      combinedRuntimeChainExtraBlurPasses = -1;
      combinedRuntimeChainLevels = new int[]{-1, -1, -1};
      combinedRuntimeChainUniformsDirty = true;
      lastCombinedExtractUniforms = new float[3][9];
      lastCombinedResolveWeights = new float[3][8];
      radiusProfileScanConfigVersion = Long.MIN_VALUE;
      radiusProfileScanChunkX = Integer.MIN_VALUE;
      radiusProfileScanChunkZ = Integer.MIN_VALUE;
      radiusProfileScanChunkRadius = Integer.MIN_VALUE;
      radiusProfileScanIndexGeneration = Long.MIN_VALUE;
   }

   private static final class BloomTargetBundle implements PostChain.TargetBundle {
      private ResourceHandle<RenderTarget> main;
      private ResourceHandle<RenderTarget> source;
      private ResourceHandle<RenderTarget> terrainDepth;
      private ResourceHandle<RenderTarget> occluderDepth;

      private BloomTargetBundle(ResourceHandle<RenderTarget> main, ResourceHandle<RenderTarget> source, ResourceHandle<RenderTarget> terrainDepth, ResourceHandle<RenderTarget> occluderDepth) {
         this.main = main;
         this.source = source;
         this.terrainDepth = terrainDepth;
         this.occluderDepth = occluderDepth;
      }

      public void replace(Identifier identifier, ResourceHandle<RenderTarget> resourceHandle) {
         if (identifier.equals(LevelTargetBundle.MAIN_TARGET_ID)) {
            this.main = resourceHandle;
         } else if (identifier.equals(BloomSourceRenderer.SOURCE_TARGET_ID)) {
            this.source = resourceHandle;
         } else if (identifier.equals(BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID)) {
            this.terrainDepth = resourceHandle;
         } else {
            if (!identifier.equals(BloomSourceRenderer.OCCLUDER_DEPTH_TARGET_ID)) {
               throw new IllegalArgumentException("No target with id " + String.valueOf(identifier));
            }

            this.occluderDepth = resourceHandle;
         }

      }

      public ResourceHandle<RenderTarget> get(Identifier identifier) {
         if (identifier.equals(LevelTargetBundle.MAIN_TARGET_ID)) {
            return this.main;
         } else if (identifier.equals(BloomSourceRenderer.SOURCE_TARGET_ID)) {
            return this.source;
         } else if (identifier.equals(BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID)) {
            return this.terrainDepth;
         } else {
            return identifier.equals(BloomSourceRenderer.OCCLUDER_DEPTH_TARGET_ID) ? this.occluderDepth : null;
         }
      }
   }

   @FunctionalInterface
   private interface UniformWriter {
      void write(Std140Builder var1);
   }
}
