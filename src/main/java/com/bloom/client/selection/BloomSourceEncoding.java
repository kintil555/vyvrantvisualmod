package com.bloom.client.selection;

public final class BloomSourceEncoding {
   private static final int LEGACY_BITS_PER_AXIS = 3;
   private static final int LEGACY_AXIS_MASK = 7;
   private static final int LEGACY_MAX = 63;
   private static final int LOW_NIBBLE_MASK = 15;
   private static final int SKY_LOW_NIBBLE_SHIFT = 16;
   private static final int SKY_EXTRA_MODE_SHIFT = 1;
   private static final int EXTENDED_MODE_MAX = 3;
   private static final int EXTENDED_STEPS_PER_MODE = 64;
   private static final int EXTENDED_TOTAL_STEPS = 192;
   private static final int MATERIAL_BASE_FLAGS_MASK = 7;
   private static final int MATERIAL_SOURCE_CODE_SHIFT = 3;
   private static final int MATERIAL_SOURCE_CODE_MASK = 31;
   private static final int MATERIAL_SOURCE_BITS_MASK = 248;
   private static final int RIM_LIGHT_INCLUDED_MASK = 256;
   private static final int MATERIAL_BLOCK_MOTION_BOTTOM_SKIRT_MASK = 512;
   private static final int MATERIAL_BLOCK_MOTION_FACE_SHIFT = 10;
   private static final int MATERIAL_BLOCK_MOTION_FACE_MASK = 7168;
   private static final int MATERIAL_SHORE_FOAM_PROFILE_MARKER = 128;
   private static final int MATERIAL_SHORE_FOAM_PROFILE_MASK = 127;
   private static final int SODIUM_PACKED_LIGHT_RIM_LIGHT_INCLUDED_MASK = 1;
   private static final int SODIUM_PACKED_LIGHT_RADIUS_PROFILE_MASK = 8;
   private static final int SODIUM_PACKED_LIGHT_TERRAIN_CAUSTICS_WATER_MASK = 2;
   private static final int SODIUM_PACKED_LIGHT_SHORE_FOAM_EDGE_LOW_SHIFT = 2;
   private static final int SODIUM_PACKED_LIGHT_SHORE_FOAM_EDGE_LOW_MASK = 4;
   private static final int SODIUM_PACKED_LIGHT_SHORE_FOAM_EDGE_HIGH_SHIFT = 16;
   private static final int SODIUM_PACKED_LIGHT_SHORE_FOAM_EDGE_HIGH_MASK = 458752;
   private static final int SODIUM_LIGHT_BYTE_MASK = 255;
   private static final int SODIUM_LIGHT_FLAG_MASK = 7;
   private static final int SODIUM_LIGHT_MAX_RAW = 240;
   private static final int SODIUM_LIGHT_FLAGGED_MAX_RAW = 232;
   private static final int SODIUM_PACKED_LIGHT_BYTES_MASK = 16711935;
   private static final int TERRAIN_CAUSTICS_WATER_MASK = 512;
   private static final int SHORE_FOAM_EDGE_SHIFT = 10;
   private static final int SHORE_FOAM_EDGE_MASK = 15360;
   private static final int SHORE_FOAM_PROFILE_SHIFT = 24;
   private static final int SHORE_FOAM_PROFILE_MASK = 2130706432;
   private static final int BIOME_LAVA_COLOR_MASK = 16384;
   private static final int RADIUS_PROFILE_LOW_MASK = 32768;
   private static final int RADIUS_PROFILE_HIGH_MASK = Integer.MIN_VALUE;
   private static final int GRASS_BLADE_ID_SHIFT = 24;
   private static final int GRASS_BLADE_ID_MASK = 520093696;
   private static final int LEGACY_SOURCE_CODE_MAX = 20;
   private static final int LEGACY_SOURCE_STEP = 5;
   private static final int EXTENDED_SOURCE_CODE_COUNT = 11;
   private static final double EXTENDED_SOURCE_STEP = 36.36363636363637;

   private BloomSourceEncoding() {
   }

   public static int encodePackedLight(int packedLight, double sourceStrength) {
      double clamped = Math.max((double)0.0F, Math.min((double)500.0F, sourceStrength));
      int low6;
      int mode;
      if (clamped <= (double)100.0F) {
         low6 = (int)Math.round(clamped / (double)100.0F * (double)63.0F);
         mode = 0;
      } else {
         double t = (clamped - (double)100.0F) / (double)400.0F;
         int code = 1 + (int)Math.ceil(t * (double)191.0F);
         mode = (code - 1) / 64 + 1;
         low6 = (code - 1) % 64;
      }

      int blockNibble = low6 & 7 | (mode & 1) << 3;
      int skyNibble = low6 >>> 3 & 7 | (mode >>> 1 & 1) << 3;
      int cleared = packedLight & -16 & -983041;
      return cleared | blockNibble | skyNibble << 16;
   }

