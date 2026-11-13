package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The game and Fabric API calls that differ between 26.x versions. This copy is for 1.21.5 to 1.21.8. */
public final class Compat {
	private Compat() {
	}

	public static Vec3 cameraPosition() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
	}

	public static boolean isCubeMob(LivingEntity entity) {
		return entity instanceof Slime;
	}

	public static boolean isMagmaCube(LivingEntity entity) {
		return entity instanceof MagmaCube;
	}

	public static Block shulkerBox(DyeColor color) {
		if (color == null) {
			return Blocks.SHULKER_BOX;
		}
		return switch (color) {
			case WHITE -> Blocks.WHITE_SHULKER_BOX;
			case ORANGE -> Blocks.ORANGE_SHULKER_BOX;
			case MAGENTA -> Blocks.MAGENTA_SHULKER_BOX;
			case LIGHT_BLUE -> Blocks.LIGHT_BLUE_SHULKER_BOX;
			case YELLOW -> Blocks.YELLOW_SHULKER_BOX;
			case LIME -> Blocks.LIME_SHULKER_BOX;
			case PINK -> Blocks.PINK_SHULKER_BOX;
			case GRAY -> Blocks.GRAY_SHULKER_BOX;
			case LIGHT_GRAY -> Blocks.LIGHT_GRAY_SHULKER_BOX;
			case CYAN -> Blocks.CYAN_SHULKER_BOX;
			case PURPLE -> Blocks.PURPLE_SHULKER_BOX;
			case BLUE -> Blocks.BLUE_SHULKER_BOX;
			case BROWN -> Blocks.BROWN_SHULKER_BOX;
			case GREEN -> Blocks.GREEN_SHULKER_BOX;
			case RED -> Blocks.RED_SHULKER_BOX;
			case BLACK -> Blocks.BLACK_SHULKER_BOX;
		};
	}

	/** 1.21 has no full-width widget row; a lone small entry keeps the widget's own width. */
	public static void addWide(OptionsList list, AbstractWidget widget) {
		list.addSmall(widget, null);
	}

	/** Block and sky light, packed the way the renderer takes them. */
	public static int packLight(int block, int sky) {
		return net.minecraft.client.renderer.LightTexture.pack(block, sky);
	}

	/** Registers a texture under a new id of this mod's and returns the id. */
	public static Object registerTexture(String namespace, String path, net.minecraft.client.renderer.texture.AbstractTexture texture) {
		net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		return id;
	}

	public static void releaseTexture(Object id) {
		Minecraft.getInstance().getTextureManager().release((net.minecraft.resources.ResourceLocation) id);
	}

	/** Opens a resource of the game's or a resource pack's, or returns null when there is none. */
	public static java.io.InputStream openResource(Object id) throws java.io.IOException {
		var resource = Minecraft.getInstance().getResourceManager().getResource((net.minecraft.resources.ResourceLocation) id);
		return resource.isEmpty() ? null : resource.get().open();
	}

	/** A section title: this version's options list has no header rows, so it is a line of centred text. */
	public static void addHeader(OptionsList list, net.minecraft.network.chat.Component title, net.minecraft.client.gui.Font font) {
		net.minecraft.client.gui.components.StringWidget text = new net.minecraft.client.gui.components.StringWidget(310, 20, title, font);
		list.addSmall(text, null);
	}

	/** Poses a model for a render state, as the renderer does before drawing it. Only entity models take a state here. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void setupAnim(net.minecraft.client.model.Model model, Object state) {
		if (model instanceof net.minecraft.client.model.EntityModel entityModel && state instanceof net.minecraft.client.renderer.entity.state.EntityRenderState renderState) {
			entityModel.setupAnim(renderState);
		}
	}

	public static float vx(net.minecraft.client.model.geom.ModelPart.Vertex v) {
		return v.pos().x();
	}

	public static float vy(net.minecraft.client.model.geom.ModelPart.Vertex v) {
		return v.pos().y();
	}

	public static float vz(net.minecraft.client.model.geom.ModelPart.Vertex v) {
		return v.pos().z();
	}

	public static float vu(net.minecraft.client.model.geom.ModelPart.Vertex v) {
		return v.u();
	}

	public static float vv(net.minecraft.client.model.geom.ModelPart.Vertex v) {
		return v.v();
	}

	public static net.minecraft.client.model.geom.ModelPart.Vertex vertex(float x, float y, float z, float u, float v) {
		return new net.minecraft.client.model.geom.ModelPart.Vertex(x, y, z, u, v);
	}

	/** The state has no light of its own here; the renderer takes the body's light from the ragdoll data instead. */
	public static void setLight(net.minecraft.client.renderer.entity.state.EntityRenderState state, int light) {
		RagdollRenderData.setLight(state, light);
	}

	/** The shadow is skipped for ragdoll bodies where it is drawn (EntityRenderDispatcherMixin). */
	public static void hideShadow(net.minecraft.client.renderer.entity.state.EntityRenderState state) {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static net.minecraft.client.renderer.entity.state.EntityRenderState extractState(net.minecraft.world.entity.Entity entity, float partialTicks) {
		net.minecraft.client.renderer.entity.EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		return renderer.createRenderState(entity, partialTicks);
	}

	/** A texture whose pixels the mod can change. */
	public static net.minecraft.client.renderer.texture.DynamicTexture newTexture(String label, com.mojang.blaze3d.platform.NativeImage image) {
		return new net.minecraft.client.renderer.texture.DynamicTexture(() -> label, image);
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	/** Opens the "Create New World" screen, for the self-test. */
	public static void openCreateWorld(net.minecraft.client.Minecraft minecraft) {
		net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(minecraft, screen());
	}

	/** The screen open right now, or null. */
	public static net.minecraft.client.gui.screens.Screen screen() {
		return net.minecraft.client.Minecraft.getInstance().screen;
	}

	/** The model's root part, holding every other part by name. */
	public static net.minecraft.client.model.geom.ModelPart root(net.minecraft.client.model.Model model) {
		return model.root();
	}

	static Rest initialPose(net.minecraft.client.model.geom.ModelPart part) {
		net.minecraft.client.model.geom.PartPose p = part.getInitialPose();
		return new Rest(p.x(), p.y(), p.z(), p.xRot(), p.yRot(), p.zRot(), p.xScale(), p.yScale(), p.zScale());
	}

	static net.minecraft.client.model.geom.ModelPart.Polygon[] polygons(net.minecraft.client.model.geom.ModelPart.Cube cube) {
		return cube.polygons;
	}

	static net.minecraft.client.model.geom.ModelPart.Vertex[] vertices(net.minecraft.client.model.geom.ModelPart.Polygon polygon) {
		return polygon.vertices();
	}

	static org.joml.Vector3fc normal(net.minecraft.client.model.geom.ModelPart.Polygon polygon) {
		return polygon.normal();
	}

	static net.minecraft.client.model.geom.ModelPart.Polygon newPolygon(net.minecraft.client.model.geom.ModelPart.Vertex[] corners, org.joml.Vector3fc normal) {
		return new net.minecraft.client.model.geom.ModelPart.Polygon(corners, new org.joml.Vector3f(normal));
	}

	static int getPixel(com.mojang.blaze3d.platform.NativeImage image, int x, int y) {
		return image.getPixel(x, y);
	}

	static void setPixel(com.mojang.blaze3d.platform.NativeImage image, int x, int y, int argb) {
		image.setPixel(x, y, argb);
	}

	/** How far the renderer turns the mob over as it dies (90: onto its side, 180: onto its back). */
	@SuppressWarnings("rawtypes")
	static float flipDegrees(net.minecraft.client.renderer.entity.LivingEntityRenderer renderer, net.minecraft.world.entity.LivingEntity entity) {
		return ((com.frostfleee.ragdolldeaths.mixin.LivingEntityRendererAccessor) renderer).ragdolldeaths$getFlipDegrees();
	}

	/** Saves a screenshot to the game's screenshots folder, for the self-test. */
	static void screenshot() {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), message -> {
		});
	}
}
