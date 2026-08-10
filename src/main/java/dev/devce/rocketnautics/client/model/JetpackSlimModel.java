package dev.devce.rocketnautics.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class JetpackSlimModel<T extends Entity> extends EntityModel<T> implements IJetpackModel {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("rocketnautics", "jetpack_slim"), "main");
	private final ModelPart root;
	private final ModelPart jetpack_main;
	private final ModelPart jetpack_arm_L;
	private final ModelPart jetpack_arm_R;
	private final ModelPart jetpack_leg_L;
	private final ModelPart jetpack_leg_R;

	@Override
	public ModelPart getJetpackMain() {
		return jetpack_main;
	}

	@Override
	public ModelPart getLeftArm() {
		return jetpack_arm_L;
	}

	@Override
	public ModelPart getRightArm() {
		return jetpack_arm_R;
	}

	@Override
	public ModelPart getLeftLeg() {
		return jetpack_leg_L;
	}

	@Override
	public ModelPart getRightLeg() {
		return jetpack_leg_R;
	}

	public JetpackSlimModel(ModelPart root) {
		this.root = root.getChild("root");
		this.jetpack_main = this.root.getChild("jetpack_main");
		this.jetpack_arm_L = this.root.getChild("jetpack_arm_L");
		this.jetpack_arm_R = this.root.getChild("jetpack_arm_R");
		this.jetpack_leg_L = this.root.getChild("jetpack_leg_L");
		this.jetpack_leg_R = this.root.getChild("jetpack_leg_R");
	}
	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(4.0F, 21.0F, -4.0F, 0.0f, (float)Math.toRadians(180.0f), 0.0f));

		PartDefinition jetpack_main = root.addOrReplaceChild("jetpack_main", CubeListBuilder.create().texOffs(0, 12).addBox(0.0F, -12.0F, -8.0F, 8.0F, 9.0F, 2.0F, new CubeDeformation(0.02F))
				.texOffs(0, 31).addBox(1.5F, -9.0F, -2.0F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.2F))
				.texOffs(0, 0).addBox(0.0F, -13.0F, -6.0F, 8.0F, 8.0F, 4.0F, new CubeDeformation(0.5F))
				.texOffs(20, 12).addBox(0.0F, -3.0F, -6.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.5F))
				.texOffs(0, 23).addBox(1.0F, -13.0F, -6.0F, 6.0F, 4.0F, 4.0F, new CubeDeformation(0.25F))
				.texOffs(20, 32).addBox(0.25F, -8.0F, -2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(20, 32).addBox(-1.0F, -8.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(20, 32).addBox(8.0F, -8.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(20, 32).addBox(6.0F, -13.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(20, 32).addBox(1.0F, -13.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(20, 32).addBox(6.75F, -8.0F, -2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 13.0F, -4.0F, 0.0f, (float)Math.toRadians(180.0f), 0.0f));

		PartDefinition cube_r1 = jetpack_main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(20, 32).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -11.0F, -1.5F, 0.0F, 0.0F, -0.3927F));

		PartDefinition cube_r2 = jetpack_main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(20, 32).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -11.0F, -1.5F, 0.0F, 0.0F, 0.3927F));

		PartDefinition cube_r3 = jetpack_main.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(24, 0).addBox(-8.0F, -3.0F, -1.0F, 8.0F, 3.0F, 2.0F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(8.0F, -9.1716F, -9.4142F, -0.7854F, 0.0F, 0.0F));

		PartDefinition cube_r4 = jetpack_main.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(24, 5).addBox(-7.0F, 1.25F, -2.5F, 6.0F, 2.0F, 2.0F, new CubeDeformation(-0.5F))
				.texOffs(24, 9).addBox(-7.0F, 1.25F, -2.0F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(25, 6).addBox(-7.0F, 1.25F, -1.0F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(20, 18).addBox(-8.0F, -4.0F, 0.0F, 8.0F, 6.25F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.0F, -6.1832F, -8.5906F, 0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r5 = jetpack_main.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(24, 32).mirror().addBox(7.53F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(24, 32).addBox(-1.53F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -10.5F, -7.5F, 0.7854F, 0.0F, 0.0F));

		PartDefinition cube_r6 = jetpack_main.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(20, 26).addBox(-1.5F, -1.5F, 0.25F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.75F))
				.texOffs(32, 28).addBox(-1.5F, -1.5F, 1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(-0.25F))
				.texOffs(12, 31).addBox(-1.5F, -1.5F, 1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(-0.25F)), PartPose.offsetAndRotation(4.0F, -7.0F, -2.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition jetpack_arm_L = root.addOrReplaceChild("jetpack_arm_L", CubeListBuilder.create().texOffs(26, 49).addBox(-9.5F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.01F))
				.texOffs(27, 44).addBox(-10.0F, 1.0F, 1.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.26F))
				.texOffs(41, 39).addBox(-10.5F, 0.5F, 2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.01F))
				.texOffs(26, 36).addBox(-10.0F, -4.0F, 1.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.265F)), PartPose.offsetAndRotation(-8.0F, 2.0F, 3.0F, 0.0f, (float)Math.toRadians(180.0f), 0.0f));

		PartDefinition cube_r7 = jetpack_arm_L.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(34, 49).addBox(-1.0F, 0.25F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.25F))
				.texOffs(34, 49).addBox(-1.0F, 0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(40, 36).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.5F, 3.0793F, -0.1152F, -0.3927F, 0.0F, 0.0F));

		PartDefinition jetpack_arm_R = root.addOrReplaceChild("jetpack_arm_R", CubeListBuilder.create().texOffs(26, 49).mirror().addBox(7.5F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false)
				.texOffs(26, 44).mirror().addBox(7.0F, 1.0F, 1.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.26F)).mirror(false)
				.texOffs(41, 39).mirror().addBox(9.5F, 0.5F, 2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.01F)).mirror(false)
				.texOffs(25, 36).mirror().addBox(7.0F, -4.0F, 1.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.265F)).mirror(false), PartPose.offsetAndRotation(8.0F, 2.0F, 3.0F, 0.0f, (float)Math.toRadians(180.0f), 0.0f ));

		PartDefinition cube_r8 = jetpack_arm_R.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(34, 49).mirror().addBox(-1.0F, 0.25F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.25F)).mirror(false)
				.texOffs(34, 49).mirror().addBox(-1.0F, 0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(40, 36).mirror().addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(8.5F, 3.0793F, -0.1152F, -0.3927F, 0.0F, 0.0F));

		PartDefinition jetpack_leg_L = root.addOrReplaceChild("jetpack_leg_L", CubeListBuilder.create().texOffs(0, 47).addBox(-8.0F, 19.0F, 2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.3F))
				.texOffs(0, 52).addBox(-7.5F, 18.0F, 0.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.01F))
				.texOffs(16, 36).addBox(-6.0F, 18.0F, -2.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(0, 36).addBox(-8.0F, 12.0F, 2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.26F))
				.texOffs(0, 47).addBox(-8.0F, 15.0F, 2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.3F))
				.texOffs(10, 55).addBox(-9.75F, 14.0F, 3.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(20, 52).addBox(-6.75F, 14.5F, 5.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -12.0F, 4.0F, 0.0f, (float)Math.toRadians(180.0f), 0.0f));

		PartDefinition cube_r9 = jetpack_leg_L.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 57).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.25F, 19.5F, 6.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition cube_r10 = jetpack_leg_L.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(2, 57).addBox(-1.0F, -0.25F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.9047F, 17.7716F, 4.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition cube_r11 = jetpack_leg_L.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(17, 50).addBox(-1.0F, -2.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(-9.0961F, 18.2335F, 4.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition cube_r12 = jetpack_leg_L.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(4, 57).addBox(-0.5F, -0.5F, -0.499F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.0F, 15.5F, 5.5F, 0.0F, 0.0F, -0.7854F));

		PartDefinition cube_r13 = jetpack_leg_L.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(16, 45).addBox(-1.5F, 3.0F, -1.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(-0.5F))
				.texOffs(16, 45).addBox(-1.5F, 3.5F, -1.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(10, 52).addBox(-1.5F, 2.5F, -1.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, 18.3076F, 2.0328F, -0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r14 = jetpack_leg_L.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(18, 55).addBox(-3.0F, 4.5F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.5F))
				.texOffs(17, 45).addBox(-3.0F, 5.0F, -6.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(16, 49).addBox(-3.0F, 6.0F, -6.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, 10.0021F, 3.0167F, 0.3927F, 0.0F, 0.0F));

		PartDefinition jetpack_leg_R = root.addOrReplaceChild("jetpack_leg_R", CubeListBuilder.create().texOffs(0, 47).mirror().addBox(4.0F, 19.0F, 2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.3F)).mirror(false)
				.texOffs(0, 52).mirror().addBox(4.5F, 18.0F, 0.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false)
				.texOffs(16, 36).mirror().addBox(6.0F, 18.0F, -2.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(0, 36).mirror().addBox(4.0F, 12.0F, 2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.26F)).mirror(false)
				.texOffs(0, 47).mirror().addBox(4.0F, 15.0F, 2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.3F)).mirror(false)
				.texOffs(10, 55).mirror().addBox(7.75F, 14.0F, 3.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(20, 52).mirror().addBox(4.75F, 14.5F, 5.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.0F, -12.0F, 4.0F, 0.0f, (float)Math.toRadians(180.0f), 0.0f));

		PartDefinition cube_r15 = jetpack_leg_R.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(0, 57).mirror().addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.25F, 19.5F, 6.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition cube_r16 = jetpack_leg_R.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(2, 57).mirror().addBox(0.0F, -0.25F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(8.9047F, 17.7716F, 4.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition cube_r17 = jetpack_leg_R.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(17, 50).mirror().addBox(0.0F, -2.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.25F)).mirror(false), PartPose.offsetAndRotation(9.0961F, 18.2335F, 4.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition cube_r18 = jetpack_leg_R.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(4, 57).mirror().addBox(-0.5F, -0.5F, -0.499F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(9.0F, 15.5F, 5.5F, 0.0F, 0.0F, 0.7854F));

		PartDefinition cube_r19 = jetpack_leg_R.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(16, 45).mirror().addBox(-1.5F, 3.0F, -1.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(-0.5F)).mirror(false)
				.texOffs(16, 45).mirror().addBox(-1.5F, 3.5F, -1.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(10, 52).mirror().addBox(-1.5F, 2.5F, -1.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.0F, 18.3076F, 2.0328F, -0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r20 = jetpack_leg_R.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(18, 55).mirror().addBox(1.0F, 4.5F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.5F)).mirror(false)
				.texOffs(17, 45).mirror().addBox(1.0F, 5.0F, -6.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(16, 49).mirror().addBox(1.0F, 6.0F, -6.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.0F, 10.0021F, 3.0167F, 0.3927F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int packedColor) {
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, packedColor);
	}

	@Override
	public void setupAnim(T t, float v, float v1, float v2, float v3, float v4) {

	}
}