package com.bloom.mixin.client.sodium;

import com.bloom.client.experimental.render.ShoreFoamBiomeProfiles;
import com.bloom.client.experimental.render.ShoreFoamWaterMask;
import com.bloom.client.selection.BloomSelection;
import com.bloom.client.selection.BloomSelectionState;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same push/pop bridge as SectionCompilerMixin, for the Sodium chunk-build path.
 * Only loaded when Sodium is present (see SodiumMixinPlugin).
 */
@Mixin(
   value = {DefaultFluidRenderer.class},
   remap = false
)
public abstract class SodiumDefaultFluidRendererMixin {
   @Unique
   private double bloom$previousFluidStrength;
   @Unique
   private int bloom$previousRadiusProfile;
   @Unique
   private boolean shine$previousRimLightIncluded;
   @Unique
   private int shine$previousShoreFoamEdgeMask;
   @Unique
   private int shine$previousShoreFoamProfile;
   @Unique
   private boolean shine$previousBiomeLavaColor;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bloom$pushFluidStrength(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos modelOffset, TranslucentGeometryCollector collector, ChunkModelBuilder modelBuilder, Material material, ColorProvider<FluidState> colorProvider, FluidModel fluidModel, CallbackInfo ci) {
      this.bloom$previousFluidStrength = BloomSelectionState.pushFluidStrength(BloomSelection.getFluidSourceStrength(fluidState));
      this.bloom$previousRadiusProfile = BloomSelectionState.pushFluidRadiusProfile(BloomSelection.getFluidRadiusProfile(fluidState));
      this.shine$previousRimLightIncluded = BloomSelectionState.pushRimLightIncluded(false);
      int shoreFoamEdges = ShoreFoamWaterMask.edgeMask(level, blockPos, fluidState);
      this.shine$previousShoreFoamEdgeMask = BloomSelectionState.pushShoreFoamEdgeMask(shoreFoamEdges);
      this.shine$previousShoreFoamProfile = BloomSelectionState.pushShoreFoamProfile(shoreFoamEdges == 0 ? 0 : ShoreFoamBiomeProfiles.profileIndex(level, blockPos));
      this.shine$previousBiomeLavaColor = BloomSelectionState.pushBiomeLavaColor(false);
   }

   @Inject(
      method = {"render"},
      at = {@At("RETURN")},
      require = 0
   )
   private void bloom$popFluidStrength(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos modelOffset, TranslucentGeometryCollector collector, ChunkModelBuilder modelBuilder, Material material, ColorProvider<FluidState> colorProvider, FluidModel fluidModel, CallbackInfo ci) {
      BloomSelectionState.popBiomeLavaColor(this.shine$previousBiomeLavaColor);
      BloomSelectionState.popShoreFoamProfile(this.shine$previousShoreFoamProfile);
      BloomSelectionState.popShoreFoamEdgeMask(this.shine$previousShoreFoamEdgeMask);
      BloomSelectionState.popRimLightIncluded(this.shine$previousRimLightIncluded);
      BloomSelectionState.popFluidRadiusProfile(this.bloom$previousRadiusProfile);
      BloomSelectionState.popFluidStrength(this.bloom$previousFluidStrength);
   }
}