   public static boolean hasPackedLightSourceStrength(int packedLight) {
      return (packedLight & 15) != 0 || (packedLight >>> 16 & 15) != 0;
   }

   public static int encodeRimLightIncluded(int packedLight, boolean included) {
      return included ? packedLight | 256 : packedLight & -257;
   }

   public static int encodeTerrainCausticsWater(int packedLight, boolean waterAdjacent) {
      return waterAdjacent ? packedLight | 512 : packedLight & -513;
   }

   public static int encodeShoreFoamEdges(int packedLight, int edgeMask) {
      return packedLight & -15361 | (edgeMask & 15) << 10;
   }

   public static int encodeShoreFoamProfile(int packedLight, int profile) {
      return packedLight & -2130706433 | (profile & 127) << 24;
   }

   public static int encodeBiomeLavaColor(int packedLight, boolean enabled) {
      return enabled ? packedLight | 16384 : packedLight & -16385;
   }

   public static int encodeBlockMotionProfile(int packedLight, int profile) {
      int encodedProfile = profile & 15;
      return encodedProfile == 0 ? packedLight : packedLight & -513 & -15361 | encodedProfile << 10;
   }

   public static int encodeRadiusProfile(int packedLight, int profile) {
      int value = Math.max(0, Math.min(2, profile));
      int encoded = packedLight & -32769 & Integer.MAX_VALUE;
      if ((value & 1) != 0) {
         encoded |= 32768;
      }

      if ((value & 2) != 0) {
         encoded |= Integer.MIN_VALUE;
      }

      return encoded;
   }

   public static int encodeGrassBladeId(int packedLight, int bladeId) {
      return packedLight & -520093697 | (bladeId & 31) << 24;
   }

   public static int encodeSodiumPackedLightGrassBladeId(int packedLight, int bladeId) {
      int id = bladeId & 31;
      packedLight = encodeSodiumPackedLightLowFlags(packedLight, 6, (id & 3) << 1);
      return encodeSodiumPackedLightHighFlags(packedLight, 458752, (id >>> 2 & 7) << 16);
   }

   public static int encodeMaterialBits(int materialBits, double sourceStrength) {
      int sourceCode = materialSourceCode(sourceStrength);
      int preservedFlags = materialBits & 7;
      int clearedSourceBits = materialBits & -249 & -8;
      return clearedSourceBits | preservedFlags | sourceCode << 3;
   }

   public static int materialSourceCode(double sourceStrength) {
      double clamped = Math.max((double)0.0F, Math.min((double)500.0F, sourceStrength));
      if (clamped <= (double)100.0F) {
         int sourceCode = (int)Math.round(clamped / (double)5.0F);
         return Math.max(0, Math.min(20, sourceCode));
      } else {
         int sourceCode = 20 + (int)Math.round((clamped - (double)100.0F) / 36.36363636363637);
         return Math.max(20, Math.min(31, sourceCode));
      }
   }

   public static int materialSourceCode(int materialBits) {
      return materialBits >>> 3 & 31;
   }

   public static int baseMaterialBits(int materialBits) {
      return materialBits & 7;
   }

   public static boolean hasMaterialSourceStrength(int materialBits) {
      return (materialBits & 248) != 0;
   }

   public static int encodeMaterialBlockMotionFace(int materialBits, int faceCode) {
      return materialBits & -7169 | (faceCode & 7) << 10;
   }

   public static int encodeMaterialBlockMotionBottomSkirt(int materialBits, boolean enabled) {
      return enabled ? materialBits | 512 : materialBits & -513;
   }

   public static int encodeMaterialShoreFoamProfile(int materialBits, int profile) {
      return profile <= 0 ? materialBits : 128 | profile & 127;
   }

   public static int encodeMaterialRadiusProfile(int materialBits, int profile) {
      return materialBits;
   }

   public static int encodeSodiumPackedLightRadiusProfile(int packedLight, int profile) {
      int value = Math.max(0, Math.min(2, profile));
      packedLight = encodeSodiumPackedLightProfileBit(packedLight, 0, value & 1);
      return encodeSodiumPackedLightProfileBit(packedLight, 16, value >>> 1 & 1);
   }

