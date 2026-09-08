package com.bloom.client.compat.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SodiumTerrainShaderInterop {
   public static final String CONTRACT_MARKER = "// SHINE_INTEROP_CONTRACT:terrain_visibility_v2";
   public static final String CONTRACT_DEFINE = "#define SHINE_INTEROP_TERRAIN_VISIBILITY_V2 1";
   public static final String OUTPUT_INITIALIZATION_MARKER = "// SHINE_INTEROP_OUTPUTS:initialized";
   public static final String SCENE_FOG_OWNER_MARKER = "// SHINE_INTEROP_SCENE_FOG_OWNER";
   public static final String CHUNKS_FADE_IN_ADAPTER_MARKER = "// SHINE_INTEROP_ADAPTER:chunksfadein_scene_only";
   private static final String MAIN_SIGNATURE = "void main() {\n";
   private static final String CHUNKS_FADE_IN_SYMBOL = "cfi_FadeFactor";
   private static final String SHINE_SCENE_BRANCH = "if (u_ShineChunksFadeEnabled == 0) {";
   private static final String CHUNKS_FADE_IN_SCENE_BRANCH = "if (u_ShineChunksFadeEnabled == 0 || cfi_FadeFactor < 1.0) {";
   private static final String SODIUM_SCENE_ASSIGNMENT = "fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);";
   private static final Pattern GLSL_SYMBOL = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[xyzwrgba]{1,4})?");
   private static final Pattern OWNER_ID = Pattern.compile("[a-z0-9_.:-]+");
   private static volatile List<VisibilityContribution> registeredContributions = List.of();

   private SodiumTerrainShaderInterop() {
   }

   public static String installVisibilityContract(String shader) {
      if (shader != null && !shader.isEmpty() && !shader.contains("// SHINE_INTEROP_CONTRACT:terrain_visibility_v2")) {
         if (shader.contains("void main() {\n") && shader.contains("bloomColor") && shader.contains("shineRimMaskColor")) {
            String source = shader;
            int versionStart = shader.indexOf("#version");
            if (versionStart >= 0 && !shader.contains("#define SHINE_INTEROP_TERRAIN_VISIBILITY_V2 1")) {
               int versionEnd = shader.indexOf(10, versionStart);
               if (versionEnd >= 0) {
                  source = shader.substring(0, versionEnd + 1) + "#define SHINE_INTEROP_TERRAIN_VISIBILITY_V2 1\n" + shader.substring(versionEnd + 1);
               }
            }

            String contract = "void main() {\n    // SHINE_INTEROP_CONTRACT:terrain_visibility_v2\n    // SHINE_INTEROP_CHANNEL:scene_visibility\n    float shine_scene_visibility = 1.0;\n    // SHINE_INTEROP_BINDINGS:scene_visibility\n    // SHINE_INTEROP_CHANNEL:bloom_source_visibility\n    float shine_bloom_source_visibility = 1.0;\n    // SHINE_INTEROP_BINDINGS:bloom_source_visibility\n    // SHINE_INTEROP_CHANNEL:rim_mask_visibility\n    float shine_rim_mask_visibility = 1.0;\n    // SHINE_INTEROP_BINDINGS:rim_mask_visibility\n    // SHINE_INTEROP_OUTPUTS:initialized\n    bloomColor = vec4(0.0);\n    shineRimMaskColor = vec4(0.0);\n";
            return source.replace("void main() {\n", contract);
         } else {
            return shader;
         }
      } else {
         return shader;
      }
   }

   public static String multiplyVisibilityBySymbol(String shader, VisibilityChannel channel, String glslSymbol, String ownerId) {
      return multiplyVisibilityBySymbol(shader, channel, glslSymbol, ownerId, "default");
   }

   public static String multiplyVisibilityBySymbol(String shader, VisibilityChannel channel, String glslSymbol, String ownerId, String contributionId) {
      Objects.requireNonNull(channel, "channel");
      String symbol = ((String)Objects.requireNonNull(glslSymbol, "glslSymbol")).trim();
      String owner = ((String)Objects.requireNonNull(ownerId, "ownerId")).trim().toLowerCase(Locale.ROOT);
      String contribution = ((String)Objects.requireNonNull(contributionId, "contributionId")).trim().toLowerCase(Locale.ROOT);
      if (!GLSL_SYMBOL.matcher(symbol).matches()) {
         throw new IllegalArgumentException("Visibility input must be a GLSL symbol or swizzle: " + symbol);
      } else if (!OWNER_ID.matcher(owner).matches()) {
         throw new IllegalArgumentException("Invalid visibility integration owner: " + ownerId);
      } else if (!OWNER_ID.matcher(contribution).matches()) {
         throw new IllegalArgumentException("Invalid visibility contribution id: " + contributionId);
      } else if (shader != null && hasVisibilityContract(shader)) {
         String bindingMarker = "// SHINE_INTEROP_BINDING:" + owner + ":" + contribution + ":" + channel.id();
         if (shader.contains(bindingMarker)) {
            return shader;
         } else {
            String insertionPoint = channel.bindingsMarker();
            if (!shader.contains(insertionPoint)) {
               return shader;
            } else {
               String binding = bindingMarker + "\n    " + channel.variable() + " *= clamp(" + symbol + ", 0.0, 1.0);\n    " + insertionPoint;
               return shader.replace(insertionPoint, binding);
            }
         }
      } else {
         return shader;
      }
   }

   public static String applyContributions(String shader, Iterable<VisibilityContribution> contributions) {
      String patched = installVisibilityContract(shader);
      if (patched != null && contributions != null && hasVisibilityContract(patched)) {
         List<VisibilityContribution> ordered = new ArrayList();

         for(VisibilityContribution contribution : contributions) {
            if (contribution != null) {
               ordered.add(contribution);
            }
         }

         ordered.sort(Comparator.comparing(VisibilityContribution::ownerId).thenComparing(VisibilityContribution::contributionId).thenComparingInt((contributionx) -> contributionx.channel().ordinal()).thenComparing(VisibilityContribution::glslSymbol));

         for(VisibilityContribution contribution : ordered) {
            if (contribution.requiredSourceMarker().isEmpty() || patched.contains(contribution.requiredSourceMarker())) {
               patched = multiplyVisibilityBySymbol(patched, contribution.channel(), contribution.glslSymbol(), contribution.ownerId(), contribution.contributionId());
            }
         }

         return patched;
      } else {
         return patched;
      }
   }

   public static synchronized boolean registerVisibilityContribution(VisibilityContribution contribution) {
      Objects.requireNonNull(contribution, "contribution");

      for(VisibilityContribution current : registeredContributions) {
         if (current.ownerId().equals(contribution.ownerId()) && current.contributionId().equals(contribution.contributionId())) {
            if (current.equals(contribution)) {
               return false;
            }

            String var10002 = contribution.ownerId();
            throw new IllegalStateException("Visibility owner " + var10002 + " already registered contribution " + contribution.contributionId());
         }
      }

      List<VisibilityContribution> updated = new ArrayList(registeredContributions);
      updated.add(contribution);
      updated.sort(Comparator.comparing(VisibilityContribution::ownerId).thenComparing(VisibilityContribution::contributionId).thenComparingInt((value) -> value.channel().ordinal()).thenComparing(VisibilityContribution::glslSymbol));
      registeredContributions = List.copyOf(updated);
      return true;
   }

   public static String applyRegisteredContributions(String shader) {
      return applyContributions(shader, registeredContributions);
   }

   public static AdaptationResult adaptChunksFadeIn(String shader) {
      if (shader == null) {
         return new AdaptationResult((String)null, false, false, false, false);
      } else {
         String normalized = shader.replace("\r\n", "\n");
         if (normalized.contains("bloomColor") && normalized.contains("shine_decode_source_strength")) {
            if (!hasVisibilityContract(normalized)) {
               String legacy = applyLegacyChunksFadeInIsolation(normalized);
               return new AdaptationResult(legacy, !legacy.equals(normalized), false, false, true);
            } else if (!normalized.contains("cfi_FadeFactor")) {
               return new AdaptationResult(normalized, false, true, false, false);
            } else {
               String patched = multiplyVisibilityBySymbol(normalized, SodiumTerrainShaderInterop.VisibilityChannel.SCENE, "cfi_FadeFactor", "chunksfadein");
               if (patched.contains("// SHINE_INTEROP_SCENE_FOG_OWNER")) {
                  patched = patched.replace("if (u_ShineChunksFadeEnabled == 0) {", "if (u_ShineChunksFadeEnabled == 0 || cfi_FadeFactor < 1.0) {");
               }

               if (!patched.contains("// SHINE_INTEROP_ADAPTER:chunksfadein_scene_only")) {
                  patched = patched.replace("// SHINE_INTEROP_CONTRACT:terrain_visibility_v2", "// SHINE_INTEROP_CONTRACT:terrain_visibility_v2\n    // SHINE_INTEROP_ADAPTER:chunksfadein_scene_only");
               }

               boolean chunksFadeInComposedScene = !normalized.contains("fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);") && (normalized.contains("cfi_sky") || normalized.contains("_cfi_") || normalized.contains("cfi_FadeFactor < 1.0"));
               boolean sceneHookApplied = patched.contains("SHINE_INTEROP_BINDING:chunksfadein:default:scene_visibility") && patched.contains("if (u_ShineChunksFadeEnabled == 0 || cfi_FadeFactor < 1.0) {") && chunksFadeInComposedScene;
               return new AdaptationResult(patched, !patched.equals(normalized), true, sceneHookApplied, false);
            }
         } else {
            return new AdaptationResult(normalized, false, false, false, false);
         }
      }
   }

   public static boolean hasVisibilityContract(String shader) {
      if (shader != null && shader.contains("// SHINE_INTEROP_CONTRACT:terrain_visibility_v2") && shader.contains("#define SHINE_INTEROP_TERRAIN_VISIBILITY_V2 1") && shader.contains("// SHINE_INTEROP_OUTPUTS:initialized")) {
         for(VisibilityChannel channel : SodiumTerrainShaderInterop.VisibilityChannel.values()) {
            if (!shader.contains(channel.marker()) || !shader.contains(channel.bindingsMarker()) || !shader.contains(channel.variable())) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean hasDeterministicAuxiliaryOutputs(String shader) {
      if (!hasVisibilityContract(shader)) {
         return false;
      } else {
         int main = shader.indexOf("void main() {\n");
         int initialization = shader.indexOf("// SHINE_INTEROP_OUTPUTS:initialized", main);
         int firstReturn = shader.indexOf("return;", main);
         return initialization >= main && (firstReturn < 0 || initialization < firstReturn);
      }
   }

   private static String applyLegacyChunksFadeInIsolation(String shader) {
      String patched = shader.replace("float fogValue = max(1.0 - fadeFactor, total_fog_value(v_FragDistance.y, v_FragDistance.x, u_EnvironmentFog.x, u_EnvironmentFog.y, u_RenderFog.x, u_RenderFog.y));", "float fogValue = total_fog_value(v_FragDistance.y, v_FragDistance.x, u_EnvironmentFog.x, u_EnvironmentFog.y, u_RenderFog.x, u_RenderFog.y);");
      patched = patched.replace("vec4 shineFoggedSource = _linearFog(shineSource, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);", "vec4 shineFoggedSource = _linearFog(shineSource, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, 1.0);");
      if (!patched.contains("SHINE_CFI_BLOOM_GUARD") && patched.contains("void main() {\n")) {
         patched = patched.replace("void main() {\n", "void main() {\n    // SHINE_CFI_BLOOM_GUARD (legacy fallback)\n    bloomColor = vec4(0.0);\n    shineRimMaskColor = vec4(0.0);\n");
      }

      return patched;
   }

   public static enum VisibilityChannel {
      SCENE("scene_visibility", "shine_scene_visibility"),
      BLOOM_SOURCE("bloom_source_visibility", "shine_bloom_source_visibility"),
      RIM_MASK("rim_mask_visibility", "shine_rim_mask_visibility");

      private final String id;
      private final String variable;

      private VisibilityChannel(String id, String variable) {
         this.id = id;
         this.variable = variable;
      }

      public String id() {
         return this.id;
      }

      public String variable() {
         return this.variable;
      }

      public String marker() {
         return "// SHINE_INTEROP_CHANNEL:" + this.id;
      }

      private String bindingsMarker() {
         return "// SHINE_INTEROP_BINDINGS:" + this.id;
      }

      // $FF: synthetic method
      private static VisibilityChannel[] $values() {
         return new VisibilityChannel[]{SCENE, BLOOM_SOURCE, RIM_MASK};
      }
   }

   public static record AdaptationResult(String shader, boolean changed, boolean semanticContract, boolean sceneHookApplied, boolean legacyFallback) {
   }

   public static record VisibilityContribution(String ownerId, String contributionId, VisibilityChannel channel, String glslSymbol, String requiredSourceMarker) {
      public VisibilityContribution(String ownerId, VisibilityChannel channel, String glslSymbol) {
         this(ownerId, "default", channel, glslSymbol, "");
      }

      public VisibilityContribution(String ownerId, String contributionId, VisibilityChannel channel, String glslSymbol, String requiredSourceMarker) {
         ownerId = ((String)Objects.requireNonNull(ownerId, "ownerId")).trim().toLowerCase(Locale.ROOT);
         contributionId = ((String)Objects.requireNonNull(contributionId, "contributionId")).trim().toLowerCase(Locale.ROOT);
         channel = (VisibilityChannel)Objects.requireNonNull(channel, "channel");
         glslSymbol = ((String)Objects.requireNonNull(glslSymbol, "glslSymbol")).trim();
         requiredSourceMarker = requiredSourceMarker == null ? "" : requiredSourceMarker;
         if (!SodiumTerrainShaderInterop.OWNER_ID.matcher(ownerId).matches()) {
            throw new IllegalArgumentException("Invalid visibility integration owner: " + ownerId);
         } else if (!SodiumTerrainShaderInterop.OWNER_ID.matcher(contributionId).matches()) {
            throw new IllegalArgumentException("Invalid visibility contribution id: " + contributionId);
         } else if (!SodiumTerrainShaderInterop.GLSL_SYMBOL.matcher(glslSymbol).matches()) {
            throw new IllegalArgumentException("Visibility input must be a GLSL symbol or swizzle: " + glslSymbol);
         } else {
            this.ownerId = ownerId;
            this.contributionId = contributionId;
            this.channel = channel;
            this.glslSymbol = glslSymbol;
            this.requiredSourceMarker = requiredSourceMarker;
         }
      }
   }
}
