package com.bloom.client.compat;

import com.bloom.client.compat.shader.SodiumShaderSourceTransformer;
import com.bloom.client.compat.shader.SodiumTerrainShaderInterop;
import net.minecraft.resources.Identifier;

public final class SodiumShaderPatcher {
   private SodiumShaderPatcher() {
   }

   public static String patch(Identifier location, String source) {
      if (location != null && source != null && "sodium".equals(location.getNamespace())) {
         Identifier shaderId = normalizeShaderLocation(location);
         String patched = SodiumShaderSourceTransformer.patch(shaderId, source);
         if ("blocks/block_layer_opaque.fsh".equals(shaderId.getPath())) {
            patched = SodiumTerrainShaderInterop.adaptChunksFadeIn(patched).shader();
         }

         return patched;
      } else {
         return source;
      }
   }

   private static Identifier normalizeShaderLocation(Identifier location) {
      String path = location.getPath();
      return path.startsWith("shaders/") ? Identifier.fromNamespaceAndPath(location.getNamespace(), path.substring("shaders/".length())) : location;
   }
}
