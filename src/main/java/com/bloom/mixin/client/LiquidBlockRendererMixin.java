package com.bloom.mixin.client;

import com.bloom.client.selection.BloomSelectionState;
import com.bloom.client.selection.BloomSourceEncoding;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({FluidRenderer.class})
public abstract class LiquidBlockRendererMixin {
   @Redirect(
      method = {"tesselate"},
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/block/FluidRenderer;getLightCoords(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
   ordinal = 0
)
   )
   private int bloom$lightColor0(FluidRenderer instance, BlockAndTintGetter level, BlockPos blockPos) {
      return bloom$sourceStrengthFluidOrVanilla(level, blockPos);
   }

   @Redirect(
      method = {"tesselate"},
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/block/FluidRenderer;getLightCoords(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
   ordinal = 1
)
   )
   private int bloom$lightColor1(FluidRenderer instance, BlockAndTintGetter level, BlockPos blockPos) {
      return bloom$sourceStrengthFluidOrVanilla(level, blockPos);
   }

   @Redirect(
      method = {"tesselate"},
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/block/FluidRenderer;getLightCoords(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
   ordinal = 2
)
   )
   private int bloom$lightColor2(FluidRenderer instance, BlockAndTintGetter level, BlockPos blockPos) {
      return bloom$sourceStrengthFluidOrVanilla(level, blockPos);
   }

   private static int bloom$sourceStrengthFluidOrVanilla(BlockAndTintGetter level, BlockPos blockPos) {
      int packed = LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level, blockPos), LightCoordsUtil.getLightCoords(level, blockPos.above()));
      packed = BloomSourceEncoding.encodePackedLight(packed, BloomSelectionState.getFluidStrength());
      packed = BloomSourceEncoding.encodeRadiusProfile(packed, BloomSelectionState.getFluidRadiusProfile());
      packed = BloomSourceEncoding.encodeRimLightIncluded(packed, BloomSelectionState.isRimLightIncluded());
      boolean biomeLavaColor = BloomSelectionState.hasBiomeLavaColor();
      int shoreFoamEdgeMask = BloomSelectionState.getShoreFoamEdgeMask();
      packed = BloomSourceEncoding.encodeTerrainCausticsWater(packed, biomeLavaColor || shoreFoamEdgeMask != 0 || BloomSelectionState.isFluidWaterCloudinessEncoded());
      packed = BloomSourceEncoding.encodeShoreFoamEdges(packed, shoreFoamEdgeMask);
      packed = BloomSourceEncoding.encodeShoreFoamProfile(packed, BloomSelectionState.getShoreFoamProfile());
      return BloomSourceEncoding.encodeBiomeLavaColor(packed, biomeLavaColor);
   }
}
