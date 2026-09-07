package com.bloom.mixin.client.sodium;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Only applies the Sodium bridge mixins when Sodium is actually installed - Sodium is an
 * optional dependency (see fabric.mod.json "suggests"), and the vanilla-path mixins
 * (LiquidBlockRendererMixin, SectionCompilerMixin) already cover non-Sodium installs.
 */
public final class SodiumMixinPlugin implements IMixinConfigPlugin {
   private boolean sodiumLoaded;

   public void onLoad(String mixinPackage) {
      this.sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return this.sodiumLoaded;
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
