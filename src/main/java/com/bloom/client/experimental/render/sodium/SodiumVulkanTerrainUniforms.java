package com.bloom.client.experimental.render.sodium;

import com.bloom.client.coloredlight.ColoredLightRenderer;
import com.bloom.client.config.BloomMaskConfig;
import com.bloom.client.experimental.grassblades.GrassBladeInteractionController;
import com.bloom.client.experimental.render.ChunksFadeRenderer;
import com.bloom.client.experimental.render.ExperimentalBlockMotionRenderer;
import com.bloom.client.experimental.render.ExperimentalWorldShadowTint;
import com.bloom.client.experimental.render.FoliagePixelWindRenderer;
import com.bloom.client.experimental.render.TerrainCaptureState;
import com.bloom.client.experimental.render.TerrainWaterUniformBuffer;
import com.bloom.client.render.BloomSourceRenderer;
import com.bloom.client.render.ShineRenderBackend;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;
import java.nio.ByteBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.DynamicUniformStorage;

public final class SodiumVulkanTerrainUniforms {
   public static final String TERRAIN_BLOCK = "ShineTerrainCore";
   public static final String FOLIAGE_BLOCK = "ShineFoliageWind";
   public static final String INTERACTION_BLOCK = "ShineGrassInteraction";
   public static final String ANIMATION_BLOCK = "ShineTerrainAnimation";
   public static final String COLORED_REGION_BLOCK = "ShineColoredLightRegion";
   public static final String COLORED_DYNAMIC_BLOCK = "ShineColoredLightDynamic";
   public static final String COLORED_DATA_BUFFER = "u_ShineColoredLightData";
   public static final String GLSL_BLOCK_DECLARATIONS = "layout(std140) uniform ShineTerrainCore {\n    int u_ShineBloomOutputEnabled;\n    int u_ShineUseMasks;\n};\nlayout(std140) uniform ShineFoliageWind {\n    int u_ShineFoliageWindEnabled;\n    int u_ShineFoliageWindMode;\n    float u_ShineFoliageWindTime;\n    float u_ShineFoliageWindPhase;\n    float u_ShineFoliageWindStrength;\n    float u_ShineFoliageWindSpeed;\n    float u_ShineFoliageWindFrameRate;\n    float u_ShineFoliageWindMaxOffset;\n    float u_ShineFoliageWindDirection;\n    float u_ShineFoliageWindLeafWave;\n    float u_ShineFoliageWindLeafDrift;\n    float u_ShineFoliageWindGrassSlice;\n    float u_ShineFoliageWindGrassDrift;\n    float u_ShineFoliageWindTopBias;\n    float u_ShineFoliageWindGust;\n    int u_ShineFoliageWindGroundEnabled;\n    int u_ShineFoliageWindLeavesEnabled;\n    float u_ShineFoliageWindLeafStrength;\n    float u_ShineFoliageWindLeafSpeed;\n    float u_ShineFoliageWindLeafMaxOffset;\n    float u_ShineFoliageWindLeafFlutter;\n};\nlayout(std140) uniform ShineGrassInteraction {\n    int u_ShineGrassBladeInteractionEnabled;\n    vec3 u_ShineGrassBladeInteractionOrigin;\n    float shine_GrassBladeInteractionOriginPadding;\n    float u_ShineGrassBladeInteractionWorldSize;\n    float u_ShineGrassBladeInteractionMaxOffset;\n    float u_ShineTerrainInteractionTickAlpha;\n    int u_ShineGrassBladeLodEnabled;\n    float u_ShineGrassBladeLodFullDetail;\n    float u_ShineGrassBladeLodRenderDistance;\n    float u_ShineGrassBladeLodFarDensity;\n    int u_ShineGrassBladeWindEnabled;\n    float u_ShineGrassBladeWindResponse;\n    float u_ShineGrassBladeWindStiffness;\n    float u_ShineGrassBladeWindStiffnessVariation;\n    float u_ShineGrassBladeWindPhaseVariation;\n    float u_ShineGrassBladeWindTipLag;\n    int u_ShineFoliageInteractionEnabled;\n    float u_ShineFoliageInteractionTickAlpha;\n    float u_ShineFoliageInteractionBend;\n    vec4 u_ShineFoliageInteractionBounds;\n};\nlayout(std140) uniform ShineTerrainAnimation {\n    int u_ShineBlockMotionEnabled;\n    float u_ShineBlockMotionTime;\n    int u_ShineBlockMotionProfileCount;\n    vec4 u_ShineBlockMotionProfileA[16];\n    vec4 u_ShineBlockMotionProfileB[16];\n    int u_ShineChunksFadeEnabled;\n    int u_ShineChunksFadeCurve;\n    float u_ShineChunksFadeStartVisibility;\n    float u_ShineChunksFadeEdgeDistance;\n    float u_ShineChunksFadeRenderDistance;\n    vec4 u_ShineWorldShadowControl;\n    vec4 u_ShineWorldShadowStrengths;\n    vec4 u_ShineWorldShadowScales;\n    vec4 u_ShineWorldShadowAmounts;\n    vec4 u_ShineWorldShadowShape;\n};\nlayout(std140) uniform ShineColoredLightRegion {\n    int u_ShineColoredLightCount;\n    int u_ShineColoredLightHasMinBlock;\n    int u_ShineColoredLightRegionDataOffset;\n    vec3 u_ShineColoredLightRegionOrigin;\n    ivec3 u_ShineColoredLightCellDimensions;\n    float shine_ColoredLightCellDimensionsPadding;\n    float u_ShineColoredLightAnimationTime;\n};\nlayout(std140) uniform ShineColoredLightDynamic {\n    int u_ShineDynamicColoredLightCount;\n    vec4 u_ShineDynamicColoredLightPosRadius[64];\n    vec4 u_ShineDynamicColoredLightColor[64];\n    vec4 u_ShineDynamicColoredLightAnimation[64];\n};\n";
   private static DynamicUniformStorage<TerrainCoreUniform> terrainStorage;
   private static DynamicUniformStorage<FoliageUniform> foliageStorage;
   private static DynamicUniformStorage<InteractionUniform> interactionStorage;
   private static DynamicUniformStorage<AnimationUniform> animationStorage;
   private static DynamicUniformStorage<ColoredRegionUniform> coloredRegionStorage;
   private static DynamicUniformStorage<ColoredDynamicUniform> coloredDynamicStorage;
   private static TerrainCaptureState.Mode captureMode;
   private static GpuBufferSlice terrainSlice;
   private static GpuBufferSlice foliageSlice;
   private static GpuBufferSlice interactionSlice;
   private static GpuBufferSlice animationSlice;
   private static GpuBufferSlice coloredDynamicSlice;

