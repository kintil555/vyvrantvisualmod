package com.bloom.client.render;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;

public final class ShineRenderBackend {
   private ShineRenderBackend() {
   }

   public static Type current() {
      return identify(RenderSystem.tryGetDevice());
   }

   public static Type identify(GpuDevice device) {
      if (device == null) {
         return ShineRenderBackend.Type.UNKNOWN;
      } else {
         String className = device.getClass().getName().toLowerCase(Locale.ROOT);
         if (!className.contains(".vulkan.") && !className.contains("vulkandevice")) {
            if (!className.contains(".opengl.") && !className.contains("gldevice")) {
               try {
                  String backendName = device.getDeviceInfo().backendName();
                  if (backendName != null) {
                     String normalized = backendName.toLowerCase(Locale.ROOT);
                     if (normalized.contains("vulkan")) {
                        return ShineRenderBackend.Type.VULKAN;
                     }

                     if (normalized.contains("opengl")) {
                        return ShineRenderBackend.Type.OPENGL;
                     }
                  }
               } catch (RuntimeException var4) {
               }

               return ShineRenderBackend.Type.UNKNOWN;
            } else {
               return ShineRenderBackend.Type.OPENGL;
            }
         } else {
            return ShineRenderBackend.Type.VULKAN;
         }
      }
   }

   public static boolean isOpenGl() {
      return current() == ShineRenderBackend.Type.OPENGL;
   }

   public static boolean isVulkan() {
      return current() == ShineRenderBackend.Type.VULKAN;
   }

   public static boolean canUseRawOpenGl() {
      return isOpenGl();
   }

   public static enum Type {
      OPENGL,
      VULKAN,
      UNKNOWN;

      // $FF: synthetic method
      private static Type[] $values() {
         return new Type[]{OPENGL, VULKAN, UNKNOWN};
      }
   }
}
