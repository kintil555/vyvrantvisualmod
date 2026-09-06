package com.bloom.client.render;

public final class DynamicTransformCapacity {
   public static final int GUI_ATLAS_SAFE_CAPACITY = 128;
   private static final ThreadLocal<Integer> GUI_ATLAS_ALLOCATION_DEPTH = ThreadLocal.withInitial(() -> 0);

   private DynamicTransformCapacity() {
   }

   public static void beginGuiAtlasAllocation() {
      GUI_ATLAS_ALLOCATION_DEPTH.set((Integer)GUI_ATLAS_ALLOCATION_DEPTH.get() + 1);
   }

   public static void endGuiAtlasAllocation() {
      int depth = (Integer)GUI_ATLAS_ALLOCATION_DEPTH.get() - 1;
      if (depth <= 0) {
         GUI_ATLAS_ALLOCATION_DEPTH.remove();
      } else {
         GUI_ATLAS_ALLOCATION_DEPTH.set(depth);
      }

   }

   public static int reserveForGuiAtlas(int vanillaCapacity) {
      return (Integer)GUI_ATLAS_ALLOCATION_DEPTH.get() > 0 ? Math.max(vanillaCapacity, 128) : vanillaCapacity;
   }
}
