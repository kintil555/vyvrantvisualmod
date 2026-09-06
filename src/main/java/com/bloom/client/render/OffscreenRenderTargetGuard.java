package com.bloom.client.render;

import com.mojang.blaze3d.systems.RenderSystem;

public final class OffscreenRenderTargetGuard {
   private OffscreenRenderTargetGuard() {
   }

   public static boolean isActive() {
      return isActive(RenderSystem.outputColorTextureOverride != null, RenderSystem.outputDepthTextureOverride != null);
   }

   static boolean isActive(boolean colorTargetOverridden, boolean depthTargetOverridden) {
      return colorTargetOverridden || depthTargetOverridden;
   }
}
