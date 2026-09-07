package com.bloom.mixin.client.sodium;

import com.bloom.BloomMod;
import com.bloom.client.experimental.render.TerrainCausticsRenderer;
import com.bloom.client.render.BloomMaskAtlas;
import com.bloom.client.render.ShineRenderBackend;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import com.mojang.blaze3d.textures.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wires shore foam (and the existing bloom mask) into Sodium's terrain shader
 * pipeline: registers the extra samplers/color targets it needs and refreshes
 * the shore foam texture once per terrain pass.
 */
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
      BloomMaskAtlas.ensureReady();
      if (ShineRenderBackend.canUseRawOpenGl()) {
         TerrainCausticsRenderer.prepareSodiumTerrainTextures();
      }
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
      builder.withSampler("u_ShineWaterEdgeFoamTex");
      if (!shine$loggedSamplerLayout) {
         BloomMod.LOGGER.debug("Shine added Sodium terrain mask sampler to the bind group layout.");
         shine$loggedSamplerLayout = true;
      }

      return original.call(new Object[]{builder});
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
      builder.withColorTargetState(1, ColorTargetState.DEFAULT);
      builder.withColorTargetState(2, ColorTargetState.DEFAULT);

      if (!shine$loggedColorTargets) {
         BloomMod.LOGGER.debug("Shine added Sodium terrain bloom/rim color target states.");
         shine$loggedColorTargets = true;
      }

      return original.call(new Object[]{builder});
   }
}
