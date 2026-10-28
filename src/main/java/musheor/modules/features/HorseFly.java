package musheor.modules.features;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.AbstractHorseEntity;

public class HorseFly extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Double> speed = this.sgGeneral
        .add(new Builder().name("speed").description("Horizontal speed, blocks per tick.").defaultValue(5.0).range(0.1, 50.0).sliderRange(0.1, 10.0).build());
    public final Setting<Double> verticalSpeed = this.sgGeneral
        .add(
            new Builder()
                .name("vertical-speed")
                .description("Up/down speed, blocks per tick. Jump goes up, sprint goes down.")
                .defaultValue(0.5)
                .range(0.0, 5.0)
                .sliderRange(0.0, 5.0)
                .build()
        );
    public final Setting<Double> glide = this.sgGeneral
        .add(
            new Builder()
                .name("glide")
                .description("How far you sink each tick when you aren't going up or down.")
                .defaultValue(0.15)
                .range(0.0, 0.5)
                .sliderRange(0.0, 0.5)
                .decimalPlaces(3)
                .build()
        );
    public final Setting<Boolean> nanInput = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("nan-input")
                .description("Sends NaN horse steering input through ViaFabricPlus (server version <= 1.21.1). Needed to bypass Grim.")
                .defaultValue(true)
                .onChanged(value -> resetNanState())
                .build()
        );
    public final Setting<Integer> nanDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("nan-delay")
                .description("Ordinary input ticks between NaN inputs. 0 sends NaN every tick.")
                .defaultValue(0)
                .range(0, 20)
                .sliderRange(0, 20)
                .visible(this.nanInput::get)
                .onChanged(value -> resetNanState())
                .build()
        );
    private static volatile boolean sendNanThisTick;
    private static int nanDelayCounter;

    public HorseFly() {
        super(musheor.MAIN, "horse-fly", "Fly horses.");
    }

    @Override
    public void onActivate() {
        resetNanState();
    }

    @Override
    public void onDeactivate() {
        resetNanState();
    }

    public static boolean shouldSendNan() {
        return sendNanThisTick;
    }

    private static void resetNanState() {
        sendNanThisTick = false;
        nanDelayCounter = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        Entity mount = this.mc.player == null ? null : this.mc.player.getVehicle();
        boolean eligible = this.nanInput.get() && this.isControlledHorse(mount);
        sendNanThisTick = eligible && nanDelayCounter == 0;
        nanDelayCounter = eligible && nanDelayCounter < this.nanDelay.get() ? nanDelayCounter + 1 : 0;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetNanState();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetNanState();
    }

    private boolean isControlledHorse(Entity entity) {
        return this.isActive()
            && this.mc.player != null
            && this.mc.world != null
            && entity != this.mc.player
            && entity instanceof AbstractHorseEntity
            && entity.getControllingPassenger() == this.mc.player;
    }

    public boolean handleTravel(Entity e) {
        if (!this.isControlledHorse(e)) {
            return false;
        } else {
            double f = (this.mc.options.forwardKey.isPressed() ? 1 : 0) - (this.mc.options.backKey.isPressed() ? 1 : 0);
            double s = (this.mc.options.leftKey.isPressed() ? 1 : 0) - (this.mc.options.rightKey.isPressed() ? 1 : 0);
            double x = 0.0;
            double z = 0.0;
            double len = Math.sqrt(s * s + f * f);
            if (len > 1.0E-4) {
                s /= len;
                f /= len;
                double yaw = Math.toRadians(this.mc.player.getYaw());
                double sin = Math.sin(yaw);
                double cos = Math.cos(yaw);
                x = (s * cos - f * sin) * this.speed.get();
                z = (f * cos + s * sin) * this.speed.get();
            }

            boolean up = this.mc.options.jumpKey.isPressed();
            boolean down = this.mc.options.sprintKey.isPressed();
            double y = up == down ? -this.glide.get() : (up ? this.verticalSpeed.get() : -this.verticalSpeed.get());
            e.setVelocity(x, y, z);
            e.move(MovementType.SELF, e.getVelocity());
            e.onLanding();
            return true;
        }
    }
}
