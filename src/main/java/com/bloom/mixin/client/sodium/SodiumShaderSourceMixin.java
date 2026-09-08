package com.bloom.mixin.client.sodium;

import com.bloom.client.compat.SodiumShaderPatcher;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.IOException;
import java.io.Reader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ShaderManager.class})
public abstract class SodiumShaderSourceMixin {
   @WrapOperation(
      method = {"loadShader"},
      at = {@At(
   value = "INVOKE",
   target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/Reader;)Ljava/lang/String;"
)},
      require = 0
   )
   private static String shine$patchSodiumShaderSource(Reader reader, Operation<String> original, @Local(ordinal = 0,argsOnly = true) Identifier location) throws IOException {
      return SodiumShaderPatcher.patch(location, (String)original.call(new Object[]{reader}));
   }
}
