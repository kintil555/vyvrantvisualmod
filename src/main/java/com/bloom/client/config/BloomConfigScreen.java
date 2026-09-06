package com.bloom.client.config;

import com.bloom.client.render.BloomEntityMaskTextures;
import com.bloom.client.render.BloomEntityTextureCatalog;
import com.bloom.client.render.BloomMaskAtlas;
import com.bloom.client.render.BloomPostProcessor;
import com.bloom.client.render.LevelRendererRebuilds;
import com.bloom.client.selection.BloomSelection;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

public final class BloomConfigScreen extends FixedScaleScreen {
   private static final int PANEL_MARGIN = 10;
   private static final int LIST_WIDTH = 250;
   private static final int ROW_HEIGHT = 14;
   private static final int MASK_CANVAS_MAX_SIZE = 320;
   private static final int MASK_CANVAS_MAX_SCALE = 24;
   private static final int RESET_BUTTON_WIDTH = 18;
   private static final int LIST_SCROLLBAR_SIZE = 8;
   private static final int LIST_TEXT_PADDING = 4;
   private static final double VALUE_EPSILON = 1.0E-6;
   private static final List<Direction> FACE_ORDER;
   private static final Map<EditorTab, String> LAST_SEARCH_BY_TAB;
   private static final Map<EditorTab, String> LAST_SELECTED_BY_TAB;
   private static final Map<String, String> LAST_SELECTED_STATE_BY_BLOCK;
   private static final Map<String, String> LAST_SELECTED_LAYER_BY_SOURCE;
   private static final Map<EditorTab, Integer> LAST_SCROLL_BY_TAB;
   private static final Map<EditorTab, Integer> LAST_HORIZONTAL_SCROLL_BY_TAB;
   private static EditorTab lastTab;
   private static Direction lastSelectedFace;
   private static PaintTool lastPaintTool;
   private static int lastBrushSize;
   private final Screen parent;
   private final BloomConfig.Data defaults;
   private final BloomConfig.Data editing;
   private final String initialBlockId;
   private final List<SourceEntry> allEntries;
   private final List<SourceEntry> filteredEntries;
   private final Map<String, WorkingMask> workingMasks;
   private final Map<String, WorkingSourceMask> workingSourceMasks;
   private final Map<String, WorkingMask> workingEntityMasks;
   private final Set<SourceMaskTarget> clearedSourceMaskTargets;
   private final List<Button> faceButtons;
   private final List<Button> tabButtons;
   private final List<ResetControl> resetControls;
   private final List<DescribedWidget> describedWidgets;
   private EditBox searchBox;
   private int listScroll;
   private int listHorizontalScroll;
   private int selectedIndex;
   private Direction selectedFace;
   private EditorTab activeTab;
   private boolean painting;
   private boolean paintValue;
   private boolean draggingListVerticalScrollbar;
   private boolean draggingListHorizontalScrollbar;
   private boolean sourceStrengthDirty;
   private PaintTool paintTool;
   private int brushSize;
   private LabeledSlider globalStrengthSlider;
   private LabeledSlider thresholdSlider;
   private LabeledSlider radiusSlider;
   private LabeledSlider tinyRadiusSlider;
   private LabeledSlider broadRadiusSlider;
   private LabeledSlider blurPassSlider;
   private LabeledSlider distanceSlider;
   private LabeledSlider highlightClampSlider;
   private LabeledSlider softKneeSlider;
   private LabeledSlider sourceStrengthSlider;
   private LabeledSlider brushSizeSlider;
   private Button enabledButton;
   private Button resetSourceButton;
   private Button emissiveMaskButton;
   private Button applyAllStatesButton;
   private Button applyMaskToAllFacesButton;
   private Button copyToButton;
   private Button radiusProfileButton;
   private Button stateButton;
   private Button layerButton;
   private Button paintToolButton;
   private Button fillToolButton;
   private int listWidth;
   private int rightPanelX;
   private int rightPanelWidth;
   private int maskPanelX;
   private int maskPanelWidth;
   private int sliderWidth;
   private int bottomRowY;
   private int faceButtonsBottomY;
   private int maskCanvasTop;
   private int toolRowY;
   private int brushRowY;
   private boolean stateDropdownOpen;
   private int stateDropdownScroll;
   private boolean layerDropdownOpen;
   private int layerDropdownScroll;

   private BloomConfigScreen(Screen parent) {
      this(parent, (String)null);
   }

   private BloomConfigScreen(Screen parent, String initialBlockId) {
      super(Component.translatable("shine.config.title"));
      this.allEntries = new ArrayList();
      this.filteredEntries = new ArrayList();
      this.workingMasks = new HashMap();
      this.workingSourceMasks = new HashMap();
      this.workingEntityMasks = new HashMap();
      this.clearedSourceMaskTargets = new LinkedHashSet();
      this.faceButtons = new ArrayList();
      this.tabButtons = new ArrayList();
      this.resetControls = new ArrayList();
      this.describedWidgets = new ArrayList();
      this.selectedFace = Direction.NORTH;
      this.activeTab = BloomConfigScreen.EditorTab.BLOCKS;
      this.paintTool = lastPaintTool;
      this.brushSize = lastBrushSize;
      this.listWidth = 250;
      this.parent = parent;
      this.defaults = BloomConfig.defaults();
      this.editing = BloomConfig.copy();
      this.initialBlockId = normalizeBlockId(initialBlockId);
      this.selectedFace = lastSelectedFace;
      this.activeTab = this.initialBlockId == null ? lastTab : BloomConfigScreen.EditorTab.BLOCKS;
      this.rebuildEntriesForTab();
   }

   public static Screen create(Screen parent) {
      return new BloomConfigScreen(parent);
   }

   public static Screen create(Screen parent, String initialBlockId) {
      return new BloomConfigScreen(parent, initialBlockId);
   }

