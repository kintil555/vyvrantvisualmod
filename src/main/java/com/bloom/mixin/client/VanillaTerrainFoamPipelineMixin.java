package com.bloom.mixin.client;

import com.bloom.client.experimental.render.TerrainCausticsRenderer;
import com.bloom.mixin.client.accessor.GlRenderPassAccessor;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.Collection;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.mojang.blaze3d.opengl.GlCommandEncoder"}
)
public abstract class VanillaTerrainFoamPipelineMixin {
   @Inject(
      method = {"trySetup"},
      at = {@At(
   value = "INVOKE",
   target = "Lcom/mojang/blaze3d/opengl/GlProgram;getUniforms()Ljava/util/Map;",
   ordinal = 1
)}
   )
   private void bloom$uploadVanillaTerrainFoamUniforms(@Coerce Object renderPass, Collection<String> skippedUniforms, CallbackInfoReturnable<Boolean> cir) {
      GlRenderPipeline compiledPipeline = ((GlRenderPassAccessor)renderPass).shine$getPipeline();
      if (compiledPipeline != null && bloom$isVanillaTerrainPipeline(compiledPipeline.info())) {
         TerrainCausticsRenderer.uploadVanillaTerrainUniforms(compiledPipeline.program().getProgramId());
         TerrainCausticsRenderer.bindVanillaTerrainSampler((RenderPass) renderPass);
      }
   }

   private static boolean bloom$isVanillaTerrainPipeline(RenderPipeline pipeline) {
      return pipeline == RenderPipelines.SOLID_TERRAIN || pipeline == RenderPipelines.CUTOUT_TERRAIN || pipeline == RenderPipelines.TRANSLUCENT_TERRAIN;
   }
}
