package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.devce.rocketnautics.client.model.IJetpackModel;
import dev.devce.rocketnautics.client.model.JetpackModel;
import dev.devce.rocketnautics.client.model.JetpackSlimModel;
import dev.devce.rocketnautics.content.items.JetpackItem;
import dev.devce.rocketnautics.content.items.LegThrustersItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class JetpackLayer<T extends AbstractClientPlayer, M extends PlayerModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "rocketnautics",
                    "textures/models/armor/jetpack.png"
            );

    private final JetpackSlimModel modelSlim;
    private final JetpackModel modelNormal;

    public enum JetpackModelPart {
        BODY,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG
    }

    public JetpackLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet) {
        super(parent);
        this.modelSlim = new JetpackSlimModel(modelSet.bakeLayer(JetpackSlimModel.LAYER_LOCATION));
        this.modelNormal = new JetpackModel(modelSet.bakeLayer(JetpackModel.LAYER_LOCATION));
    }

    public static Vector3f modelPart2worldSpace(Player player, JetpackModelPart part, Vector3f vec) {
        Map<JetpackModelPart, Matrix4f> playerTransforms = MODEL_PART_TRANSFORMS.get(player.getId());
        if (playerTransforms == null) return new Vector3f(0);

        vec.div(16);

        Matrix4f partTransform = playerTransforms.getOrDefault(part, new Matrix4f().identity());
        partTransform.transformPosition(vec);

        Vector3f cameraPos = Minecraft.getInstance()
                .gameRenderer
                .getMainCamera()
                .getPosition()
                .toVector3f();

        vec.add(cameraPos);

        return vec;
    }
    private static final Map<Integer, Map<JetpackModelPart, Matrix4f>> MODEL_PART_TRANSFORMS = new HashMap<>();

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legStack = player.getItemBySlot(EquipmentSlot.LEGS);

        M playerModel = this.getParentModel();
        IJetpackModel model = player.getSkin().model() == PlayerSkin.Model.SLIM ? modelSlim : modelNormal;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        // Body

        poseStack.pushPose();

        playerModel.body.translateAndRotate(poseStack);

        MODEL_PART_TRANSFORMS
                .computeIfAbsent(player.getId(), p -> new HashMap<>())
                .put(JetpackModelPart.BODY, new Matrix4f(poseStack.last().pose()));

        if (chestStack.getItem() instanceof JetpackItem)
            model.getJetpackMain().render(
                    poseStack,
                    vc,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF
            );

        poseStack.popPose();

        // Left Arm

        poseStack.pushPose();

        playerModel.leftArm.translateAndRotate(poseStack);

        MODEL_PART_TRANSFORMS
                .computeIfAbsent(player.getId(), p -> new HashMap<>())
                .put(JetpackModelPart.LEFT_ARM, new Matrix4f(poseStack.last().pose()));

        if (chestStack.getItem() instanceof JetpackItem)
            model.getLeftArm().render(
                    poseStack,
                    vc,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF
            );

        poseStack.popPose();

        // Right Arm

        poseStack.pushPose();

        playerModel.rightArm.translateAndRotate(poseStack);

        MODEL_PART_TRANSFORMS
                .computeIfAbsent(player.getId(), p -> new HashMap<>())
                .put(JetpackModelPart.RIGHT_ARM, new Matrix4f(poseStack.last().pose()));

        if (chestStack.getItem() instanceof JetpackItem)
            model.getRightArm().render(
                    poseStack,
                    vc,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF
            );

        poseStack.popPose();

        // Left Leg

        poseStack.pushPose();

        playerModel.leftLeg.translateAndRotate(poseStack);

        MODEL_PART_TRANSFORMS
                .computeIfAbsent(player.getId(), p -> new HashMap<>())
                .put(JetpackModelPart.LEFT_LEG, new Matrix4f(poseStack.last().pose()));

        if (legStack.getItem() instanceof LegThrustersItem)
            model.getLeftLeg().render(
                    poseStack,
                    vc,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF
            );

        poseStack.popPose();

        // Right Leg

        poseStack.pushPose();

        playerModel.rightLeg.translateAndRotate(poseStack);

        MODEL_PART_TRANSFORMS
                .computeIfAbsent(player.getId(), p -> new HashMap<>())
                .put(JetpackModelPart.RIGHT_LEG, new Matrix4f(poseStack.last().pose()));

        if (legStack.getItem() instanceof LegThrustersItem)
            model.getRightLeg().render(
                    poseStack,
                    vc,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF
            );

        poseStack.popPose();
    }
}
