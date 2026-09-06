package com.bloom.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class BloomEntityTextureCatalog {
   private static final Set<String> OBSERVED_TEXTURES = new LinkedHashSet();
   private static final List<String> CACHED_TEXTURES = new ArrayList();
   private static boolean dirty = true;

   private BloomEntityTextureCatalog() {
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void recordEntityTexture(Identifier textureId) {
      if (textureId != null && isEntityTexture(textureId)) {
         if (OBSERVED_TEXTURES.add(textureId.toString())) {
            dirty = true;
         }

      }
   }

   public static List<String> entityTextures() {
      if (dirty) {
         rebuild();
      }

      return Collections.unmodifiableList(CACHED_TEXTURES);
   }

   public static boolean isEntityTexture(Identifier id) {
      if (id == null) {
         return false;
      } else {
         String path = id.getPath();
         return path.startsWith("textures/entity/") && path.endsWith(".png");
      }
   }

   private static void rebuild() {
      CACHED_TEXTURES.clear();
      CACHED_TEXTURES.addAll(OBSERVED_TEXTURES);
      Minecraft minecraft = Minecraft.getInstance();
      ResourceManager manager = minecraft == null ? null : minecraft.getResourceManager();
      if (manager != null) {
         manager.listResources("textures/entity", (id) -> id.getPath().endsWith(".png")).keySet().stream().map(Identifier::toString).sorted().forEach((id) -> {
            if (!CACHED_TEXTURES.contains(id)) {
               CACHED_TEXTURES.add(id);
            }

         });
      }

      CACHED_TEXTURES.sort(String::compareTo);
      dirty = false;
   }
}
