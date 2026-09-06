package com.bloom.mixin.client;

import com.bloom.client.config.FixedScaleScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class})
public abstract class FixedScaleScreenRenderMixin {
   @ModifyVariable(
      method = {"init(II)V", "resize(II)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int shine$fixedScreenWidth(int width) {
      if (this instanceof FixedScaleScreen screen) {
         return screen.fixedScreenWidth();
      } else {
         return width;
      }
   }

   @ModifyVariable(
      method = {"init(II)V", "resize(II)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int shine$fixedScreenHeight(int height) {
      if (this instanceof FixedScaleScreen screen) {
         return screen.fixedScreenHeight();
      } else {
         return height;
      }
   }

   @Inject(
      method = {"extractRenderStateWithTooltipAndSubtitles"},
      at = {@At("HEAD")}
   )
   private void shine$beginFixedScaleRender(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (this instanceof FixedScaleScreen screen) {
         screen.beginFixedScaleRender(guiGraphics);
      }

   }

   @Inject(
      method = {"extractRenderStateWithTooltipAndSubtitles"},
      at = {@At("RETURN")}
   )
   private void shine$endFixedScaleRender(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (this instanceof FixedScaleScreen screen) {
         screen.endFixedScaleRender(guiGraphics);
      }

   }

   @ModifyVariable(
      method = {"extractRenderStateWithTooltipAndSubtitles"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int shine$fixedMouseX(int mouseX) {
      if (this instanceof FixedScaleScreen screen) {
         return screen.fixedMouseX(mouseX);
      } else {
         return mouseX;
      }
   }

   @ModifyVariable(
      method = {"extractRenderStateWithTooltipAndSubtitles"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int shine$fixedMouseY(int mouseY) {
      if (this instanceof FixedScaleScreen screen) {
         return screen.fixedMouseY(mouseY);
      } else {
         return mouseY;
      }
   }
}
