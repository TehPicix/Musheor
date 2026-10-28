package musheor.utils.system;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import musheor.commands.RestockConfig;
import musheor.compat.VersionHelper;
import musheor.utils.highway.HighwayState;
import musheor.utils.hud.CustomHudButton;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public class MusheorSystem extends System<MusheorSystem> {
    public final Settings settings = new Settings();
    public static MusheorSystem Manager;
    public boolean update;
    public final List<CustomHudButton> customButtons = new ArrayList<>();
    SettingGroup placementManagerSettings = this.settings.createGroup("Placement-Manager");
    SettingGroup packetManagerSettings = this.settings.createGroup("Packet-Manager");
    SettingGroup renderManagerSettings = this.settings.createGroup("Render-Manager");
    public final Setting<Boolean> enableChatDebugging = this.settings
        .getDefaultGroup()
        .add(new Builder().name("debug-mode").description("Enable debug messages in chat.").defaultValue(false).build());
    public final Setting<Boolean> disableMutuals = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("disable-capes-and-glowing")
                .description("Stops your client from rendering your own and other people's Musheor capes and glowing effect.")
                .defaultValue(false)
                .build()
        );
    public final Setting<Keybind> openEditor = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("open-button-editor-bind")
                .description("Open the hud-button editor.")
                .defaultValue(Keybind.fromKey(345))
                .build()
        );
    public final Setting<MusheorSystem.PlacementType> placementType = this.placementManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MusheorSystem.PlacementType>()
                                .name("placement-type")
                            .description("What type of placement to use, airplace or rotations")
                        .defaultValue(MusheorSystem.PlacementType.Airplace)
                    .onChanged(val -> {
                        HighwayState.getInstance().setAirPlaceSwap(val == MusheorSystem.PlacementType.AirPlaceSwap);
                        HighwayState.getInstance().setPlacementType(val);
                    })
                .build()
        );
    public final Setting<Boolean> silentPlace = this.placementManagerSettings
        .add(
            new Builder()
                .name("silent-place")
                .description("Allows the player to place blocks without holding / selecting them (block is still required in the hotbar!)")
                .defaultValue(true)
                .build()
        );
    public final Setting<Double> placementRange = this.placementManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("place-range")
                .description("In what range the player can / should place blocks")
                .defaultValue(4.5)
                .min(1.0)
                .max(8.0)
                .decimalPlaces(1)
                .build()
        );
    public final Setting<Integer> placementTimeout = this.placementManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("placement-timeout")
                .description("Ticks to wait before retrying a failed placement (higher = safer for high ping)")
                .defaultValue(5)
                .min(1)
                .max(20)
                .build()
        );
    public final Setting<Integer> swapDelay = this.placementManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("swap-delay")
                .description("Ticks to wait after swapping to an item (higher = safer for high ping)")
                .defaultValue(3)
                .min(1)
                .max(10)
                .build()
        );
    public final Setting<Integer> globalPacketLimit = this.packetManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("global-packet-limit")
                .description("Packet limit before the server kicks you")
                .defaultValue(1249)
                .sliderMax(1500)
                .build()
        );
    public final Setting<Integer> invPacketLimit = this.packetManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("inventory-packet-limit")
                .description("Inventory action limiter before the server kicks you")
                .defaultValue(79)
                .sliderMax(150)
                .build()
        );
    public final Setting<Boolean> noPacketKick = this.packetManagerSettings
        .add(new Builder().name("anti-packet-kicks").description("Prevent inventory slot actions from getting you packet kicked").defaultValue(true).build());
    public final Setting<Boolean> placeRender = this.renderManagerSettings
        .add(
            new Builder()
                .name("enable-placement-rendering")
                .description("Renders the positions of blocks in relation to placing")
                .defaultValue(true)
                .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<Boolean> breakRender = this.renderManagerSettings
        .add(
            new Builder()
                .name("enable-break-rendering")
                .description("Renders the positions of blocks in relation to breaking")
                .defaultValue(true)
                .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<MusheorSystem.RenderType> renderType = this.renderManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MusheorSystem.RenderType>()
                                    .name("render-type")
                                .description("Choose what type of rendering applies, either single-color or automatic color mapping")
                            .defaultValue(MusheorSystem.RenderType.Static)
                        .visible(() -> this.placeRender.get() || this.breakRender.get())
                    .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<ShapeMode> renderShape = this.renderManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
                                    .name("render-shape")
                                .description("What kind of shape (lines, sides or both) block positions get rendered as")
                            .defaultValue(ShapeMode.Lines)
                        .visible(() -> this.placeRender.get() || this.breakRender.get())
                    .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<Boolean> renderLines = this.renderManagerSettings
        .add(
            new Builder()
                .name("render-lines")
                .description("Renders the outline of blocks / positions")
                .defaultValue(true)
                .visible(
                    () -> this.renderShape.get() == ShapeMode.Lines
                        || this.renderShape.get() == ShapeMode.Both && (this.placeRender.get() || this.breakRender.get())
                )
                .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<SettingColor> renderLineColor = this.renderManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("render-line-color")
                .description("Line color of rendered blocks")
                .defaultValue(new SettingColor(0, 225, 255, 200))
                .visible(
                    () -> (this.renderShape.get() == ShapeMode.Lines || this.renderShape.get() == ShapeMode.Both)
                        && this.renderLines.get()
                        && this.renderType.get() == MusheorSystem.RenderType.Static
                        && (this.placeRender.get() || this.breakRender.get())
                )
                .build()
        );
    public final Setting<Integer> renderLineAlpha = this.renderManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-line-alpha")
                .description("Line color intensity of rendered blocks")
                .defaultValue(200)
                .min(0)
                .max(255)
                .sliderMin(0)
                .sliderMax(255)
                .visible(
                    () -> (this.renderShape.get() == ShapeMode.Lines || this.renderShape.get() == ShapeMode.Both)
                        && this.renderLines.get()
                        && this.renderType.get() == MusheorSystem.RenderType.Mapped
                        && (this.placeRender.get() || this.breakRender.get())
                )
                .build()
        );
    public final Setting<Boolean> renderSides = this.renderManagerSettings
        .add(
            new Builder()
                .name("render-sides")
                .description("Renders the sides of blocks / positions")
                .defaultValue(false)
                .visible(
                    () -> this.renderShape.get() == ShapeMode.Sides
                        || this.renderShape.get() == ShapeMode.Both && (this.placeRender.get() || this.breakRender.get())
                )
                .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<SettingColor> renderSideColor = this.renderManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("render-side-color")
                .description("Side color of rendered blocks")
                .defaultValue(new SettingColor(0, 200, 255, 100))
                .visible(
                    () -> (this.renderShape.get() == ShapeMode.Sides || this.renderShape.get() == ShapeMode.Both)
                        && this.renderSides.get()
                        && this.renderType.get() == MusheorSystem.RenderType.Static
                        && (this.placeRender.get() || this.breakRender.get())
                )
                .build()
        );
    public final Setting<Integer> renderSideAlpha = this.renderManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-side-alpha")
                .description("Side color intensity of rendered blocks")
                .defaultValue(100)
                .min(0)
                .max(255)
                .sliderMin(0)
                .sliderMax(255)
                .visible(
                    () -> (this.renderShape.get() == ShapeMode.Sides || this.renderShape.get() == ShapeMode.Both)
                        && this.renderSides.get()
                        && this.renderType.get() == MusheorSystem.RenderType.Mapped
                        && (this.placeRender.get() || this.breakRender.get())
                )
                .build()
        );
    SettingGroup flightManagerSettings = this.settings.createGroup("Flight-Manager");
    public final Setting<Boolean> autoRocket = this.flightManagerSettings
        .add(
            new Builder()
                .name("auto-rocket")
                .description("Fires a rocket automatically whenever the previous one despawns.")
                .defaultValue(true)
                .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<Double> rocketDelay = this.flightManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("rocket-delay")
                .description("Seconds between rockets when auto-rocket is off.")
                .defaultValue(3.5)
                .sliderRange(0.5, 15.0)
                .decimalPlaces(1)
                .visible(() -> !this.autoRocket.get())
                .build()
        );
    public final Setting<Integer> launchDelay = this.flightManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("launch-delay")
                .description("Delay in ticks before launching the player from the ground")
                .defaultValue(0)
                .sliderRange(0, 5)
                .build()
        );
    public final Setting<Integer> rocketTimeout = this.flightManagerSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("rocket-timeout")
                .description("Timeout in ticks before assuming a rocket has been fired, increase this if you are on higher ping")
                .defaultValue(3)
                .sliderRange(0, 10)
                .build()
        );
    SettingGroup highwaySettings = this.settings.createGroup("Highways");
    public final Setting<Integer> cardinalHighwayWidth = this.highwaySettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("cardinal-width")
                .description("Pavement width for cardinal and ring road highways.")
                .defaultValue(4)
                .min(1)
                .sliderMax(16)
                .build()
        );
    public final Setting<Integer> diagonalHighwayWidth = this.highwaySettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("diagonal-width")
                .description("Pavement width for diagonal and diamond highways.")
                .defaultValue(9)
                .min(1)
                .sliderMax(16)
                .build()
        );
    public final Setting<Integer> gridHighwayWidth = this.highwaySettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("grid-width")
                .description("Pavement width for grid highways.")
                .defaultValue(3)
                .min(1)
                .sliderMax(16)
                .build()
        );
    public final Setting<Integer> highwayForwardRange = this.highwaySettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("forward-range-highway")
                .description("How many blocks ahead of the player need to be checked or used for breaking and placing blocks")
                .defaultValue(2)
                .sliderRange(1, 5)
                .build()
        );
    public final Setting<Boolean> postStats = this.highwaySettings
        .add(
            new Builder()
                .name("post-stats")
                .description(
                    "Allow stats to be posted to musheck's server and tracked on a leaderboard (ONLY HIGHWAY PLACEMENT / BREAK STATS, COORDINATES OR LOCATION IS NEVER SENT!"
                )
                .defaultValue(false)
                .onChanged(i -> this.update = true)
                .build()
        );
    public final Setting<String> apiKey = this.highwaySettings
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("post-api-key")
                .description("Your personal key for stats-tracking. Join discord.gg/crankers to request or create your own API key.")
                .defaultValue("")
                .visible(this.postStats::get)
                .build()
        );
    public final Setting<Integer> postIntervalSeconds = this.highwaySettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("post-interval")
                .description("How often posting is sent back to the API, in seconds. Lower value = more accurate")
                .defaultValue(10)
                .min(5)
                .sliderRange(5, 60)
                .visible(this.postStats::get)
                .build()
        );

    public MusheorSystem() {
        super("musheor");
        Manager = this;
    }

    public static MusheorSystem get() {
        return Systems.get(MusheorSystem.class);
    }

    public Settings getSettings() {
        return this.settings;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.put("settings", this.settings.toTag());
        tag.put("restock_containers", RestockConfig.toNbt());
        NbtList buttonList = new NbtList();

        for (CustomHudButton btn : this.customButtons) {
            buttonList.add(btn.toTag());
        }

        tag.put("screen-button-editor", buttonList);
        return tag;
    }

    public MusheorSystem fromTag(NbtCompound tag) {
        if (tag.contains("settings")) {
            this.settings.fromTag(VersionHelper.get().getCompound(tag, "settings"));
        }

        if (tag.contains("restock_containers")) {
            RestockConfig.fromNbt(VersionHelper.get().getCompound(tag, "restock_containers"));
        }

        this.customButtons.clear();
        if (tag.contains("screen-button-editor")) {
            for (NbtElement el : VersionHelper.get().getList(tag, "screen-button-editor", 10)) {
                CustomHudButton btn = CustomHudButton.fromTag((NbtCompound)el);
                this.customButtons.add(btn);
            }
        }

        return this;
    }

    public static void debug(String format, Object... args) {
        if (format != null && args != null) {
            for (Object arg : args) {
                if (arg == null) {
                    return;
                }
            }

            String formattedMessage = String.format(format, args);
            LocalTime now = LocalTime.now();
            String timestamp = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String messageWithTimestamp = String.format("[%s] %s", timestamp, formattedMessage);
            boolean enableChatDebugging = Manager.enableChatDebugging.get();
            if (enableChatDebugging && messageWithTimestamp.length() < 256) {
                ChatUtils.info(messageWithTimestamp);
            }
        }
    }

    public static enum PlacementType {
        Airplace,
        AirPlaceSwap,
        Rotate;
    }

    public static enum RenderType {
        Static,
        Mapped;
    }

    public static enum TabListMode {
        All,
        Friends;
    }
}
