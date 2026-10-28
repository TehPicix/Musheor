package musheor.modules.features;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class DepthInteract extends Module {
    public static DepthInteract INSTANCE;
    private final MinecraftClient client = MinecraftClient.getInstance();
    public BlockPos targetContainerPos = null;

    public DepthInteract() {
        super(musheor.MAIN, "depth-interact", "Interact with containers through blocks.");
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        this.targetContainerPos = null;
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.client.player != null && this.client.world != null) {
            this.targetContainerPos = this.findContainerInView(this.client.player.getBlockInteractionRange());
        }
    }

    private BlockPos findContainerInView(double maxDistance) {
        Vec3d start = this.client.player.getCameraPosVec(1.0F);
        Vec3d look = this.client.player.getRotationVec(1.0F);

        for (double d = 0.0; d <= maxDistance; d += 0.1) {
            Vec3d point = start.add(look.multiply(d));
            BlockPos pos = BlockPos.ofFloored(point);
            if (this.client.world.isChunkLoaded(pos)) {
                BlockEntity be = this.client.world.getBlockEntity(pos);
                if (this.isContainer(be)) {
                    return pos;
                }
            }
        }

        return null;
    }

    private boolean isContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity
            || be instanceof TrappedChestBlockEntity
            || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity
            || be instanceof EnderChestBlockEntity;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.targetContainerPos != null && this.client.world != null) {
            event.renderer.box(this.targetContainerPos, Color.CYAN, Color.CYAN, ShapeMode.Lines, 0);
        }
    }
}