   private SodiumVulkanTerrainUniforms() {
   }

   public static void beginTerrainPass(TerrainRenderPass pass) {
      if (!ShineRenderBackend.isVulkan()) {
         clearPassState();
      } else if (pass == null) {
         throw new IllegalStateException("Cannot prepare Shine Vulkan terrain descriptors without a Sodium terrain pass");
      } else {
         captureMode = TerrainCaptureState.mode();
         boolean skyCapture = captureMode == TerrainCaptureState.Mode.SKY_LIGHT;
         boolean ordinaryTerrain = captureMode == TerrainCaptureState.Mode.NONE;

         try {
            ColoredLightRenderer.prepareSodiumVulkanTerrainAtlas(128, 64, 128);
            terrainSlice = terrainStorage().writeUniform(new TerrainCoreUniform(ordinaryTerrain && BloomSourceRenderer.hasPreparedSourceThisFrame(), ordinaryTerrain && BloomMaskConfig.hasCustomMasks()));
            foliageSlice = foliageStorage().writeUniform(new FoliageUniform(FoliagePixelWindRenderer.sodiumVulkanUniforms(), skyCapture));
            interactionSlice = interactionStorage().writeUniform(new InteractionUniform(GrassBladeInteractionController.sodiumVulkanUniforms(), skyCapture));
            animationSlice = animationStorage().writeUniform(new AnimationUniform(ExperimentalBlockMotionRenderer.sodiumVulkanUniforms(), ChunksFadeRenderer.sodiumVulkanUniforms(), ExperimentalWorldShadowTint.sodiumVulkanUniforms(), !ordinaryTerrain));
            coloredDynamicSlice = null;
            if (!TerrainWaterUniformBuffer.beginTerrainPass(pass.isTranslucent(), captureMode)) {
               throw new IllegalStateException("ShineWater rejected Vulkan terrain-pass preparation");
            }
         } catch (RuntimeException exception) {
            clearPassState();
            throw new IllegalStateException("Shine failed to prepare mandatory Vulkan Sodium terrain descriptors", exception);
         }
      }
   }

