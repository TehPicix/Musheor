package musheor.modules.features;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.BlockRenderUtils;
import musheor.utils.HighwayUtils;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AirPlace extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final Setting<Boolean> render = this.sgGeneral
        .add(new Builder().name("render").description("Renders an overlay where the block will be placed.").defaultValue(true).build());
    private final Setting<Boolean> customRange = this.sgGeneral
        .add(new Builder().name("custom-range").description("Use custom range for air place.").defaultValue(false).build());
    private final Setting<Double> range = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("range")
                .description("Custom range to place at.")
                .visible(this.customRange::get)
                .defaultValue(4.5)
                .min(0.0)
                .sliderMax(6.0)
                .build()
        );
    private BlockPos targetPos = null;
    private boolean wasUsePressed = false;
    private int holdTicks = 0;

    public AirPlace() {
        super(musheor.MAIN, "airplace", "Place blocks mid-air");
    }

    @Override
    public void onActivate() {
        this.wasUsePressed = false;
        this.holdTicks = 0;
        this.targetPos = null;
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (client.player != null && client.world != null) {
            this.targetPos = this.findTargetPos();
            boolean pressed = client.options.useKey.isPressed();
            boolean justPressed = pressed && !this.wasUsePressed;
            this.wasUsePressed = pressed;
            if (!pressed) {
                this.holdTicks = 0;
            } else if (this.targetPos != null) {
                if (client.player.getMainHandStack().getItem() instanceof BlockItem || client.player.getMainHandStack().getItem() instanceof SpawnEggItem) {
                    if (BlockUtils.canPlace(this.targetPos, true)) {
                        if (justPressed) {
                            this.holdTicks = 0;
                            this.placeAt(this.targetPos);
                        } else {
                            this.holdTicks++;
                            if (this.holdTicks >= 4) {
                                this.holdTicks = 0;
                                this.placeAt(this.targetPos);
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockPos findTargetPos() {
        if (client.player != null && client.world != null) {
            double r = this.customRange.get() ? this.range.get() : client.player.getBlockInteractionRange();
            if (client.getCameraEntity() == null) {
                return null;
            } else if (client.getCameraEntity().raycast(r, 0.0F, false) instanceof BlockHitResult bhr) {
                BlockPos hitPos = bhr.getBlockPos();
                if (client.world.getBlockState(hitPos).isReplaceable()) {
                    return hitPos;
                } else {
                    BlockPos adjacent = hitPos.offset(bhr.getSide());
                    return client.world.getBlockState(adjacent).isReplaceable() ? adjacent : null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void placeAt(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.DOWN, pos, false);
        HighwayUtils.swapOffhand();
        HighwayUtils.sendInteractBlock(Hand.OFF_HAND, hit);
        HighwayUtils.swapOffhand();
        if (client.player.getMainHandStack().getItem() instanceof BlockItem bi) {
            client.world.setBlockState(pos, bi.getBlock().getDefaultState(), 3);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (client.player != null && client.world != null && this.render.get() && this.targetPos != null) {
            if (client.player.getMainHandStack().getItem() instanceof BlockItem || client.player.getMainHandStack().getItem() instanceof SpawnEggItem) {
                if (client.world.getBlockState(this.targetPos).isReplaceable()) {
                    BlockRenderUtils.renderPosition(event, this.targetPos, Block.getBlockFromItem(client.player.getMainHandStack().getItem()));
                }
            }
        }
    }
}
