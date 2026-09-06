package com.bloom.client.selection;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;

public final class BloomSelectionState {
   private static final ThreadLocal<Double> BLOCK_STRENGTH = ThreadLocal.withInitial(() -> (double)0.0F);
   private static final ThreadLocal<Double> FLUID_STRENGTH = ThreadLocal.withInitial(() -> (double)0.0F);
   private static final ThreadLocal<Double> FLUID_WATER_CLOUDINESS = ThreadLocal.withInitial(() -> (double)0.0F);
   private static final ThreadLocal<Boolean> FLUID_WATER_CLOUDINESS_ENCODED = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Integer> BLOCK_RADIUS_PROFILE = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Integer> FLUID_RADIUS_PROFILE = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<String> BLOCK_ID = ThreadLocal.withInitial(() -> "");
   private static final ThreadLocal<Boolean> RIM_LIGHT_INCLUDED = ThreadLocal.withInitial(() -> true);
   private static final ThreadLocal<BlockAndTintGetter> TERRAIN_CAUSTICS_LEVEL = new ThreadLocal();
   private static final ThreadLocal<BlockPos> TERRAIN_CAUSTICS_BLOCK_POS = new ThreadLocal();
   private static final ThreadLocal<Boolean> TERRAIN_CAUSTICS_WATER_ADJACENT = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Integer> SHORE_FOAM_EDGE_MASK = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Integer> SHORE_FOAM_PROFILE = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Boolean> BIOME_LAVA_COLOR = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Integer> BLOCK_MOTION_PROFILE = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Integer> BLOCK_MOTION_FACE_CODE = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Boolean> BLOCK_MOTION_BOTTOM_SKIRT = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Integer> GRASS_BLADE_ID = ThreadLocal.withInitial(() -> -1);
   private static final ThreadLocal<BlockAndTintGetter> FLUID_LEVEL = new ThreadLocal();
   private static final ThreadLocal<BlockPos> FLUID_BLOCK_POS = new ThreadLocal();
   private static final ThreadLocal<Integer> FLUID_BASE_WATER_COLOR = ThreadLocal.withInitial(() -> -1);

   private BloomSelectionState() {
   }

   public static double pushBlockStrength(double strength) {
      double previous = (Double)BLOCK_STRENGTH.get();
      BLOCK_STRENGTH.set(strength);
      return previous;
   }

   public static void popBlockStrength(double previous) {
      BLOCK_STRENGTH.set(previous);
   }

   public static double getBlockStrength() {
      return (Double)BLOCK_STRENGTH.get();
   }

   public static int pushBlockRadiusProfile(int profile) {
      int previous = (Integer)BLOCK_RADIUS_PROFILE.get();
      BLOCK_RADIUS_PROFILE.set(Math.max(0, Math.min(2, profile)));
      return previous;
   }

   public static void popBlockRadiusProfile(int previous) {
      BLOCK_RADIUS_PROFILE.set(Math.max(0, Math.min(2, previous)));
   }

   public static int getBlockRadiusProfile() {
      return (Integer)BLOCK_RADIUS_PROFILE.get();
   }

   public static String pushBlockId(String blockId) {
      String previous = (String)BLOCK_ID.get();
      BLOCK_ID.set(blockId == null ? "" : blockId);
      return previous;
   }

   public static void popBlockId(String previous) {
      BLOCK_ID.set(previous == null ? "" : previous);
   }

   public static String getBlockId() {
      return (String)BLOCK_ID.get();
   }

   public static double pushFluidStrength(double strength) {
      double previous = (Double)FLUID_STRENGTH.get();
      FLUID_STRENGTH.set(strength);
      return previous;
   }

   public static void popFluidStrength(double previous) {
      FLUID_STRENGTH.set(previous);
   }

   public static double getFluidStrength() {
      return (Double)FLUID_STRENGTH.get();
   }

   public static double pushFluidWaterCloudiness(double cloudiness) {
      double previous = (Double)FLUID_WATER_CLOUDINESS.get();
      FLUID_WATER_CLOUDINESS.set(Math.max((double)0.0F, Math.min((double)1.0F, cloudiness)));
      return previous;
   }

   public static void popFluidWaterCloudiness(double previous) {
      FLUID_WATER_CLOUDINESS.set(Math.max((double)0.0F, Math.min((double)1.0F, previous)));
   }

   public static double getFluidWaterCloudiness() {
      return (Double)FLUID_WATER_CLOUDINESS.get();
   }

   public static boolean pushFluidWaterCloudinessEncoded(boolean encoded) {
      boolean previous = (Boolean)FLUID_WATER_CLOUDINESS_ENCODED.get();
      FLUID_WATER_CLOUDINESS_ENCODED.set(encoded);
      return previous;
   }

