package com.bloom.mixin.client.sodium;

import com.bloom.BloomMod;
import com.bloom.client.coloredlight.ColoredLightRenderer;
import com.bloom.client.experimental.grassblades.GrassBladeInteractionController;
import com.bloom.client.experimental.render.FoliagePixelWindRenderer;
import com.bloom.client.experimental.render.TerrainCaptureState;
import com.bloom.client.experimental.render.TerrainCausticsRenderer;
import com.bloom.client.experimental.render.sodium.SodiumChunkFadeBlendState;
import com.bloom.client.experimental.render.sodium.SodiumTerrainRenderState;
import com.bloom.client.experimental.render.sodium.SodiumVulkanTerrainUniforms;
import com.bloom.client.experimental.render.sodium.WaterReflectionShaderBridge;
import com.bloom.client.render.BloomMaskAtlas;
import com.bloom.client.render.ShineRenderBackend;
import com.bloom.mixin.client.accessor.RenderPipelineBuilderAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ShaderChunkRenderer.class},
   remap = false
)
public abstract class SodiumShaderChunkRendererMixin {
   @Unique
   private static boolean shine$loggedSamplerLayout;
   @Unique
   private static boolean shine$loggedColorTargets;

   @Inject(
      method = {"begin"},
      at = {@At("HEAD")},
      require = 1
   )
   private void shine$prepareTerrainPass(TerrainRenderPass pass, FogParameters fogParameters, GpuSampler mipSampler, CallbackInfo ci) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         SodiumChunkFadeBlendState.restore();
         WaterReflectionShaderBridge.restoreSodiumTextures();
         GrassBladeInteractionController.restoreSodiumTerrainSampler();
      }

      SodiumTerrainRenderState.begin(pass);
      if (!TerrainCaptureState.isCapturing(TerrainCaptureState.Mode.SKY_LIGHT)) {
         BloomMaskAtlas.ensureReady();
         if (ShineRenderBackend.canUseRawOpenGl()) {
            TerrainCausticsRenderer.prepareSodiumTerrainTextures();
         }

         FoliagePixelWindRenderer.prepareSodiumTerrainMask();
         GrassBladeInteractionController.prepareSodiumTerrainField();
      }

      SodiumVulkanTerrainUniforms.beginTerrainPass(pass);
   }

   @Inject(
      method = {"end"},
      at = {@At("RETURN")},
      require = 1
   )
   private void shine$finishTerrainPass(TerrainRenderPass pass, CallbackInfo ci) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         SodiumChunkFadeBlendState.restore();
         WaterReflectionShaderBridge.restoreSodiumTextures();
         GrassBladeInteractionController.restoreSodiumTerrainSampler();
      }

      SodiumVulkanTerrainUniforms.endTerrainPass();
      ColoredLightRenderer.setCurrentSodiumProgramId(0);
      SodiumTerrainRenderState.end();
   }

   @WrapOperation(
      method = {"<clinit>"},
      at = {@At(
   value = "INVOKE",
   target = "Lcom/mojang/blaze3d/pipeline/BindGroupLayout$Builder;build()Lcom/mojang/blaze3d/pipeline/BindGroupLayout;"
)},
      require = 1
   )
   private static BindGroupLayout shine$addMaskSampler(BindGroupLayout.Builder builder, Operation<BindGroupLayout> original) {
      builder.withSampler("u_ShineMaskTex");
      builder.withSampler("u_ShineCausticsTex");
      builder.withSampler("u_ShineCloudTex");
      builder.withSampler("u_ShineFoliageWindMaskTex");
      builder.withSampler("u_ShineGrassBladeInteractionTex");
      builder.withSampler("u_ShineWaterEdgeFoamTex");
      builder.withSampler("u_ShineWaterOpaqueDepthTex");
      builder.withSampler("u_ShineWaterReflectionTex");
      builder.withSampler("u_ShineWaterWakeTex");
      if (ShineRenderBackend.isVulkan()) {
         builder.withUniform("ShineTerrainCore", UniformType.UNIFORM_BUFFER);
         builder.withUniform("ShineFoliageWind", UniformType.UNIFORM_BUFFER);
         builder.withUniform("ShineGrassInteraction", UniformType.UNIFORM_BUFFER);
         builder.withUniform("ShineTerrainAnimation", UniformType.UNIFORM_BUFFER);
         builder.withUniform("ShineColoredLightRegion", UniformType.UNIFORM_BUFFER);
         builder.withUniform("ShineColoredLightDynamic", UniformType.UNIFORM_BUFFER);
         builder.withUniform("ShineWater", UniformType.UNIFORM_BUFFER);
         builder.withUniform("u_ShineColoredLightData", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT);
      }

      if (!shine$loggedSamplerLayout) {
         BloomMod.LOGGER.debug("Shine added Sodium terrain mask sampler to the bind group layout.");
         shine$loggedSamplerLayout = true;
      }

      return (BindGroupLayout)original.call(new Object[]{builder});
   }

   @WrapOperation(
      method = {"createShader"},
      at = {@At(
   value = "INVOKE",
   target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
)},
      require = 1
   )
   private RenderPipeline shine$addExtraColorTargets(RenderPipeline.Builder builder, Operation<RenderPipeline> original) {
      if (ShineRenderBackend.isVulkan()) {
         ColorTargetState sceneTargetState = ((RenderPipelineBuilderAccessor)builder).shine$getColorTargetStates()[0];
         if (sceneTargetState == null) {
            sceneTargetState = ColorTargetState.DEFAULT;
         }

         builder.withColorTargetState(0, ColorTargetState.DEFAULT);
         builder.withColorTargetState(1, ColorTargetState.DEFAULT);
         builder.withColorTargetState(2, sceneTargetState);
         builder.withCull(false);
      } else {
         builder.withColorTargetState(1, ColorTargetState.DEFAULT);
         builder.withColorTargetState(2, ColorTargetState.DEFAULT);
      }

      if (!shine$loggedColorTargets) {
         BloomMod.LOGGER.debug("Shine added Sodium terrain bloom/rim color target states.");
         shine$loggedColorTargets = true;
      }

      return (RenderPipeline)original.call(new Object[]{builder});
   }
}
