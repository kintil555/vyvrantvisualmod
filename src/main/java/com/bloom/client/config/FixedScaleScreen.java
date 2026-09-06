package com.bloom.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class FixedScaleScreen extends Screen {
   private static final int FIXED_GUI_SCALE = 3;
   private static final int REFERENCE_WINDOW_WIDTH = 2560;
   private static final int REFERENCE_WINDOW_HEIGHT = 1440;
   private static final int REFERENCE_GUI_WIDTH = 854;
   private static final int REFERENCE_GUI_HEIGHT = 480;

   protected FixedScaleScreen(Component title) {
      super(title);
   }

   public final void applyFixedScaleDimensions() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getWindow() != null) {
         this.width = this.fixedScreenWidth();
         this.height = this.fixedScreenHeight();
      }
   }

   public final void beginFixedScaleRender(GuiGraphicsExtractor guiGraphics) {
      this.applyFixedScaleDimensions();
      float scale = (float)this.fixedRenderScale();
      guiGraphics.pose().pushMatrix();
      guiGraphics.pose().scale(scale, scale);
   }

   public final void endFixedScaleRender(GuiGraphicsExtractor guiGraphics) {
      guiGraphics.pose().popMatrix();
   }

   public final int fixedMouseX(int mouseX) {
      return (int)Math.floor((double)mouseX / this.fixedRenderScale());
   }

   public final int fixedMouseY(int mouseY) {
      return (int)Math.floor((double)mouseY / this.fixedRenderScale());
   }

   public final double fixedMouseX(double mouseX) {
      return mouseX / this.fixedRenderScale();
   }

   public final double fixedMouseY(double mouseY) {
      return mouseY / this.fixedRenderScale();
   }

   public void mouseMoved(double mouseX, double mouseY) {
      super.mouseMoved(this.fixedMouseX(mouseX), this.fixedMouseY(mouseY));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean playSound) {
      return super.mouseClicked(this.fixedMouseEvent(event), playSound);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      double scale = this.fixedRenderScale();
      return super.mouseDragged(this.fixedMouseEvent(event), dragX / scale, dragY / scale);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return super.mouseReleased(this.fixedMouseEvent(event));
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return super.mouseScrolled(this.fixedMouseX(mouseX), this.fixedMouseY(mouseY), horizontalAmount, verticalAmount);
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return super.isMouseOver(this.fixedMouseX(mouseX), this.fixedMouseY(mouseY));
   }

   private MouseButtonEvent fixedMouseEvent(MouseButtonEvent event) {
      return new MouseButtonEvent(this.fixedMouseX(event.x()), this.fixedMouseY(event.y()), event.buttonInfo());
   }

   public final int fixedScreenWidth() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft != null && minecraft.getWindow() != null ? Math.max(854, (int)Math.round((double)minecraft.getWindow().getWidth() / this.fixedFramebufferScale())) : 854;
   }

   public final int fixedScreenHeight() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft != null && minecraft.getWindow() != null ? Math.max(480, (int)Math.round((double)minecraft.getWindow().getHeight() / this.fixedFramebufferScale())) : 480;
   }

   private double fixedRenderScale() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getWindow() != null) {
         int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
         return this.fixedFramebufferScale() / (double)guiScale;
      } else {
         return (double)1.0F;
      }
   }

   private double fixedFramebufferScale() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getWindow() != null) {
         double fitScale = Math.min((double)minecraft.getWindow().getWidth() / (double)854.0F, (double)minecraft.getWindow().getHeight() / (double)480.0F);
         return Math.max(0.1, fitScale);
      } else {
         return (double)1.0F;
      }
   }
}
