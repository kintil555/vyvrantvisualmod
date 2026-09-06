package com.bloom.client.experimental.render;

import com.bloom.BloomMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

public final class ShoreFoamBiomeProfiles {
   public static final int MAX_PROFILE_INDEX = 127;
   private static final Map<ProfileKey, Integer> PROFILE_INDICES = new HashMap();
   private static final String[] BIOME_IDS = new String[128];
   private static final int[] SOURCE_CODES = new int[128];
   private static final int[] BASE_MATERIALS = new int[128];
   private static long mappingVersion;
   private static boolean loggedOverflow;

   private ShoreFoamBiomeProfiles() {
   }

   public static int profileIndex(BlockAndTintGetter level, BlockPos pos) {
      if (level != null && pos != null) {
         String biomeId = "";
         if (level instanceof LevelReader) {
            LevelReader levelReader = (LevelReader)level;
            biomeId = (String)levelReader.getBiome(pos).unwrapKey().map((key) -> key.identifier().toString()).orElse("");
         }

         if (biomeId.isBlank()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.level != null) {
               biomeId = (String)minecraft.level.getBiome(pos).unwrapKey().map((key) -> key.identifier().toString()).orElse("");
            }
         }

         return profileIndex(biomeId, 0, 0);
      } else {
         return 0;
      }
   }

   public static int profileIndex(String biomeId, int sourceCode, int baseMaterial) {
      if (biomeId != null && !biomeId.isBlank()) {
         ProfileKey key = new ProfileKey(biomeId, sourceCode & 31, baseMaterial & 7);
         synchronized(PROFILE_INDICES) {
            Integer existing = (Integer)PROFILE_INDICES.get(key);
            if (existing != null) {
               return existing;
            } else {
               int next = PROFILE_INDICES.size() + 1;
               if (next > 127) {
                  if (!loggedOverflow) {
                     BloomMod.LOGGER.warn("Shine Shore Foam encountered more than {} biome profiles; additional biomes will use the global foam profile.", 127);
                     loggedOverflow = true;
                  }

                  return 0;
               } else {
                  PROFILE_INDICES.put(key, next);
                  BIOME_IDS[next] = biomeId;
                  SOURCE_CODES[next] = key.sourceCode();
                  BASE_MATERIALS[next] = key.baseMaterial();
                  ++mappingVersion;
                  return next;
               }
            }
         }
      } else {
         return 0;
      }
   }

   public static String biomeId(int profileIndex) {
      if (profileIndex > 0 && profileIndex <= 127) {
         synchronized(PROFILE_INDICES) {
            String biomeId = BIOME_IDS[profileIndex];
            return biomeId == null ? "" : biomeId;
         }
      } else {
         return "";
      }
   }

   public static long mappingVersion() {
      synchronized(PROFILE_INDICES) {
         return mappingVersion;
      }
   }

   public static int sourceCode(int profileIndex) {
      if (profileIndex > 0 && profileIndex <= 127) {
         synchronized(PROFILE_INDICES) {
            return SOURCE_CODES[profileIndex];
         }
      } else {
         return 0;
      }
   }

   public static int baseMaterial(int profileIndex) {
      if (profileIndex > 0 && profileIndex <= 127) {
         synchronized(PROFILE_INDICES) {
            return BASE_MATERIALS[profileIndex];
         }
      } else {
         return 0;
      }
   }

   private static record ProfileKey(String biomeId, int sourceCode, int baseMaterial) {
   }
}
