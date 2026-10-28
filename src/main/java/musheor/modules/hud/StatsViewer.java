package musheor.modules.hud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import musheor.musheor;
import musheor.modules.automation.HighwayBuilder;
import musheor.utils.HighwayStatsUtils;
import musheor.utils.highway.HighwayState;
import net.minecraft.client.MinecraftClient;

public class StatsViewer extends HudElement {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public static final HudElementInfo<StatsViewer> INFO = new HudElementInfo<>(
        musheor.MUSHEOR_HUD, "stats-viewer", "View your stats while using the HighwayBuilder module.", StatsViewer::new
    );
    private final Setting<Double> scale = this.sgGeneral
        .add(new Builder().name("scale").description("I mean cmon do I really have to explain??").defaultValue(1.0).build());
    private final Setting<Integer> sectionSize = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("section-size")
                .description("Distance or size of a single highway section")
                .defaultValue(10000)
                .build()
        );
    private final Setting<Boolean> displaySession = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("display-session")
                .description("Display statistics of the current session.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> displayBlocks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("display-blocks")
                .description("Display statistics of blocks mined and placed.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> displayPerformance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("display-performance")
                .description("Display statistics of paver's performance.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> displayInfo = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("display-info")
                .description("Display extra information about the environment, inventory and ETA.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> displayLifetime = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("display-lifetime")
                .description("Display lifetime statistics about the paver.")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> prefixColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("prefix-color")
                .description("Text color of the hud info lines.")
                .defaultValue(new SettingColor(255, 255, 225, 255))
                .build()
        );
    private final Setting<SettingColor> valueColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("value-color")
                .description("Text color of the hud info lines.")
                .defaultValue(new SettingColor(255, 0, 208, 255))
                .build()
        );

    public StatsViewer() {
        super(INFO);
    }

    private String[][] buildLines() {
        List<String[]> lines = new ArrayList<>();
        HighwayState state = HighwayState.getInstance();
        String[][] session = new String[][]{
            {"Latest session: ", ""},
            {"  Runtime: ", HighwayStatsUtils.formatTicksAsTime(state.getRuntimeTicks())},
            {"  Direction: ", String.valueOf(HighwayBuilder.getDirection()).toLowerCase()},
            {"  Distance travelled: ", String.valueOf(HighwayStatsUtils.getDistanceTravelled())}
        };
        String[][] blocks = new String[][]{
            {"Blocks", ""},
            {"  Obsidian Placed: ", HighwayStatsUtils.formatCompact(state.getObsidianPlaced())},
            {"  Obsidian Mined: ", HighwayStatsUtils.formatCompact(state.getObsidianMined())},
            {"  Enderchests mined: ", HighwayStatsUtils.formatCompact(state.getEchestsMined())},
            {"  Netherrack mined: ", HighwayStatsUtils.formatCompact(state.getNetherrackMined())},
            {"  Total mined: ", HighwayStatsUtils.formatCompact(state.getObsidianMined() + state.getNetherrackMined() + state.getEchestsMined())}
        };
        String[][] performance = new String[][]{
            {"Performance", ""},
            {"  Placements / s: ", HighwayStatsUtils.formatPlacementsPerSecond(HighwayStatsUtils.getPlacementsPerSecond())},
            {"  Placements / h: ", HighwayStatsUtils.formatPlacementsPerHour(HighwayStatsUtils.getPlacementsPerSecond())},
            {"  Breaks / s: ", HighwayStatsUtils.formatBreaksPerSecond(HighwayStatsUtils.getBreaksPerSecond())},
            {"  Distance / s: ", HighwayStatsUtils.formatDistancePerSecond(HighwayStatsUtils.getDistanceTravelled())},
            {"  Distance / h: ", HighwayStatsUtils.formatDistancePerHour(HighwayStatsUtils.getDistanceTravelled())}
        };
        String[][] info = new String[][]{
            {"Info", ""},
            {"  Distance to next section: ", String.valueOf(HighwayStatsUtils.getDistanceToNextSection(this.sectionSize.get()))},
            {"  Percentage completed: ", HighwayStatsUtils.formatSectionProgress(this.sectionSize.get())},
            {"  Material Count: ", String.valueOf(HighwayStatsUtils.getMaterialCount())},
            {"  ETA to next section: ", HighwayStatsUtils.formatSectionEta(this.sectionSize.get())},
            {"  ETA to next refill: ", HighwayStatsUtils.formatRefillEta()}
        };
        String[][] lifetime = new String[][]{
            {"Lifetime: ", ""},
            {"  Obsidian Placed: ", HighwayStatsUtils.formatCompact(state.getLifetimeObsidianPlaced())},
            {"  Obsidian Mined: ", HighwayStatsUtils.formatCompact(state.getLifetimeObsidianMined())},
            {"  Enderchests mined: ", HighwayStatsUtils.formatCompact(state.getLifetimeEchestsMined())},
            {"  Netherrack mined: ", HighwayStatsUtils.formatCompact(state.getLifetimeNetherrackMined())},
            {"  Total mined: ", HighwayStatsUtils.formatCompact(state.getLifetimeBlocksMined())}
        };
        if (this.displaySession.get()) {
            lines.addAll(Arrays.asList(session));
        }

        if (this.displayBlocks.get()) {
            lines.addAll(Arrays.asList(blocks));
        }

        if (this.displayPerformance.get()) {
            lines.addAll(Arrays.asList(performance));
        }

        if (this.displayInfo.get()) {
            lines.addAll(Arrays.asList(info));
        }

        if (this.displayLifetime.get()) {
            lines.addAll(Arrays.asList(lifetime));
        }

        return lines.toArray(new String[0][]);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().world != null) {
            String[][] lines = this.buildLines();
            double lineHeight = renderer.textHeight(true, this.scale.get());
            double width = 0.0;

            for (String[] line : lines) {
                double lineWidth = renderer.textWidth(line[0], true) + renderer.textWidth(": ", true) + renderer.textWidth(line[1], true, this.scale.get());
                width = Math.max(width, lineWidth);
            }

            this.setSize(width, lineHeight * lines.length);
            double currentY = this.y;

            for (String[] line : lines) {
                double currentX = this.x;
                renderer.text(line[0], currentX, currentY, this.prefixColor.get(), true, this.scale.get());
                currentX += renderer.textWidth(line[0], true, this.scale.get());
                renderer.text("", currentX, currentY, this.prefixColor.get(), true, this.scale.get());
                currentX += renderer.textWidth("", true, this.scale.get());
                if (line[0].equals("Progress")) {
                    String percentageText = line[1];
                    String doneText = " done";
                    double percentageWidth = renderer.textWidth(percentageText, true);
                    renderer.textWidth(doneText, true, this.scale.get());
                    renderer.text(percentageText, currentX, currentY, this.valueColor.get(), true, this.scale.get());
                    currentX += percentageWidth;
                    renderer.text(doneText, currentX, currentY, this.prefixColor.get(), true, this.scale.get());
                } else {
                    renderer.text(line[1], currentX, currentY, this.valueColor.get(), true, this.scale.get());
                }

                currentY += lineHeight;
            }
        }
    }
}
