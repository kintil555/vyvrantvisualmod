package com.bloom.client.render;

import net.minecraft.client.Minecraft;

public final class LevelRendererRebuilds {
   private LevelRendererRebuilds() {
   }

   public static void requestChunkGeometryRebuild() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null) {
         minecraft.execute(() -> {
            if (minecraft.level != null && minecraft.levelRenderer != null) {
               minecraft.levelRenderer.invalidateCompiledGeometry(minecraft.level, minecraft.options, minecraft.gameRenderer.mainCamera(), minecraft.getBlockColors());
            }

         });
      }
   }
}