   public static void popFluidWaterCloudinessEncoded(boolean previous) {
      FLUID_WATER_CLOUDINESS_ENCODED.set(previous);
   }

   public static boolean isFluidWaterCloudinessEncoded() {
      return (Boolean)FLUID_WATER_CLOUDINESS_ENCODED.get();
   }

   public static int pushFluidRadiusProfile(int profile) {
      int previous = (Integer)FLUID_RADIUS_PROFILE.get();
      FLUID_RADIUS_PROFILE.set(Math.max(0, Math.min(2, profile)));
      return previous;
   }

   public static void popFluidRadiusProfile(int previous) {
      FLUID_RADIUS_PROFILE.set(Math.max(0, Math.min(2, previous)));
   }

   public static int getFluidRadiusProfile() {
      return (Integer)FLUID_RADIUS_PROFILE.get();
   }

   public static boolean pushRimLightIncluded(boolean included) {
      boolean previous = (Boolean)RIM_LIGHT_INCLUDED.get();
      RIM_LIGHT_INCLUDED.set(included);
      return previous;
   }

   public static void popRimLightIncluded(boolean previous) {
      RIM_LIGHT_INCLUDED.set(previous);
   }

   public static boolean isRimLightIncluded() {
      return (Boolean)RIM_LIGHT_INCLUDED.get();
   }

   public static BlockAndTintGetter pushTerrainCausticsLevel(BlockAndTintGetter level) {
      BlockAndTintGetter previous = (BlockAndTintGetter)TERRAIN_CAUSTICS_LEVEL.get();
      TERRAIN_CAUSTICS_LEVEL.set(level);
      return previous;
   }

   public static void popTerrainCausticsLevel(BlockAndTintGetter previous) {
      if (previous == null) {
         TERRAIN_CAUSTICS_LEVEL.remove();
      } else {
         TERRAIN_CAUSTICS_LEVEL.set(previous);
      }

   }

   public static BlockAndTintGetter getTerrainCausticsLevel() {
      return (BlockAndTintGetter)TERRAIN_CAUSTICS_LEVEL.get();
   }

   public static BlockPos pushTerrainCausticsBlockPos(BlockPos blockPos) {
      BlockPos previous = (BlockPos)TERRAIN_CAUSTICS_BLOCK_POS.get();
      TERRAIN_CAUSTICS_BLOCK_POS.set(blockPos == null ? null : blockPos.immutable());
      return previous;
   }

   public static void popTerrainCausticsBlockPos(BlockPos previous) {
      if (previous == null) {
         TERRAIN_CAUSTICS_BLOCK_POS.remove();
      } else {
         TERRAIN_CAUSTICS_BLOCK_POS.set(previous);
      }

   }

   public static BlockPos getTerrainCausticsBlockPos() {
      return (BlockPos)TERRAIN_CAUSTICS_BLOCK_POS.get();
   }

   public static boolean isTerrainBlockScope(BlockAndTintGetter level, BlockPos blockPos) {
      return level != null && blockPos != null && TERRAIN_CAUSTICS_LEVEL.get() == level && blockPos.equals(TERRAIN_CAUSTICS_BLOCK_POS.get());
   }

   public static boolean pushTerrainCausticsWaterAdjacent(boolean waterAdjacent) {
      boolean previous = (Boolean)TERRAIN_CAUSTICS_WATER_ADJACENT.get();
      TERRAIN_CAUSTICS_WATER_ADJACENT.set(waterAdjacent);
      return previous;
   }

   public static void popTerrainCausticsWaterAdjacent(boolean previous) {
      TERRAIN_CAUSTICS_WATER_ADJACENT.set(previous);
   }

   public static boolean isTerrainCausticsWaterAdjacent() {
      return (Boolean)TERRAIN_CAUSTICS_WATER_ADJACENT.get();
   }

   public static int pushShoreFoamEdgeMask(int edgeMask) {
      int previous = (Integer)SHORE_FOAM_EDGE_MASK.get();
      SHORE_FOAM_EDGE_MASK.set(edgeMask & 15);
      return previous;
   }

   public static void popShoreFoamEdgeMask(int previous) {
      SHORE_FOAM_EDGE_MASK.set(previous & 15);
   }

   public static int getShoreFoamEdgeMask() {
      return (Integer)SHORE_FOAM_EDGE_MASK.get();
   }

   public static int pushShoreFoamProfile(int profile) {
      int previous = (Integer)SHORE_FOAM_PROFILE.get();
      SHORE_FOAM_PROFILE.set(Math.max(0, Math.min(127, profile)));
      return previous;
   }

