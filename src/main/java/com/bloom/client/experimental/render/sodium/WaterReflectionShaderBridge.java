package com.bloom.client.experimental.render.sodium;

/**
 * Minimal stub. The full water-reflection feature (and everything that reads
 * these ready flags) isn't part of this build; {@link
 * com.bloom.client.compat.shader.SodiumShaderSourceTransformer} still calls
 * these hooks while patching Sodium's terrain shaders, so they're kept as
 * no-ops rather than pulling in the whole reflection subsystem.
 */
public final class WaterReflectionShaderBridge {
   private WaterReflectionShaderBridge() {
   }

   public static void markFragmentShaderReady(boolean ready) {
   }

   public static void markVertexShaderReady(boolean ready) {
   }
}
