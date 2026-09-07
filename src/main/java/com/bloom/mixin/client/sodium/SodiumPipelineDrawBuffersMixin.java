package com.bloom.mixin.client.sodium;

import com.bloom.BloomMod;
import com.bloom.client.coloredlight.ColoredLightRenderer;
import com.bloom.client.config.BloomMaskConfig;
import com.bloom.client.experimental.grassblades.GrassBladeInteractionController;
import com.bloom.client.experimental.render.ChunksFadeRenderer;
import com.bloom.client.experimental.render.ExperimentalBlockMotionRenderer;
import com.bloom.client.experimental.render.ExperimentalWorldShadowTint;
import com.bloom.client.experimental.render.FoliagePixelWindRenderer;
import com.bloom.client.experimental.render.TerrainCaptureState;
import com.bloom.client.experimental.render.TerrainCausticsRenderer;
import com.bloom.client.experimental.render.sodium.SodiumChunkFadeBlendState;
import com.bloom.client.experimental.render.sodium.SodiumTerrainRenderState;
import com.bloom.client.experimental.render.sodium.WaterReflectionShaderBridge;
import com.bloom.client.render.BloomSourceRenderer;
import com.bloom.client.render.ShineRenderBackend;
import com.bloom.mixin.client.accessor.GlRenderPassAccessor;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Collection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.mojang.blaze3d.opengl.GlCommandEncoder"}
)
public abstract class SodiumPipelineDrawBuffersMixin {
   @Unique
   private static boolean shine$loggedSodiumProgramOutputs;

