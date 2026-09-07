package com.bloom.mixin.client.sodium;

import com.bloom.BloomMod;
import com.bloom.client.config.BloomMaskConfig;
import com.bloom.client.experimental.render.TerrainCausticsRenderer;
import com.bloom.client.render.BloomSourceRenderer;
import com.bloom.client.render.ShineRenderBackend;
import com.bloom.mixin.client.accessor.GlRenderPassAccessor;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Collection;
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

/**
 * Uploads bloom-mask and shore foam shader uniforms right after Sodium sets
 * up its draw buffers for a terrain program.
 */
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
         GlRenderPipeline compiledPipeline = ((GlRenderPassAccessor) renderPass).shine$getPipeline();
         if (compiledPipeline != null && shine$isSodiumTerrainPipeline(compiledPipeline.info())) {
            int programId = compiledPipeline.program().getProgramId();
            TerrainCausticsRenderer.uploadSodiumTerrainUniforms(programId);
            shine$setInt(programId, "u_ShineBloomOutputEnabled", BloomSourceRenderer.hasPreparedSourceThisFrame() ? 1 : 0);
            shine$setInt(programId, "u_ShineUseMasks", BloomMaskConfig.hasCustomMasks() ? 1 : 0);
            shine$logProgramOutputs(programId);
         }
      }
   }

   @Unique
   private static void shine$setInt(int programId, String name, int value) {
      int location = GL20.glGetUniformLocation(programId, name);
      if (location >= 0) {
         GL20.glUniform1i(location, value);
      }
   }

   @Unique
   private static void shine$logProgramOutputs(int programId) {
      if (!shine$loggedSodiumProgramOutputs && programId != 0) {
         BloomMod.LOGGER.debug("Shine Sodium program outputs: bloomColor={} shineRimMaskColor={} u_ShineUseMasks={} u_ShineMaskTex={}.", GL30.glGetFragDataLocation(programId, "bloomColor"), GL30.glGetFragDataLocation(programId, "shineRimMaskColor"), GL20.glGetUniformLocation(programId, "u_ShineUseMasks"), GL20.glGetUniformLocation(programId, "u_ShineMaskTex"));
         shine$loggedSodiumProgramOutputs = true;
      }
   }

   private static boolean shine$isSodiumTerrainPipeline(RenderPipeline pipeline) {
      Identifier vertexShader = pipeline.getVertexShader();
      return "sodium".equals(vertexShader.getNamespace()) && "blocks/block_layer_opaque".equals(vertexShader.getPath());
   }
}
