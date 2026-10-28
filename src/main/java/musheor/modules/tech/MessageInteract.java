package musheor.modules.tech;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.modules.features.MoreTags;
import musheor.utils.HighwayUtils;
import musheor.utils.PearlStore;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.manager.InventoryManager;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class MessageInteract extends Module {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<MessageInteract.Action> action = this.sgGeneral
        .add(
            new Builder<MessageInteract.Action>().name("action").description("What kind of action to perform.")
                    .defaultValue(MessageInteract.Action.LoadPearl)
                .build()
        );
    private final Setting<BlockPos> position = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("position")
                .description("What block position the action is supposed to be for")
                .defaultValue(new BlockPos(0, 0, 0))
                .visible(() -> this.action.get() != MessageInteract.Action.LoadPearl)
                .build()
        );
    private final Setting<Boolean> dropPearl = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("drop-pearl")
                .description("Drops a pearl after performing the interaction")
                .defaultValue(true)
                .visible(() -> this.action.get() == MessageInteract.Action.LoadPearl)
                .build()
        );
    private final Setting<Boolean> returnToStartPos = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("return-to-start-pos")
                .description("Pathfinds back to your starting position.")
                .defaultValue(true)
                .visible(() -> this.action.get() == MessageInteract.Action.LoadPearl)
                .build()
        );
    private final Setting<String> allowedPlayer = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("allowed-player")
                .description("If set, only reacts to !tp whispers from this player. Leave empty to allow anyone.")
                .defaultValue("")
                .build()
        );
    boolean actionPending = false;
    int reinteractDelay = 0;
    private int aimTicks = 0;
    private BlockPos pearlTrapdoorPos = null;
    private BlockPos startPos = null;
    private static final Pattern WHISPER_PATTERN = Pattern.compile("([A-Za-z0-9_]{3,16}) whispers: ");
    private static final Random random = new Random();

    public MessageInteract() {
        super(musheor.MAIN, "message-interact", "Performs a certain action upon receiving a message from a specific player.");
    }

    @Override
    public void onDeactivate() {
        BaritoneHelper.cancelAll();
        this.actionPending = false;
        this.reinteractDelay = 0;
        this.aimTicks = 0;
        this.pearlTrapdoorPos = null;
        this.startPos = null;
    }

    @Override
    public void onActivate() {
        if (this.action.get() == MessageInteract.Action.LoadPearl && !Modules.get().get(MoreTags.class).isActive()) {
            Modules.get().get(MoreTags.class).toggle();
        }
    }

    private BlockPos findPearlTrapdoor(String ownerName) {
        if (client.world != null && client.player != null) {
            BlockPos closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Entry<Integer, String> entry : MoreTags.pearlOwners.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(ownerName) && client.world.getEntityById(entry.getKey()) instanceof EnderPearlEntity pearl) {
                    for (BlockPos candidate : new BlockPos[]{pearl.getBlockPos(), pearl.getBlockPos().down()}) {
                        if (client.world.getBlockState(candidate).getBlock() instanceof TrapdoorBlock) {
                            double dist = VersionHelper.get().getPlayerPos().squaredDistanceTo(Vec3d.ofCenter(candidate));
                            if (dist < closestDist) {
                                closestDist = dist;
                                closest = candidate;
                            }
                        }
                    }
                }
            }

            if (closest != null) {
                return closest;
            }
        }

        List<PearlStore.PearlEntry> saved = PearlStore.getByOwner(ownerName);
        if (saved.isEmpty()) {
            return null;
        } else {
            return client.player != null && saved.size() != 1
                ? saved.stream()
                    .min(Comparator.comparingDouble(r -> VersionHelper.get().getPlayerPos().squaredDistanceTo(Vec3d.ofCenter(r.trapdoorPos))))
                    .map(r -> r.trapdoorPos)
                    .orElse(null)
                : saved.getFirst().trapdoorPos;
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        if (msg.contains("!tp")) {
            Matcher matcher = WHISPER_PATTERN.matcher(msg);
            if (matcher.find()) {
                String senderName = matcher.group(1);
                this.info("Received message from: " + senderName, new Object[0]);
                if (this.allowedPlayer.get().isEmpty() || senderName.equalsIgnoreCase(this.allowedPlayer.get())) {
                    if (this.action.get() == MessageInteract.Action.LoadPearl) {
                        this.pearlTrapdoorPos = this.findPearlTrapdoor(senderName);
                        if (this.pearlTrapdoorPos == null) {
                            this.info("!tp from " + senderName + " but no tracked pearl with trapdoor found.", new Object[0]);
                            this.sendWhisper(senderName, "Could not find a tracked pearl or trapdoor for you.", generateAntiSpam(8));
                            return;
                        }

                        if (client.player.squaredDistanceTo(Vec3d.ofCenter(this.pearlTrapdoorPos)) > 256.0) {
                            this.info("!tp from " + senderName + " but pearl is out of render distance.", new Object[0]);
                            this.sendWhisper(senderName, "Pearl is out of render distance.", generateAntiSpam(8));
                            return;
                        }

                        this.info("Pearl found at " + this.pearlTrapdoorPos + " — pathfinding to load for " + senderName, new Object[0]);
                        this.sendWhisper(senderName, "Pearl found, loading...", generateAntiSpam(8));
                        this.startPos = BlockPos.ofFloored(VersionHelper.get().getPlayerPos());
                    }

                    this.aimTicks = 0;
                    this.actionPending = true;
                }
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (client.player != null && client.world != null && this.actionPending) {
            BlockPos resolvedPos = this.action.get() == MessageInteract.Action.LoadPearl ? this.pearlTrapdoorPos : this.position.get();
            if (resolvedPos == null) {
                this.actionPending = false;
            } else if (this.reinteractDelay > 0) {
                this.reinteractDelay--;
                if (this.reinteractDelay == 0) {
                    InventoryManager.interactBlock(new BlockHitResult(Vec3d.ofCenter(resolvedPos), Direction.UP, resolvedPos, false));
                    if (this.action.get() == MessageInteract.Action.LoadPearl && this.dropPearl.get()) {
                        int pearlSlot = InventoryManager.findInInventory(Items.ENDER_PEARL);
                        if (pearlSlot != -1) {
                            InventoryManager.dropSlot(pearlSlot, false);
                        }
                    }

                    this.actionPending = false;
                    this.pearlTrapdoorPos = null;
                    if (this.returnToStartPos.get() && this.startPos != null) {
                        BaritoneHelper.setGoalAndPath(new GoalBlock(this.startPos));
                    }
                }
            } else if (!HighwayUtils.isInRange(resolvedPos, 4.0)) {
                this.aimTicks = 0;
                BaritoneHelper.setGoalAndPath(new GoalNear(resolvedPos, 2));
            } else {
                BaritoneHelper.cancelAll();
                double yaw = Rotations.getYaw(resolvedPos);
                double pitch = Rotations.getPitch(resolvedPos);
                Rotations.rotate(yaw, pitch);
                this.aimTicks++;
                if (this.aimTicks >= 3) {
                    if (this.action.get() == MessageInteract.Action.Hit) {
                        client.interactionManager.attackBlock(resolvedPos, Direction.UP);
                        this.aimTicks = 0;
                        this.actionPending = false;
                    } else if (this.action.get() == MessageInteract.Action.Interact) {
                        InventoryManager.interactBlock(new BlockHitResult(Vec3d.ofCenter(resolvedPos), Direction.UP, resolvedPos, false));
                        this.aimTicks = 0;
                        this.actionPending = false;
                    } else if (client.world.getBlockState(resolvedPos).getBlock() instanceof TrapdoorBlock) {
                        InventoryManager.interactBlock(new BlockHitResult(Vec3d.ofCenter(resolvedPos), Direction.UP, resolvedPos, false));
                        this.aimTicks = 0;
                        this.reinteractDelay = 10;
                    } else {
                        this.info("Trapdoor no longer present at " + resolvedPos + ", aborting.", new Object[0]);
                        this.aimTicks = 0;
                        this.actionPending = false;
                        this.pearlTrapdoorPos = null;
                    }
                }
            }
        }
    }

    private void sendWhisper(String playerName, String message, String antiSpam) {
        if (client.player != null) {
            client.player.networkHandler.sendChatCommand("msg " + playerName + " " + message + " " + antiSpam);
        }
    }

    public static String generateAntiSpam(int length) {
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    .charAt(random.nextInt("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".length()))
            );
        }

        return "[" + sb + "]";
    }

    public static enum Action {
        Interact,
        Hit,
        LoadPearl;
    }
}
