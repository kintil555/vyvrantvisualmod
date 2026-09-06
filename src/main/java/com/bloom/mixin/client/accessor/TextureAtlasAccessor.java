package com.bloom.mixin.client.accessor;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({TextureAtlas.class})
public interface TextureAtlasAccessor {
   @Accessor("texturesByName")
   Map<Identifier, TextureAtlasSprite> bloom$getTexturesByName();

   @Accessor("width")
   int bloom$getWidth();

   @Accessor("height")
   int bloom$getHeight();
}