   public static void bindTextures(RenderPass renderPass) {
      if (ShineRenderBackend.isVulkan()) {
         if (renderPass == null) {
            throw new IllegalStateException("Cannot bind Shine Vulkan terrain textures without a render pass");
         } else {
            FoliagePixelWindRenderer.bindSodiumTerrainSampler(renderPass);
            GrassBladeInteractionController.bindSodiumTerrainSampler(renderPass);
            TerrainWaterUniformBuffer.bindTextures(renderPass);
         }
      }
   }

   public static void bindRegion(RenderPass renderPass, RenderRegion region) {
      if (ShineRenderBackend.isVulkan()) {
         if (renderPass != null && region != null) {
            if (terrainSlice != null && foliageSlice != null && interactionSlice != null && animationSlice != null) {
               try {
                  double minX = (double)region.getOriginX();
                  double minY = (double)region.getOriginY();
                  double minZ = (double)region.getOriginZ();
                  ColoredLightRenderer.SodiumVulkanUniforms colored = ColoredLightRenderer.sodiumVulkanUniformsForBounds(minX, minY, minZ, minX + (double)128.0F, minY + (double)64.0F, minZ + (double)128.0F);
                  boolean disableColoredLights = captureMode == TerrainCaptureState.Mode.SKY_LIGHT;
                  GpuBufferSlice coloredRegionSlice = coloredRegionStorage().writeUniform(new ColoredRegionUniform(colored, disableColoredLights));
                  if (coloredDynamicSlice == null) {
                     coloredDynamicSlice = coloredDynamicStorage().writeUniform(new ColoredDynamicUniform(colored, disableColoredLights));
                  }

                  renderPass.setUniform("ShineTerrainCore", terrainSlice);
                  renderPass.setUniform("ShineFoliageWind", foliageSlice);
                  renderPass.setUniform("ShineGrassInteraction", interactionSlice);
                  renderPass.setUniform("ShineTerrainAnimation", animationSlice);
                  renderPass.setUniform("ShineColoredLightRegion", coloredRegionSlice);
                  renderPass.setUniform("ShineColoredLightDynamic", coloredDynamicSlice);
                  GpuBuffer coloredData = ColoredLightRenderer.sodiumVulkanLightDataBuffer();
                  renderPass.setUniform("u_ShineColoredLightData", coloredData);
                  if (!TerrainWaterUniformBuffer.bindForRegion(renderPass, region.getOriginX(), region.getOriginY(), region.getOriginZ())) {
                     throw new IllegalStateException("ShineWater failed to bind its mandatory Vulkan terrain descriptor");
                  }
               } catch (RuntimeException exception) {
                  throw new IllegalStateException("Shine failed to bind mandatory Vulkan Sodium terrain descriptors for region " + region.getOriginX() + "," + region.getOriginY() + "," + region.getOriginZ(), exception);
               }
            } else {
               throw new IllegalStateException("Shine Vulkan terrain descriptor state was not prepared before the region draw");
            }
         } else {
            throw new IllegalStateException("Cannot bind Shine Vulkan terrain descriptors without a render pass and region");
         }
      }
   }

   public static void endTerrainPass() {
      TerrainWaterUniformBuffer.endTerrainPass();
      clearPassState();
   }

   public static void endFrame() {
      if (terrainStorage != null) {
         terrainStorage.endFrame();
      }

      if (foliageStorage != null) {
         foliageStorage.endFrame();
      }

      if (interactionStorage != null) {
         interactionStorage.endFrame();
      }

      if (animationStorage != null) {
         animationStorage.endFrame();
      }

      if (coloredRegionStorage != null) {
         coloredRegionStorage.endFrame();
      }

      if (coloredDynamicStorage != null) {
         coloredDynamicStorage.endFrame();
      }

      ColoredLightRenderer.endVulkanFrame();
   }

   public static void shutdown() {
      clearPassState();
      if (terrainStorage != null) {
         terrainStorage.close();
      }

      if (foliageStorage != null) {
         foliageStorage.close();
      }

      if (interactionStorage != null) {
         interactionStorage.close();
      }

      if (animationStorage != null) {
         animationStorage.close();
      }

      if (coloredRegionStorage != null) {
         coloredRegionStorage.close();
      }

      if (coloredDynamicStorage != null) {
         coloredDynamicStorage.close();
      }

      terrainStorage = null;
      foliageStorage = null;
      interactionStorage = null;
      animationStorage = null;
      coloredRegionStorage = null;
      coloredDynamicStorage = null;
      ColoredLightRenderer.shutdownVulkanResources();
   }