   public static void popShoreFoamProfile(int previous) {
      SHORE_FOAM_PROFILE.set(Math.max(0, Math.min(127, previous)));
   }

   public static int getShoreFoamProfile() {
      return (Integer)SHORE_FOAM_PROFILE.get();
   }

   public static boolean pushBiomeLavaColor(boolean enabled) {
      boolean previous = (Boolean)BIOME_LAVA_COLOR.get();
      BIOME_LAVA_COLOR.set(enabled);
      return previous;
   }

   public static void popBiomeLavaColor(boolean previous) {
      BIOME_LAVA_COLOR.set(previous);
   }

   public static boolean hasBiomeLavaColor() {
      return (Boolean)BIOME_LAVA_COLOR.get();
   }

   public static int pushBlockMotionProfile(int profile) {
      int previous = (Integer)BLOCK_MOTION_PROFILE.get();
      BLOCK_MOTION_PROFILE.set(profile & 15);
      return previous;
   }

   public static void popBlockMotionProfile(int previous) {
      BLOCK_MOTION_PROFILE.set(previous & 15);
   }

   public static int getBlockMotionProfile() {
      return (Integer)BLOCK_MOTION_PROFILE.get();
   }

   public static int pushBlockMotionFaceCode(int faceCode) {
      int previous = (Integer)BLOCK_MOTION_FACE_CODE.get();
      BLOCK_MOTION_FACE_CODE.set(faceCode & 7);
      return previous;
   }

   public static void popBlockMotionFaceCode(int previous) {
      BLOCK_MOTION_FACE_CODE.set(previous & 7);
   }

   public static int getBlockMotionFaceCode() {
      return (Integer)BLOCK_MOTION_FACE_CODE.get();
   }

   public static boolean pushBlockMotionBottomSkirt(boolean enabled) {
      boolean previous = (Boolean)BLOCK_MOTION_BOTTOM_SKIRT.get();
      BLOCK_MOTION_BOTTOM_SKIRT.set(enabled);
      return previous;
   }

   public static void popBlockMotionBottomSkirt(boolean previous) {
      BLOCK_MOTION_BOTTOM_SKIRT.set(previous);
   }

   public static boolean hasBlockMotionBottomSkirt() {
      return (Boolean)BLOCK_MOTION_BOTTOM_SKIRT.get();
   }

   public static int pushGrassBladeId(int bladeId) {
      int previous = (Integer)GRASS_BLADE_ID.get();
      GRASS_BLADE_ID.set(Math.max(-1, Math.min(31, bladeId)));
      return previous;
   }

   public static void popGrassBladeId(int previous) {
      GRASS_BLADE_ID.set(Math.max(-1, Math.min(31, previous)));
   }

   public static int getGrassBladeId() {
      return (Integer)GRASS_BLADE_ID.get();
   }

   public static BlockAndTintGetter pushFluidLevel(BlockAndTintGetter level) {
      BlockAndTintGetter previous = (BlockAndTintGetter)FLUID_LEVEL.get();
      FLUID_LEVEL.set(level);
      return previous;
   }

   public static void popFluidLevel(BlockAndTintGetter previous) {
      if (previous == null) {
         FLUID_LEVEL.remove();
      } else {
         FLUID_LEVEL.set(previous);
      }

   }

   public static BlockAndTintGetter getFluidLevel() {
      return (BlockAndTintGetter)FLUID_LEVEL.get();
   }

   public static BlockPos pushFluidBlockPos(BlockPos pos) {
      BlockPos previous = (BlockPos)FLUID_BLOCK_POS.get();
      FLUID_BLOCK_POS.set(pos == null ? null : pos.immutable());
      return previous;
   }

   public static void popFluidBlockPos(BlockPos previous) {
      if (previous == null) {
         FLUID_BLOCK_POS.remove();
      } else {
         FLUID_BLOCK_POS.set(previous);
      }

   }

   public static BlockPos getFluidBlockPos() {
      return (BlockPos)FLUID_BLOCK_POS.get();
   }

   public static int pushFluidBaseWaterColor(int color) {
      int previous = (Integer)FLUID_BASE_WATER_COLOR.get();
      FLUID_BASE_WATER_COLOR.set(color < 0 ? -1 : color & 16777215);
      return previous;
   }

   public static void popFluidBaseWaterColor(int previous) {
      if (previous < 0) {
         FLUID_BASE_WATER_COLOR.remove();
      } else {
         FLUID_BASE_WATER_COLOR.set(previous & 16777215);
      }

   }

   public static int getFluidBaseWaterColor() {
      return (Integer)FLUID_BASE_WATER_COLOR.get();
   }
}
