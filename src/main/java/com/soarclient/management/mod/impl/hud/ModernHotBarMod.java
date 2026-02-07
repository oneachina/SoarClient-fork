package com.soarclient.management.mod.impl.hud;

import com.soarclient.event.EventBus;
import com.soarclient.event.client.RenderHotbarEvent;
import com.soarclient.event.client.RenderSkiaEvent;
import com.soarclient.management.mod.api.hud.HUDMod;
import com.soarclient.management.mod.settings.impl.BooleanSetting;
import com.soarclient.skia.font.Icon;
import com.soarclient.skia.font.Fonts;
import com.soarclient.skia.Skia;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;

@SuppressWarnings("all")
public class ModernHotBarMod extends HUDMod {

    private static ModernHotBarMod instance;

    private final Color COLOR_SURFACE = new Color(231, 224, 236, 160);
    private final Color COLOR_ON_SURFACE = new Color(29, 27, 32);
    private final Color COLOR_PRIMARY = new Color(160, 140, 218);
    private final Color COLOR_ACCENT = new Color(232, 222, 248);
    private final Color COLOR_ERR = new Color(218, 117, 111);
    private final Color COLOR_ERR_BG = new Color(249, 222, 220);
    private final Color COLOR_TRT = new Color(225, 145, 221);
    private final Color COLOR_TRT_BG = new Color(255, 216, 228);

    private final BooleanSetting showExperienceBar = new BooleanSetting("setting.showexperiencebar", "Exp Bar", Icon.SETTINGS, this, true);
    private final BooleanSetting showNumericalValues = new BooleanSetting("setting.shownumericalvalues", "Values", Icon.SETTINGS, this, true);

    private long lastTimeNanos = System.nanoTime();
    private int animTargetSlot = -1;
    private float animProgress = 1f;
    private float animatedSlotX = 0f;
    private float stretch = 0f;

    private float dispHP = 0f;
    private float dispHunger = 0f;
    private float dispExp = 0f;
    private float offhandAlpha = 0f;

    public ModernHotBarMod() {
        super("mod.modernhotbar.name", "MD3 Hotbar", Icon.SETTINGS);
        instance = this;
    }

    public static ModernHotBarMod getInstance() {
        if (instance == null) instance = new ModernHotBarMod();
        return instance;
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null || player.isSpectator()) return;

        long now = System.nanoTime();
        float delta = (now - lastTimeNanos) / 1_000_000_000.0f;
        lastTimeNanos = now;

        updateMotion(player, delta);

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        float hotbarW = 182f + 16f;
        float hotbarH = 28f;
        float x = (sw - hotbarW) / 2f;
        float y = sh - hotbarH - 12f;

        begin();

        Skia.drawRoundedRect(x, y, hotbarW, hotbarH, 8f, COLOR_SURFACE);

        boolean hasOffhand = !player.getOffHandStack().isEmpty();
        offhandAlpha = lerp(offhandAlpha, hasOffhand ? 1f : 0f, delta / 0.2f);

        if (offhandAlpha > 0.01f) {
            float offSize = 28f;
            float offX = x - offSize - 6f;
            int alphaInt = (int)(offhandAlpha * 160);
            Color offBg = new Color(COLOR_SURFACE.getRed(), COLOR_SURFACE.getGreen(), COLOR_SURFACE.getBlue(), alphaInt);
            Skia.drawRoundedRect(offX, y, offSize, hotbarH, 8f, offBg);
        }

        float indicatorW = 22f + (stretch * 12f);
        float indicatorX = x + 8f + animatedSlotX - (stretch * 6f) - 1f;
        Skia.drawRoundedRect(indicatorX, y + 3f, indicatorW, 22f, 6f, COLOR_ACCENT);

        if (!player.isCreative()) {
            float barW = 75f;
            float barH = 7f;
            float sY = y - barH - 10f;

            dispHP = lerp(dispHP, barW * MathHelper.clamp(player.getHealth() / player.getMaxHealth(), 0, 1), delta / 0.25f);
            drawBar(x, sY, barW, barH, dispHP, COLOR_ERR, COLOR_ERR_BG, (int)player.getHealth() + "");

            dispHunger = lerp(dispHunger, barW * MathHelper.clamp(player.getHungerManager().getFoodLevel() / 20f, 0, 1), delta / 0.25f);
            drawBar(x + hotbarW - barW, sY, barW, barH, dispHunger, COLOR_TRT, COLOR_TRT_BG, (int)player.getHungerManager().getFoodLevel() + "");
        }

        if (showExperienceBar.isEnabled() && player.experienceProgress > 0) {
            float eW = hotbarW - 16f;
            dispExp = lerp(dispExp, eW * player.experienceProgress, delta / 0.25f);
            Skia.drawRoundedRect(x + 8f, y + 1.5f, eW, 2f, 1f, new Color(255, 255, 255, 60));
            Skia.drawRoundedRect(x + 8f, y + 1.5f, dispExp, 2f, 1f, COLOR_PRIMARY);
        }

        finish();
    };

    private void drawBar(float x, float y, float w, float h, float fW, Color c, Color bg, String v) {
        Skia.drawRoundedRect(x, y, w, h, 3f, bg);
        if (fW > 0) Skia.drawRoundedRect(x, y, fW, h, 3f, c);
        if (showNumericalValues.isEnabled() && v != null) {
            Skia.drawHeightCenteredText(v, x + 5f, y + h/2f, COLOR_ON_SURFACE, Fonts.getRegular(6.5f));
        }
    }

    private void updateMotion(PlayerEntity p, float d) {
        int slot = p.getInventory().selectedSlot;
        if (animTargetSlot == -1) {
            animTargetSlot = slot;
            animatedSlotX = slot * 20f;
        }

        if (slot != animTargetSlot) {
            animTargetSlot = slot;
            animProgress = 0f;
        }

        if (animProgress < 1f) {
            animProgress = Math.min(1f, animProgress + d / 0.35f);
            float eased = 1f - (float)Math.pow(1f - animProgress, 4);
            float targetX = animTargetSlot * 20f;
            float diff = targetX - animatedSlotX;
            animatedSlotX += diff * eased;
            stretch = MathHelper.clamp(Math.abs(diff) / 60f, 0, 0.8f) * (1f - animProgress);
        } else {
            stretch = lerp(stretch, 0, d / 0.2f);
        }
    }

    public final EventBus.EventListener<RenderHotbarEvent> onRenderHotbarEvent = event -> {
        DrawContext ctx = event.getContext();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ctx == null || mc.player == null) return;
        event.setCancelled(true);
        float hX = (mc.getWindow().getScaledWidth() - (182f + 16f)) / 2f;
        float hY = mc.getWindow().getScaledHeight() - 28f - 12f;

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty()) {
                int ix = (int)(hX + 8f + i * 20f + 2);
                int iy = (int)(hY + 6);
                ctx.drawItem(s, ix, iy);
                ctx.drawStackOverlay(mc.textRenderer, s, ix, iy);
            }
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (!offhand.isEmpty() && offhandAlpha > 0.5f) {
            float offX = hX - 28f - 6f;
            int ix = (int)(offX + 6);
            int iy = (int)(hY + 6);
            ctx.drawItem(offhand, ix, iy);
            ctx.drawStackOverlay(mc.textRenderer, offhand, ix, iy);
        }
    };

    private float lerp(float s, float e, float t) { return s + (e - s) * MathHelper.clamp(t, 0, 1); }

    @Override
    public float getRadius() { return 8.0f; }
}
