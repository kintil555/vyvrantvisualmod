package com.bloom.mixin.client;

import com.bloom.client.experimental.render.ShoreFoamBiomeProfiles;
import com.bloom.client.experimental.render.ShoreFoamWaterMask;
import com.bloom.client.selection.BloomSelection;
import com.bloom.client.selection.BloomSelectionState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pushes per-block/per-fluid bloom + shore foam state before vanilla tesselates each
 * block/fluid into the chunk mesh, so LiquidBlockRendererMixin and BloomSelection can
 * read it while encoding packed light. Trimmed to bloom + shore foam only: the fields
 * this mixin does not feed (rim light, block motion) are pushed as neutral defaults
 * since those features aren't part of this build.
 */
@Mixin({SectionCompiler.class})
public abstract class SectionCompilerMixin {
   @WrapOperation(
      method = {"compile"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"
)},
      require = 0
   )
   private void bloom$wrapFluidTesselate(FluidRenderer fluidRenderer, BlockAndTintGetter level, BlockPos blockPos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, Operation<Void> original) {
      double previousStrength = BloomSelectionState.pushFluidStrength(BloomSelection.getFluidSourceStrength(fluidState));
      int previousRadiusProfile = BloomSelectionState.pushFluidRadiusProfile(BloomSelection.getFluidRadiusProfile(fluidState));
      boolean previousRim = BloomSelectionState.pushRimLightIncluded(false);
      int shoreFoamEdges = ShoreFoamWaterMask.edgeMask(level, blockPos, fluidState);
      int previousShoreFoam = BloomSelectionState.pushShoreFoamEdgeMask(shoreFoamEdges);
      int previousShoreFoamProfile = BloomSelectionState.pushShoreFoamProfile(shoreFoamEdges == 0 ? 0 : ShoreFoamBiomeProfiles.profileIndex(level, blockPos));
      boolean previousBiomeLavaColor = BloomSelectionState.pushBiomeLavaColor(false);
      BlockAndTintGetter previousFluidLevel = BloomSelectionState.pushFluidLevel(level);
      BlockPos previousFluidBlockPos = BloomSelectionState.pushFluidBlockPos(blockPos);
      boolean perVertexWaterNoise = fluidState.is(FluidTags.WATER);
      int previousFluidBaseWaterColor = BloomSelectionState.pushFluidBaseWaterColor(perVertexWaterNoise ? BiomeColors.getAverageWaterColor(level, blockPos) : -1);

      try {
         original.call(new Object[]{fluidRenderer, level, blockPos, output, blockState, fluidState});
      } finally {
         BloomSelectionState.popFluidBaseWaterColor(previousFluidBaseWaterColor);
         BloomSelectionState.popFluidBlockPos(previousFluidBlockPos);
         BloomSelectionState.popFluidLevel(previousFluidLevel);
         BloomSelectionState.popBiomeLavaColor(previousBiomeLavaColor);
         BloomSelectionState.popShoreFoamProfile(previousShoreFoamProfile);
         BloomSelectionState.popShoreFoamEdgeMask(previousShoreFoam);
         BloomSelectionState.popRimLightIncluded(previousRim);
         BloomSelectionState.popFluidRadiusProfile(previousRadiusProfile);
         BloomSelectionState.popFluidStrength(previousStrength);
      }

   }

   @WrapOperation(
      method = {"compile"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"
)},
      require = 0
   )
   private void bloom$wrapModelBlockTesselate(ModelBlockRenderer modelBlockRenderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos blockPos, BlockState blockState, BlockStateModel blockStateModel, long seed, Operation<Void> original) {
      bloom$withBlockSelection(modelBlockRenderer, output, x, y, z, level, blockPos, blockState, blockStateModel, seed, original);
   }

   @WrapOperation(
      method = {"compile"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;tesselateBlockProxy(Lnet/minecraft/client/renderer/block/ModelBlockRenderer;Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"
)},
      require = 0
   )
   private void bloom$wrapProxyBlockTesselate(ModelBlockRenderer modelBlockRenderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos blockPos, BlockState blockState, BlockStateModel blockStateModel, long seed, Operation<Void> original) {
      bloom$withBlockSelection(modelBlockRenderer, output, x, y, z, level, blockPos, blockState, blockStateModel, seed, original);
   }

   private static void bloom$withBlockSelection(ModelBlockRenderer modelBlockRenderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos blockPos, BlockState blockState, BlockStateModel blockStateModel, long seed, Operation<Void> original) {
      double previousStrength = BloomSelectionState.pushBlockStrength(BloomSelection.getBlockSourceStrength(blockState));
      int previousRadiusProfile = BloomSelectionState.pushBlockRadiusProfile(BloomSelection.getBlockRadiusProfile(blockState));
      String previousBlockId = BloomSelectionState.pushBlockId(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString());
      boolean previousRim = BloomSelectionState.pushRimLightIncluded(false);
      BlockAndTintGetter previousLevel = BloomSelectionState.pushTerrainCausticsLevel(level);
      BlockPos previousPos = BloomSelectionState.pushTerrainCausticsBlockPos(blockPos);
      int previousMotion = BloomSelectionState.pushBlockMotionProfile(0);

      try {
         original.call(new Object[]{modelBlockRenderer, output, x, y, z, level, blockPos, blockState, blockStateModel, seed});
      } finally {
         BloomSelectionState.popBlockMotionProfile(previousMotion);
         BloomSelectionState.popTerrainCausticsBlockPos(previousPos);
         BloomSelectionState.popTerrainCausticsLevel(previousLevel);
         BloomSelectionState.popRimLightIncluded(previousRim);
         BloomSelectionState.popBlockId(previousBlockId);
         BloomSelectionState.popBlockRadiusProfile(previousRadiusProfile);
         BloomSelectionState.popBlockStrength(previousStrength);
      }

   }
}