   private static void clearPassState() {
      captureMode = TerrainCaptureState.Mode.NONE;
      terrainSlice = null;
      foliageSlice = null;
      interactionSlice = null;
      animationSlice = null;
      coloredDynamicSlice = null;
   }

   private static DynamicUniformStorage<TerrainCoreUniform> terrainStorage() {
      if (terrainStorage == null) {
         terrainStorage = new DynamicUniformStorage("Shine Vulkan terrain core", 64, 16);
      }

      return terrainStorage;
   }

   private static DynamicUniformStorage<FoliageUniform> foliageStorage() {
      if (foliageStorage == null) {
         foliageStorage = new DynamicUniformStorage("Shine Vulkan foliage wind", 256, 16);
      }

      return foliageStorage;
   }

   private static DynamicUniformStorage<InteractionUniform> interactionStorage() {
      if (interactionStorage == null) {
         interactionStorage = new DynamicUniformStorage("Shine Vulkan foliage interaction", 256, 16);
      }

      return interactionStorage;
   }

   private static DynamicUniformStorage<AnimationUniform> animationStorage() {
      if (animationStorage == null) {
         animationStorage = new DynamicUniformStorage("Shine Vulkan terrain animation", 1024, 16);
      }

      return animationStorage;
   }

   private static DynamicUniformStorage<ColoredRegionUniform> coloredRegionStorage() {
      if (coloredRegionStorage == null) {
         coloredRegionStorage = new DynamicUniformStorage("Shine Vulkan colored-light regions", 128, 512);
      }

      return coloredRegionStorage;
   }

   private static DynamicUniformStorage<ColoredDynamicUniform> coloredDynamicStorage() {
      if (coloredDynamicStorage == null) {
         coloredDynamicStorage = new DynamicUniformStorage("Shine Vulkan dynamic colored lights", 4096, 16);
      }

      return coloredDynamicStorage;
   }

   private static void putVec4Array(Std140Builder out, float[] values, int count) {
      for(int i = 0; i < count; ++i) {
         int offset = i * 4;
         out.putVec4(values != null && offset < values.length ? values[offset] : 0.0F, values != null && offset + 1 < values.length ? values[offset + 1] : 0.0F, values != null && offset + 2 < values.length ? values[offset + 2] : 0.0F, values != null && offset + 3 < values.length ? values[offset + 3] : 0.0F);
      }

   }

   private static void putVec4(Std140Builder out, float[] values) {
      out.putVec4(values != null && values.length > 0 ? values[0] : 0.0F, values != null && values.length > 1 ? values[1] : 0.0F, values != null && values.length > 2 ? values[2] : 0.0F, values != null && values.length > 3 ? values[3] : 0.0F);
   }

   static {
      captureMode = TerrainCaptureState.Mode.NONE;
   }

