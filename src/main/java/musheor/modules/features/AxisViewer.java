package musheor.modules.features;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.modules.automation.highway.HighwayNetwork;
import net.minecraft.util.math.Vec3d;

public class AxisViewer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgOverworld = this.settings.createGroup("Overworld");
    private final SettingGroup sgNether = this.settings.createGroup("Nether");
    private final SettingGroup sgEnd = this.settings.createGroup("End");
    private final Setting<Integer> renderRange = this.sgGeneral
        .add(new Builder().name("render-range").description("How far the lines should extend from the player.").defaultValue(128).min(8).sliderMax(256).build());
    private final Setting<Boolean> renderRingRoads = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-ring-roads")
                .description("Display square ring roads at specific intervals.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> renderDiamondHighways = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-diamond-highways")
                .description("Display diamond-shaped highways connecting cardinals.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> renderGrid = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-grid")
                .description("Display grid highways every 5000 blocks.")
                .defaultValue(true)
                .build()
        );
    private final Setting<AxisViewer.AxisType> overworldRender = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AxisViewer.AxisType>()
                            .name("render")
                        .description("Which axes to display.")
                    .defaultValue(AxisViewer.AxisType.None)
                .build()
        );
    private final Setting<Integer> overworldHeight = this.sgOverworld
        .add(
            new Builder()
                .name("height")
                .defaultValue(63)
                .sliderMin(-64)
                .sliderMax(319)
                .visible(() -> this.overworldRender.get() != AxisViewer.AxisType.None)
                .build()
        );
    private final Setting<SettingColor> overworldColor = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("color")
                .description("The line color.")
                .defaultValue(new SettingColor(25, 25, 225, 255))
                .visible(() -> this.overworldRender.get() != AxisViewer.AxisType.None)
                .build()
        );
    private final Setting<AxisViewer.AxisType> netherRender = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AxisViewer.AxisType>()
                            .name("render")
                        .description("Which axes to display.")
                    .defaultValue(AxisViewer.AxisType.Both)
                .build()
        );
    private final Setting<Integer> netherHeight = this.sgNether
        .add(
            new Builder()
                .name("height")
                .defaultValue(120)
                .sliderMin(0)
                .sliderMax(255)
                .visible(() -> this.netherRender.get() != AxisViewer.AxisType.None)
                .build()
        );
    private final Setting<SettingColor> netherColor = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("color")
                .description("The line color.")
                .defaultValue(new SettingColor(225, 25, 25, 255))
                .visible(() -> this.netherRender.get() != AxisViewer.AxisType.None)
                .build()
        );
    private final Setting<AxisViewer.AxisType> endRender = this.sgEnd
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AxisViewer.AxisType>()
                            .name("render")
                        .description("Which axes to display.")
                    .defaultValue(AxisViewer.AxisType.None)
                .build()
        );
    private final Setting<Integer> endHeight = this.sgEnd
        .add(new Builder().name("height").defaultValue(64).sliderMin(0).sliderMax(255).visible(() -> this.endRender.get() != AxisViewer.AxisType.None).build());
    private final Setting<SettingColor> endColor = this.sgEnd
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("color")
                .description("The line color.")
                .defaultValue(new SettingColor(225, 25, 25, 255))
                .visible(() -> this.endRender.get() != AxisViewer.AxisType.None)
                .build()
        );

    public AxisViewer() {
        super(musheor.MAIN, "axis-viewer", "Render highway axis'.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!this.mc.options.hudHidden && this.mc.player != null && this.mc.world != null) {
            AxisViewer.AxisType axisType;
            int y;
            Color lineColor;
            switch (PlayerUtils.getDimension()) {
                case Overworld:
                    axisType = this.overworldRender.get();
                    y = this.overworldHeight.get();
                    lineColor = this.overworldColor.get();
                    break;
                case Nether:
                    axisType = this.netherRender.get();
                    y = this.netherHeight.get();
                    lineColor = this.netherColor.get();
                    break;
                case End:
                    axisType = this.endRender.get();
                    y = this.endHeight.get();
                    lineColor = this.endColor.get();
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + PlayerUtils.getDimension());
            }

            if (axisType != AxisViewer.AxisType.None) {
                double renderY = y;
                double range = this.renderRange.get().intValue();
                double rangeSq = range * range;
                double px = this.mc.player.getX();
                double pz = this.mc.player.getZ();
                if (axisType.showsCardinals()) {
                    double distToLineSqX = pz * pz;
                    if (distToLineSqX < rangeSq) {
                        double offset = Math.sqrt(rangeSq - distToLineSqX);
                        this.drawLine(event, new Vec3d(px - offset, renderY, 0.0), new Vec3d(px + offset, renderY, 0.0), lineColor);
                    }

                    double distToLineSqZ = px * px;
                    if (distToLineSqZ < rangeSq) {
                        double offset = Math.sqrt(rangeSq - distToLineSqZ);
                        this.drawLine(event, new Vec3d(0.0, renderY, pz - offset), new Vec3d(0.0, renderY, pz + offset), lineColor);
                    }
                }

                if (axisType.showsDiagonals()) {
                    this.drawDiagonal(event, px, pz, renderY, range, true, lineColor);
                    this.drawDiagonal(event, px, pz, renderY, range, false, lineColor);
                }

                if (this.renderRingRoads.get()) {
                    for (double dist : HighwayNetwork.getRingDistances()) {
                        this.drawRingRoadSide(event, px, pz, -dist, true, renderY, range, lineColor);
                        this.drawRingRoadSide(event, px, pz, dist, true, renderY, range, lineColor);
                        this.drawRingRoadSide(event, px, pz, -dist, false, renderY, range, lineColor);
                        this.drawRingRoadSide(event, px, pz, dist, false, renderY, range, lineColor);
                    }
                }

                if (this.renderGrid.get()) {
                    this.drawGrid(event, px, pz, renderY, range, lineColor);
                }

                if (this.renderDiamondHighways.get()) {
                    for (double dist : HighwayNetwork.getDiamondDistances()) {
                        this.drawDiamondEdge(event, px, pz, dist, true, true, renderY, range, lineColor);
                        this.drawDiamondEdge(event, px, pz, -dist, false, false, renderY, range, lineColor);
                        this.drawDiamondEdge(event, px, pz, -dist, true, false, renderY, range, lineColor);
                        this.drawDiamondEdge(event, px, pz, dist, false, true, renderY, range, lineColor);
                    }
                }
            }
        }
    }

    private void drawDiagonal(Render3DEvent event, double px, double pz, double y, double range, boolean isPositive, Color color) {
        double cx = isPositive ? (px + pz) / 2.0 : (px - pz) / 2.0;
        double cz = isPositive ? cx : -cx;
        double dx = px - cx;
        double dz = pz - cz;
        double distToLineSq = dx * dx + dz * dz;
        if (distToLineSq < range * range) {
            double halfChord = Math.sqrt(range * range - distToLineSq);
            double spread = halfChord / Math.sqrt(2.0);
            this.drawLine(
                event, new Vec3d(cx - spread, y, cz - (isPositive ? spread : -spread)), new Vec3d(cx + spread, y, cz + (isPositive ? spread : -spread)), color
            );
        }
    }

    private void drawGrid(Render3DEvent event, double px, double pz, double y, double range, Color color) {
        double rangeSq = range * range;

        for (int i = 1; i <= 10; i++) {
            double coord = i * 5000.0 + 0.5;

            for (double c : new double[]{coord, -coord}) {
                double distZSq = (pz - c) * (pz - c);
                if (distZSq < rangeSq) {
                    double offset = Math.sqrt(rangeSq - distZSq);
                    double startX = Math.max(-50000.5, px - offset);
                    double endX = Math.min(50000.5, px + offset);
                    if (startX < endX) {
                        this.drawLine(event, new Vec3d(startX, y, c), new Vec3d(endX, y, c), color);
                    }
                }

                double distXSq = (px - c) * (px - c);
                if (distXSq < rangeSq) {
                    double offset = Math.sqrt(rangeSq - distXSq);
                    double startZ = Math.max(-50000.5, pz - offset);
                    double endZ = Math.min(50000.5, pz + offset);
                    if (startZ < endZ) {
                        this.drawLine(event, new Vec3d(c, y, startZ), new Vec3d(c, y, endZ), color);
                    }
                }
            }
        }
    }

    private void drawRingRoadSide(Render3DEvent event, double px, double pz, double coord, boolean isHorizontal, double y, double range, Color color) {
        double distToLineSq = isHorizontal ? Math.pow(pz - coord, 2.0) : Math.pow(px - coord, 2.0);
        if (distToLineSq < range * range) {
            double offset = Math.sqrt(range * range - distToLineSq);
            double limit = Math.abs(coord);
            if (isHorizontal) {
                double startX = Math.max(-limit, px - offset);
                double endX = Math.min(limit, px + offset);
                if (startX < endX) {
                    this.drawLine(event, new Vec3d(startX, y, coord), new Vec3d(endX, y, coord), color);
                }
            } else {
                double startZ = Math.max(-limit, pz - offset);
                double endZ = Math.min(limit, pz + offset);
                if (startZ < endZ) {
                    this.drawLine(event, new Vec3d(coord, y, startZ), new Vec3d(coord, y, endZ), color);
                }
            }
        }
    }

    private void drawDiamondEdge(
        Render3DEvent event, double px, double pz, double K, boolean isSum, boolean xMustBePositive, double y, double range, Color color
    ) {
        double cx = isSum ? (px - pz + K) / 2.0 : (px + pz + K) / 2.0;
        double cz = isSum ? (pz - px + K) / 2.0 : (pz + px - K) / 2.0;
        double dx = px - cx;
        double dz = pz - cz;
        double distSq = dx * dx + dz * dz;
        if (distSq < range * range) {
            double halfChord = Math.sqrt(range * range - distSq);
            double spread = halfChord / Math.sqrt(2.0);
            double x1 = cx - spread;
            if (isSum) {
                double var10000 = K - x1;
            } else {
                double var49 = x1 - K;
            }

            double x2 = cx + spread;
            if (isSum) {
                double var50 = K - x2;
            } else {
                double var51 = x2 - K;
            }

            double lowerX = xMustBePositive ? 0.0 : -Math.abs(K);
            double upperX = xMustBePositive ? Math.abs(K) : 0.0;
            double finalX1 = Math.max(lowerX, Math.min(upperX, x1));
            double finalX2 = Math.max(lowerX, Math.min(upperX, x2));
            double finalZ1 = isSum ? K - finalX1 : finalX1 - K;
            double finalZ2 = isSum ? K - finalX2 : finalX2 - K;
            if (Math.abs(finalX1 - finalX2) > 0.01) {
                this.drawLine(event, new Vec3d(finalX1, y, finalZ1), new Vec3d(finalX2, y, finalZ2), color);
            }
        }
    }

    private void drawLine(Render3DEvent event, Vec3d start, Vec3d end, Color color) {
        event.renderer.line(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), color);
    }

    public static enum AxisType {
        Both,
        Cardinals,
        Diagonals,
        None;

        boolean showsCardinals() {
            return this == Both || this == Cardinals;
        }

        boolean showsDiagonals() {
            return this == Both || this == Diagonals;
        }
    }
}
