package com.bloom.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PostPass;

public final class PostPassExecutionGate {
   private static PostPass skippedPass;

   private PostPassExecutionGate() {
   }

   public static void beginSkipping(PostPass pass) {
      RenderSystem.assertOnRenderThread();
      if (pass != null) {
         if (skippedPass != null) {
            throw new IllegalStateException("A Shine post-pass skip is already active");
         } else {
            skippedPass = pass;
         }
      }
   }

   public static void endSkipping(PostPass pass) {
      RenderSystem.assertOnRenderThread();
      if (pass != null) {
         if (skippedPass != pass) {
            throw new IllegalStateException("Mismatched Shine post-pass skip scope");
         } else {
            skippedPass = null;
         }
      }
   }

   public static boolean shouldSkip(PostPass pass) {
      return pass != null && pass == skippedPass;
   }
}
