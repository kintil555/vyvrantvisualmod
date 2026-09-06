package com.bloom.client.render;

import com.bloom.BloomMod;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

final class OpenGlBloomFramebufferBridge {
   private static final int BLOOM_ATTACHMENT = 36065;
   private static final int[] MRT_DRAW_BUFFERS = new int[]{36064, 36065};
   private static Field gpuDeviceBackendField;
   private static Method glDeviceDirectStateAccessMethod;
   private static int framebuffer;
   private static GpuTexture framebufferColor;
   private static GpuTexture framebufferDepth;
   private static GpuTexture framebufferBloom;
   private static boolean loggedBackendFailure;
   private static boolean loggedAttachmentFailure;

   private OpenGlBloomFramebufferBridge() {
   }

   static boolean enable(RenderTarget target, GpuTexture bloomTexture) {
      if (ShineRenderBackend.canUseRawOpenGl() && target != null && bloomTexture != null) {
         DirectStateAccess directStateAccess = resolveDirectStateAccess(RenderSystem.getDevice());
         if (directStateAccess != null) {
            GpuTexture depthTexture = target.getColorTexture();
            if (depthTexture instanceof GlTexture) {
               GlTexture colorTexture = (GlTexture)depthTexture;
               if (bloomTexture instanceof GlTexture) {
                  GlTexture glBloomTexture = (GlTexture)bloomTexture;
                  depthTexture = target.getDepthTexture();
                  if (depthTexture != null && !(depthTexture instanceof GlTexture)) {
                     return false;
                  }

                  if (framebuffer == 0 || framebufferColor != target.getColorTexture() || framebufferDepth != depthTexture || framebufferBloom != bloomTexture) {
                     reset();
                     framebuffer = directStateAccess.createFrameBufferObject();
                     framebufferColor = target.getColorTexture();
                     framebufferDepth = depthTexture;
                     framebufferBloom = bloomTexture;
                  }

                  int var10000;
                  if (depthTexture instanceof GlTexture) {
                     GlTexture glDepthTexture = (GlTexture)depthTexture;
                     var10000 = glDepthTexture.glId();
                  } else {
                     var10000 = 0;
                  }

                  int depthId = var10000;
                  directStateAccess.bindFrameBufferTextures(framebuffer, new int[]{colorTexture.glId(), glBloomTexture.glId()}, new int[]{0, 0}, depthId, 0, 36160);
                  int status = GL30.glCheckFramebufferStatus(36160);
                  if (status != 36053) {
                     if (!loggedAttachmentFailure) {
                        BloomMod.LOGGER.warn("Shine bloom attachment framebuffer is incomplete: {}", Integer.toHexString(status));
                        loggedAttachmentFailure = true;
                     }

                     disable();
                     return false;
                  }

                  GL20.glDrawBuffers(MRT_DRAW_BUFFERS);
                  GL30.glColorMaski(1, true, true, true, true);
                  GL30.glDisablei(3042, 1);
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static void disable() {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         GL20.glDrawBuffers(36064);
      }

   }

   static void reset() {
      if (framebuffer != 0) {
         GlStateManager._glDeleteFramebuffers(framebuffer);
         framebuffer = 0;
      }

      framebufferColor = null;
      framebufferDepth = null;
      framebufferBloom = null;
      loggedAttachmentFailure = false;
   }

   private static DirectStateAccess resolveDirectStateAccess(GpuDevice device) {
      try {
         if (gpuDeviceBackendField == null) {
            gpuDeviceBackendField = GpuDevice.class.getDeclaredField("backend");
            gpuDeviceBackendField.setAccessible(true);
         }

         Object backend = gpuDeviceBackendField.get(device);
         if (backend == null) {
            return null;
         } else {
            if (glDeviceDirectStateAccessMethod == null || glDeviceDirectStateAccessMethod.getDeclaringClass() != backend.getClass()) {
               glDeviceDirectStateAccessMethod = backend.getClass().getMethod("directStateAccess");
               glDeviceDirectStateAccessMethod.setAccessible(true);
            }

            Object directStateAccess = glDeviceDirectStateAccessMethod.invoke(backend);
            DirectStateAccess var10000;
            if (directStateAccess instanceof DirectStateAccess) {
               DirectStateAccess casted = (DirectStateAccess)directStateAccess;
               var10000 = casted;
            } else {
               var10000 = null;
            }

            return var10000;
         }
      } catch (RuntimeException | ReflectiveOperationException e) {
         if (!loggedBackendFailure) {
            BloomMod.LOGGER.warn("Shine failed to access the OpenGL backend for the legacy bloom framebuffer bridge.", e);
            loggedBackendFailure = true;
         }

         return null;
      }
   }
}
