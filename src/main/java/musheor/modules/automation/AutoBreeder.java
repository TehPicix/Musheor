package musheor.modules.automation;

import baritone.api.pathing.goals.GoalBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.manager.InventoryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public class AutoBreeder extends Module {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
        .add(new Builder().name("entities").description("Specific entities to breed.").defaultValue().onlyAttackable().build());
    private final Setting<Double> distance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("distance")
                .description("Maximum distance to pathfind to new mobs.")
                .defaultValue(32.0)
                .sliderRange(4.0, 128.0)
                .decimalPlaces(1)
                .build()
        );
    private final Map<UUID, Long> bredAnimals = new HashMap<>();
    private UUID pathTarget = null;

    public AutoBreeder() {
        super(musheor.AUTOMATION, "auto-breeder", "Automatically breeds specific animals.");
    }

    @Override
    public void onActivate() {
        this.bredAnimals.clear();
        this.pathTarget = null;
    }

    @Override
    public void onDeactivate() {
        BaritoneHelper.cancelAll();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.client.player != null && this.client.world != null) {
            long now = System.currentTimeMillis();
            this.bredAnimals.entrySet().removeIf(e -> now - e.getValue() >= 300000L);
            List<AnimalEntity> candidates = new ArrayList<>();

            for (Entity entity : this.client.world.getEntities()) {
                if (entity instanceof AnimalEntity animal
                    && this.entities.get().contains(animal.getType())
                    && !this.bredAnimals.containsKey(animal.getUuid())
                    && !animal.isBaby()
                    && animal.isBreedingItem(this.client.player.getMainHandStack())
                    && PlayerUtils.isWithin(animal, this.distance.get())) {
                    candidates.add(animal);
                }
            }

            candidates.sort(Comparator.comparingDouble(a -> a.squaredDistanceTo(this.client.player)));
            if (!candidates.isEmpty()) {
                AnimalEntity first = candidates.getFirst();
                if (!first.isBreedingItem(this.client.player.getMainHandStack())) {
                    for (int slot = 0; slot < this.client.player.getInventory().main.size(); slot++) {
                        ItemStack stack = this.client.player.getInventory().getStack(slot);
                        if (first.isBreedingItem(stack) && slot < 8) {
                            this.client.player.getInventory().selectedSlot = slot;
                        } else {
                            if (!first.isBreedingItem(stack)) {
                                this.info("No breeding items found in inventory, disabling...", new Object[0]);
                                this.toggle();
                                return;
                            }

                            InventoryManager.moveToHotbar(stack.getItem());
                        }
                    }

                    return;
                }
            }

            Iterator var10 = candidates.iterator();
            if (var10.hasNext()) {
                AnimalEntity animal = (AnimalEntity)var10.next();
                if (animal.isInRange(this.client.player, 2.5)) {
                    BaritoneHelper.cancelAll();
                    this.pathTarget = null;
                    float yaw = (float)Rotations.getYaw(animal);
                    float pitch = (float)Rotations.getPitch(animal);
                    Rotations.rotate(yaw, pitch);
                    this.client
                        .interactionManager
                        .sendSequencedPacket(this.client.world, sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));
                    VersionHelper.get().interactEntityAt(animal, Hand.MAIN_HAND);
                    this.client
                        .interactionManager
                        .sendSequencedPacket(this.client.world, sequence -> PlayerInteractEntityC2SPacket.interact(animal, false, Hand.MAIN_HAND));
                    this.bredAnimals.put(animal.getUuid(), System.currentTimeMillis());
                } else {
                    UUID uuid = animal.getUuid();
                    if (!uuid.equals(this.pathTarget) || !BaritoneHelper.isPathing()) {
                        this.pathTarget = uuid;
                        BaritoneHelper.setGoalAndPath(new GoalBlock(animal.getBlockPos()));
                    }
                }
            }
        }
    }
}
