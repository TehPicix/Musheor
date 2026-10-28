package musheor.utils.placement;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import musheor.modules.automation.HighwayBuilder;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.manager.RotationManager;
import musheor.utils.network.PacketLimiter;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BlockPlacer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static int placeBlocks(BlockPos[] positions, Block block, Direction side, MusheorSystem.PlacementType type) {
        if (mc.player != null && mc.world != null) {
            HighwayState state = HighwayState.getInstance();
            int placed = 0;
            boolean canPlace = false;

            for (BlockPos pos : positions) {
                if (mc.world.getBlockState(pos).getBlock() != block
                    && BlockUtils.canPlace(pos, true)
                    && isWithinPlacementRange(pos)
                    && !state.getPendingPlacements().containsKey(pos)) {
                    canPlace = true;
                    break;
                }
            }

            if (!canPlace) {
                return 0;
            } else {
                boolean silent = MusheorSystem.Manager.silentPlace.get();
                InventoryManager.swapToItem(block.asItem(), silent);
                switch (type) {
                    case Airplace:
                    case AirPlaceSwap:
                        if (type == MusheorSystem.PlacementType.AirPlaceSwap) {
                            swapWithOffhand();
                        }

                        for (BlockPos posx : positions) {
                            if (BlockUtils.canPlace(posx, true) && isWithinPlacementRange(posx)) {
                                if (!PacketLimiter.canSendPlacePacket()) {
                                    break;
                                }

                                if (!state.getPendingPlacements().containsKey(posx)) {
                                    state.setPlacing(true);
                                    sendInteract(type == MusheorSystem.PlacementType.AirPlaceSwap ? Hand.OFF_HAND : Hand.MAIN_HAND, createHitResult(posx, side));
                                    state.getPendingPlacements().put(posx, state.getRuntimeTicks());
                                    if (block == Blocks.OBSIDIAN) {
                                        state.incrementObsidianPlaced();
                                    }

                                    placed++;
                                    logPlacement(block, posx);
                                }
                            }
                        }

                        if (type == MusheorSystem.PlacementType.AirPlaceSwap) {
                            swapWithOffhand();
                        }
                        break;
                    case Rotate:
                        for (BlockPos posxx : positions) {
                            Direction placeSide = BlockUtils.getPlaceSide(posxx);
                            if (placeSide != null && BlockUtils.canPlace(posxx, true) && isWithinPlacementRange(posxx)) {
                                if (PacketLimiter.canSendPlacePacket()) {
                                    state.setPlacing(true);
                                    BlockUtils.place(posxx, Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, true, 0, false, true, false);
                                    logPlacement(block, posxx);
                                    placed++;
                                }
                                break;
                            }
                        }
                }

                if (silent) {
                    InvUtils.swapBack();
                }

                state.setPlacing(false);
                return placed;
            }
        } else {
            return 0;
        }
    }

    public static BlockPlacer.PlaceBatch beginBatch(Block block, MusheorSystem.PlacementType type) {
        if (type == null) {
            type = MusheorSystem.Manager.placementType.get();
        }

        if (mc.player != null && mc.world != null && mc.interactionManager != null) {
            Item item = block.asItem();
            boolean silent = MusheorSystem.Manager.silentPlace.get();
            boolean ready = InventoryManager.selectItem(item);
            BlockPlacer.PlaceBatch batch = new BlockPlacer.PlaceBatch(block, type, silent, ready, InventoryManager.findInHotbar(item));
            if (ready && type == MusheorSystem.PlacementType.AirPlaceSwap) {
                swapWithOffhand();
                batch.offhandSwapped = true;
            }

            return batch;
        } else {
            return new BlockPlacer.PlaceBatch(block, type, false, false, -1);
        }
    }

    public static void place(BlockPos pos, Direction direction, MusheorSystem.PlacementType type, boolean silent) {
        if (mc.player != null && mc.world != null) {
            switch (type) {
                case Airplace:
                    airPlace(pos, direction, false, silent);
                    break;
                case AirPlaceSwap:
                    airPlace(pos, direction, true, silent);
                    break;
                case Rotate:
                    placeRotated(pos);
            }
        }
    }

    public static boolean airPlace(BlockPos pos, Direction direction, boolean requireSwap, boolean silent) {
        return airPlace(pos, direction, Block.getBlockFromItem(mc.player.getMainHandStack().getItem()), requireSwap, silent);
    }

    public static boolean airPlace(BlockPos pos, Direction direction, Block block, boolean requireSwap, boolean silent) {
        if (mc.player != null && mc.getNetworkHandler() != null && mc.interactionManager != null) {
            InvUtils.swap(InventoryManager.findInHotbar(block.asItem()), silent);
            if (requireSwap) {
                swapWithOffhand();
            }

            sendInteract(requireSwap ? Hand.OFF_HAND : Hand.MAIN_HAND, createHitResult(pos, direction));
            if (requireSwap) {
                swapWithOffhand();
            }

            if (silent) {
                InvUtils.swapBack();
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean placeRotated(BlockPos pos) {
        if (mc.player != null && mc.getNetworkHandler() != null && mc.interactionManager != null) {
            Direction side = BlockUtils.getPlaceSide(pos);
            return side == null ? false : BlockUtils.place(pos, Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, true, 0, false, true, false);
        } else {
            return false;
        }
    }

    private static void sendInteract(Hand hand, BlockHitResult hit) {
        mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerInteractBlockC2SPacket(hand, hit, sequence));
    }

    private static BlockHitResult createHitResult(BlockPos pos, Direction direction) {
        return new BlockHitResult(Vec3d.ofCenter(pos), direction, pos, false);
    }

    private static void swapWithOffhand() {
        if (mc.player != null) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            ItemStack mainHand = mc.player.getMainHandStack();
            ItemStack offHand = mc.player.getOffHandStack();
            mc.player.getInventory().setStack(mc.player.getInventory().selectedSlot, offHand);
            mc.player.getInventory().setStack(40, mainHand);
        }
    }

    private static boolean isWithinPlacementRange(BlockPos pos) {
        if (mc.player == null) {
            return false;
        } else {
            double rangeSq = MusheorSystem.Manager.placementRange.get();
            return mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) <= rangeSq * rangeSq;
        }
    }

    private static void logPlacement(Block block, BlockPos pos) {
        if (HighwayBuilder.INSTANCE != null && MusheorSystem.Manager.enableChatDebugging.get()) {
            MusheorSystem.debug("placed §5%s §rat %s, %s, %s", Registries.ITEM.getId(block.asItem()).toString(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static final class PlaceBatch implements AutoCloseable {
        private final Block block;
        private final MusheorSystem.PlacementType placementType;
        private final boolean silent;
        private final boolean ready;
        private final int slot;
        private boolean offhandSwapped;
        private boolean closed;
        private boolean rotationQueued;
        private int placedCount;

        private PlaceBatch(Block block, MusheorSystem.PlacementType type, boolean silent, boolean ready, int slot) {
            this.block = block;
            this.placementType = type;
            this.silent = silent;
            this.ready = ready;
            this.slot = slot;
        }

        public boolean isReady() {
            return this.ready;
        }

        public Hand getHand() {
            return this.offhandSwapped ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }

        public boolean place(BlockPos pos, BlockHitResult hit, boolean allowRotate) {
            if (this.ready
                && !this.closed
                && !this.rotationQueued
                && BlockPlacer.mc.player != null
                && BlockPlacer.mc.world != null
                && BlockPlacer.mc.interactionManager != null) {
                if (this.placementType == MusheorSystem.PlacementType.Rotate && allowRotate && this.slot >= 0 && !RotationManager.isActive()) {
                    Vec3d target = hit.getPos();
                    Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), () -> {
                        InvUtils.swap(this.slot, this.silent);
                        BlockUtils.interact(hit, Hand.MAIN_HAND, true);
                        if (this.silent) {
                            InvUtils.swapBack();
                        }
                    });
                    this.rotationQueued = true;
                } else {
                    BlockPlacer.sendInteract(this.getHand(), hit);
                }

                this.placedCount++;
                BlockPlacer.logPlacement(this.block, pos);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                if (this.offhandSwapped) {
                    BlockPlacer.swapWithOffhand();
                    this.offhandSwapped = false;
                }

                if (this.silent) {
                    InvUtils.swapBack();
                }
            }
        }
    }
}
