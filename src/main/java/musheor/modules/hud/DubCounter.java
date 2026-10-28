package musheor.modules.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import musheor.musheor;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class DubCounter extends HudElement {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Boolean> doubleChests = this.sgGeneral
        .add(new Builder().name("double-chests").description("Counts connected double chests (including trapped ones).").defaultValue(true).build());
    private final Setting<Boolean> singleChests = this.sgGeneral
        .add(new Builder().name("single-chests").description("Counts single, unconnected chests (including trapped ones).").defaultValue(false).build());
    private final Setting<List<BlockEntityType<?>>> otherStorages = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StorageBlockListSetting.Builder()
                .name("other-storages")
                .description("Other storage blocks to count. Chests are handled by the settings above.")
                .defaultValue()
                .build()
        );
    private final Setting<Boolean> separateLines = this.sgGeneral
        .add(new Builder().name("separate-lines").description("Shows a line per storage type instead of a single total.").defaultValue(true).build());
    private final Setting<Integer> updateDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("update-delay")
                .description("Ticks between rescans of the loaded chunks.")
                .defaultValue(10)
                .min(1)
                .sliderRange(1, 100)
                .build()
        );
    private final Setting<Double> textScale = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .sliderRange(0.1, 3.0)
                .decimalPlaces(1)
                .build()
        );
    private final Setting<SettingColor> labelColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("label-color")
                .description("Color of the labels.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> valueColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("value-color")
                .description("Color of the counts.")
                .defaultValue(new SettingColor(175, 175, 175, 255))
                .build()
        );
    public static final HudElementInfo<DubCounter> INFO = new HudElementInfo<>(
        musheor.MUSHEOR_HUD, "dub-counter", "Counts connected double chests (and optionally other storage) in render distance", DubCounter::new
    );
    private int doubleChestCount;
    private int singleChestCount;
    private final Map<BlockEntityType<?>, Integer> storageCounts = new LinkedHashMap<>();
    private int ticksUntilRescan;

    public DubCounter() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (this.client.world != null) {
            if (--this.ticksUntilRescan <= 0) {
                this.ticksUntilRescan = this.updateDelay.get();
                this.rescan();
            }
        }
    }

    private void rescan() {
        this.doubleChestCount = 0;
        this.singleChestCount = 0;
        this.storageCounts.clear();

        for (BlockEntityType<?> type : this.otherStorages.get()) {
            if (!isChestType(type)) {
                this.storageCounts.put(type, 0);
            }
        }

        for (BlockEntity be : Utils.blockEntities()) {
            if (be instanceof ChestBlockEntity) {
                BlockState state = be.getCachedState();
                if (state.contains(ChestBlock.CHEST_TYPE)) {
                    ChestType typex = state.get(ChestBlock.CHEST_TYPE);
                    if (typex == ChestType.SINGLE) {
                        this.singleChestCount++;
                    } else if (typex == ChestType.LEFT || !this.isPartnerChunkLoaded(be.getPos(), state)) {
                        this.doubleChestCount++;
                    }
                }
            } else {
                this.storageCounts.computeIfPresent(be.getType(), (t, n) -> n + 1);
            }
        }
    }

    private boolean isPartnerChunkLoaded(BlockPos pos, BlockState state) {
        BlockPos partner = pos.offset(ChestBlock.getFacing(state));
        return this.client.world.getChunkManager().isChunkLoaded(partner.getX() >> 4, partner.getZ() >> 4);
    }

    private static boolean isChestType(BlockEntityType<?> type) {
        return type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST;
    }

    @Override
    public void render(HudRenderer renderer) {
        List<String[]> rows = new ArrayList<>();
        int total = 0;
        if (this.doubleChests.get()) {
            rows.add(new String[]{"Dubs: ", Integer.toString(this.doubleChestCount)});
            total += this.doubleChestCount;
        }

        if (this.singleChests.get()) {
            rows.add(new String[]{"Single Chests: ", Integer.toString(this.singleChestCount)});
            total += this.singleChestCount;
        }

        for (Entry<BlockEntityType<?>, Integer> entry : this.storageCounts.entrySet()) {
            rows.add(new String[]{formatTypeName(entry.getKey()) + ": ", Integer.toString(entry.getValue())});
            total += entry.getValue();
        }

        if (!this.separateLines.get() || rows.isEmpty()) {
            String label = rows.size() == 1 ? rows.get(0)[0] : "Storage: ";
            rows.clear();
            rows.add(new String[]{label, Integer.toString(total)});
        }

        double lineHeight = renderer.textHeight(true, this.textScale.get());
        double width = 0.0;
        double cy = this.y;

        for (String[] row : rows) {
            double labelW = renderer.textWidth(row[0], true, this.textScale.get());
            renderer.text(row[0], this.x, cy, this.labelColor.get(), true, this.textScale.get());
            renderer.text(row[1], this.x + labelW, cy, this.valueColor.get(), true, this.textScale.get());
            width = Math.max(width, labelW + renderer.textWidth(row[1], true, this.textScale.get()));
            cy += lineHeight;
        }

        this.setSize(width, cy - this.y);
    }

    private static String formatTypeName(BlockEntityType<?> type) {
        Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(type);
        if (id == null) {
            return "Unknown";
        } else {
            StringBuilder sb = new StringBuilder();

            for (String word : id.getPath().split("_")) {
                if (!word.isEmpty()) {
                    if (!sb.isEmpty()) {
                        sb.append(' ');
                    }

                    sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }

            return sb.toString();
        }
    }
}
