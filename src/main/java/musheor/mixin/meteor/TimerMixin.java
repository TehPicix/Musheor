package musheor.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {Timer.class},
    remap = false
)
public abstract class TimerMixin extends Module {
    @Shadow
    @Final
    private Setting<Double> multiplier;
    @Unique
    private Setting<Boolean> autoTimer;
    @Unique
    private Setting<Boolean> onlyWhenFlying;
    @Unique
    private Setting<Double> minThreshold;
    @Unique
    private Setting<Double> maxThreshold;
    @Unique
    private Setting<Integer> scanRadius;
    @Unique
    private Setting<Integer> unloadedThreshold;
    @Unique
    private Setting<Double> adjustmentFactor;
    @Unique
    private double currentThreshold;

    public TimerMixin() {
        super(musheor.AUTOMATION, "auto-timer", "Automatically changes the speed of everything in your client");
    }

    @Inject(
        method = {"<init>"},
        at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo ci) {
        this.autoTimer = this.settings
            .getDefaultGroup()
            .add(new Builder().name("auto-timer").description("Automatically adjust timer speed based on chunk loading").defaultValue(false).build());
        this.onlyWhenFlying = this.settings
            .getDefaultGroup()
            .add(
                new Builder()
                    .name("only-when-flying")
                    .description("Only activates and adjusts itself when the player is flying with an elytra")
                    .visible(this.autoTimer::get)
                    .defaultValue(true)
                    .build()
            );
        this.minThreshold = this.settings
            .getDefaultGroup()
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("min-threshold")
                    .description("Minimum value timer is allowed to set itself to")
                    .defaultValue(0.4)
                    .sliderRange(0.1, 1.0)
                    .decimalPlaces(2)
                    .visible(this.autoTimer::get)
                    .build()
            );
        this.maxThreshold = this.settings
            .getDefaultGroup()
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("max-threshold")
                    .description("Maximum value timer is allowed to set itself to [default = 1.0]")
                    .defaultValue(1.0)
                    .sliderRange(0.1, 1.0)
                    .decimalPlaces(2)
                    .visible(this.autoTimer::get)
                    .build()
            );
        this.scanRadius = this.settings
            .getDefaultGroup()
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("scan-radius")
                    .description("Radius in chunks to scan")
                    .defaultValue(4)
                    .sliderRange(1, 10)
                    .visible(this.autoTimer::get)
                    .build()
            );
        this.unloadedThreshold = this.settings
            .getDefaultGroup()
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("unloaded-threshold")
                    .description("Amount of unloaded chunks before timer is allowed to adjust itself")
                    .defaultValue(5)
                    .sliderRange(1, 20)
                    .visible(this.autoTimer::get)
                    .build()
            );
        this.adjustmentFactor = this.settings
            .getDefaultGroup()
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("adjustment-factor")
                    .description("The factor in which timer is allowed to adjust itself")
                    .defaultValue(0.1)
                    .sliderRange(0.1, 1.0)
                    .decimalPlaces(2)
                    .visible(this.autoTimer::get)
                    .build()
            );
    }

    @Override
    public void onActivate() {
        this.currentThreshold = this.multiplier.get();
    }

    @Unique
    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null && this.autoTimer.get()) {
            if (this.mc.player.isGliding() || !this.onlyWhenFlying.get()) {
                int unloadedChunks = this.countUnloadedChunks();
                double severity = Math.min(1.0, unloadedChunks / (this.unloadedThreshold.get().intValue() * 2.0));
                double targetSpeed = unloadedChunks > this.unloadedThreshold.get()
                    ? this.minThreshold.get() + (this.maxThreshold.get() - this.minThreshold.get()) * (1.0 - severity)
                    : this.maxThreshold.get();
                double difference = targetSpeed - this.currentThreshold;
                if (Math.abs(difference) > 0.01) {
                    this.currentThreshold = this.currentThreshold + difference * this.adjustmentFactor.get();
                    this.multiplier.set(this.currentThreshold);
                }
            }
        }
    }

    @Unique
    private int countUnloadedChunks() {
        ClientChunkManager chunkManager = this.mc.world.getChunkManager();
        ChunkPos playerPos = this.mc.player.getChunkPos();
        int radius = this.scanRadius.get();
        int unloadedCount = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (chunkManager.getChunk(playerPos.x + x, playerPos.z + z, ChunkStatus.FULL, false) == null) {
                    if (++unloadedCount > this.unloadedThreshold.get()) {
                        return unloadedCount;
                    }
                }
            }
        }

        return unloadedCount;
    }
}
