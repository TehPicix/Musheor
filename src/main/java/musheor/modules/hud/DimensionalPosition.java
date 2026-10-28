package musheor.modules.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import musheor.musheor;
import musheor.modules.features.CoordHider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

public class DimensionalPosition extends HudElement {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Boolean> showOppositeDimension = this.sgGeneral
        .add(new Builder().name("show-opposite-dimension").description("Show relative position in opposite dimension.").defaultValue(true).build());
    private final Setting<Boolean> displayHorizontal = this.sgGeneral
        .add(
            new Builder()
                .name("display-horizontal")
                .description("Displays opposite dimension horizontally instead of vertically.")
                .defaultValue(true)
                .visible(this.showOppositeDimension::get)
                .build()
        );
    private final Setting<Double> textScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .sliderRange(0.1, 3.0)
                .decimalPlaces(1)
                .build()
        );
    private final Setting<Boolean> showDecimalPlaces = this.sgGeneral
        .add(
            new Builder()
                .name("show-decimal-places")
                .description("Displays the decimal places of the coordinates.")
                .defaultValue(true)
                .visible(this.showOppositeDimension::get)
                .build()
        );
    private final Setting<SettingColor> overworldColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("overworld-color")
                .description("Color for overworld coordinates.")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .build()
        );
    private final Setting<SettingColor> netherColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("nether-color")
                .description("Color for nether coordinates.")
                .defaultValue(new SettingColor(255, 0, 0, 255))
                .build()
        );
    private final Setting<SettingColor> endColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("end-color")
                .description("Color for end coordinates.")
                .defaultValue(new SettingColor(255, 0, 255, 255))
                .build()
        );
    public static final HudElementInfo<DimensionalPosition> INFO = new HudElementInfo<>(
        musheor.MUSHEOR_HUD, "dimensional-position", "A hud module that displays your position", DimensionalPosition::new
    );

    public DimensionalPosition() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (this.client.player != null && this.client.world != null) {
            CoordHider ch = Modules.get().get(CoordHider.class);
            boolean hiding = ch != null && ch.isActive();
            double px = this.client.player.getX();
            double py = this.client.player.getY();
            double pz = this.client.player.getZ();
            String sx = hiding ? "?" : this.formatCoordinate(px);
            String sy = hiding ? "?" : this.formatCoordinate(py);
            String sz = hiding ? "?" : this.formatCoordinate(pz);
            List<String[]> rows = new ArrayList<>();
            List<SettingColor> colors = new ArrayList<>();
            if (this.client.world.getRegistryKey() == World.OVERWORLD) {
                rows.add(new String[]{"Overworld:", sx, sy, sz});
                colors.add(this.overworldColor.get());
                if (this.showOppositeDimension.get()) {
                    rows.add(new String[]{"Nether:", hiding ? "?" : this.formatCoordinate(px / 8.0), sy, hiding ? "?" : this.formatCoordinate(pz / 8.0)});
                    colors.add(this.netherColor.get());
                }
            } else if (this.client.world.getRegistryKey() == World.NETHER) {
                rows.add(new String[]{"Nether:", sx, sy, sz});
                colors.add(this.netherColor.get());
                if (this.showOppositeDimension.get()) {
                    rows.add(new String[]{"Overworld:", hiding ? "?" : this.formatCoordinate(px * 8.0), sy, hiding ? "?" : this.formatCoordinate(pz * 8.0)});
                    colors.add(this.overworldColor.get());
                }
            } else if (this.client.world.getRegistryKey() == World.END) {
                rows.add(new String[]{"End:", sx, sy, sz});
                colors.add(this.endColor.get());
            }

            if (!rows.isEmpty()) {
                double lineHeight = renderer.textHeight(true, this.textScale.get());
                double sepW = renderer.textWidth(", ", true, this.textScale.get());
                double gapW = renderer.textWidth(" ", true, this.textScale.get());
                if (rows.size() == 2 && this.displayHorizontal.get()) {
                    double cx = this.x;

                    for (int i = 0; i < rows.size(); i++) {
                        if (i > 0) {
                            cx += renderer.textWidth("   ", true, this.textScale.get());
                        }

                        cx = this.drawRowInline(renderer, rows.get(i), colors.get(i), cx, this.y, sepW, gapW);
                    }

                    this.setSize(cx - this.x, lineHeight);
                } else {
                    double labelW = 0.0;
                    double xW = 0.0;
                    double yW = 0.0;
                    double zW = 0.0;

                    for (String[] row : rows) {
                        labelW = Math.max(labelW, renderer.textWidth(row[0], true, this.textScale.get()));
                        xW = Math.max(xW, renderer.textWidth(row[1], true, this.textScale.get()));
                        yW = Math.max(yW, renderer.textWidth(row[2], true, this.textScale.get()));
                        zW = Math.max(zW, renderer.textWidth(row[3], true, this.textScale.get()));
                    }

                    double totalWidth = labelW + gapW + xW + sepW + yW + sepW + zW;
                    double currentY = this.y;

                    for (int i = 0; i < rows.size(); i++) {
                        String[] row = rows.get(i);
                        SettingColor color = colors.get(i);
                        double cx = this.x;
                        renderer.text(row[0], cx + labelW - renderer.textWidth(row[0], true, this.textScale.get()), currentY, color, true, this.textScale.get());
                        cx += labelW + gapW;
                        renderer.text(row[1], cx + xW - renderer.textWidth(row[1], true, this.textScale.get()), currentY, color, true, this.textScale.get());
                        cx += xW;
                        renderer.text(" ", cx, currentY, color, true, this.textScale.get());
                        cx += sepW;
                        renderer.text(row[2], cx + yW - renderer.textWidth(row[2], true, this.textScale.get()), currentY, color, true, this.textScale.get());
                        cx += yW;
                        renderer.text(" ", cx, currentY, color, true, this.textScale.get());
                        cx += sepW;
                        renderer.text(row[3], cx + zW - renderer.textWidth(row[3], true, this.textScale.get()), currentY, color, true, this.textScale.get());
                        currentY += lineHeight;
                    }

                    this.setSize(totalWidth, currentY - this.y);
                }
            }
        }
    }

    private double drawRowInline(HudRenderer renderer, String[] row, SettingColor color, double startX, double startY, double sepW, double gapW) {
        renderer.text(row[0], startX, startY, color, true, this.textScale.get());
        double cx = startX + (renderer.textWidth(row[0], true, this.textScale.get()) + gapW);
        renderer.text(row[1], cx, startY, color, true, this.textScale.get());
        cx += renderer.textWidth(row[1], true, this.textScale.get());
        renderer.text(" ", cx, startY, color, true, this.textScale.get());
        cx += sepW;
        renderer.text(row[2], cx, startY, color, true, this.textScale.get());
        cx += renderer.textWidth(row[2], true, this.textScale.get());
        renderer.text(" ", cx, startY, color, true, this.textScale.get());
        cx += sepW;
        renderer.text(row[3], cx, startY, color, true, this.textScale.get());
        return cx + renderer.textWidth(row[3], true, this.textScale.get());
    }

    private String formatCoordinate(double v) {
        return !this.showDecimalPlaces.get() ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%,.1f", v);
    }
}