   private static record TerrainCoreUniform(boolean bloomOutput, boolean useMasks) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(this.bloomOutput ? 1 : 0);
         out.putInt(this.useMasks ? 1 : 0);
         out.align(16);
      }
   }

   private static record FoliageUniform(FoliagePixelWindRenderer.VulkanUniforms values, boolean disabled) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(!this.disabled && this.values.enabled() ? 1 : 0);
         out.putInt(this.values.mode());
         out.putFloat(this.values.time());
         out.putFloat(this.values.phase());
         out.putFloat(this.values.strength());
         out.putFloat(this.values.speed());
         out.putFloat(this.values.frameRate());
         out.putFloat(this.values.maxOffset());
         out.putFloat(this.values.direction());
         out.putFloat(this.values.leafWave());
         out.putFloat(this.values.leafDrift());
         out.putFloat(this.values.grassSlice());
         out.putFloat(this.values.grassDrift());
         out.putFloat(this.values.topBias());
         out.putFloat(this.values.gust());
         out.putInt(!this.disabled && this.values.groundEnabled() ? 1 : 0);
         out.putInt(!this.disabled && this.values.leavesEnabled() ? 1 : 0);
         out.putFloat(this.values.leafStrength());
         out.putFloat(this.values.leafSpeed());
         out.putFloat(this.values.leafMaxOffset());
         out.putFloat(this.values.leafFlutter());
         out.align(16);
      }
   }

   private static record InteractionUniform(GrassBladeInteractionController.VulkanUniforms values, boolean disabled) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(!this.disabled && this.values.interactionEnabled() ? 1 : 0);
         out.putVec3(this.values.originX(), this.values.originY(), this.values.originZ());
         out.putFloat(this.values.worldSize());
         out.putFloat(this.values.maxOffset());
         out.putFloat(this.values.tickAlpha());
         out.putInt(!this.disabled && this.values.lodEnabled() ? 1 : 0);
         out.putFloat(this.values.lodFullDetail());
         out.putFloat(this.values.lodRenderDistance());
         out.putFloat(this.values.lodFarDensity());
         out.putInt(!this.disabled && this.values.windEnabled() ? 1 : 0);
         out.putFloat(this.values.windResponse());
         out.putFloat(this.values.windStiffness());
         out.putFloat(this.values.windStiffnessVariation());
         out.putFloat(this.values.windPhaseVariation());
         out.putFloat(this.values.windTipLag());
         out.putInt(!this.disabled && this.values.foliageInteractionEnabled() ? 1 : 0);
         out.putFloat(this.values.foliageTickAlpha());
         out.putFloat(this.values.foliageBend());
         out.putVec4(this.values.foliageMinX(), this.values.foliageMinZ(), this.values.foliageMaxX(), this.values.foliageMaxZ());
         out.align(16);
      }
   }

   private static record AnimationUniform(ExperimentalBlockMotionRenderer.VulkanUniforms blockMotion, ChunksFadeRenderer.VulkanUniforms chunkFade, ExperimentalWorldShadowTint.VulkanUniforms worldShadow, boolean disabled) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(!this.disabled && this.blockMotion.enabled() ? 1 : 0);
         out.putFloat(this.blockMotion.time());
         out.putInt(this.disabled ? 0 : this.blockMotion.profileCount());
         SodiumVulkanTerrainUniforms.putVec4Array(out, this.blockMotion.profileA(), 16);
         SodiumVulkanTerrainUniforms.putVec4Array(out, this.blockMotion.profileB(), 16);
         out.putInt(0);
         out.putInt(this.chunkFade.curve());
         out.putFloat(this.chunkFade.startVisibility());
         out.putFloat(this.chunkFade.edgeDistance());
         out.putFloat(this.chunkFade.renderDistance());
         SodiumVulkanTerrainUniforms.putVec4(out, this.disabled ? null : this.worldShadow.control());
         SodiumVulkanTerrainUniforms.putVec4(out, this.disabled ? null : this.worldShadow.strengths());
         SodiumVulkanTerrainUniforms.putVec4(out, this.disabled ? null : this.worldShadow.scales());
         SodiumVulkanTerrainUniforms.putVec4(out, this.disabled ? null : this.worldShadow.amounts());
         SodiumVulkanTerrainUniforms.putVec4(out, this.disabled ? null : this.worldShadow.shape());
         out.align(16);
      }
   }

   private static record ColoredRegionUniform(ColoredLightRenderer.SodiumVulkanUniforms values, boolean disabled) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(this.disabled ? 0 : this.values.staticCount());
         out.putInt(!this.disabled && this.values.hasMinBlockLight() ? 1 : 0);
         out.putInt(this.values.regionDataOffset());
         out.putVec3(this.values.regionOriginX(), this.values.regionOriginY(), this.values.regionOriginZ());
         out.putIVec3(this.values.cellsX(), this.values.cellsY(), this.values.cellsZ());
         out.putFloat(this.values.animationTime());
         out.align(16);
      }
   }

   private static record ColoredDynamicUniform(ColoredLightRenderer.SodiumVulkanUniforms values, boolean disabled) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(this.disabled ? 0 : this.values.dynamicCount());
         SodiumVulkanTerrainUniforms.putVec4Array(out, this.values.dynamicPosRadius(), 64);
         SodiumVulkanTerrainUniforms.putVec4Array(out, this.values.dynamicColor(), 64);
         SodiumVulkanTerrainUniforms.putVec4Array(out, this.values.dynamicAnimation(), 64);
         out.align(16);
      }
   }
}