   protected void init() {
      this.applyFixedScaleDimensions();
      this.describedWidgets.clear();
      int top = 54;
      this.computeLayout();
      this.createTabButtons();
      this.searchBox = (EditBox)this.addRenderableWidget(new EditBox(this.font, 10, top, this.listWidth, 20, Component.translatable("shine.config.search")));
      this.searchBox.setMaxLength(120);
      this.searchBox.setResponder((query) -> this.applyFilter(query == null ? "" : query));
      String lastSearch = lastSearchForTab(this.activeTab);
      if (!lastSearch.isBlank()) {
         this.searchBox.setValue(lastSearch);
      }

      int resetX = this.rightPanelX + this.sliderWidth + 4;
      this.globalStrengthSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, top, this.sliderWidth, Component.translatable("shine.config.strength"), (double)0.0F, (double)10.0F, 0.01, () -> this.editing.strength, (v) -> this.editing.strength = v, (v) -> String.format(Locale.ROOT, "%.2f", v), (Runnable)null), () -> "Final bloom contribution added back to the scene.");
      this.addRenderableWidget(this.resetButton(resetX, top, () -> this.editing.strength = this.defaults.strength, () -> changed(this.editing.strength, this.defaults.strength)));
      int y = top + 22;
      this.thresholdSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.threshold"), (double)0.0F, (double)1.0F, 0.01, () -> this.editing.threshold, (v) -> this.editing.threshold = v, (v) -> String.format(Locale.ROOT, "%.2f", v), (Runnable)null), () -> "Minimum brightness required before pixels enter the bloom pass.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.threshold = this.defaults.threshold, () -> changed(this.editing.threshold, this.defaults.threshold)));
      y += 22;
      this.radiusSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.radius"), (double)0.0F, (double)700.0F, (double)1.0F, () -> this.editing.radius, (v) -> this.editing.radius = v, (v) -> String.format(Locale.ROOT, "%.0f", v), (Runnable)null), () -> "Controls how strongly bloom spreads into larger pyramid levels.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.radius = this.defaults.radius, () -> changed(this.editing.radius, this.defaults.radius)));
      y += 22;
      this.tinyRadiusSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.literal("Tiny Radius"), (double)0.0F, (double)700.0F, (double)1.0F, () -> this.editing.tinyRadius, (v) -> this.editing.tinyRadius = v, (v) -> String.format(Locale.ROOT, "%.0f", v), (Runnable)null), () -> "Bloom spread used by blocks assigned to the Tiny profile.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.tinyRadius = this.defaults.tinyRadius, () -> changed(this.editing.tinyRadius, this.defaults.tinyRadius)));
      y += 22;
      this.broadRadiusSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.literal("Broad Radius"), (double)0.0F, (double)700.0F, (double)1.0F, () -> this.editing.broadRadius, (v) -> this.editing.broadRadius = v, (v) -> String.format(Locale.ROOT, "%.0f", v), (Runnable)null), () -> "Bloom spread used by blocks assigned to the Broad profile.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.broadRadius = this.defaults.broadRadius, () -> changed(this.editing.broadRadius, this.defaults.broadRadius)));
      y += 22;
      this.blurPassSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.blur_pass_count"), (double)1.0F, (double)4.0F, (double)1.0F, () -> (double)this.editing.blurPassCount, (v) -> this.editing.blurPassCount = (int)Math.round(v), (v) -> String.format(Locale.ROOT, "%.0f", v), (Runnable)null), () -> "Controls how many bloom pyramid levels are active.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.blurPassCount = this.defaults.blurPassCount, () -> this.editing.blurPassCount != this.defaults.blurPassCount));
      y += 22;
      this.distanceSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.distance"), (double)1.0F, (double)256.0F, (double)1.0F, () -> this.editing.bloomDistance, (v) -> this.editing.bloomDistance = v, (v) -> String.format(Locale.ROOT, "%.0f", v), (Runnable)null), () -> "Live camera radius for bloom.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.bloomDistance = this.defaults.bloomDistance, () -> changed(this.editing.bloomDistance, this.defaults.bloomDistance)));
      y += 22;
      this.highlightClampSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.highlight_clamp"), 0.01, (double)4.0F, 0.01, () -> this.editing.highlightClamp, (v) -> this.editing.highlightClamp = v, (v) -> String.format(Locale.ROOT, "%.2f", v), (Runnable)null), () -> "Lower values reduce blinding bloom on pale surfaces.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.highlightClamp = this.defaults.highlightClamp, () -> changed(this.editing.highlightClamp, this.defaults.highlightClamp)));
      y += 22;
      this.softKneeSlider = (LabeledSlider)this.addDescribedWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.soft_knee"), 0.01, (double)1.0F, 0.01, () -> this.editing.softKnee, (v) -> this.editing.softKnee = v, (v) -> String.format(Locale.ROOT, "%.2f", v), (Runnable)null), () -> "Controls how gradually pixels transition into bloom above the threshold.");
      this.addRenderableWidget(this.resetButton(resetX, y, () -> this.editing.softKnee = this.defaults.softKnee, () -> changed(this.editing.softKnee, this.defaults.softKnee)));
      y += 26;
      this.sourceStrengthSlider = (LabeledSlider)this.addRenderableWidget(new LabeledSlider(this.rightPanelX, y, this.sliderWidth, Component.translatable("shine.config.block_strength"), (double)0.0F, (double)500.0F, (double)1.0F, this::getSelectedStrength, this::setSelectedStrength, (v) -> String.format(Locale.ROOT, "%.0f", v), this::markSourceStrengthDirty));
      this.resetSourceButton = (Button)this.addRenderableWidget(this.resetButton(resetX, y, this::resetSelectedSource, this::selectedSourceCanReset));
      y += 24;
      this.radiusProfileButton = (Button)this.addDescribedWidget(Button.builder(Component.empty(), (button) -> this.cycleSelectedRadiusProfile()).bounds(this.rightPanelX, y, this.sliderWidth, 20).build(), () -> "Chooses whether this block uses the Default, Tiny, or Broad bloom radius.");
      y += 22;
      this.emissiveMaskButton = (Button)this.addDescribedWidget(Button.builder(Component.empty(), (button) -> this.toggleSelectedEmissiveMask()).bounds(this.rightPanelX, y, this.sliderWidth, 20).build(), () -> "Makes the painted bloom pixels ignore terrain light levels.");
      y += 22;
      int copyGap = 4;
      int copyWidth = Math.max(52, (this.sliderWidth - copyGap) / 2);
      this.applyAllStatesButton = (Button)this.addDescribedWidget(Button.builder(Component.literal("Apply to All States"), (button) -> this.applyCurrentStateToAllStates()).bounds(this.rightPanelX, y, copyWidth, 20).build(), () -> "Copies the current block state's strength, painted mask, texture layer, and emissive-mask flag to every state of this block.");
      this.copyToButton = (Button)this.addDescribedWidget(Button.builder(Component.literal("Copy To..."), (button) -> this.openCopyToScreen()).bounds(this.rightPanelX + copyWidth + copyGap, y, Math.max(52, this.sliderWidth - copyWidth - copyGap), 20).build(), () -> "Copies this block's complete Bloom settings and painted masks to one or more other blocks.");
      int actionGap = 8;
      int actionWidth = Math.max(62, (this.rightPanelWidth - actionGap * 2) / 3);
      int actionX = this.rightPanelX + Math.max(0, (this.rightPanelWidth - (actionWidth * 3 + actionGap * 2)) / 2);
      this.enabledButton = (Button)this.addDescribedWidget(Button.builder(this.getEnabledLabel(), (button) -> {
         this.editing.enabled = !this.editing.enabled;
         button.setMessage(this.getEnabledLabel());
      }).bounds(actionX, this.bottomRowY, actionWidth, 20).build(), () -> "Master switch for the bloom post effect.");
      this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), (button) -> this.saveAndClose()).bounds(actionX + actionWidth + actionGap, this.bottomRowY, actionWidth, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), (button) -> this.onClose()).bounds(actionX + (actionWidth + actionGap) * 2, this.bottomRowY, actionWidth, 20).build());
      int toolGap = 4;
      int toolWidth = Math.max(46, (this.maskPanelWidth - toolGap) / 2);
      this.paintToolButton = (Button)this.addRenderableWidget(Button.builder(this.toolLabel(BloomConfigScreen.PaintTool.PAINT), (button) -> this.selectPaintTool(BloomConfigScreen.PaintTool.PAINT)).bounds(this.maskPanelX, this.toolRowY, toolWidth, 18).build());
      this.fillToolButton = (Button)this.addRenderableWidget(Button.builder(this.toolLabel(BloomConfigScreen.PaintTool.FILL), (button) -> this.selectPaintTool(BloomConfigScreen.PaintTool.FILL)).bounds(this.maskPanelX + toolWidth + toolGap, this.toolRowY, toolWidth, 18).build());
      this.brushSizeSlider = (LabeledSlider)this.addRenderableWidget(new LabeledSlider(this.maskPanelX, this.brushRowY, this.maskPanelWidth, Component.literal("Brush"), (double)1.0F, (double)16.0F, (double)1.0F, () -> (double)this.brushSize, (v) -> {
         this.brushSize = Math.max(1, (int)Math.round(v));
         lastBrushSize = this.brushSize;
      }, (v) -> String.format(Locale.ROOT, "%.0f", v), (Runnable)null));
      this.stateButton = (Button)this.addDescribedWidget(Button.builder(Component.empty(), (button) -> this.toggleStateDropdown()).bounds(this.maskPanelX, 42, this.maskPanelWidth, 18).build(), () -> "Select which block state is used for the preview, source strength, and painted mask.");
      this.layerButton = (Button)this.addDescribedWidget(Button.builder(Component.empty(), (button) -> this.toggleLayerDropdown()).bounds(this.maskPanelX, 42, this.maskPanelWidth, 18).build(), () -> "Select which texture layer from the block model is used for the preview and painted mask.");
      this.createFaceButtons(this.rightPanelX, y + 28, this.rightPanelWidth);
      this.applyMaskToAllFacesButton = (Button)this.addDescribedWidget(Button.builder(Component.literal("Apply Current Mask to All Faces"), (button) -> this.applyCurrentMaskToAllFaces()).bounds(this.rightPanelX, this.faceButtonsBottomY + 4, this.rightPanelWidth, 20).build(), () -> "Copies the current face's painted mask and emissive-mask flag to the corresponding texture layer on every face of this block state.");
      if (this.initialBlockId == null) {
         this.restoreLastSelection();
      } else {
         if (this.searchBox != null) {
            this.searchBox.setValue("");
         }

         this.applyFilter("");
         this.selectEntry(this.initialBlockId);
         this.keepSelectedEntryVisible();
      }

      if (!this.filteredEntries.isEmpty()) {
         this.selectedIndex = Math.min(this.selectedIndex, this.filteredEntries.size() - 1);
      }

      this.updateModeWidgets();
   }

   private Button resetButton(int x, int y, Runnable resetAction, BooleanSupplier canReset) {
      Button button = Button.builder(Component.empty(), (clicked) -> {
         resetAction.run();
         this.updateResetButtons();
      }).bounds(x, y, 18, 20).build();
      this.resetControls.add(new ResetControl(button, canReset));
      button.active = canReset.getAsBoolean();
      return button;
   }

   private <T extends AbstractWidget> T addDescribedWidget(T widget, Supplier<String> description) {
      this.describedWidgets.add(new DescribedWidget(widget, description));
      return (T)(this.addRenderableWidget(widget));
   }

   private String hoverDescription(int mouseX, int mouseY) {
      for(DescribedWidget described : this.describedWidgets) {
         AbstractWidget widget = described.widget();
         if (widget.visible && mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth() && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight()) {
            return (String)described.description().get();
         }
      }

      return "";
   }

   private void renderResetIcons(GuiGraphicsExtractor guiGraphics) {
      for(ResetControl control : this.resetControls) {
         renderResetIcon(guiGraphics, control.button);
      }

   }

   private static void renderResetIcon(GuiGraphicsExtractor guiGraphics, Button button) {
      int centerX = button.getX() + button.getWidth() / 2;
      int centerY = button.getY() + button.getHeight() / 2;
      int color = button.active ? -1644826 : -8947849;
      int shadow = button.active ? -10855846 : -13290187;
      drawResetIconShape(guiGraphics, centerX + 1, centerY + 1, shadow);
      drawResetIconShape(guiGraphics, centerX, centerY, color);
   }

   private static void drawResetIconShape(GuiGraphicsExtractor guiGraphics, int centerX, int centerY, int color) {
      guiGraphics.fill(centerX - 4, centerY - 6, centerX + 3, centerY - 4, color);
      guiGraphics.fill(centerX - 6, centerY - 4, centerX - 4, centerY + 3, color);
      guiGraphics.fill(centerX - 4, centerY + 3, centerX + 4, centerY + 5, color);
      guiGraphics.fill(centerX + 3, centerY + 1, centerX + 5, centerY + 4, color);
      guiGraphics.fill(centerX + 1, centerY - 8, centerX + 4, centerY - 6, color);
      guiGraphics.fill(centerX + 3, centerY - 6, centerX + 6, centerY - 4, color);
      guiGraphics.fill(centerX + 1, centerY - 4, centerX + 6, centerY - 2, color);
   }

   private void computeLayout() {
      int availableWidth = this.width - 20;
      int panelGap = 12;
      int minControlPanel = 220;
      int minMaskPanel = 150;
      int preferredList = Math.min(250, Math.max(120, availableWidth / 4));
      if (availableWidth - preferredList - panelGap * 2 < minControlPanel + minMaskPanel) {
         preferredList = Math.max(96, availableWidth - panelGap * 2 - minControlPanel - minMaskPanel);
      }

      this.listWidth = Math.max(96, preferredList);
      this.rightPanelX = 10 + this.listWidth + panelGap;
      int remaining = Math.max(minControlPanel, this.width - this.rightPanelX - 10);
      this.rightPanelWidth = Math.min(340, Math.max(minControlPanel, remaining * 45 / 100));
      this.maskPanelX = this.rightPanelX + this.rightPanelWidth + panelGap;
      this.maskPanelWidth = Math.max(minMaskPanel, this.width - this.maskPanelX - 10);
      if (this.maskPanelWidth < minMaskPanel && this.rightPanelWidth > minControlPanel) {
         int take = Math.min(this.rightPanelWidth - minControlPanel, minMaskPanel - this.maskPanelWidth);
         this.rightPanelWidth -= take;
         this.maskPanelX -= take;
         this.maskPanelWidth += take;
      }

      this.sliderWidth = Math.max(80, this.rightPanelWidth - 18 - 4);
      this.bottomRowY = this.height - 10 - 20;
      this.brushRowY = this.bottomRowY - 24;
      this.toolRowY = this.brushRowY - 22;
      this.maskCanvasTop = 84;
   }

   private int listTop() {
      return 78;
   }

   private int listBottom() {
      return Math.max(this.listTop() + 14 + 4, this.bottomRowY - 8);
   }

   private int listContentLeft() {
      return 14;
   }

   private int listContentRight() {
      return 10 + this.listWidth - 8 - 2;
   }

   private int listContentTop() {
      return this.listTop() + 2;
   }

   private int listContentBottom() {
      return this.listBottom() - 8 - 2;
   }

   private int listVerticalScrollbarX() {
      return 10 + this.listWidth - 8;
   }

   private int listHorizontalScrollbarY() {
      return this.listBottom() - 8;
   }

   private void createTabButtons() {
      this.tabButtons.clear();
      int gap = 4;
      int tabWidth = Math.max(66, (this.listWidth - gap * 2) / 3);
      int x = 10;
      int y = 32;

      for(EditorTab tab : BloomConfigScreen.EditorTab.values()) {
         Button button = (Button)this.addRenderableWidget(Button.builder(tab.label(), (b) -> this.switchTab(tab)).bounds(x, y, tabWidth, 18).build());
         this.tabButtons.add(button);
         x += tabWidth + gap;
      }

      this.updateTabButtonLabels();
   }

   private void createFaceButtons(int x, int y, int availableWidth) {
      this.faceButtons.clear();
      int gap = 4;
      int columns = availableWidth >= 396 + gap * 5 ? 6 : 3;
      int buttonWidth = Math.max(52, (availableWidth - gap * (columns - 1)) / columns);
      int rowHeight = 18;

      for(int i = 0; i < FACE_ORDER.size(); ++i) {
         Direction direction = (Direction)FACE_ORDER.get(i);
         int col = i % columns;
         int row = i / columns;
         int bx = x + col * (buttonWidth + gap);
         int by = y + row * (rowHeight + gap);
         Button button = (Button)this.addRenderableWidget(Button.builder(Component.literal(direction.getName().toUpperCase(Locale.ROOT)), (b) -> {
            this.selectedFace = direction;
            this.stateDropdownOpen = false;
            this.layerDropdownOpen = false;
            this.saveSessionState();
            this.updateModeWidgets();
         }).bounds(bx, by, buttonWidth, rowHeight).build());
         this.faceButtons.add(button);
      }

      int rows = (FACE_ORDER.size() + columns - 1) / columns;
      this.faceButtonsBottomY = y + rows * rowHeight + (rows - 1) * gap;
   }

   private Component getEnabledLabel() {
      return Component.translatable("shine.config.enabled").append(": ").append(this.editing.enabled ? Component.translatable("options.on") : Component.translatable("options.off"));
   }

   public void onClose() {
      this.saveSessionState();
      Minecraft.getInstance().gui.setScreen(this.parent);
   }

   private void saveAndClose() {
      BloomConfig.set(this.editing);
      BloomConfig.save();

      for(WorkingMask mask : this.workingMasks.values()) {
         BloomMaskConfig.writeMask(mask.spriteId, mask.width, mask.height, mask.bits);
      }

      for(SourceMaskTarget target : this.clearedSourceMaskTargets) {
         BloomMaskConfig.removeSourceMasks(target.sourceId, target.fluid);
      }

      for(WorkingSourceMask mask : this.workingSourceMasks.values()) {
         BloomMaskConfig.writeSourceMask(mask.sourceId, mask.fluid, mask.face, mask.stateKey, mask.layerSpriteId, mask.spriteId, mask.width, mask.height, mask.bits, mask.emissive);
      }

      for(WorkingMask mask : this.workingEntityMasks.values()) {
         BloomMaskConfig.writeEntityTextureMask(mask.spriteId, mask.width, mask.height, mask.bits);
      }

      BloomMaskConfig.save();
      BloomMaskAtlas.markDirty();
      BloomEntityMaskTextures.markDirty();
      BloomPostProcessor.onConfigSaved();
      rebuildChunksForSourceStrengthChanges();
      this.onClose();
   }

   private static void rebuildChunksForSourceStrengthChanges() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null && minecraft.levelRenderer != null) {
         LevelRendererRebuilds.requestChunkGeometryRebuild();
      }
   }

   private void resetGlobalSettings() {
      this.editing.enabled = this.defaults.enabled;
      this.editing.strength = this.defaults.strength;
      this.editing.threshold = this.defaults.threshold;
      this.editing.radius = this.defaults.radius;
      this.editing.tinyRadius = this.defaults.tinyRadius;
      this.editing.broadRadius = this.defaults.broadRadius;
      this.editing.blurPassCount = this.defaults.blurPassCount;
      this.editing.bloomDistance = this.defaults.bloomDistance;
      this.editing.highlightClamp = this.defaults.highlightClamp;
      this.editing.softKnee = this.defaults.softKnee;
      this.editing.defaultLightSourceStrength = this.defaults.defaultLightSourceStrength;
      this.editing.defaultNonLightStrength = this.defaults.defaultNonLightStrength;
      this.editing.defaultEntityTextureStrength = this.defaults.defaultEntityTextureStrength;
      this.editing.defaultParticleStrength = this.defaults.defaultParticleStrength;
      if (this.enabledButton != null) {
         this.enabledButton.setMessage(this.getEnabledLabel());
      }

   }

   private void resetSelectedSource() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null) {
         switch (selected.kind.ordinal()) {
            case 0:
            case 1:
               String stateKey = this.selectedSourceStateKey(selected);
               if (!stateKey.isBlank() && this.editing.stateSourceStrengthOverrides.containsKey(stateKey)) {
                  this.editing.stateSourceStrengthOverrides.remove(stateKey);
               } else {
                  for(String id : linkedSourceIds(selected.id)) {
                     this.editing.sourceStrengthOverrides.remove(id);
                  }
               }

               for(String id : linkedSourceIds(selected.id)) {
                  this.editing.sourceRadiusProfiles.remove(id);
               }
               break;
            case 2:
               this.editing.entityTextureStrengthOverrides.remove(selected.id);
               break;
            case 3:
               this.editing.particleStrengthOverrides.remove(selected.id);
         }

         this.sourceStrengthDirty = true;
      }
   }

   private boolean selectedSourceCanReset() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected == null) {
         return false;
      } else {
         boolean var10000;
         switch (selected.kind.ordinal()) {
            case 0:
            case 1:
               String stateKey = this.selectedSourceStateKey(selected);
               if (stateKey.isBlank() || !this.editing.stateSourceStrengthOverrides.containsKey(stateKey)) {
                  Stream var3 = linkedSourceIds(selected.id).stream();
                  Map var10001 = this.editing.sourceStrengthOverrides;
                  Objects.requireNonNull(var10001);
                  if (!var3.anyMatch(var10001::containsKey)) {
                     var3 = linkedSourceIds(selected.id).stream();
                     var10001 = this.editing.sourceRadiusProfiles;
                     Objects.requireNonNull(var10001);
                     if (!var3.anyMatch(var10001::containsKey)) {
                        var10000 = false;
                        break;
                     }
                  }
               }

               var10000 = true;
               break;
            case 2:
               var10000 = this.editing.entityTextureStrengthOverrides.containsKey(selected.id);
               break;
            case 3:
               var10000 = this.editing.particleStrengthOverrides.containsKey(selected.id);
               break;
            default:
               throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private void resetSelectedMask() {
      SpritePaintTarget target = this.getPaintTarget();
      if (target != null) {
         Arrays.fill(target.mask.bits, true);
         BloomMaskAtlas.markDirty();
         BloomEntityMaskTextures.markDirty();
      }
   }

   private void applyCurrentStateToAllStates() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK) {
         List<BlockState> states = this.selectedBlockStates();
         if (states.size() > 1) {
            double strength = Math.max((double)0.0F, Math.min((double)500.0F, this.getSelectedStrength()));
            WorkingSourceMask currentMask = this.selectedWorkingSourceMask();
            boolean[] sourceBits = currentMask == null ? null : Arrays.copyOf(currentMask.bits, currentMask.bits.length);

            for(BlockState state : states) {
               if (state != null) {
                  String stateKey = BloomSelection.blockStateKey(state);
                  if (!stateKey.isBlank()) {
                     double baseline = this.blockBaselineStrength(selected.id);
                     Double defaultStateOverride = (Double)this.defaults.stateSourceStrengthOverrides.get(stateKey);
                     if (defaultStateOverride != null) {
                        baseline = defaultStateOverride;
                     }

                     setOverride(this.editing.stateSourceStrengthOverrides, stateKey, strength, baseline);
                     if (currentMask != null && sourceBits != null) {
                        SpriteResolution targetSprite = this.resolveSpriteForState(selected, currentMask.face, state, currentMask.layerSpriteId, currentMask.spriteId);
                        String spriteId = targetSprite != null && targetSprite.spriteId() != null && !targetSprite.spriteId().isBlank() ? targetSprite.spriteId() : currentMask.spriteId;
                        String layerSpriteId = currentMask.layerSpriteId.isBlank() ? "" : spriteId;
                        String key = sourceMaskSessionKey(selected.id, false, currentMask.face, stateKey, layerSpriteId);
                        this.workingSourceMasks.put(key, new WorkingSourceMask(selected.id, false, currentMask.face, stateKey, layerSpriteId, spriteId, currentMask.width, currentMask.height, Arrays.copyOf(sourceBits, sourceBits.length), currentMask.emissive));
                     }
                  }
               }
            }

            this.sourceStrengthDirty = true;
            BloomMaskAtlas.markDirty();
            BloomEntityMaskTextures.markDirty();
            this.updateModeWidgets();
         }
      }
   }

   private void applyCurrentMaskToAllFaces() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK) {
         WorkingSourceMask currentMask = this.selectedWorkingSourceMask();
         BlockState state = this.selectedBlockState();
         if (currentMask != null && state != null) {
            List<ModelLayerOption> sourceLayers = this.resolveModelLayerOptions(selected, currentMask.face, state);
            int sourceLayerIndex = modelLayerIndex(sourceLayers, currentMask.layerSpriteId, currentMask.spriteId);

            for(Direction targetFace : FACE_ORDER) {
               if (targetFace != currentMask.face) {
                  List<ModelLayerOption> targetLayers = this.resolveModelLayerOptions(selected, targetFace, state);
                  String targetSpriteId = currentMask.spriteId;
                  TextureAtlasSprite targetSprite = null;
                  if (!targetLayers.isEmpty()) {
                     ModelLayerOption targetLayer = (ModelLayerOption)targetLayers.get(Math.min(Math.max(0, sourceLayerIndex), targetLayers.size() - 1));
                     targetSpriteId = targetLayer.spriteId();
                     targetSprite = targetLayer.sprite();
                  } else {
                     SpriteResolution resolution = this.resolveSpriteForState(selected, targetFace, state, "", currentMask.spriteId);
                     if (resolution != null && resolution.spriteId() != null && !resolution.spriteId().isBlank()) {
                        targetSpriteId = resolution.spriteId();
                        targetSprite = resolution.sprite();
                     }
                  }

                  if (targetSpriteId != null && !targetSpriteId.isBlank()) {
                     int targetWidth = targetSprite == null ? currentMask.width : Math.max(1, targetSprite.contents().width());
                     int targetHeight = targetSprite == null ? currentMask.height : Math.max(1, targetSprite.contents().height());
                     String targetLayerSpriteId = currentMask.layerSpriteId.isBlank() ? "" : targetSpriteId;
                     String key = sourceMaskSessionKey(selected.id, false, targetFace, currentMask.stateKey, targetLayerSpriteId);
                     this.workingSourceMasks.put(key, new WorkingSourceMask(selected.id, false, targetFace, currentMask.stateKey, targetLayerSpriteId, targetSpriteId, targetWidth, targetHeight, resizeMask(currentMask.bits, currentMask.width, currentMask.height, targetWidth, targetHeight), currentMask.emissive));
                  }
               }
            }

            BloomMaskAtlas.markDirty();
            BloomEntityMaskTextures.markDirty();
            this.updateModeWidgets();
         }
      }
   }

   private void openCopyToScreen() {
      SourceEntry source = this.getSelectedEntry();
      if (source != null && (source.kind == BloomConfigScreen.EntryKind.BLOCK || source.kind == BloomConfigScreen.EntryKind.FLUID)) {
         List<String> candidates = new ArrayList();

         for(SourceEntry entry : this.allEntries) {
            if (entry.kind == source.kind && !entry.id.equals(source.id)) {
               candidates.add(entry.id);
            }
         }

         String noun = source.kind == BloomConfigScreen.EntryKind.FLUID ? "fluids" : "blocks";
         Component title = Component.literal(source.kind == BloomConfigScreen.EntryKind.FLUID ? "Copy Bloom To Fluids" : "Copy Bloom To Blocks");
         Minecraft.getInstance().gui.setScreen(ExperimentalBiomeBulkApplyScreen.create(this, title, source.id, noun, candidates, (targetIds) -> this.copySourceToTargets(source, targetIds)));
      }
   }

   private void copySourceToTargets(SourceEntry source, Set<String> targetIds) {
      if (source != null && targetIds != null && !targetIds.isEmpty()) {
         List<WorkingSourceMask> sourceMasks = this.effectiveSourceMasks(source);

         for(String targetId : targetIds) {
            SourceEntry target = this.sourceEntry(targetId, source.kind);
            if (target != null && !target.id.equals(source.id)) {
               this.copySourceSettings(source, target);
               this.copySourceMasks(source, target, sourceMasks);
            }
         }

         this.sourceStrengthDirty = true;
         BloomMaskAtlas.markDirty();
         BloomEntityMaskTextures.markDirty();
         this.updateModeWidgets();
      }
   }

   private SourceEntry sourceEntry(String id, EntryKind kind) {
      if (id != null && !id.isBlank()) {
         for(SourceEntry entry : this.allEntries) {
            if (entry.kind == kind && id.equals(entry.id)) {
               return entry;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void copySourceSettings(SourceEntry source, SourceEntry target) {
      double sourceBaseStrength = this.sourceBaseStrength(source);
      double targetDefaultStrength = this.defaultBaseStrength(target);

      for(String linkedId : linkedSourceIds(target.id)) {
         setOverride(this.editing.sourceStrengthOverrides, linkedId, sourceBaseStrength, targetDefaultStrength);
      }

      int radiusProfile = Math.max(0, Math.min(2, (Integer)this.editing.sourceRadiusProfiles.getOrDefault(source.id, 0)));

      for(String linkedId : linkedSourceIds(target.id)) {
         if (radiusProfile == 0) {
            this.editing.sourceRadiusProfiles.remove(linkedId);
         } else {
            this.editing.sourceRadiusProfiles.put(linkedId, radiusProfile);
         }
      }

      if (source.kind == BloomConfigScreen.EntryKind.BLOCK && target.kind == BloomConfigScreen.EntryKind.BLOCK) {
         this.removeStateStrengthOverrides(target.id);
         List<BlockState> sourceStates = this.blockStates(source);
         List<BlockState> targetStates = this.blockStates(target);
         BlockState sourceFallback = this.defaultBlockState(source);

         for(BlockState targetState : targetStates) {
            BlockState sourceState = matchingState(sourceStates, BloomSelection.blockStateKey(targetState), sourceFallback);
            double strength = this.sourceStateStrength(source, sourceState);
            String targetStateKey = BloomSelection.blockStateKey(targetState);
            double baseline = this.blockBaselineStrength(target.id);
            Double defaultOverride = (Double)this.defaults.stateSourceStrengthOverrides.get(targetStateKey);
            if (defaultOverride != null) {
               baseline = defaultOverride;
            }

            setOverride(this.editing.stateSourceStrengthOverrides, targetStateKey, strength, baseline);
         }

      }
   }

   private double sourceBaseStrength(SourceEntry source) {
      Double override = (Double)this.editing.sourceStrengthOverrides.get(source.id);
      if (override != null) {
         return override;
      } else {
         Double configuredDefault = (Double)this.defaults.sourceStrengthOverrides.get(source.id);
         if (configuredDefault != null) {
            return configuredDefault;
         } else {
            return source.lightSource ? this.editing.defaultLightSourceStrength : this.editing.defaultNonLightStrength;
         }
      }
   }

   private double defaultBaseStrength(SourceEntry target) {
      Double configuredDefault = (Double)this.defaults.sourceStrengthOverrides.get(target.id);
      if (configuredDefault != null) {
         return configuredDefault;
      } else {
         return target.lightSource ? this.defaults.defaultLightSourceStrength : this.defaults.defaultNonLightStrength;
      }
   }

   private double sourceStateStrength(SourceEntry source, BlockState state) {
      if (state != null) {
         Double override = (Double)this.editing.stateSourceStrengthOverrides.get(BloomSelection.blockStateKey(state));
         if (override != null) {
            return override;
         }
      }

      return this.sourceBaseStrength(source);
   }

   private void removeStateStrengthOverrides(String blockId) {
      this.editing.stateSourceStrengthOverrides.keySet().removeIf((key) -> blockId.equals(blockIdFromStateKey(key)));
   }

   private List<WorkingSourceMask> effectiveSourceMasks(SourceEntry source) {
      boolean fluid = source.kind == BloomConfigScreen.EntryKind.FLUID;
      SourceMaskTarget sourceTarget = new SourceMaskTarget(source.id, fluid);
      Map<String, WorkingSourceMask> masks = new LinkedHashMap();
      if (!this.clearedSourceMaskTargets.contains(sourceTarget)) {
         BloomMaskConfig.Data savedMasks = BloomMaskConfig.copy();

         for(BloomMaskConfig.SourceMaskEntry entry : savedMasks.sourceMasks.values()) {
            if (entry != null && entry.fluid == fluid && source.id.equals(entry.sourceId)) {
               Direction face = Direction.byName(entry.face);
               if (face != null && entry.width > 0 && entry.height > 0) {
                  String stateKey = entry.stateKey == null ? "" : entry.stateKey;
                  String layerSpriteId = entry.layerSpriteId == null ? "" : entry.layerSpriteId;
                  boolean[] bits = BloomMaskConfig.readSourceMask(entry.sourceId, entry.fluid, face, stateKey, layerSpriteId, entry.spriteId, entry.width, entry.height);
                  masks.put(sourceMaskSessionKey(source.id, fluid, face, stateKey, layerSpriteId), new WorkingSourceMask(source.id, fluid, face, stateKey, layerSpriteId, entry.spriteId, entry.width, entry.height, bits, entry.emissive));
               }
            }
         }
      }

      for(WorkingSourceMask mask : this.workingSourceMasks.values()) {
         if (mask.fluid == fluid && source.id.equals(mask.sourceId)) {
            masks.put(sourceMaskSessionKey(source.id, fluid, mask.face, mask.stateKey, mask.layerSpriteId), mask);
         }
      }

      return new ArrayList(masks.values());
   }

   private void copySourceMasks(SourceEntry source, SourceEntry target, List<WorkingSourceMask> sourceMasks) {
      boolean targetFluid = target.kind == BloomConfigScreen.EntryKind.FLUID;
      this.workingSourceMasks.entrySet().removeIf((entry) -> {
         WorkingSourceMask mask = (WorkingSourceMask)entry.getValue();
         return mask != null && mask.fluid == targetFluid && target.id.equals(mask.sourceId);
      });
      this.clearedSourceMaskTargets.add(new SourceMaskTarget(target.id, targetFluid));

      for(WorkingSourceMask sourceMask : sourceMasks) {
         WorkingSourceMask copied = this.remapSourceMask(source, target, sourceMask);
         if (copied != null) {
            String key = sourceMaskSessionKey(target.id, targetFluid, copied.face, copied.stateKey, copied.layerSpriteId);
            this.workingSourceMasks.put(key, copied);
         }
      }

   }

   private WorkingSourceMask remapSourceMask(SourceEntry source, SourceEntry target, WorkingSourceMask sourceMask) {
      BlockState sourceState = this.stateForKey(source, sourceMask.stateKey);
      BlockState targetState = target.kind == BloomConfigScreen.EntryKind.BLOCK ? matchingState(this.blockStates(target), sourceMask.stateKey, this.defaultBlockState(target)) : null;
      String targetStateKey = !sourceMask.stateKey.isBlank() && target.kind == BloomConfigScreen.EntryKind.BLOCK ? BloomSelection.blockStateKey(targetState) : "";
      String targetLayerSpriteId = "";
      String targetSpriteId = sourceMask.spriteId;
      TextureAtlasSprite targetSprite = null;
      if (target.kind == BloomConfigScreen.EntryKind.BLOCK) {
         List<ModelLayerOption> sourceLayers = this.resolveModelLayerOptions(source, sourceMask.face, sourceState);
         List<ModelLayerOption> targetLayers = this.resolveModelLayerOptions(target, sourceMask.face, targetState);
         int sourceLayerIndex = modelLayerIndex(sourceLayers, sourceMask.layerSpriteId, sourceMask.spriteId);
         if (!targetLayers.isEmpty()) {
            ModelLayerOption targetLayer = (ModelLayerOption)targetLayers.get(Math.min(Math.max(0, sourceLayerIndex), targetLayers.size() - 1));
            targetSpriteId = targetLayer.spriteId();
            targetSprite = targetLayer.sprite();
            if (!sourceMask.layerSpriteId.isBlank()) {
               targetLayerSpriteId = targetSpriteId;
            }
         }
      } else {
         SpriteResolution resolution = this.resolveSpriteForState(target, sourceMask.face, (BlockState)null, "", "");
         if (resolution != null) {
            targetSpriteId = resolution.spriteId();
            targetSprite = resolution.sprite();
         }
      }

      if (targetSpriteId != null && !targetSpriteId.isBlank()) {
         int targetWidth = targetSprite == null ? sourceMask.width : Math.max(1, targetSprite.contents().width());
         int targetHeight = targetSprite == null ? sourceMask.height : Math.max(1, targetSprite.contents().height());
         boolean[] targetBits = resizeMask(sourceMask.bits, sourceMask.width, sourceMask.height, targetWidth, targetHeight);
         return new WorkingSourceMask(target.id, target.kind == BloomConfigScreen.EntryKind.FLUID, sourceMask.face, targetStateKey, targetLayerSpriteId, targetSpriteId, targetWidth, targetHeight, targetBits, sourceMask.emissive);
      } else {
         return null;
      }
   }

   private static int modelLayerIndex(List<ModelLayerOption> layers, String preferredLayerSpriteId, String preferredSpriteId) {
      if (layers != null && !layers.isEmpty()) {
         for(int i = 0; i < layers.size(); ++i) {
            String spriteId = ((ModelLayerOption)layers.get(i)).spriteId();
            if (!preferredLayerSpriteId.isBlank() && preferredLayerSpriteId.equals(spriteId) || !preferredSpriteId.isBlank() && preferredSpriteId.equals(spriteId)) {
               return i;
            }
         }

         return 0;
      } else {
         return 0;
      }
   }

   private static boolean[] resizeMask(boolean[] source, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
      if (source != null && sourceWidth > 0 && sourceHeight > 0 && source.length == sourceWidth * sourceHeight) {
         if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return Arrays.copyOf(source, source.length);
         } else {
            boolean[] resized = new boolean[targetWidth * targetHeight];

            for(int y = 0; y < targetHeight; ++y) {
               int sourceY = Math.min(sourceHeight - 1, y * sourceHeight / targetHeight);

               for(int x = 0; x < targetWidth; ++x) {
                  int sourceX = Math.min(sourceWidth - 1, x * sourceWidth / targetWidth);
                  resized[y * targetWidth + x] = source[sourceY * sourceWidth + sourceX];
               }
            }

            return resized;
         }
      } else {
         boolean[] filled = new boolean[Math.max(1, targetWidth * targetHeight)];
         Arrays.fill(filled, true);
         return filled;
      }
   }

   private List<BlockState> blockStates(SourceEntry entry) {
      if (entry != null && entry.kind == BloomConfigScreen.EntryKind.BLOCK) {
         try {
            Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.id));
            return (List<BlockState>)(block == null ? List.of() : block.getStateDefinition().getPossibleStates());
         } catch (Exception var3) {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private BlockState defaultBlockState(SourceEntry entry) {
      if (entry != null && entry.kind == BloomConfigScreen.EntryKind.BLOCK) {
         try {
            Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.id));
            return block == null ? null : block.defaultBlockState();
         } catch (Exception var3) {
            return null;
         }
      } else {
         return null;
      }
   }

   private BlockState stateForKey(SourceEntry entry, String stateKey) {
      if (stateKey != null && !stateKey.isBlank()) {
         for(BlockState state : this.blockStates(entry)) {
            if (stateKey.equals(BloomSelection.blockStateKey(state))) {
               return state;
            }
         }
      }

      return this.defaultBlockState(entry);
   }

   private static BlockState matchingState(List<BlockState> states, String sourceStateKey, BlockState fallback) {
      if (states != null && !states.isEmpty()) {
         String signature = statePropertySignature(sourceStateKey);
         if (!signature.isBlank()) {
            for(BlockState state : states) {
               if (signature.equals(statePropertySignature(BloomSelection.blockStateKey(state)))) {
                  return state;
               }
            }
         }

         return fallback == null ? (BlockState)states.get(0) : fallback;
      } else {
         return fallback;
      }
   }

   private static String statePropertySignature(String stateKey) {
      if (stateKey == null) {
         return "";
      } else {
         int propertiesStart = stateKey.indexOf(91);
         return propertiesStart < 0 ? "" : stateKey.substring(propertiesStart);
      }
   }

   private static String blockIdFromStateKey(String stateKey) {
      if (stateKey == null) {
         return "";
      } else {
         int propertiesStart = stateKey.indexOf(91);
         return propertiesStart < 0 ? stateKey : stateKey.substring(0, propertiesStart);
      }
   }

   private void switchTab(EditorTab tab) {
      if (tab != this.activeTab) {
         this.saveSessionState();
         this.activeTab = tab;
         lastTab = tab;
         this.rebuildEntriesForTab();
         if (this.searchBox != null) {
            this.searchBox.setValue(lastSearchForTab(tab));
         }

         this.restoreLastSelection();
         this.updateTabButtonLabels();
         this.updateModeWidgets();
      }
   }

   private void rebuildEntriesForTab() {
      this.allEntries.clear();
      this.allEntries.addAll(collectEntries(this.activeTab));
      this.applyFilter(lastSearchForTab(this.activeTab));
   }

   private void applyFilter(String query) {
      String previouslySelectedId = null;
      SourceEntry current = this.getSelectedEntry();
      if (current != null) {
         previouslySelectedId = current.id;
      }

      this.filteredEntries.clear();
      String lowered = query.toLowerCase(Locale.ROOT);

      for(SourceEntry entry : this.allEntries) {
         if (lowered.isBlank() || entry.id.toLowerCase(Locale.ROOT).contains(lowered)) {
            this.filteredEntries.add(entry);
         }
      }

      if (this.filteredEntries.isEmpty()) {
         this.selectedIndex = -1;
      } else if (previouslySelectedId != null) {
         int preserved = -1;

         for(int i = 0; i < this.filteredEntries.size(); ++i) {
            if (previouslySelectedId.equals(((SourceEntry)this.filteredEntries.get(i)).id)) {
               preserved = i;
               break;
            }
         }

         this.selectedIndex = preserved >= 0 ? preserved : 0;
      } else {
         this.selectedIndex = 0;
      }

      this.listScroll = 0;
      this.listHorizontalScroll = 0;
      this.updateModeWidgets();
   }

   private void restoreLastSelection() {
      String lastSelectedSourceId = (String)LAST_SELECTED_BY_TAB.get(this.activeTab);
      if (lastSelectedSourceId != null) {
         this.selectEntry(lastSelectedSourceId);
      } else if (!this.filteredEntries.isEmpty()) {
         this.selectedIndex = Math.min(Math.max(this.selectedIndex, 0), this.filteredEntries.size() - 1);
      }

      this.listScroll = this.clampListScroll((Integer)LAST_SCROLL_BY_TAB.getOrDefault(this.activeTab, 0));
      this.listHorizontalScroll = this.clampListHorizontalScroll((Integer)LAST_HORIZONTAL_SCROLL_BY_TAB.getOrDefault(this.activeTab, 0));
      this.keepSelectedEntryVisible();
      this.updateModeWidgets();
   }

   private void selectEntry(String sourceId) {
      if (sourceId != null) {
         for(int i = 0; i < this.filteredEntries.size(); ++i) {
            if (sourceId.equals(((SourceEntry)this.filteredEntries.get(i)).id)) {
               this.selectedIndex = i;
               return;
            }
         }

         if (!this.filteredEntries.isEmpty()) {
            this.selectedIndex = Math.min(Math.max(this.selectedIndex, 0), this.filteredEntries.size() - 1);
         }

      }
   }

   private void keepSelectedEntryVisible() {
      if (this.selectedIndex >= 0) {
         int maxRows = this.visibleListRows();
         if (this.selectedIndex < this.listScroll) {
            this.listScroll = this.selectedIndex;
         } else if (this.selectedIndex >= this.listScroll + maxRows) {
            this.listScroll = this.selectedIndex - maxRows + 1;
         }

         this.listScroll = this.clampListScroll(this.listScroll);
      }
   }

   private int clampListScroll(int scroll) {
      int maxScroll = Math.max(0, this.filteredEntries.size() - this.visibleListRows());
      return Math.max(0, Math.min(scroll, maxScroll));
   }

   private int clampListHorizontalScroll(int scroll) {
      return Math.max(0, Math.min(scroll, this.maxListHorizontalScroll()));
   }

   private int maxListHorizontalScroll() {
      return Math.max(0, this.maxListTextWidth() - this.visibleListTextWidth());
   }

   private int visibleListTextWidth() {
      return Math.max(1, this.listContentRight() - this.listContentLeft());
   }

   private int maxListTextWidth() {
      if (this.filteredEntries.isEmpty()) {
         return this.font.width("No matches");
      } else {
         int max = 0;

         for(SourceEntry entry : this.filteredEntries) {
            max = Math.max(max, this.font.width(entry.id));
         }

         return max;
      }
   }

   private int visibleListRows() {
      return Math.max(1, (this.listContentBottom() - this.listContentTop()) / 14);
   }

   private void saveSessionState() {
      LAST_SEARCH_BY_TAB.put(this.activeTab, this.searchBox == null ? "" : this.searchBox.getValue());
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null) {
         LAST_SELECTED_BY_TAB.put(this.activeTab, selected.id);
      }

      lastSelectedFace = this.selectedFace == null ? Direction.NORTH : this.selectedFace;
      LAST_SCROLL_BY_TAB.put(this.activeTab, this.listScroll);
      LAST_HORIZONTAL_SCROLL_BY_TAB.put(this.activeTab, this.listHorizontalScroll);
      lastTab = this.activeTab;
   }

   private static String lastSearchForTab(EditorTab tab) {
      return (String)LAST_SEARCH_BY_TAB.getOrDefault(tab, "");
   }

   private void updateTabButtonLabels() {
      for(int i = 0; i < this.tabButtons.size() && i < BloomConfigScreen.EditorTab.values().length; ++i) {
         EditorTab tab = BloomConfigScreen.EditorTab.values()[i];
         Button button = (Button)this.tabButtons.get(i);
         button.setMessage(Component.literal(tab.displayName));
         button.active = tab != this.activeTab;
      }

   }

   private void selectPaintTool(PaintTool tool) {
      this.paintTool = tool;
      lastPaintTool = tool;
      this.updateModeWidgets();
   }

   private void updateToolButtonLabels() {
      if (this.paintToolButton != null) {
         this.paintToolButton.setMessage(this.toolLabel(BloomConfigScreen.PaintTool.PAINT));
      }

      if (this.fillToolButton != null) {
         this.fillToolButton.setMessage(this.toolLabel(BloomConfigScreen.PaintTool.FILL));
      }

   }

   private Component toolLabel(PaintTool tool) {
      return Component.literal(tool.displayName);
   }

   private void toggleStateDropdown() {
      List<BlockState> states = this.selectedBlockStates();
      if (states.size() <= 1) {
         this.stateDropdownOpen = false;
      } else {
         this.stateDropdownScroll = this.clampStateDropdownScroll(this.stateDropdownScroll, states);
         this.layerDropdownOpen = false;
         this.stateDropdownOpen = !this.stateDropdownOpen;
      }
   }

   private void toggleLayerDropdown() {
      List<ModelLayerOption> layers = this.selectedModelLayerOptions();
      if (layers.size() <= 1) {
         this.layerDropdownOpen = false;
      } else {
         this.layerDropdownScroll = this.clampLayerDropdownScroll(this.layerDropdownScroll, layers);
         this.stateDropdownOpen = false;
         this.layerDropdownOpen = !this.layerDropdownOpen;
      }
   }

   private void updateModeWidgets() {
      boolean blockMode = this.activeTab == BloomConfigScreen.EditorTab.BLOCKS;
      boolean hasMask = this.getPaintTarget() != null;
      boolean hasTerrainMask = hasMask && this.selectedSupportsEmissiveMask();

      for(int i = 0; i < this.faceButtons.size(); ++i) {
         Button button = (Button)this.faceButtons.get(i);
         Direction direction = i < FACE_ORDER.size() ? (Direction)FACE_ORDER.get(i) : null;
         button.visible = blockMode;
         button.active = blockMode && direction != this.selectedFace;
      }

      if (this.paintToolButton != null) {
         this.paintToolButton.visible = hasMask;
         this.paintToolButton.active = hasMask && this.paintTool != BloomConfigScreen.PaintTool.PAINT;
      }

      if (this.fillToolButton != null) {
         this.fillToolButton.visible = hasMask;
         this.fillToolButton.active = hasMask && this.paintTool != BloomConfigScreen.PaintTool.FILL;
      }

      if (this.brushSizeSlider != null) {
         this.brushSizeSlider.visible = hasMask;
         this.brushSizeSlider.active = hasMask && this.paintTool != BloomConfigScreen.PaintTool.FILL;
      }

      if (this.emissiveMaskButton != null) {
         this.emissiveMaskButton.visible = hasTerrainMask;
         this.emissiveMaskButton.active = hasTerrainMask;
         this.emissiveMaskButton.setMessage(this.emissiveMaskLabel());
      }

      if (this.radiusProfileButton != null) {
         SourceEntry selected = this.getSelectedEntry();
         boolean visible = selected != null && (selected.kind == BloomConfigScreen.EntryKind.BLOCK || selected.kind == BloomConfigScreen.EntryKind.FLUID);
         this.radiusProfileButton.visible = visible;
         this.radiusProfileButton.active = visible;
         this.radiusProfileButton.setMessage(Component.literal("Radius Profile: " + radiusProfileName(this.getSelectedRadiusProfile())));
      }

      if (this.applyAllStatesButton != null) {
         List<BlockState> states = this.selectedBlockStates();
         boolean visible = blockMode && states.size() > 1 && hasTerrainMask;
         this.applyAllStatesButton.visible = visible;
         this.applyAllStatesButton.active = visible;
         this.applyAllStatesButton.setMessage(Component.literal("Apply to All States"));
      }

      if (this.applyMaskToAllFacesButton != null) {
         boolean visible = blockMode && hasTerrainMask;
         this.applyMaskToAllFacesButton.visible = visible;
         this.applyMaskToAllFacesButton.active = visible;
      }

      if (this.copyToButton != null) {
         SourceEntry selected = this.getSelectedEntry();
         boolean visible = selected != null && (selected.kind == BloomConfigScreen.EntryKind.BLOCK || selected.kind == BloomConfigScreen.EntryKind.FLUID);
         this.copyToButton.visible = visible;
         this.copyToButton.active = visible;
         if (visible) {
            boolean applyVisible = this.applyAllStatesButton != null && this.applyAllStatesButton.visible;
            int gap = 4;
            if (applyVisible) {
               int half = Math.max(52, (this.sliderWidth - gap) / 2);
               this.applyAllStatesButton.setX(this.rightPanelX);
               this.applyAllStatesButton.setWidth(half);
               this.copyToButton.setX(this.rightPanelX + half + gap);
               this.copyToButton.setWidth(Math.max(52, this.sliderWidth - half - gap));
            } else {
               this.copyToButton.setX(this.rightPanelX);
               this.copyToButton.setWidth(this.sliderWidth);
            }
         }
      }

      if (this.stateButton != null) {
         List<BlockState> states = this.selectedBlockStates();
         List<ModelLayerOption> layers = this.selectedModelLayerOptions();
         boolean stateVisible = blockMode && states.size() > 1;
         boolean layerVisible = blockMode && layers.size() > 1;
         this.layoutStateAndLayerButtons(stateVisible, layerVisible);
         this.stateButton.visible = stateVisible;
         this.stateButton.active = stateVisible;
         this.stateButton.setMessage(Component.literal(this.stateButtonLabel(states, this.stateButton.getWidth())));
         if (!stateVisible) {
            this.stateDropdownOpen = false;
            this.stateDropdownScroll = 0;
         }

         if (this.layerButton != null) {
            this.layerButton.visible = layerVisible;
            this.layerButton.active = layerVisible;
            this.layerButton.setMessage(Component.literal(this.layerButtonLabel(layers, this.layerButton.getWidth())));
            if (!layerVisible) {
               this.layerDropdownOpen = false;
               this.layerDropdownScroll = 0;
            }
         }
      }

      this.updateToolButtonLabels();
      this.updateResetButtons();
   }

   private List<BlockState> selectedBlockStates() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK) {
         try {
            Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(selected.id));
            return (List<BlockState>)(block == null ? List.of() : block.getStateDefinition().getPossibleStates());
         } catch (Exception var3) {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private BlockState selectedBlockState() {
      List<BlockState> states = this.selectedBlockStates();
      if (states.isEmpty()) {
         return null;
      } else {
         String selectedKey = this.selectedBlockStateKey();

         for(BlockState state : states) {
            if (selectedKey.equals(BloomSelection.blockStateKey(state))) {
               return state;
            }
         }

         return (BlockState)states.get(0);
      }
   }

   private String selectedBlockStateKey() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK) {
         List<BlockState> states = this.selectedBlockStates();
         if (states.isEmpty()) {
            return "";
         } else {
            String stored = (String)LAST_SELECTED_STATE_BY_BLOCK.get(selected.id);
            if (stored != null) {
               for(BlockState state : states) {
                  if (stored.equals(BloomSelection.blockStateKey(state))) {
                     return stored;
                  }
               }
            }

            String fallback = "";

            try {
               Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(selected.id));
               if (block != null) {
                  fallback = BloomSelection.blockStateKey(block.defaultBlockState());
               }
            } catch (Exception var6) {
            }

            if (fallback.isBlank()) {
               fallback = BloomSelection.blockStateKey((BlockState)states.get(0));
            }

            LAST_SELECTED_STATE_BY_BLOCK.put(selected.id, fallback);
            return fallback;
         }
      } else {
         return "";
      }
   }

   private String selectedSourceStateKey(SourceEntry selected) {
      return selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK ? this.selectedBlockStateKey() : "";
   }

   private List<ModelLayerOption> selectedModelLayerOptions() {
      SourceEntry selected = this.getSelectedEntry();
      return selected == null ? List.of() : this.resolveModelLayerOptions(selected, this.selectedFace);
   }

   private ModelLayerOption selectedModelLayerOption(List<ModelLayerOption> layers) {
      if (layers != null && !layers.isEmpty()) {
         SourceEntry selected = this.getSelectedEntry();
         String key = this.selectedLayerSessionKey(selected);
         String stored = key == null ? null : (String)LAST_SELECTED_LAYER_BY_SOURCE.get(key);
         if (stored != null) {
            for(ModelLayerOption layer : layers) {
               if (stored.equals(layer.spriteId())) {
                  return layer;
               }
            }
         }

         ModelLayerOption fallback = (ModelLayerOption)layers.get(0);
         if (key != null) {
            LAST_SELECTED_LAYER_BY_SOURCE.put(key, fallback.spriteId());
         }

         return fallback;
      } else {
         return null;
      }
   }

   private String selectedLayerSessionKey(SourceEntry selected) {
      if (selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK) {
         String stateKey = this.selectedSourceStateKey(selected);
         String sourcePart = stateKey != null && !stateKey.isBlank() ? stateKey : selected.id;
         Direction face = this.selectedFace == null ? Direction.NORTH : this.selectedFace;
         return "block:" + sourcePart + "#" + face.getName();
      } else {
         return null;
      }
   }

   private String selectedSourceLayerSpriteId(SourceEntry selected, String selectedSpriteId) {
      if (selected != null && selected.kind == BloomConfigScreen.EntryKind.BLOCK && selectedSpriteId != null && !selectedSpriteId.isBlank()) {
         List<ModelLayerOption> layers = this.resolveModelLayerOptions(selected, this.selectedFace);
         if (layers.size() <= 1) {
            return "";
         } else {
            ModelLayerOption selectedLayer = this.selectedModelLayerOption(layers);
            return selectedLayer == null ? selectedSpriteId : selectedLayer.spriteId();
         }
      } else {
         return "";
      }
   }

   private void layoutStateAndLayerButtons(boolean stateVisible, boolean layerVisible) {
      int x = this.maskPanelX;
      int y = 42;
      int gap = 4;
      if (stateVisible && layerVisible) {
         int buttonWidth = Math.max(52, (this.maskPanelWidth - gap) / 2);
         this.stateButton.setX(x);
         this.stateButton.setY(y);
         this.stateButton.setWidth(buttonWidth);
         if (this.layerButton != null) {
            this.layerButton.setX(x + buttonWidth + gap);
            this.layerButton.setY(y);
            this.layerButton.setWidth(Math.max(52, this.maskPanelWidth - buttonWidth - gap));
         }

      } else {
         if (stateVisible) {
            this.stateButton.setX(x);
            this.stateButton.setY(y);
            this.stateButton.setWidth(this.maskPanelWidth);
         }

         if (layerVisible && this.layerButton != null) {
            this.layerButton.setX(x);
            this.layerButton.setY(y);
            this.layerButton.setWidth(this.maskPanelWidth);
         }

      }
   }

   private String stateButtonLabel(List<BlockState> states, int width) {
      BlockState state = this.selectedBlockState();
      if (state == null) {
         return "State";
      } else {
         String var10000 = this.fitText(stateLabel(state), Math.max(32, width - this.font.width("State: ")));
         return "State: " + var10000;
      }
   }

   private static String stateLabel(BlockState state) {
      String key = BloomSelection.blockStateKey(state);
      int bracket = key.indexOf(91);
      return bracket >= 0 && key.endsWith("]") ? key.substring(bracket + 1, key.length() - 1) : "default";
   }

   private int clampStateDropdownScroll(int scroll, List<BlockState> states) {
      int maxScroll = Math.max(0, states.size() - 8);
      return Math.max(0, Math.min(scroll, maxScroll));
   }

   private String layerButtonLabel(List<ModelLayerOption> layers, int width) {
      ModelLayerOption selected = this.selectedModelLayerOption(layers);
      if (selected == null) {
         return "Texture";
      } else {
         String var10000 = this.fitText(layerLabel(selected.spriteId()), Math.max(32, width - this.font.width("Texture: ")));
         return "Texture: " + var10000;
      }
   }

   private static String layerLabel(String spriteId) {
      if (spriteId != null && !spriteId.isBlank()) {
         int slash = spriteId.lastIndexOf(47);
         String label = slash >= 0 && slash < spriteId.length() - 1 ? spriteId.substring(slash + 1) : spriteId;
         int colon = label.indexOf(58);
         return colon >= 0 && colon < label.length() - 1 ? label.substring(colon + 1) : label;
      } else {
         return "default";
      }
   }

   private int clampLayerDropdownScroll(int scroll, List<ModelLayerOption> layers) {
      int maxScroll = Math.max(0, layers.size() - 8);
      return Math.max(0, Math.min(scroll, maxScroll));
   }

   private boolean selectedSupportsEmissiveMask() {
      SourceEntry selected = this.getSelectedEntry();
      return selected != null && (selected.kind == BloomConfigScreen.EntryKind.BLOCK || selected.kind == BloomConfigScreen.EntryKind.FLUID);
   }

   private Component emissiveMaskLabel() {
      return Component.literal("Emissive Mask: " + (this.selectedEmissiveMaskEnabled() ? "ON" : "OFF"));
   }

   private boolean selectedEmissiveMaskEnabled() {
      WorkingSourceMask mask = this.selectedWorkingSourceMask();
      return mask != null && mask.emissive;
   }

   private void toggleSelectedEmissiveMask() {
      WorkingSourceMask mask = this.selectedWorkingSourceMask();
      if (mask != null) {
         mask.emissive = !mask.emissive;
         BloomMaskAtlas.markDirty();
         this.updateModeWidgets();
      }
   }

   private WorkingSourceMask selectedWorkingSourceMask() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && this.selectedSupportsEmissiveMask()) {
         BloomMaskAtlas.ensureReady();
         SpriteResolution resolution = this.resolveSprite(selected, this.selectedFace);
         String spriteId = resolution == null ? guessSpriteId(selected, this.selectedFace) : resolution.spriteId();
         BloomMaskAtlas.SpriteInfo spriteInfo = BloomMaskAtlas.getSpriteInfo(spriteId);
         int spriteW = spriteInfo == null ? (resolution != null && resolution.sprite() != null ? Math.max(1, resolution.sprite().contents().width()) : 16) : spriteInfo.width();
         int spriteH = spriteInfo == null ? (resolution != null && resolution.sprite() != null ? Math.max(1, resolution.sprite().contents().height()) : 16) : spriteInfo.height();
         return this.getOrCreateWorkingSourceMask(selected, spriteId, spriteW, spriteH);
      } else {
         return null;
      }
   }

   private void updateResetButtons() {
      for(ResetControl control : this.resetControls) {
         control.button.active = control.canReset.getAsBoolean();
      }

   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      double fixedMouseX = this.fixedMouseX(mouseX);
      double fixedMouseY = this.fixedMouseY(mouseY);
      if (this.layerDropdownOpen && this.handleLayerDropdownScroll(fixedMouseX, fixedMouseY, verticalAmount)) {
         return true;
      } else if (this.stateDropdownOpen && this.handleStateDropdownScroll(fixedMouseX, fixedMouseY, verticalAmount)) {
         return true;
      } else {
         int listTop = this.listTop();
         int listBottom = this.listBottom();
         if (fixedMouseX >= (double)10.0F && fixedMouseX <= (double)(10 + this.listWidth) && fixedMouseY >= (double)listTop && fixedMouseY <= (double)listBottom) {
            if (!(Math.abs(horizontalAmount) > (double)0.0F) && !isShiftDown()) {
               this.listScroll = this.clampListScroll(this.listScroll - (int)Math.signum(verticalAmount));
            } else {
               double amount = Math.abs(horizontalAmount) > (double)0.0F ? horizontalAmount : verticalAmount;
               this.listHorizontalScroll = this.clampListHorizontalScroll(this.listHorizontalScroll - (int)Math.signum(amount) * 24);
            }

            this.saveSessionState();
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean playSound) {
      double mouseX = this.fixedMouseX(event.x());
      double mouseY = this.fixedMouseY(event.y());
      if (this.layerDropdownOpen && this.handleLayerDropdownClick(mouseX, mouseY)) {
         return true;
      } else if (this.stateDropdownOpen && this.handleStateDropdownClick(mouseX, mouseY)) {
         return true;
      } else if (event.button() == 0 && this.handleListScrollbarClick(mouseX, mouseY)) {
         return true;
      } else if (this.handleListClick(mouseX, mouseY)) {
         this.stateDropdownOpen = false;
         this.layerDropdownOpen = false;
         return true;
      } else {
         return this.handleMaskPaint(mouseX, mouseY, event.button(), true) ? true : super.mouseClicked(event, playSound);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      double mouseX = this.fixedMouseX(event.x());
      double mouseY = this.fixedMouseY(event.y());
      if (this.draggingListVerticalScrollbar) {
         this.setListScrollFromMouse(mouseY);
         return true;
      } else if (this.draggingListHorizontalScrollbar) {
         this.setListHorizontalScrollFromMouse(mouseX);
         return true;
      } else {
         return this.painting && this.handleMaskPaint(mouseX, mouseY, event.button(), false) ? true : super.mouseDragged(event, dragX, dragY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.painting = false;
      this.draggingListVerticalScrollbar = false;
      this.draggingListHorizontalScrollbar = false;
      return super.mouseReleased(event);
   }

   private boolean handleListScrollbarClick(double mouseX, double mouseY) {
      int listX = 10;
      int verticalX = this.listVerticalScrollbarX();
      int horizontalY = this.listHorizontalScrollbarY();
      if (mouseX >= (double)verticalX && mouseX <= (double)(listX + this.listWidth) && mouseY >= (double)this.listTop() && mouseY <= (double)horizontalY) {
         this.draggingListVerticalScrollbar = true;
         this.setListScrollFromMouse(mouseY);
         return true;
      } else if (mouseX >= (double)listX && mouseX <= (double)verticalX && mouseY >= (double)horizontalY && mouseY <= (double)this.listBottom()) {
         this.draggingListHorizontalScrollbar = true;
         this.setListHorizontalScrollFromMouse(mouseX);
         return true;
      } else {
         return false;
      }
   }

   private void setListScrollFromMouse(double mouseY) {
      int maxScroll = Math.max(0, this.filteredEntries.size() - this.visibleListRows());
      if (maxScroll <= 0) {
         this.listScroll = 0;
      } else {
         int trackTop = this.listTop() + 2;
         int trackHeight = Math.max(1, this.listHorizontalScrollbarY() - trackTop - 1);
         int thumbHeight = this.listVerticalThumbHeight(trackHeight);
         int travel = Math.max(1, trackHeight - thumbHeight);
         double progress = (mouseY - (double)trackTop - (double)thumbHeight / (double)2.0F) / (double)travel;
         this.listScroll = this.clampListScroll((int)Math.round((double)maxScroll * Math.max((double)0.0F, Math.min((double)1.0F, progress))));
         this.saveSessionState();
      }
   }

   private void setListHorizontalScrollFromMouse(double mouseX) {
      int maxScroll = this.maxListHorizontalScroll();
      if (maxScroll <= 0) {
         this.listHorizontalScroll = 0;
      } else {
         int trackLeft = 12;
         int trackWidth = Math.max(1, this.listVerticalScrollbarX() - trackLeft - 1);
         int thumbWidth = this.listHorizontalThumbWidth(trackWidth);
         int travel = Math.max(1, trackWidth - thumbWidth);
         double progress = (mouseX - (double)trackLeft - (double)thumbWidth / (double)2.0F) / (double)travel;
         this.listHorizontalScroll = this.clampListHorizontalScroll((int)Math.round((double)maxScroll * Math.max((double)0.0F, Math.min((double)1.0F, progress))));
         this.saveSessionState();
      }
   }

   private int listVerticalThumbHeight(int trackHeight) {
      int totalRows = Math.max(this.visibleListRows(), this.filteredEntries.size());
      if (totalRows <= 0) {
         return trackHeight;
      } else {
         int thumbHeight = trackHeight * this.visibleListRows() / totalRows;
         return Math.min(trackHeight, Math.max(12, thumbHeight));
      }
   }

   private int listHorizontalThumbWidth(int trackWidth) {
      int contentWidth = Math.max(this.visibleListTextWidth(), this.maxListTextWidth());
      if (contentWidth <= 0) {
         return trackWidth;
      } else {
         int thumbWidth = trackWidth * this.visibleListTextWidth() / contentWidth;
         return Math.min(trackWidth, Math.max(12, thumbWidth));
      }
   }

   private boolean handleListClick(double mouseX, double mouseY) {
      int listX = 10;
      int contentTop = this.listContentTop();
      int contentBottom = this.listContentBottom();
      if (!(mouseX < (double)listX) && !(mouseX > (double)this.listVerticalScrollbarX()) && !(mouseY < (double)contentTop) && !(mouseY > (double)contentBottom)) {
         int row = (int)((mouseY - (double)contentTop) / (double)14.0F);
         int index = this.listScroll + row;
         if (index >= 0 && index < this.filteredEntries.size()) {
            this.selectedIndex = index;
            this.saveSessionState();
            this.updateModeWidgets();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean handleStateDropdownClick(double mouseX, double mouseY) {
      if (this.stateButton != null && this.stateButton.visible) {
         List<BlockState> states = this.selectedBlockStates();
         int rowHeight = 14;
         int rows = Math.min(8, states.size());
         int x = this.stateButton.getX();
         int y = this.stateButton.getY() + this.stateButton.getHeight() + 1;
         int width = this.stateButton.getWidth();
         int height = rows * rowHeight + 4;
         if (mouseX >= (double)this.stateButton.getX() && mouseX <= (double)(this.stateButton.getX() + this.stateButton.getWidth()) && mouseY >= (double)this.stateButton.getY() && mouseY <= (double)(this.stateButton.getY() + this.stateButton.getHeight())) {
            this.stateDropdownOpen = false;
            return true;
         } else if (!(mouseX < (double)x) && !(mouseX > (double)(x + width)) && !(mouseY < (double)y) && !(mouseY > (double)(y + height))) {
            int row = (int)((mouseY - (double)y - (double)2.0F) / (double)rowHeight);
            int index = this.stateDropdownScroll + row;
            if (index >= 0 && index < states.size()) {
               SourceEntry selected = this.getSelectedEntry();
               if (selected != null) {
                  LAST_SELECTED_STATE_BY_BLOCK.put(selected.id, BloomSelection.blockStateKey((BlockState)states.get(index)));
               }

               this.stateDropdownOpen = false;
               this.stateDropdownScroll = 0;
               this.updateModeWidgets();
               this.saveSessionState();
               return true;
            } else {
               return true;
            }
         } else {
            this.stateDropdownOpen = false;
            return false;
         }
      } else {
         this.stateDropdownOpen = false;
         return false;
      }
   }

   private boolean handleStateDropdownScroll(double mouseX, double mouseY, double verticalAmount) {
      if (this.stateButton != null && this.stateButton.visible) {
         List<BlockState> states = this.selectedBlockStates();
         int rows = Math.min(8, states.size());
         int x = this.stateButton.getX();
         int y = this.stateButton.getY() + this.stateButton.getHeight() + 1;
         int width = this.stateButton.getWidth();
         int height = rows * 14 + 4;
         if (!(mouseX < (double)x) && !(mouseX > (double)(x + width)) && !(mouseY < (double)y) && !(mouseY > (double)(y + height))) {
            this.stateDropdownScroll = this.clampStateDropdownScroll(this.stateDropdownScroll - (int)Math.signum(verticalAmount), states);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean handleLayerDropdownClick(double mouseX, double mouseY) {
      if (this.layerButton != null && this.layerButton.visible) {
         List<ModelLayerOption> layers = this.selectedModelLayerOptions();
         int rowHeight = 14;
         int rows = Math.min(8, layers.size());
         int x = this.layerButton.getX();
         int y = this.layerButton.getY() + this.layerButton.getHeight() + 1;
         int width = this.layerButton.getWidth();
         int height = rows * rowHeight + 4;
         if (mouseX >= (double)this.layerButton.getX() && mouseX <= (double)(this.layerButton.getX() + this.layerButton.getWidth()) && mouseY >= (double)this.layerButton.getY() && mouseY <= (double)(this.layerButton.getY() + this.layerButton.getHeight())) {
            this.layerDropdownOpen = false;
            return true;
         } else if (!(mouseX < (double)x) && !(mouseX > (double)(x + width)) && !(mouseY < (double)y) && !(mouseY > (double)(y + height))) {
            int row = (int)((mouseY - (double)y - (double)2.0F) / (double)rowHeight);
            int index = this.layerDropdownScroll + row;
            if (index >= 0 && index < layers.size()) {
               SourceEntry selected = this.getSelectedEntry();
               String key = this.selectedLayerSessionKey(selected);
               if (key != null) {
                  LAST_SELECTED_LAYER_BY_SOURCE.put(key, ((ModelLayerOption)layers.get(index)).spriteId());
               }

               this.layerDropdownOpen = false;
               this.layerDropdownScroll = 0;
               this.updateModeWidgets();
               this.saveSessionState();
               return true;
            } else {
               return true;
            }
         } else {
            this.layerDropdownOpen = false;
            return false;
         }
      } else {
         this.layerDropdownOpen = false;
         return false;
      }
   }

   private boolean handleLayerDropdownScroll(double mouseX, double mouseY, double verticalAmount) {
      if (this.layerButton != null && this.layerButton.visible) {
         List<ModelLayerOption> layers = this.selectedModelLayerOptions();
         int rows = Math.min(8, layers.size());
         int x = this.layerButton.getX();
         int y = this.layerButton.getY() + this.layerButton.getHeight() + 1;
         int width = this.layerButton.getWidth();
         int height = rows * 14 + 4;
         if (!(mouseX < (double)x) && !(mouseX > (double)(x + width)) && !(mouseY < (double)y) && !(mouseY > (double)(y + height))) {
            this.layerDropdownScroll = this.clampLayerDropdownScroll(this.layerDropdownScroll - (int)Math.signum(verticalAmount), layers);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean handleMaskPaint(double mouseX, double mouseY, int button, boolean start) {
      SpritePaintTarget target = this.getPaintTarget();
      if (target == null) {
         return false;
      } else if (target.canvasWidth > 0 && target.canvasHeight > 0) {
         int px = (int)((mouseX - (double)target.canvasX) * (double)target.mask.width / (double)target.canvasWidth);
         int py = (int)((mouseY - (double)target.canvasY) * (double)target.mask.height / (double)target.canvasHeight);
         if (px >= 0 && py >= 0 && px < target.mask.width && py < target.mask.height) {
            if (start && button < 0) {
               return false;
            } else {
               if (start) {
                  this.painting = true;
                  this.paintValue = button == 0 && !isShiftDown();
                  if (this.paintTool == BloomConfigScreen.PaintTool.FILL) {
                     Arrays.fill(target.mask.bits, this.paintValue);
                     this.painting = false;
                     BloomMaskAtlas.markDirty();
                     BloomEntityMaskTextures.markDirty();
                     return true;
                  }
               }

               this.applyBrush(target, px, py, this.paintValue);
               BloomMaskAtlas.markDirty();
               BloomEntityMaskTextures.markDirty();
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void applyBrush(SpritePaintTarget target, int centerX, int centerY, boolean value) {
      int size = Math.max(1, this.brushSize);
      int minX = centerX - (size - 1) / 2;
      int minY = centerY - (size - 1) / 2;

      for(int y = 0; y < size; ++y) {
         int py = minY + y;
         if (py >= 0 && py < target.mask.height) {
            for(int x = 0; x < size; ++x) {
               int px = minX + x;
               if (px >= 0 && px < target.mask.width) {
                  target.mask.bits[py * target.mask.width + px] = value;
               }
            }
         }
      }

   }

   private static boolean isShiftDown() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getWindow() != null) {
         Window window = minecraft.getWindow();
         return InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
      } else {
         return false;
      }
   }

   private SpritePaintTarget getPaintTarget() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && selected.kind != BloomConfigScreen.EntryKind.PARTICLE) {
         if (selected.kind == BloomConfigScreen.EntryKind.ENTITY_TEXTURE) {
            BloomEntityMaskTextures.TextureSize size = BloomEntityMaskTextures.getTextureSize(selected.id);
            int width = size == null ? 16 : size.width();
            int height = size == null ? 16 : size.height();
            WorkingMask workingMask = (WorkingMask)this.workingEntityMasks.computeIfAbsent(selected.id, (id) -> new WorkingMask(id, width, height, BloomMaskConfig.readEntityTextureMask(id, width, height)));
            SpritePaintTarget target = this.createPaintTargetLayout(workingMask, this.maskCanvasTop);
            return new SpritePaintTarget(target.mask, (TextureAtlasSprite)null, Identifier.parse(selected.id), target.canvasX, target.canvasY, target.canvasWidth, target.canvasHeight);
         } else {
            BloomMaskAtlas.ensureReady();
            SpriteResolution resolution = this.resolveSprite(selected, this.selectedFace);
            String spriteId = resolution == null ? guessSpriteId(selected, this.selectedFace) : resolution.spriteId();
            TextureAtlasSprite sprite = resolution == null ? null : resolution.sprite();
            BloomMaskAtlas.SpriteInfo spriteInfo = BloomMaskAtlas.getSpriteInfo(spriteId);
            int spriteW = spriteInfo == null ? (sprite == null ? 16 : Math.max(1, sprite.contents().width())) : spriteInfo.width();
            int spriteH = spriteInfo == null ? (sprite == null ? 16 : Math.max(1, sprite.contents().height())) : spriteInfo.height();
            WorkingSourceMask sourceMask = this.getOrCreateWorkingSourceMask(selected, spriteId, spriteW, spriteH);
            WorkingMask workingMask = new WorkingMask(spriteId, sourceMask.width, sourceMask.height, sourceMask.bits);
            SpritePaintTarget target = this.createPaintTargetLayout(workingMask, this.maskCanvasTop);
            return new SpritePaintTarget(workingMask, sprite, (Identifier)null, target.canvasX, target.canvasY, target.canvasWidth, target.canvasHeight);
         }
      } else {
         return null;
      }
   }

   private WorkingSourceMask getOrCreateWorkingSourceMask(SourceEntry selected, String spriteId, int width, int height) {
      boolean fluid = selected.kind == BloomConfigScreen.EntryKind.FLUID;
      String stateKey = this.selectedSourceStateKey(selected);
      String layerSpriteId = this.selectedSourceLayerSpriteId(selected, spriteId);
      String key = sourceMaskSessionKey(selected.id, fluid, this.selectedFace, stateKey, layerSpriteId);
      return (WorkingSourceMask)this.workingSourceMasks.computeIfAbsent(key, (ignored) -> new WorkingSourceMask(selected.id, fluid, this.selectedFace, stateKey, layerSpriteId, spriteId, width, height, BloomMaskConfig.readSourceMask(selected.id, fluid, this.selectedFace, stateKey, layerSpriteId, spriteId, width, height), BloomMaskConfig.isSourceMaskEmissive(selected.id, fluid, this.selectedFace, stateKey, layerSpriteId)));
   }

   private SpritePaintTarget createPaintTargetLayout(WorkingMask workingMask, int canvasTop) {
      int panelX = this.maskPanelX;
      int maxCanvasWidth = Math.max(56, Math.min(320, this.maskPanelWidth - 6));
      int maxCanvasHeight = Math.max(24, this.toolRowY - canvasTop - 10);
      int maxCanvas = Math.max(24, Math.min(maxCanvasWidth, maxCanvasHeight));
      int maxDimension = Math.max(1, Math.max(workingMask.width, workingMask.height));
      int integerScale = Math.max(1, Math.min(24, maxCanvas / maxDimension));
      int canvasWidth = Math.min(maxCanvas, workingMask.width * integerScale);
      int canvasHeight = Math.min(maxCanvas, workingMask.height * integerScale);
      int canvasX = panelX + Math.max(0, (this.maskPanelWidth - canvasWidth) / 2);
      return new SpritePaintTarget(workingMask, (TextureAtlasSprite)null, (Identifier)null, canvasX, canvasTop, canvasWidth, canvasHeight);
   }

   private SpriteResolution resolveSprite(SourceEntry entry, Direction face) {
      return this.resolveSpriteForState(entry, face, this.selectedBlockState(), "", "");
   }

   private SpriteResolution resolveSpriteForState(SourceEntry entry, Direction face, BlockState preferredState, String preferredLayerSpriteId, String preferredSpriteId) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getTextureManager() != null) {
         AbstractTexture var8 = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
         if (!(var8 instanceof TextureAtlas)) {
            return null;
         } else {
            TextureAtlas atlas = (TextureAtlas)var8;
            if (entry.kind == BloomConfigScreen.EntryKind.FLUID) {
               String spriteId = guessSpriteId(entry, face);
               TextureAtlasSprite sprite = atlas.getSprite(Identifier.parse(spriteId));
               return sprite != null && sprite != atlas.missingSprite() ? new SpriteResolution(sprite.contents().name().toString(), sprite) : new SpriteResolution(spriteId, (TextureAtlasSprite)null);
            } else {
               Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.id));
               if (block == null) {
                  return null;
               } else {
                  List<ModelLayerOption> layers = this.resolveModelLayerOptions(entry, face, preferredState);
                  ModelLayerOption selectedLayer = null;
                  if (preferredLayerSpriteId != null && !preferredLayerSpriteId.isBlank()) {
                     for(ModelLayerOption layer : layers) {
                        if (preferredLayerSpriteId.equals(layer.spriteId())) {
                           selectedLayer = layer;
                           break;
                        }
                     }
                  }

                  if (selectedLayer == null && preferredSpriteId != null && !preferredSpriteId.isBlank()) {
                     for(ModelLayerOption layer : layers) {
                        if (preferredSpriteId.equals(layer.spriteId())) {
                           selectedLayer = layer;
                           break;
                        }
                     }
                  }

                  if (selectedLayer == null) {
                     selectedLayer = this.selectedModelLayerOption(layers);
                  }

                  return selectedLayer != null && selectedLayer.sprite() != null ? new SpriteResolution(selectedLayer.spriteId(), selectedLayer.sprite()) : null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private List<ModelLayerOption> resolveModelLayerOptions(SourceEntry entry, Direction face) {
      return this.resolveModelLayerOptions(entry, face, this.selectedBlockState());
   }

   private List<ModelLayerOption> resolveModelLayerOptions(SourceEntry entry, Direction face, BlockState preferredState) {
      if (entry != null && entry.kind == BloomConfigScreen.EntryKind.BLOCK) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft != null && minecraft.getTextureManager() != null) {
            AbstractTexture var7 = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            TextureAtlas var10000;
            if (var7 instanceof TextureAtlas) {
               TextureAtlas blockAtlas = (TextureAtlas)var7;
               var10000 = blockAtlas;
            } else {
               var10000 = null;
            }

            TextureAtlas atlas = var10000;

            Block block;
            try {
               block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.id));
            } catch (Exception var10) {
               return List.of();
            }

            if (block == null) {
               return List.of();
            } else {
               BlockState state = this.selectedBlockState();
               if (preferredState != null && preferredState.getBlock() == block) {
                  state = preferredState;
               }

               if (state == null || state.getBlock() != block) {
                  state = block.defaultBlockState();
               }

               BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
               List<ModelLayerOption> layers = new ArrayList(resolveModelLayerOptions(model, face));
               addSiblingEmissiveLayerOptions(layers, atlas);
               return layers;
            }
         } else {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static List<ModelLayerOption> resolveModelLayerOptions(BlockStateModel model, Direction face) {
      if (model == null) {
         return List.of();
      } else {
         List<BlockStateModelPart> parts = new ArrayList();
         model.collectParts(RandomSource.create(42L), parts);
         List<ModelLayerOption> layers = new ArrayList();
         addModelLayerOptionsForFace(layers, parts, face);
         if (!layers.isEmpty()) {
            return layers;
         } else {
            for(Direction direction : Direction.values()) {
               addModelLayerOptionsForFace(layers, parts, direction);
            }

            if (!layers.isEmpty()) {
               return layers;
            } else {
               for(BlockStateModelPart part : parts) {
                  if (part != null && part.particleMaterial() != null) {
                     addModelLayerOption(layers, part.particleMaterial().sprite());
                  }
               }

               return layers;
            }
         }
      }
   }

   private static void addModelLayerOptionsForFace(List<ModelLayerOption> layers, List<BlockStateModelPart> parts, Direction face) {
      if (layers != null && parts != null) {
         for(BlockStateModelPart part : parts) {
            if (part != null) {
               List<BakedQuad> quads = part.getQuads(face);
               if (quads != null && !quads.isEmpty()) {
                  for(BakedQuad quad : quads) {
                     if (quad != null) {
                        addModelLayerOption(layers, quad.materialInfo().sprite());
                     }
                  }
               }
            }
         }

      }
   }

   private static void addModelLayerOption(List<ModelLayerOption> layers, TextureAtlasSprite sprite) {
      if (layers != null && sprite != null) {
         String spriteId = sprite.contents().name().toString();

         for(ModelLayerOption layer : layers) {
            if (spriteId.equals(layer.spriteId())) {
               return;
            }
         }

         layers.add(new ModelLayerOption(spriteId, sprite));
      }
   }

   private static void addSiblingEmissiveLayerOptions(List<ModelLayerOption> layers, TextureAtlas atlas) {
      if (layers != null && !layers.isEmpty() && atlas != null) {
         for(ModelLayerOption layer : new ArrayList(layers)) {
            String spriteId = layer.spriteId();
            if (spriteId != null && !spriteId.isBlank() && !spriteId.endsWith("_emissive")) {
               String emissiveSpriteId = spriteId + "_emissive";

               try {
                  TextureAtlasSprite emissiveSprite = atlas.getSprite(Identifier.parse(emissiveSpriteId));
                  if (emissiveSprite != null && emissiveSprite != atlas.missingSprite()) {
                     addModelLayerOption(layers, emissiveSprite);
                  }
               } catch (Exception var8) {
               }
            }
         }

      }
   }

   private static TextureAtlasSprite resolveModelSprite(BlockStateModel model, Direction face) {
      if (model == null) {
         return null;
      } else {
         List<BlockStateModelPart> parts = new ArrayList();
         model.collectParts(RandomSource.create(42L), parts);
         TextureAtlasSprite sprite = resolveModelSpriteForFace(parts, face);
         if (sprite != null) {
            return sprite;
         } else {
            for(Direction direction : Direction.values()) {
               sprite = resolveModelSpriteForFace(parts, direction);
               if (sprite != null) {
                  return sprite;
               }
            }

            for(BlockStateModelPart part : parts) {
               if (part != null && part.particleMaterial() != null) {
                  return part.particleMaterial().sprite();
               }
            }

            return null;
         }
      }
   }

   private static TextureAtlasSprite resolveModelSpriteForFace(List<BlockStateModelPart> parts, Direction face) {
      if (parts == null) {
         return null;
      } else {
         for(BlockStateModelPart part : parts) {
            if (part != null) {
               List<BakedQuad> quads = part.getQuads(face);
               if (quads != null && !quads.isEmpty()) {
                  TextureAtlasSprite sprite = ((BakedQuad)quads.get(0)).materialInfo().sprite();
                  if (sprite != null) {
                     return sprite;
                  }
               }
            }
         }

         return null;
      }
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.updateResetButtons();
      super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
      this.renderResetIcons(guiGraphics);
      guiGraphics.text(this.font, this.title, 10, 10, -1);
      String description = this.hoverDescription(mouseX, mouseY);
      if (!description.isBlank()) {
         guiGraphics.centeredText(this.font, Component.literal(this.fitText(description, this.width - 40)), this.width / 2, 26, -4342339);
      }

      this.renderEntryList(guiGraphics);
      this.renderSelectionDetails(guiGraphics);
      this.renderMaskCanvas(guiGraphics, mouseX, mouseY);
      this.renderStateDropdown(guiGraphics);
      this.renderLayerDropdown(guiGraphics);
   }

   private void renderEntryList(GuiGraphicsExtractor guiGraphics) {
      int listX = 10;
      int listTop = this.listTop();
      int listBottom = this.listBottom();
      guiGraphics.fill(listX, listTop, listX + this.listWidth, listBottom, 1996488704);
      this.listScroll = this.clampListScroll(this.listScroll);
      this.listHorizontalScroll = this.clampListHorizontalScroll(this.listHorizontalScroll);
      int contentLeft = this.listContentLeft();
      int contentRight = this.listContentRight();
      int contentTop = this.listContentTop();
      int contentBottom = this.listContentBottom();
      int maxRows = this.visibleListRows();
      int end = Math.min(this.filteredEntries.size(), this.listScroll + maxRows);
      int y = contentTop;
      guiGraphics.enableScissor(contentLeft, contentTop - 1, contentRight, contentBottom);
      if (this.filteredEntries.isEmpty()) {
         guiGraphics.text(this.font, Component.literal("No matches"), contentLeft - this.listHorizontalScroll, contentTop, -5592406);
         guiGraphics.disableScissor();
         this.renderListScrollbars(guiGraphics);
      } else {
         for(int i = this.listScroll; i < end; ++i) {
            SourceEntry entry = (SourceEntry)this.filteredEntries.get(i);
            boolean selected = i == this.selectedIndex;
            if (selected) {
               guiGraphics.fill(contentLeft - 2, y - 1, contentRight, y + 14 - 2, 1442840575);
            }

            guiGraphics.text(this.font, entry.id, contentLeft - this.listHorizontalScroll, y, selected ? -1 : -2236963);
            y += 14;
         }

         guiGraphics.disableScissor();
         this.renderListScrollbars(guiGraphics);
      }
   }

   private void renderListScrollbars(GuiGraphicsExtractor guiGraphics) {
      int listX = 10;
      int listTop = this.listTop();
      int listBottom = this.listBottom();
      int verticalX = this.listVerticalScrollbarX();
      int horizontalY = this.listHorizontalScrollbarY();
      int trackColor = -1441787888;
      int borderColor = -14540254;
      int enabledThumb = -6645094;
      int disabledThumb = -11184811;
      guiGraphics.fill(verticalX, listTop, listX + this.listWidth, horizontalY, trackColor);
      guiGraphics.fill(listX, horizontalY, verticalX, listBottom, trackColor);
      guiGraphics.fill(verticalX, horizontalY, listX + this.listWidth, listBottom, -872415232);
      int verticalTrackTop = listTop + 2;
      int verticalTrackHeight = Math.max(1, horizontalY - verticalTrackTop - 1);
      int verticalThumbHeight = this.listVerticalThumbHeight(verticalTrackHeight);
      int maxVerticalScroll = Math.max(0, this.filteredEntries.size() - this.visibleListRows());
      int verticalTravel = Math.max(0, verticalTrackHeight - verticalThumbHeight);
      int verticalThumbY = verticalTrackTop + (maxVerticalScroll == 0 ? 0 : Math.round((float)verticalTravel * ((float)this.listScroll / (float)maxVerticalScroll)));
      int verticalThumbColor = maxVerticalScroll > 0 ? enabledThumb : disabledThumb;
      guiGraphics.fill(verticalX + 1, verticalThumbY, listX + this.listWidth - 1, verticalThumbY + verticalThumbHeight, verticalThumbColor);
      guiGraphics.fill(verticalX, listTop, verticalX + 1, horizontalY, borderColor);
      int horizontalTrackLeft = listX + 2;
      int horizontalTrackWidth = Math.max(1, verticalX - horizontalTrackLeft - 1);
      int horizontalThumbWidth = this.listHorizontalThumbWidth(horizontalTrackWidth);
      int maxHorizontalScroll = this.maxListHorizontalScroll();
      int horizontalTravel = Math.max(0, horizontalTrackWidth - horizontalThumbWidth);
      int horizontalThumbX = horizontalTrackLeft + (maxHorizontalScroll == 0 ? 0 : Math.round((float)horizontalTravel * ((float)this.listHorizontalScroll / (float)maxHorizontalScroll)));
      int horizontalThumbColor = maxHorizontalScroll > 0 ? enabledThumb : disabledThumb;
      guiGraphics.fill(horizontalThumbX, horizontalY + 1, horizontalThumbX + horizontalThumbWidth, listBottom - 1, horizontalThumbColor);
      guiGraphics.fill(listX, horizontalY, verticalX, horizontalY + 1, borderColor);
   }

   private void renderSelectionDetails(GuiGraphicsExtractor guiGraphics) {
      SourceEntry entry = this.getSelectedEntry();
      int x = this.maskPanelX;
      int y = 64;
      if (entry == null) {
         guiGraphics.text(this.font, Component.translatable("shine.editor.no_selection"), x, y, -4473925);
      } else {
         String label = entry.id;
         if (entry.kind == BloomConfigScreen.EntryKind.BLOCK || entry.kind == BloomConfigScreen.EntryKind.FLUID) {
            label = label + " / " + this.selectedFace.getName().toUpperCase(Locale.ROOT);
         }

         guiGraphics.text(this.font, Component.literal(this.fitText(label, this.maskPanelWidth)), x, y, -1);
      }
   }

   private void renderStateDropdown(GuiGraphicsExtractor guiGraphics) {
      if (this.stateDropdownOpen && this.stateButton != null && this.stateButton.visible) {
         List<BlockState> states = this.selectedBlockStates();
         if (states.size() <= 1) {
            this.stateDropdownOpen = false;
         } else {
            this.stateDropdownScroll = this.clampStateDropdownScroll(this.stateDropdownScroll, states);
            int rowHeight = 14;
            int rows = Math.min(8, states.size());
            int x = this.stateButton.getX();
            int y = this.stateButton.getY() + this.stateButton.getHeight() + 1;
            int width = this.stateButton.getWidth();
            int height = rows * rowHeight + 4;
            guiGraphics.fill(x, y, x + width, y + height, -301660923);
            renderOutline(guiGraphics, x, y, x + width, y + height, -8947849);
            String current = this.selectedBlockStateKey();

            for(int row = 0; row < rows; ++row) {
               int index = this.stateDropdownScroll + row;
               if (index >= 0 && index < states.size()) {
                  BlockState state = (BlockState)states.get(index);
                  String key = BloomSelection.blockStateKey(state);
                  int rowY = y + 2 + row * rowHeight;
                  boolean selected = key.equals(current);
                  if (selected) {
                     guiGraphics.fill(x + 1, rowY - 1, x + width - 1, rowY + rowHeight - 1, 1728053247);
                  }

                  guiGraphics.text(this.font, Component.literal(this.fitText(stateLabel(state), width - 8)), x + 4, rowY, selected ? -1 : -2236963);
               }
            }

         }
      }
   }

   private void renderLayerDropdown(GuiGraphicsExtractor guiGraphics) {
      if (this.layerDropdownOpen && this.layerButton != null && this.layerButton.visible) {
         List<ModelLayerOption> layers = this.selectedModelLayerOptions();
         if (layers.size() <= 1) {
            this.layerDropdownOpen = false;
         } else {
            this.layerDropdownScroll = this.clampLayerDropdownScroll(this.layerDropdownScroll, layers);
            int rowHeight = 14;
            int rows = Math.min(8, layers.size());
            int x = this.layerButton.getX();
            int y = this.layerButton.getY() + this.layerButton.getHeight() + 1;
            int width = this.layerButton.getWidth();
            int height = rows * rowHeight + 4;
            guiGraphics.fill(x, y, x + width, y + height, -301660923);
            renderOutline(guiGraphics, x, y, x + width, y + height, -8947849);
            ModelLayerOption current = this.selectedModelLayerOption(layers);
            String currentSpriteId = current == null ? "" : current.spriteId();

            for(int row = 0; row < rows; ++row) {
               int index = this.layerDropdownScroll + row;
               if (index >= 0 && index < layers.size()) {
                  ModelLayerOption layer = (ModelLayerOption)layers.get(index);
                  int rowY = y + 2 + row * rowHeight;
                  boolean selected = layer.spriteId().equals(currentSpriteId);
                  if (selected) {
                     guiGraphics.fill(x + 1, rowY - 1, x + width - 1, rowY + rowHeight - 1, 1728053247);
                  }

                  guiGraphics.text(this.font, Component.literal(this.fitText(layerLabel(layer.spriteId()), width - 8)), x + 4, rowY, selected ? -1 : -2236963);
               }
            }

         }
      }
   }

   private String fitText(String text, int width) {
      if (this.font.width(text) <= width) {
         return text;
      } else {
         String var10000 = this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("...")));
         return var10000 + "...";
      }
   }

   private void renderMaskCanvas(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
      SpritePaintTarget target = this.getPaintTarget();
      if (target != null) {
         guiGraphics.fill(target.canvasX - 2, target.canvasY - 2, target.canvasX + target.canvasWidth + 2, target.canvasY + target.canvasHeight + 2, -12961222);
         this.renderCanvasBackground(guiGraphics, target);
         if (target.sprite != null) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, target.sprite, target.canvasX, target.canvasY, target.canvasWidth, target.canvasHeight);
         } else if (target.textureId != null) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, target.textureId, target.canvasX, target.canvasY, 0.0F, 0.0F, target.canvasWidth, target.canvasHeight, target.mask.width, target.mask.height, target.mask.width, target.mask.height);
         }

         for(int y = 0; y < target.mask.height; ++y) {
            int py0 = target.canvasY + (int)((long)y * (long)target.canvasHeight / (long)target.mask.height);
            int py1 = target.canvasY + (int)((long)(y + 1) * (long)target.canvasHeight / (long)target.mask.height);
            if (py1 > py0) {
               for(int x = 0; x < target.mask.width; ++x) {
                  int px0 = target.canvasX + (int)((long)x * (long)target.canvasWidth / (long)target.mask.width);
                  int px1 = target.canvasX + (int)((long)(x + 1) * (long)target.canvasWidth / (long)target.mask.width);
                  if (px1 > px0) {
                     boolean on = target.mask.bits[y * target.mask.width + x];
                     int color = on ? 905969663 : -1441787888;
                     guiGraphics.fill(px0, py0, px1, py1, color);
                  }
               }
            }
         }

         int cellWidth = target.canvasWidth / Math.max(1, target.mask.width);
         int cellHeight = target.canvasHeight / Math.max(1, target.mask.height);
         if (cellWidth >= 4 && cellHeight >= 4) {
            this.renderCanvasGrid(guiGraphics, target);
         }

         this.renderBrushPreview(guiGraphics, target, mouseX, mouseY);
      }
   }

   private void renderCanvasBackground(GuiGraphicsExtractor guiGraphics, SpritePaintTarget target) {
      int tileSize = 8;

      for(int y = target.canvasY; y < target.canvasY + target.canvasHeight; y += tileSize) {
         for(int x = target.canvasX; x < target.canvasX + target.canvasWidth; x += tileSize) {
            int x2 = Math.min(target.canvasX + target.canvasWidth, x + tileSize);
            int y2 = Math.min(target.canvasY + target.canvasHeight, y + tileSize);
            boolean dark = ((x - target.canvasX) / tileSize + (y - target.canvasY) / tileSize) % 2 == 0;
            guiGraphics.fill(x, y, x2, y2, dark ? -14671840 : -14079703);
         }
      }

   }

   private void renderCanvasGrid(GuiGraphicsExtractor guiGraphics, SpritePaintTarget target) {
      for(int x = 1; x < target.mask.width; ++x) {
         int gridX = target.canvasX + (int)((long)x * (long)target.canvasWidth / (long)target.mask.width);
         guiGraphics.fill(gridX, target.canvasY, gridX + 1, target.canvasY + target.canvasHeight, 1428300322);
      }

      for(int y = 1; y < target.mask.height; ++y) {
         int gridY = target.canvasY + (int)((long)y * (long)target.canvasHeight / (long)target.mask.height);
         guiGraphics.fill(target.canvasX, gridY, target.canvasX + target.canvasWidth, gridY + 1, 1428300322);
      }

   }

   private void renderBrushPreview(GuiGraphicsExtractor guiGraphics, SpritePaintTarget target, int mouseX, int mouseY) {
      if (mouseX >= target.canvasX && mouseX < target.canvasX + target.canvasWidth && mouseY >= target.canvasY && mouseY < target.canvasY + target.canvasHeight) {
         if (this.paintTool == BloomConfigScreen.PaintTool.FILL) {
            renderOutline(guiGraphics, target.canvasX, target.canvasY, target.canvasX + target.canvasWidth, target.canvasY + target.canvasHeight, -855638017);
         } else {
            int px = (mouseX - target.canvasX) * target.mask.width / target.canvasWidth;
            int py = (mouseY - target.canvasY) * target.mask.height / target.canvasHeight;
            int size = Math.max(1, this.brushSize);
            int minCellX = Math.max(0, px - (size - 1) / 2);
            int minCellY = Math.max(0, py - (size - 1) / 2);
            int maxCellX = Math.min(target.mask.width, minCellX + size);
            int maxCellY = Math.min(target.mask.height, minCellY + size);
            int x0 = target.canvasX + (int)((long)minCellX * (long)target.canvasWidth / (long)target.mask.width);
            int y0 = target.canvasY + (int)((long)minCellY * (long)target.canvasHeight / (long)target.mask.height);
            int x1 = target.canvasX + (int)((long)maxCellX * (long)target.canvasWidth / (long)target.mask.width);
            int y1 = target.canvasY + (int)((long)maxCellY * (long)target.canvasHeight / (long)target.mask.height);
            guiGraphics.fill(x0, y0, x1, y1, 1442840575);
            renderOutline(guiGraphics, x0, y0, x1, y1, -570425345);
         }
      }
   }

   private static void renderOutline(GuiGraphicsExtractor guiGraphics, int x0, int y0, int x1, int y1, int color) {
      guiGraphics.fill(x0, y0, x1, y0 + 1, color);
      guiGraphics.fill(x0, y1 - 1, x1, y1, color);
      guiGraphics.fill(x0, y0, x0 + 1, y1, color);
      guiGraphics.fill(x1 - 1, y0, x1, y1, color);
   }

   private SourceEntry getSelectedEntry() {
      return this.selectedIndex >= 0 && this.selectedIndex < this.filteredEntries.size() ? (SourceEntry)this.filteredEntries.get(this.selectedIndex) : null;
   }

   private double getSelectedStrength() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected == null) {
         return (double)0.0F;
      } else if (selected.kind == BloomConfigScreen.EntryKind.ENTITY_TEXTURE) {
         return (Double)this.editing.entityTextureStrengthOverrides.getOrDefault(selected.id, this.editing.defaultEntityTextureStrength);
      } else if (selected.kind == BloomConfigScreen.EntryKind.PARTICLE) {
         return (Double)this.editing.particleStrengthOverrides.getOrDefault(selected.id, this.editing.defaultParticleStrength);
      } else {
         String stateKey = this.selectedSourceStateKey(selected);
         Double stateOverride = stateKey.isBlank() ? null : (Double)this.editing.stateSourceStrengthOverrides.get(stateKey);
         if (stateOverride != null) {
            return stateOverride;
         } else {
            Double override = (Double)this.editing.sourceStrengthOverrides.get(selected.id);
            if (override != null) {
               return override;
            } else {
               Double baseline = (Double)this.defaults.sourceStrengthOverrides.get(selected.id);
               if (baseline != null) {
                  return baseline;
               } else {
                  return isLikelyLightSource(selected.id) ? this.editing.defaultLightSourceStrength : this.editing.defaultNonLightStrength;
               }
            }
         }
      }
   }

   private int getSelectedRadiusProfile() {
      SourceEntry selected = this.getSelectedEntry();
      return selected != null && (selected.kind == BloomConfigScreen.EntryKind.BLOCK || selected.kind == BloomConfigScreen.EntryKind.FLUID) ? Math.max(0, Math.min(2, (Integer)this.editing.sourceRadiusProfiles.getOrDefault(selected.id, 0))) : 0;
   }

   private void cycleSelectedRadiusProfile() {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null && (selected.kind == BloomConfigScreen.EntryKind.BLOCK || selected.kind == BloomConfigScreen.EntryKind.FLUID)) {
         int next = (this.getSelectedRadiusProfile() + 1) % 3;

         for(String id : linkedSourceIds(selected.id)) {
            if (next == 0) {
               this.editing.sourceRadiusProfiles.remove(id);
            } else {
               this.editing.sourceRadiusProfiles.put(id, next);
            }
         }

         this.sourceStrengthDirty = true;
         this.updateModeWidgets();
      }
   }

   private static String radiusProfileName(int profile) {
      String var10000;
      switch (profile) {
         case 1 -> var10000 = "Tiny";
         case 2 -> var10000 = "Broad";
         default -> var10000 = "Default";
      }

      return var10000;
   }

   private void setSelectedStrength(double value) {
      SourceEntry selected = this.getSelectedEntry();
      if (selected != null) {
         double clamped = Math.max((double)0.0F, Math.min((double)500.0F, value));
         if (selected.kind == BloomConfigScreen.EntryKind.ENTITY_TEXTURE) {
            setOverride(this.editing.entityTextureStrengthOverrides, selected.id, clamped, (Double)this.defaults.entityTextureStrengthOverrides.getOrDefault(selected.id, this.defaults.defaultEntityTextureStrength));
            this.sourceStrengthDirty = true;
         } else if (selected.kind == BloomConfigScreen.EntryKind.PARTICLE) {
            setOverride(this.editing.particleStrengthOverrides, selected.id, clamped, (Double)this.defaults.particleStrengthOverrides.getOrDefault(selected.id, this.defaults.defaultParticleStrength));
            this.sourceStrengthDirty = true;
         } else {
            String stateKey = this.selectedSourceStateKey(selected);
            if (!stateKey.isBlank()) {
               double baseline = this.blockBaselineStrength(selected.id);
               Double defaultStateOverride = (Double)this.defaults.stateSourceStrengthOverrides.get(stateKey);
               if (defaultStateOverride != null) {
                  baseline = defaultStateOverride;
               }

               setOverride(this.editing.stateSourceStrengthOverrides, stateKey, clamped, baseline);
               this.sourceStrengthDirty = true;
            } else {
               Double baseOverride = (Double)this.defaults.sourceStrengthOverrides.get(selected.id);
               double baseline;
               if (baseOverride != null) {
                  baseline = baseOverride;
               } else {
                  baseline = isLikelyLightSource(selected.id) ? this.defaults.defaultLightSourceStrength : this.defaults.defaultNonLightStrength;
               }

               if (Math.abs(clamped - baseline) < 1.0E-6) {
                  for(String id : linkedSourceIds(selected.id)) {
                     this.editing.sourceStrengthOverrides.remove(id);
                  }
               } else {
                  for(String id : linkedSourceIds(selected.id)) {
                     this.editing.sourceStrengthOverrides.put(id, clamped);
                  }
               }

               this.sourceStrengthDirty = true;
            }
         }
      }
   }

   private static void setOverride(Map<String, Double> overrides, String id, double value, double baseline) {
      if (Math.abs(value - baseline) < 1.0E-6) {
         overrides.remove(id);
      } else {
         overrides.put(id, value);
      }

   }

   private static List<String> linkedSourceIds(String id) {
      List var10000;
      switch (id) {
         case "minecraft:torch":
         case "minecraft:wall_torch":
            var10000 = List.of("minecraft:torch", "minecraft:wall_torch");
            break;
         case "minecraft:soul_torch":
         case "minecraft:soul_wall_torch":
            var10000 = List.of("minecraft:soul_torch", "minecraft:soul_wall_torch");
            break;
         case "minecraft:redstone_torch":
         case "minecraft:redstone_wall_torch":
            var10000 = List.of("minecraft:redstone_torch", "minecraft:redstone_wall_torch");
            break;
         case "minecraft:water":
         case "minecraft:flowing_water":
            var10000 = List.of("minecraft:water", "minecraft:flowing_water");
            break;
         case "minecraft:lava":
         case "minecraft:flowing_lava":
            var10000 = List.of("minecraft:lava", "minecraft:flowing_lava");
            break;
         default:
            var10000 = List.of(id);
      }

      return var10000;
   }

   private static String sourceMaskSessionKey(String sourceId, boolean fluid, Direction face, String stateKey, String layerSpriteId) {
      String sourcePart = stateKey != null && !stateKey.isBlank() ? stateKey : sourceId;
      return layerSpriteId != null && !layerSpriteId.isBlank() ? (fluid ? "fluid:" : "block:") + sourcePart + "#layer@" + layerSpriteId : (fluid ? "fluid:" : "block:") + sourcePart + "#" + face.getName();
   }

   private void markSourceStrengthDirty() {
      this.sourceStrengthDirty = true;
   }

   public void tick() {
      super.tick();
      if (this.sourceStrengthDirty) {
         this.sourceStrengthDirty = false;
         rebuildChunksForSourceStrengthChanges();
      }

   }

   private static String guessSpriteId(SourceEntry entry, Direction face) {
      Identifier id = Identifier.parse(entry.id);
      String namespace = id.getNamespace();
      String path = id.getPath();
      if (entry.kind == BloomConfigScreen.EntryKind.FLUID) {
         if (path.contains("water")) {
            String suffix = face != Direction.UP && face != Direction.DOWN ? "water_flow" : "water_still";
            return namespace + ":block/" + suffix;
         }

         if (path.contains("lava")) {
            String suffix = face != Direction.UP && face != Direction.DOWN ? "lava_flow" : "lava_still";
            return namespace + ":block/" + suffix;
         }
      }

      return namespace + ":block/" + path;
   }

   private static List<SourceEntry> collectEntries(EditorTab tab) {
      List var10000;
      switch (tab.ordinal()) {
         case 0 -> var10000 = collectBlockEntries();
         case 1 -> var10000 = collectEntityTextureEntries();
         case 2 -> var10000 = collectParticleEntries();
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static List<SourceEntry> collectBlockEntries() {
      Map<String, SourceEntry> entries = new LinkedHashMap();

      for(Block block : BuiltInRegistries.BLOCK) {
         Identifier id = BuiltInRegistries.BLOCK.getKey(block);
         if (id != null) {
            String key = id.toString();
            if (!"minecraft:air".equals(key) && !"minecraft:cave_air".equals(key) && !"minecraft:void_air".equals(key)) {
               entries.put(key, new SourceEntry(key, BloomConfigScreen.EntryKind.BLOCK, blockEmitsLight(block)));
            }
         }
      }

      for(Fluid fluid : BuiltInRegistries.FLUID) {
         Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
         if (id != null && !"minecraft:empty".equals(id.toString())) {
            String key = id.toString();
            entries.putIfAbsent(key, new SourceEntry(key, BloomConfigScreen.EntryKind.FLUID, false));
         }
      }

      List<SourceEntry> out = new ArrayList(entries.values());
      out.sort(Comparator.comparing((entry) -> entry.id));
      return out;
   }

   private static List<SourceEntry> collectEntityTextureEntries() {
      List<SourceEntry> out = new ArrayList();

      for(String textureId : BloomEntityTextureCatalog.entityTextures()) {
         out.add(new SourceEntry(textureId, BloomConfigScreen.EntryKind.ENTITY_TEXTURE, false));
      }

      out.sort(Comparator.comparing((entry) -> entry.id));
      return out;
   }

   private static List<SourceEntry> collectParticleEntries() {
      List<SourceEntry> out = new ArrayList();

      for(ParticleType<?> particleType : BuiltInRegistries.PARTICLE_TYPE) {
         Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particleType);
         if (id != null) {
            out.add(new SourceEntry(id.toString(), BloomConfigScreen.EntryKind.PARTICLE, false));
         }
      }

      out.sort(Comparator.comparing((entry) -> entry.id));
      return out;
   }

   private static boolean isLikelyLightSource(String sourceId) {
      try {
         Identifier id = Identifier.parse(sourceId);
         Block block = (Block)BuiltInRegistries.BLOCK.getValue(id);
         return block == null ? false : blockEmitsLight(block);
      } catch (Exception var3) {
         return false;
      }
   }

   private static boolean blockEmitsLight(Block block) {
      try {
         UnmodifiableIterator var1 = block.getStateDefinition().getPossibleStates().iterator();

         while(var1.hasNext()) {
            BlockState state = (BlockState)var1.next();
            if (state != null && state.getLightEmission() > 0) {
               return true;
            }
         }
      } catch (Exception var3) {
      }

      return block.defaultBlockState().getLightEmission() > 0;
   }

   private static String normalizeBlockId(String blockId) {
      if (blockId != null && !blockId.isBlank()) {
         try {
            Identifier id = Identifier.parse(blockId);
            return BuiltInRegistries.BLOCK.containsKey(id) ? id.toString() : null;
         } catch (Exception var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean changed(double current, double baseline) {
      return Math.abs(current - baseline) > 1.0E-6;
   }

   private double blockBaselineStrength(String sourceId) {
      Double baseOverride = (Double)this.editing.sourceStrengthOverrides.get(sourceId);
      if (baseOverride != null) {
         return baseOverride;
      } else {
         baseOverride = (Double)this.defaults.sourceStrengthOverrides.get(sourceId);
         if (baseOverride != null) {
            return baseOverride;
         } else {
            return isLikelyLightSource(sourceId) ? this.editing.defaultLightSourceStrength : this.editing.defaultNonLightStrength;
         }
      }
   }

   static {
      FACE_ORDER = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN);
      LAST_SEARCH_BY_TAB = new EnumMap(EditorTab.class);
      LAST_SELECTED_BY_TAB = new EnumMap(EditorTab.class);
      LAST_SELECTED_STATE_BY_BLOCK = new HashMap();
      LAST_SELECTED_LAYER_BY_SOURCE = new HashMap();
      LAST_SCROLL_BY_TAB = new EnumMap(EditorTab.class);
      LAST_HORIZONTAL_SCROLL_BY_TAB = new EnumMap(EditorTab.class);
      lastTab = BloomConfigScreen.EditorTab.BLOCKS;
      lastSelectedFace = Direction.NORTH;
      lastPaintTool = BloomConfigScreen.PaintTool.PAINT;
      lastBrushSize = 1;
   }

   private static enum EditorTab {
      BLOCKS("Blocks"),
      ENTITIES("Entities"),
      PARTICLES("Particles");

      private final String displayName;

      private EditorTab(String displayName) {
         this.displayName = displayName;
      }

      private Component label() {
         return Component.literal(this.displayName);
      }

      // $FF: synthetic method
      private static EditorTab[] $values() {
         return new EditorTab[]{BLOCKS, ENTITIES, PARTICLES};
      }
   }

   private static enum PaintTool {
      PAINT("Paint"),
      FILL("Fill");

      private final String displayName;

      private PaintTool(String displayName) {
         this.displayName = displayName;
      }

      // $FF: synthetic method
      private static PaintTool[] $values() {
         return new PaintTool[]{PAINT, FILL};
      }
   }

   private static enum EntryKind {
      BLOCK,
      FLUID,
      ENTITY_TEXTURE,
      PARTICLE;

      // $FF: synthetic method
      private static EntryKind[] $values() {
         return new EntryKind[]{BLOCK, FLUID, ENTITY_TEXTURE, PARTICLE};
      }
   }

   private static record SourceEntry(String id, EntryKind kind, boolean lightSource) {
   }

   private static record SourceMaskTarget(String sourceId, boolean fluid) {
   }

   private static final class WorkingMask {
      private final String spriteId;
      private final int width;
      private final int height;
      private final boolean[] bits;

      private WorkingMask(String spriteId, int width, int height, boolean[] bits) {
         this.spriteId = spriteId;
         this.width = width;
         this.height = height;
         this.bits = bits;
      }
   }

   private static final class WorkingSourceMask {
      private final String sourceId;
      private final boolean fluid;
      private final Direction face;
      private final String stateKey;
      private final String layerSpriteId;
      private final String spriteId;
      private final int width;
      private final int height;
      private final boolean[] bits;
      private boolean emissive;

      private WorkingSourceMask(String sourceId, boolean fluid, Direction face, String stateKey, String layerSpriteId, String spriteId, int width, int height, boolean[] bits, boolean emissive) {
         this.sourceId = sourceId;
         this.fluid = fluid;
         this.face = face;
         this.stateKey = stateKey == null ? "" : stateKey;
         this.layerSpriteId = layerSpriteId == null ? "" : layerSpriteId;
         this.spriteId = spriteId;
         this.width = width;
         this.height = height;
         this.bits = bits;
         this.emissive = emissive;
      }
   }

   private static record SpriteResolution(String spriteId, TextureAtlasSprite sprite) {
   }

   private static record ModelLayerOption(String spriteId, TextureAtlasSprite sprite) {
   }

   private static record SpritePaintTarget(WorkingMask mask, TextureAtlasSprite sprite, Identifier textureId, int canvasX, int canvasY, int canvasWidth, int canvasHeight) {
   }

   private static record ResetControl(Button button, BooleanSupplier canReset) {
   }

   private static record DescribedWidget(AbstractWidget widget, Supplier<String> description) {
   }

   private static final class LabeledSlider extends AbstractSliderButton {
      private final Component label;
      private final double min;
      private final double max;
      private final double step;
      private final DoubleSupplier getter;
      private final DoubleConsumer setter;
      private final DoubleFunction<String> formatter;
      private final Runnable onChanged;

      private LabeledSlider(int x, int y, int width, Component label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> formatter, Runnable onChanged) {
         super(x, y, width, 20, Component.empty(), (double)0.0F);
         this.label = label;
         this.min = min;
         this.max = max;
         this.step = step;
         this.getter = getter;
         this.setter = setter;
         this.formatter = formatter;
         this.onChanged = onChanged;
         this.syncFromValue();
      }

      public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
         this.syncFromValue();
         super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
      }

      private void syncFromValue() {
         double current = this.clamp(this.getter.getAsDouble());
         this.value = (current - this.min) / (this.max - this.min);
         this.updateMessage();
      }

      private double clamp(double value) {
         return Math.max(this.min, Math.min(this.max, value));
      }

      protected void updateMessage() {
         double resolved = this.min + this.value * (this.max - this.min);
         resolved = (double)Math.round(resolved / this.step) * this.step;
         this.setMessage(this.label.copy().append(": ").append((String)this.formatter.apply(this.clamp(resolved))));
      }

      protected void applyValue() {
         double resolved = this.min + this.value * (this.max - this.min);
         resolved = (double)Math.round(resolved / this.step) * this.step;
         this.setter.accept(this.clamp(resolved));
         if (this.onChanged != null) {
            this.onChanged.run();
         }

      }
   }
}
