package com.bloom.mixin.client.sodium;

import com.bloom.client.experimental.render.TerrainCaptureState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {TerrainRenderPass.class},
   remap = false
)
public abstract class SodiumTerrainRenderPassMixin {
   @Inject(
      method = {"getTarget"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void shine$useAlternateTerrainCaptureTarget(CallbackInfoReturnable<RenderTarget> cir) {
      if (TerrainCaptureState.isCapturing() && TerrainCaptureState.target() != null) {
         cir.setReturnValue(TerrainCaptureState.target());
      }

   }
}
