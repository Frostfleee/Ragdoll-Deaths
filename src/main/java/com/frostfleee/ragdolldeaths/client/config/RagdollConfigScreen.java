package com.frostfleee.ragdolldeaths.client.config;

import com.frostfleee.ragdolldeaths.client.Compat;
import com.frostfleee.ragdolldeaths.client.ConfigStore;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.WaterBehavior;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class RagdollConfigScreen extends OptionsSubScreen {
	private static final String KEY = "ragdolldeaths.config.";
	private static final int BUTTON_WIDTH = 150;
	private static final int LIST_WIDTH = 310;

	private final RagdollConfig draft;

	public RagdollConfigScreen(Screen parent) {
		this(parent, ConfigStore.get().copy());
	}

	private RagdollConfigScreen(Screen parent, RagdollConfig draft) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
		this.draft = draft;
	}

	@Override
	protected void addOptions() {
		RagdollConfig c = draft;

		Compat.addHeader(list, header("general"), font);
		list.addSmall(
				toggle("enabled", c.enabled, v -> c.enabled = v),
				toggle("playerRagdolls", c.playerRagdolls, v -> c.playerRagdolls = v),
				slider("bodyLifetimeTicks", 1, RagdollConfig.MAX_BODY_LIFETIME_TICKS / 20, c.bodyLifetimeTicks / 20,
						v -> Component.translatable(KEY + "wholeSeconds", v), v -> c.bodyLifetimeTicks = v * 20),
				percent("animationSpeed", 50, 200, c.animationSpeed, v -> c.animationSpeed = v),
				slider("maxRenderDistance", 8, 256, (int) c.maxRenderDistance, v -> Component.translatable(KEY + "blocks", v), v -> c.maxRenderDistance = v),
				slider("maxActiveRagdolls", 4, 256, c.maxActiveRagdolls, v -> Component.literal(Integer.toString(v)), v -> c.maxActiveRagdolls = v),
				toggle("hideDeathTint", c.hideDeathTint, v -> c.hideDeathTint = v));

		Compat.addHeader(list, header("physics"), font);
		list.addSmall(
				toggle("physicsRagdolls", c.physicsRagdolls, v -> c.physicsRagdolls = v),
				quality(c),
				slider("maxBodies", 4, 128, c.maxBodies, v -> Component.literal(Integer.toString(v)), v -> c.maxBodies = v),
				percent("explosionStrength", 0, 200, c.explosionStrength, v -> c.explosionStrength = v),
				slider("muscleSeconds", 0, 50, (int) Math.round(c.muscleSeconds * 10.0),
						v -> Component.translatable(KEY + "wholeSeconds", String.format(Locale.ROOT, "%.1f", v / 10.0)), v -> c.muscleSeconds = v / 10.0),
				toggle("reflexes", c.reflexes, v -> c.reflexes = v),
				toggle("skeletonsBreak", c.skeletonsBreak, v -> c.skeletonsBreak = v),
				toggle("impactSounds", c.impactSounds, v -> c.impactSounds = v),
				toggle("impactParticles", c.impactParticles, v -> c.impactParticles = v),
				toggle("pushBodies", c.pushBodies, v -> c.pushBodies = v),
				toggle("deathCamera", c.deathCamera, v -> c.deathCamera = v),
				slider("deathCameraDistance", 3, 20, (int) Math.round(c.deathCameraDistance * 2.0),
						v -> Component.translatable(KEY + "blocks", String.format(Locale.ROOT, "%.1f", v / 2.0)), v -> c.deathCameraDistance = v / 2.0),
				toggle("deathCameraCircles", c.deathCameraCircles, v -> c.deathCameraCircles = v),
				toggle("playerBodyUntilRespawn", c.playerBodyUntilRespawn, v -> c.playerBodyUntilRespawn = v));

		// Features still being worked on, off by default.
		Compat.addHeader(list, header("beta"), font);
		list.addSmall(
				toggle("kneesAndElbows", c.kneesAndElbows, v -> c.kneesAndElbows = v),
				toggle("blood", c.blood, v -> c.blood = v));

		Compat.addHeader(list, header("motion"), font);
		list.addSmall(
				percent("hitReactionStrength", 0, 200, c.hitReactionStrength, v -> c.hitReactionStrength = v),
				percent("bounceStrength", 0, 200, c.bounceStrength, v -> c.bounceStrength = v),
				toggle("limbAnimation", c.limbAnimation, v -> c.limbAnimation = v),
				toggle("legKicks", c.legKicks, v -> c.legKicks = v));

		Compat.addHeader(list, header("environment"), font);
		list.addSmall(
				waterBehavior(c),
				toggle("lavaSink", c.lavaSink, v -> c.lavaSink = v),
				toggle("fallImpact", c.fallImpact, v -> c.fallImpact = v),
				toggle("throwRiders", c.throwRiders, v -> c.throwRiders = v));

		Compat.addHeader(list, header("excludedEntities"), font);
		Compat.addWide(list, idList("excludedEntities", c.excludedEntities, ids -> c.excludedEntities = ids));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable(KEY + "reset"), button -> Compat.setScreen(new RagdollConfigScreen(lastScreen, RagdollConfig.defaults())))
				.width(BUTTON_WIDTH)
				.build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(BUTTON_WIDTH).build());
	}

	@Override
	public void onClose() {
		if (list != null) {
			list.applyUnsavedChanges();
		}
		ConfigStore.save(draft);
		super.onClose();
	}

	private static Component header(String section) {
		return Component.translatable(KEY + "section." + section);
	}

	private static OptionInstance<Boolean> toggle(String name, boolean value, Consumer<Boolean> setter) {
		return OptionInstance.createBoolean(KEY + name, tooltip(name), value, setter::accept);
	}

	private static OptionInstance<Integer> slider(String name, int min, int max, int value, IntFunction<Component> label, IntConsumer setter) {
		return new OptionInstance<>(
				KEY + name,
				tooltip(name),
				(caption, v) -> Options.genericValueLabel(caption, label.apply(v)),
				new OptionInstance.IntRange(min, max, true),
				Math.clamp(value, min, max),
				setter::accept);
	}

	private static OptionInstance<Integer> percent(String name, int min, int max, double value, DoubleConsumer setter) {
		return slider(name, min, max, (int) Math.round(value * 100.0), v -> Component.literal(v + "%"), v -> setter.accept(v / 100.0));
	}

	private static OptionInstance<RagdollConfig.Quality> quality(RagdollConfig c) {
		List<RagdollConfig.Quality> values = List.of(RagdollConfig.Quality.values());
		return new OptionInstance<>(
				KEY + "physicsQuality",
				tooltip("physicsQuality"),
				// The cycle button puts the caption in front itself: only the value here.
				(caption, v) -> Component.translatable(KEY + "physicsQuality." + v.name().toLowerCase(Locale.ROOT)),
				new OptionInstance.Enum<>(values, Codec.STRING.xmap(RagdollConfig.Quality::valueOf, RagdollConfig.Quality::name)),
				c.physicsQuality,
				v -> c.physicsQuality = v);
	}

	private static OptionInstance<WaterBehavior> waterBehavior(RagdollConfig c) {
		List<WaterBehavior> values = List.of(WaterBehavior.values());
		return new OptionInstance<>(
				KEY + "waterBehavior",
				tooltip("waterBehavior"),
				(caption, v) -> Component.translatable(KEY + "waterBehavior." + v.name().toLowerCase(Locale.ROOT)),
				new OptionInstance.Enum<>(values, Codec.STRING.xmap(WaterBehavior::valueOf, WaterBehavior::name)),
				c.waterBehavior,
				v -> c.waterBehavior = v);
	}

	private EditBox idList(String name, List<String> ids, Consumer<List<String>> setter) {
		EditBox box = new EditBox(font, 0, 0, LIST_WIDTH, 20, Component.translatable(KEY + name));
		box.setMaxLength(4096);
		box.setValue(String.join(", ", ids));
		box.setHint(Component.translatable(KEY + name + ".hint"));
		box.setTooltip(Tooltip.create(Component.translatable(KEY + name + ".tooltip")));
		box.setResponder(text -> setter.accept(RagdollConfig.splitIds(text)));
		return box;
	}

	private static <T> OptionInstance.TooltipSupplier<T> tooltip(String name) {
		return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip"));
	}
}
