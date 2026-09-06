package com.bloom.client.experimental.render;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class ShoreFoamWaterMask {
   public static final int NORTH = 1;
   public static final int EAST = 2;
   public static final int SOUTH = 4;
   public static final int WEST = 8;

   private ShoreFoamWaterMask() {
   }

   public static int edgeMask(BlockAndTintGetter level, BlockPos waterPos, FluidState fluidState) {
      if (level != null && waterPos != null && isWater(fluidState) && fluidState.isSource()) {
         if (isWater(level.getFluidState(waterPos.above()))) {
            return 0;
         } else {
            int mask = 0;
            mask |= contactMask(level, waterPos, Direction.NORTH, 1);
            mask |= contactMask(level, waterPos, Direction.EAST, 2);
            mask |= contactMask(level, waterPos, Direction.SOUTH, 4);
            mask |= contactMask(level, waterPos, Direction.WEST, 8);
            return mask;
         }
      } else {
         return 0;
      }
   }

   private static int contactMask(BlockAndTintGetter level, BlockPos waterPos, Direction direction, int bit) {
      BlockPos neighborPos = waterPos.relative(direction);
      if (isWater(level.getFluidState(neighborPos))) {
         return 0;
      } else {
         BlockState neighbor = level.getBlockState(neighborPos);
         if (isContactBlock(neighbor)) {
            return bit;
         } else {
            BlockState lowerNeighbor = level.getBlockState(neighborPos.below());
            return isContactBlock(lowerNeighbor) ? bit : 0;
         }
      }
   }

   private static boolean isContactBlock(BlockState state) {
      if (state != null && !state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) {
         return !isWater(state.getFluidState()) && state.canOcclude();
      } else {
         return false;
      }
   }

   private static boolean isWater(FluidState fluidState) {
      return fluidState != null && fluidState.is(FluidTags.WATER);
   }
}