   public static int encodeSodiumPackedLightRimLightIncluded(int packedLight, boolean included) {
      return encodeSodiumPackedLightLowFlags(packedLight, 1, included ? 1 : 0);
   }

   public static int encodeSodiumPackedLightTerrainCausticsWater(int packedLight, boolean waterAdjacent) {
      return encodeSodiumPackedLightLowFlags(packedLight, 2, waterAdjacent ? 2 : 0);
   }

   public static int encodeSodiumPackedLightShoreFoamEdges(int packedLight, int edgeMask) {
      int edge = edgeMask & 15;
      packedLight = encodeSodiumPackedLightLowFlags(packedLight, 4, (edge & 1) << 2);
      return encodeSodiumPackedLightHighFlags(packedLight, 458752, (edge >>> 1 & 7) << 16);
   }

   public static int encodeSodiumPackedLightBlockMotionProfile(int packedLight, int profile) {
      int encodedProfile = profile & 15;
      if (encodedProfile == 0) {
         return packedLight;
      } else {
         packedLight = encodeSodiumPackedLightTerrainCausticsWater(packedLight, false);
         return encodeSodiumPackedLightShoreFoamEdges(packedLight, encodedProfile);
      }
   }

   public static int encodeSodiumPackedLightDefaultChunkMetadata(int packedLight) {
      int blockBaseLight = packedLight & 240;
      if (blockBaseLight >= 240) {
         blockBaseLight = 224;
      }

      int skyBaseLight = packedLight >>> 16 & 240;
      int cleared = packedLight & -16711936;
      return cleared | blockBaseLight | 1 | skyBaseLight << 16;
   }

   public static int encodeSodiumPackedLightDefaultTranslucentMetadata(int packedLight) {
      int blockBaseLight = packedLight & 255 & -8;
      if (blockBaseLight >= 240) {
         blockBaseLight = 232;
      }

      int skyBaseLight = packedLight >>> 16 & 255 & -8;
      int cleared = packedLight & -16711936;
      return cleared | blockBaseLight | 1 | skyBaseLight << 16;
   }

   public static int encodeMaterialDefaultMetadata(int materialBits) {
      return materialBits & -7929;
   }

   private static int encodeSodiumPackedLightLowFlags(int packedLight, int flagMask, int flagBits) {
      return encodeSodiumPackedLightFlags(packedLight, 0, flagMask, flagBits);
   }

   private static int encodeSodiumPackedLightHighFlags(int packedLight, int flagMask, int flagBits) {
      return encodeSodiumPackedLightFlags(packedLight, 16, flagMask, flagBits);
   }

   private static int encodeSodiumPackedLightProfileBit(int packedLight, int shift, int profileBit) {
      int byteMask = 255 << shift;
      int rawLight = packedLight >>> shift & 255;
      int lowFlags = rawLight & 7;
      int baseLight = rawLight & 240;
      if ((profileBit != 0 || lowFlags != 0) && baseLight >= 240) {
         baseLight = 224;
      }

      int encodedRaw = baseLight | lowFlags | (profileBit & 1) * 8;
      return packedLight & ~byteMask | encodedRaw << shift;
   }

   private static int encodeSodiumPackedLightFlags(int packedLight, int shift, int flagMask, int flagBits) {
      int localFlagMask = flagMask >>> shift & 7;
      int localFlagBits = flagBits >>> shift & localFlagMask;
      int byteMask = 255 << shift;
      int rawLight = packedLight >>> shift & 255;
      int existingFlags = rawLight & 7;
      int mergedFlags = existingFlags & ~localFlagMask | localFlagBits;
      int baseLight = rawLight & -8;
      if (mergedFlags != 0 && baseLight >= 240) {
         baseLight = 232;
      }

      return packedLight & ~byteMask | (baseLight | mergedFlags) << shift;
   }

   public static int decodeLegacy6Bit(int blockNibble, int skyNibble) {
      return blockNibble & 7 | (skyNibble & 7) << 3;
   }

   public static int decodeExtendedMode(int blockNibble, int skyNibble) {
      return blockNibble >>> 3 & 1 | (skyNibble >>> 3 & 1) << 1;
   }

   public static int legacyMax() {
      return 63;
   }

   public static int extendedStepsPerMode() {
      return 64;
   }

   public static int extendedTotalSteps() {
      return 192;
   }
}
