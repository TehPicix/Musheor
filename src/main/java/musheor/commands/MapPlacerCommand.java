package musheor.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.modules.automation.MapPlacer;
import musheor.modules.features.CoordHider;
import musheor.utils.mapart.MapArtGrid;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;

public class MapPlacerCommand extends Command {
    private static final MapArtGrid canvas = MapArtGrid.getInstance();
    private static MapArtGrid.Corner pendingCorner;

    public MapPlacerCommand() {
        super("mp", "Sets up the map-placer canvas for hanging a mapart.", "mapplacer");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(this::executeStatus);
        builder.then(
            ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal("origin")
                            .then(literal("topleft").executes(ctx -> this.executeOrigin(MapArtGrid.Corner.TopLeft))))
                        .then(literal("tl").executes(ctx -> this.executeOrigin(MapArtGrid.Corner.TopLeft))))
                    .then(literal("bottomleft").executes(ctx -> this.executeOrigin(MapArtGrid.Corner.BottomLeft))))
                .then(literal("bl").executes(ctx -> this.executeOrigin(MapArtGrid.Corner.BottomLeft)))
        );
        builder.then(
            literal("size")
                .then(
                    argument("width", IntegerArgumentType.integer(0, 128))
                        .then(argument("height", IntegerArgumentType.integer(0, 128)).executes(this::executeSize))
                )
        );
        builder.then(literal("base").then(argument("base", IntegerArgumentType.integer(0, 1)).executes(this::executeBase)));
        builder.then(literal("scan").executes(this::executeScan));
        builder.then(literal("list").executes(this::executeList));
        builder.then(literal("place").executes(this::executePlace));
        builder.then(literal("pause").executes(this::executePause));
        builder.then(literal("resume").executes(this::executeResume));
        builder.then(literal("stop").executes(this::executeStop));
        builder.then(literal("clear").executes(this::executeClear));
        builder.then(literal("name").then(argument("name", StringArgumentType.greedyString()).executes(this::executeName)));
    }

    private int executeStatus(CommandContext<CommandSource> context) {
        if (!canvas.isAnchored()) {
            this.info("No canvas set. Look at the mapart's §b0,0§r frame and run:", new Object[0]);
            this.info("  §b1. .mp origin topleft§r §7(or bottomleft) — anchors the grid and shows the preview", new Object[0]);
            this.info("  §b2. .mp size <w> <h>§r §7— only needed for maps named with a single index", new Object[0]);
            this.info("  §b3. .mp place§r §7— starts placing; you walk along the wall yourself", new Object[0]);
            return 1;
        } else {
            MapPlacer module = MapPlacer.getInstance();
            this.info(
                "Canvas: §b%s§r, origin §b%s§r, facing §b%s§r, base §b%d§r",
                new Object[]{
                    canvas.getWidth() > 0 && canvas.getHeight() > 0 ? canvas.getWidth() + "x" + canvas.getHeight() : "auto-size",
                    canvas.getOrigin(),
                    canvas.getFacing(),
                    canvas.getIndexBase()
                }
            );
            this.info("Status: %s%s", new Object[]{module.getStatusText(), module.isActive() ? "" : " §7[module off]"});
            this.info(
                "Maps in inventory: §b%d§r%s",
                new Object[]{
                    MapPlacer.countMapsInInventory(),
                    canvas.getUnmatchedMapNames().isEmpty() ? "" : String.format(" §7(%d unplaceable — see .mp list)", canvas.getUnmatchedMapNames().size())
                }
            );
            this.info("§7.mp scan | list | place | pause | resume | stop | clear | name <text>", new Object[0]);
            return 1;
        }
    }

    private int executeOrigin(MapArtGrid.Corner corner) {
        if (mc.player == null) {
            this.error("You must be in a world.", new Object[0]);
            return 1;
        } else if (mc.targetedEntity instanceof ItemFrameEntity frame) {
            applyOrigin(frame, corner);
            return 1;
        } else {
            pendingCorner = corner;
            if (!MeteorClient.EVENT_BUS.isListening(MapPlacerCommand.PickListener.class)) {
                MeteorClient.EVENT_BUS.subscribe(MapPlacerCommand.PickListener.INSTANCE);
            }

            this.info("Right-click the §b%s§r frame of the mapart to make it §b0,0§r. §7(.mp clear cancels)", new Object[]{corner});
            return 1;
        }
    }

    private static void applyOrigin(ItemFrameEntity frame, MapArtGrid.Corner corner) {
        canvas.setAnchor(frame, corner, mc.player.getYaw());
        int found = canvas.scanFrames();
        canvas.matchInventoryMaps();
        ChatUtils.info("Origin set: §b%s§r at §b%s§r, facing §b%s§r.", corner, formatPos(canvas.getAnchorPos()), canvas.getFacing());
        ChatUtils.info("Columns run §b%s§r, rows run §b%s§r. Scanned §b%d§r frames.", canvas.getColumnDirection(), canvas.getRowDirection(), found);
        if (canvas.getFacing().getAxis().isVertical()) {
            ChatUtils.warning(
                "Flat mapart — the axes were taken from the way you are facing right now. Re-run this while standing at the mapart's corner if they come out wrong."
            );
        }

        if (found <= 1) {
            ChatUtils.warning("Only the origin frame is loaded. Walk along the wall to pick the rest up (auto-scan does it for you, or run §b.mp scan§r).");
        }

        MapPlacer.setPaused(true);
        MapPlacer module = MapPlacer.getInstance();
        if (!module.isActive()) {
            module.toggle();
        }

        ChatUtils.info("Preview on, §epaused§r — check the labels on the frames, then run §b.mp place§r.");
    }

    private static String formatPos(BlockPos pos) {
        CoordHider hider = Modules.get().get(CoordHider.class);
        return hider != null && hider.isActive() ? "? ? ?" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void cancelPick() {
        pendingCorner = null;
        if (MeteorClient.EVENT_BUS.isListening(MapPlacerCommand.PickListener.class)) {
            MeteorClient.EVENT_BUS.unsubscribe(MapPlacerCommand.PickListener.INSTANCE);
        }
    }

    private int executeSize(CommandContext<CommandSource> context) {
        int width = IntegerArgumentType.getInteger(context, "width");
        int height = IntegerArgumentType.getInteger(context, "height");
        canvas.setSize(width, height);
        canvas.scanFrames();
        canvas.matchInventoryMaps();
        if (width == 0 && height == 0) {
            this.info("Size cleared — the canvas takes every aligned frame it can see.", new Object[0]);
            this.warning("Maps named with a single index cannot be placed without a width to wrap rows at.", new Object[0]);
        } else {
            this.info("Size set to §b%d§r wide x §b%d§r tall (§b%d§r frames scanned so far).", new Object[]{width, height, canvas.getCellCount()});
        }

        return 1;
    }

    private int executeBase(CommandContext<CommandSource> context) {
        int base = IntegerArgumentType.getInteger(context, "base");
        canvas.setIndexBase(base);
        canvas.matchInventoryMaps();
        this.info("Coordinates in map names now count from §b%d§r. §7(\"Clown 0 4\" is base 0, \"Clown 1 5\" base 1)", new Object[]{base});
        return 1;
    }

    private int executeScan(CommandContext<CommandSource> context) {
        if (!this.requireCanvas()) {
            return 1;
        } else {
            int before = canvas.getCellCount();
            int found = canvas.scanFrames();
            canvas.matchInventoryMaps();
            this.info("Scanned §b%d§r frames (§b+%d§r new), §b%d§r already filled.", new Object[]{found, found - before, canvas.countFilled()});
            return 1;
        }
    }

    private int executeList(CommandContext<CommandSource> context) {
        if (!this.requireCanvas()) {
            return 1;
        } else {
            canvas.refreshFilled();
            canvas.matchInventoryMaps();
            if (canvas.getCellCount() == 0) {
                this.warning("No frames scanned yet. Walk to the mapart and run §b.mp scan§r.", new Object[0]);
                return 1;
            } else {
                int width = canvas.getWidth();
                int height = canvas.getHeight();

                for (MapArtGrid.Cell cell : canvas.getCells()) {
                    width = Math.max(width, cell.col + 1);
                    height = Math.max(height, cell.row + 1);
                }

                this.info("§a#§r placed  §b#§r ready  §c#§r map missing  §8.§r no frame", new Object[0]);

                for (int row = 0; row < height; row++) {
                    StringBuilder line = new StringBuilder();

                    for (int col = 0; col < width; col++) {
                        MapArtGrid.Cell cell = this.findCell(col, row);
                        if (cell == null) {
                            line.append("§8.");
                        } else if (cell.isFilled()) {
                            line.append("§a#");
                        } else if (cell.getMapSlot() >= 0) {
                            line.append("§b#");
                        } else {
                            line.append("§c#");
                        }
                    }

                    this.info("§7%2d§r %s", new Object[]{row + canvas.getIndexBase(), line});
                }

                this.info(
                    "§b%d§r/§b%d§r frames filled, §b%d§r maps in inventory.",
                    new Object[]{canvas.countFilled(), canvas.getCellCount(), MapPlacer.countMapsInInventory()}
                );
                List<String> unmatched = canvas.getUnmatchedMapNames();
                if (!unmatched.isEmpty()) {
                    this.warning("§b%d§r map(s) do not land on this canvas:", new Object[]{unmatched.size()});

                    for (String name : unmatched.stream().distinct().limit(10L).toList()) {
                        this.info("  §7%s §8-> %s", new Object[]{name, canvas.describeMapName(name)});
                    }

                    if (unmatched.size() > 10) {
                        this.info("  §7...and %d more", new Object[]{unmatched.size() - 10});
                    }
                }

                return 1;
            }
        }
    }

    private MapArtGrid.Cell findCell(int col, int row) {
        for (MapArtGrid.Cell cell : canvas.getCells()) {
            if (cell.col == col && cell.row == row) {
                return cell;
            }
        }

        return null;
    }

    private int executePlace(CommandContext<CommandSource> context) {
        if (!this.requireCanvas()) {
            return 1;
        } else {
            MapPlacer.setPaused(false);
            MapPlacer module = MapPlacer.getInstance();
            if (!module.isActive()) {
                module.toggle();
            } else {
                this.info("Already placing — %s", new Object[]{module.getStatusText()});
            }

            return 1;
        }
    }

    private int executePause(CommandContext<CommandSource> context) {
        MapPlacer.setPaused(true);
        this.info("Paused. §7The preview stays up — .mp resume carries on where it left off.", new Object[0]);
        return 1;
    }

    private int executeResume(CommandContext<CommandSource> context) {
        if (!this.requireCanvas()) {
            return 1;
        } else {
            MapPlacer.setPaused(false);
            MapPlacer module = MapPlacer.getInstance();
            if (!module.isActive()) {
                module.toggle();
            }

            this.info("Resumed — %s", new Object[]{module.getStatusText()});
            return 1;
        }
    }

    private int executeStop(CommandContext<CommandSource> context) {
        MapPlacer module = MapPlacer.getInstance();
        if (module.isActive()) {
            module.toggle();
        }

        this.info("Stopped — preview off. §7The canvas is kept, so .mp place picks it straight back up.", new Object[0]);
        return 1;
    }

    private int executeClear(CommandContext<CommandSource> context) {
        cancelPick();
        MapPlacer module = MapPlacer.getInstance();
        if (module.isActive()) {
            module.toggle();
        }

        canvas.clear();
        MapPlacer.setPaused(false);
        this.info("Canvas cleared.", new Object[0]);
        return 1;
    }

    private int executeName(CommandContext<CommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        this.info("§7%s §8-> §r%s", new Object[]{name, canvas.describeMapName(name)});
        return 1;
    }

    private boolean requireCanvas() {
        if (canvas.isAnchored()) {
            return true;
        } else {
            this.error("No canvas — look at the mapart's corner frame and run §b.mp origin topleft§r.", new Object[0]);
            return false;
        }
    }

    private static class PickListener {
        private static final MapPlacerCommand.PickListener INSTANCE = new MapPlacerCommand.PickListener();

        @EventHandler(
            priority = 200
        )
        private void onInteractEntity(InteractEntityEvent event) {
            if (MapPlacerCommand.pendingCorner != null) {
                if (event.entity instanceof ItemFrameEntity frame) {
                    MapArtGrid.Corner var4 = MapPlacerCommand.pendingCorner;
                    MapPlacerCommand.cancelPick();
                    MapPlacerCommand.applyOrigin(frame, var4);
                    event.cancel();
                }
            }
        }
    }
}