   @Inject(
      method = {"trySetup"},
      at = {@At(
   value = "INVOKE",
   target = "Lorg/lwjgl/opengl/GL33C;glDrawBuffers([I)V",
   shift = Shift.AFTER
)},
      require = 0
   )
   private void shine$setupSodiumTerrainOutputs(@Coerce Object renderPass, Collection<String> dynamicUniforms, CallbackInfoReturnable<Boolean> cir) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         GlRenderPipeline compiledPipeline = ((GlRenderPassAccessor)renderPass).shine$getPipeline();
         if (compiledPipeline != null && shine$isSodiumTerrainPipeline(compiledPipeline.info())) {
            SodiumChunkFadeBlendState.restore();
            int programId = compiledPipeline.program().getProgramId();
            ColoredLightRenderer.setCurrentSodiumProgramId(programId);
            TerrainRenderPass terrainPass = SodiumTerrainRenderState.pass();
            boolean translucent = terrainPass != null && terrainPass.isTranslucent();
            TerrainCaptureState.Mode captureMode = TerrainCaptureState.mode();
            WaterReflectionShaderBridge.uploadAndBind(programId, translucent, captureMode);
            shine$setInt(programId, "u_ShineBloomOutputEnabled", captureMode == TerrainCaptureState.Mode.NONE && BloomSourceRenderer.hasPreparedSourceThisFrame() ? 1 : 0);
            if (captureMode == TerrainCaptureState.Mode.SKY_LIGHT) {
               shine$setInt(programId, "u_ShineUseMasks", 0);
               shine$setInt(programId, "u_ShineColoredLightCount", 0);
               shine$setInt(programId, "u_ShineDynamicColoredLightCount", 0);
               shine$disableAnimatedTerrain(programId, true);
               shine$uploadRegionState(programId, false);
            } else if (captureMode == TerrainCaptureState.Mode.WATER_REFLECTION) {
               shine$setInt(programId, "u_ShineUseMasks", 0);
               ColoredLightRenderer.uploadSodiumUniforms(programId);
               FoliagePixelWindRenderer.uploadSodiumTerrainUniforms(programId);
               GrassBladeInteractionController.uploadSodiumTerrainUniforms(programId);
               shine$disableAnimatedTerrain(programId, false);
               shine$uploadRegionState(programId, true);
            } else {
               ColoredLightRenderer.uploadSodiumUniforms(programId);
               TerrainCausticsRenderer.uploadSodiumTerrainUniforms(programId);
               ExperimentalBlockMotionRenderer.uploadSodiumTerrainUniforms(programId);
               FoliagePixelWindRenderer.uploadSodiumTerrainUniforms(programId);
               GrassBladeInteractionController.uploadSodiumTerrainUniforms(programId);
               ChunksFadeRenderer.uploadSodiumTerrainUniforms(programId);
               ExperimentalWorldShadowTint.uploadSodiumTerrainUniforms(programId);
               shine$setInt(programId, "u_ShineUseMasks", BloomMaskConfig.hasCustomMasks() ? 1 : 0);
               shine$uploadRegionState(programId, true);
               SodiumChunkFadeBlendState.enable(terrainPass);
            }

            shine$logProgramOutputs(programId);
         }
      }
   }

   @Unique
   private static void shine$uploadRegionState(int programId, boolean coloredLights) {
      RenderRegion region = SodiumTerrainRenderState.region();
      if (region != null) {
         TerrainCausticsRenderer.uploadSodiumRegionOrigin(programId, region.getOriginX(), region.getOriginY(), region.getOriginZ());
         if (coloredLights) {
            double minX = (double)region.getOriginX();
            double minY = (double)region.getOriginY();
            double minZ = (double)region.getOriginZ();
            ColoredLightRenderer.uploadSodiumUniformsForBounds(programId, minX, minY, minZ, minX + (double)128.0F, minY + (double)64.0F, minZ + (double)128.0F);
         }
      }
   }

   @Unique
   private static void shine$disableAnimatedTerrain(int programId, boolean disableFoliage) {
      if (disableFoliage) {
         shine$setInt(programId, "u_ShineFoliageWindEnabled", 0);
         shine$setInt(programId, "u_ShineGrassBladeInteractionEnabled", 0);
         shine$setInt(programId, "u_ShineFoliageInteractionEnabled", 0);
         shine$setInt(programId, "u_ShineGrassBladeLodEnabled", 0);
      }

      shine$setInt(programId, "u_ShineBlockMotionEnabled", 0);
      shine$setInt(programId, "u_ShineBlockMotionProfileCount", 0);
      shine$setInt(programId, "u_ShineChunksFadeEnabled", 0);
      shine$setInt(programId, "u_ShineChunksFadeCurve", 0);
      shine$setFloat(programId, "u_ShineChunksFadeStartVisibility", 1.0F);
      shine$setFloat(programId, "u_ShineChunksFadeEdgeDistance", 0.0F);
      shine$setFloat(programId, "u_ShineChunksFadeRenderDistance", 0.0F);
      shine$setVec4(programId, "u_ShineWorldShadowControl", 0.0F, 0.0F, 0.0F, 0.0F);
      shine$setInt(programId, "u_ShineTerrainCausticsEnabled", 0);
      shine$setInt(programId, "u_ShineShoreFoamEnabled", 0);
   }

   @Unique
   private static void shine$setInt(int programId, String name, int value) {
      int location = GL20.glGetUniformLocation(programId, name);
      if (location >= 0) {
         GL20.glUniform1i(location, value);
      }

   }

   @Unique
   private static void shine$setFloat(int programId, String name, float value) {
      int location = GL20.glGetUniformLocation(programId, name);
      if (location >= 0) {
         GL20.glUniform1f(location, value);
      }

   }

   @Unique
   private static void shine$setVec4(int programId, String name, float x, float y, float z, float w) {
      int location = GL20.glGetUniformLocation(programId, name);
      if (location >= 0) {
         GL20.glUniform4f(location, x, y, z, w);
      }

   }

   @Unique
   private static void shine$logProgramOutputs(int programId) {
      if (!shine$loggedSodiumProgramOutputs && programId != 0) {
         BloomMod.LOGGER.debug("Shine Sodium program outputs: bloomColor={} shineRimMaskColor={} u_ShineUseMasks={} u_ShineMaskTex={}.", new Object[]{GL30.glGetFragDataLocation(programId, "bloomColor"), GL30.glGetFragDataLocation(programId, "shineRimMaskColor"), GL20.glGetUniformLocation(programId, "u_ShineUseMasks"), GL20.glGetUniformLocation(programId, "u_ShineMaskTex")});
         shine$loggedSodiumProgramOutputs = true;
      }
   }

   private static boolean shine$isSodiumTerrainPipeline(RenderPipeline pipeline) {
      Identifier vertexShader = pipeline.getVertexShader();
      return "sodium".equals(vertexShader.getNamespace()) && "blocks/block_layer_opaque".equals(vertexShader.getPath());
   }
}
