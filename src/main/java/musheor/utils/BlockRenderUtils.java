package musheor.utils;

import it.unimi.dsi.fastutil.Pair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BlockRenderUtils {
    public static void renderBlocks(Render3DEvent event, List<Pair<BlockPos, Block>> blocks) {
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().world != null) {
            if (blocks != null && !blocks.isEmpty()) {
                boolean renderLines = MusheorSystem.Manager.renderLines.get()
                    && (MusheorSystem.Manager.renderShape.get() == ShapeMode.Lines || MusheorSystem.Manager.renderShape.get() == ShapeMode.Both);
                boolean renderSides = MusheorSystem.Manager.renderSides.get()
                    && (MusheorSystem.Manager.renderShape.get() == ShapeMode.Sides || MusheorSystem.Manager.renderShape.get() == ShapeMode.Both);
                if (renderLines || renderSides) {
                    if (MusheorSystem.Manager.renderType.get() == MusheorSystem.RenderType.Mapped) {
                        renderMapColored(event, blocks, renderLines, renderSides);
                    } else {
                        renderUniformColor(event, blocks, renderLines, renderSides);
                    }
                }
            }
        }
    }

    public static void renderPositions(Render3DEvent event, List<BlockPos> positions) {
        renderPositions(event, positions, null);
    }

    public static void renderPositions(Render3DEvent event, List<BlockPos> positions, Block defaultBlock) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null) {
            if (positions != null && !positions.isEmpty()) {
                List<Pair<BlockPos, Block>> blocks;
                if (defaultBlock != null) {
                    blocks = positions.stream().map(pos -> Pair.of(pos, defaultBlock)).collect(Collectors.toList());
                } else {
                    blocks = positions.stream().map(pos -> {
                        Block block = mc.world.getBlockState(pos).getBlock();
                        return Pair.of(pos, block);
                    }).collect(Collectors.toList());
                }

                renderBlocks(event, blocks);
            }
        }
    }

    public static void renderPositions(Render3DEvent event, List<BlockPos> positions, Color lineColor, Color sideColor, ShapeMode shapeMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null) {
            if (positions != null && !positions.isEmpty()) {
                boolean renderLines = shapeMode == ShapeMode.Lines || shapeMode == ShapeMode.Both;
                boolean renderSides = shapeMode == ShapeMode.Sides || shapeMode == ShapeMode.Both;
                if (renderLines || renderSides) {
                    Set<BlockPos> blockSet = new HashSet<>(positions);
                    renderBlockSet(event, blockSet, renderLines, renderSides, lineColor, sideColor);
                }
            }
        }
    }

    public static void renderPosition(Render3DEvent event, BlockPos position) {
        renderPositions(event, List.of(position));
    }

    public static void renderPosition(Render3DEvent event, BlockPos position, Block defaultBlock) {
        renderPositions(event, List.of(position), defaultBlock);
    }

    public static void renderPosition(Render3DEvent event, BlockPos position, Color lineColor, Color sideColor, ShapeMode shapeMode) {
        renderPositions(event, List.of(position), lineColor, sideColor, shapeMode);
    }

    private static void renderMapColored(Render3DEvent event, List<Pair<BlockPos, Block>> blocks, boolean renderLines, boolean renderSides) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Map<Integer, Set<BlockPos>> colorGroups = new HashMap<>();

        for (Pair<BlockPos, Block> pair : blocks) {
            BlockPos pos = (BlockPos)pair.first();
            Block block = (Block)pair.second();
            BlockState state = block.getDefaultState();
            int mapColorId = state.getMapColor(mc.world, pos).color;
            colorGroups.computeIfAbsent(mapColorId, k -> new HashSet<>()).add(pos);
        }

        int lineAlpha = MusheorSystem.Manager.renderLineAlpha.get();
        int sideAlpha = MusheorSystem.Manager.renderSideAlpha.get();

        for (Entry<Integer, Set<BlockPos>> entry : colorGroups.entrySet()) {
            int rgbColor = entry.getKey();
            Set<BlockPos> blockSet = entry.getValue();
            int r = rgbColor >> 16 & 0xFF;
            int g = rgbColor >> 8 & 0xFF;
            int b = rgbColor & 0xFF;
            Color lineColor = new Color(r, g, b, lineAlpha);
            Color sideColor = new Color(r, g, b, sideAlpha);
            renderBlockSet(event, blockSet, renderLines, renderSides, lineColor, sideColor);
        }
    }

    private static void renderUniformColor(Render3DEvent event, List<Pair<BlockPos, Block>> blocks, boolean renderLines, boolean renderSides) {
        Set<BlockPos> blockSet = blocks.stream().<BlockPos>map(Pair::first).collect(Collectors.toSet());
        renderBlockSet(event, blockSet, renderLines, renderSides, MusheorSystem.Manager.renderLineColor.get(), MusheorSystem.Manager.renderSideColor.get());
    }

    private static void renderBlockSet(Render3DEvent event, Set<BlockPos> blockSet, boolean renderLines, boolean renderSides, Color lineColor, Color sideColor) {
        MinecraftClient mc = MinecraftClient.getInstance();

        for (BlockPos pos : blockSet) {
            for (Direction side : Direction.values()) {
                BlockPos neighbor = pos.offset(side);
                if (!blockSet.contains(neighbor)) {
                    if (renderLines) {
                        renderFaceEdges(event, pos, side, blockSet, lineColor);
                    }

                    if (renderSides) {
                        renderFace(event, pos, side, sideColor);
                    }
                }
            }
        }
    }

    public static void renderFaceEdges(Render3DEvent event, BlockPos pos, Direction side, Set<BlockPos> blockSet, Color lineColor) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        switch (side) {
            case UP:
            case DOWN:
                double fy = side == Direction.UP ? y + 1.0 : y;
                if (shouldDrawEdge(pos, side, Direction.NORTH, blockSet)) {
                    event.renderer.line(x, fy, z, x + 1.0, fy, z, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.SOUTH, blockSet)) {
                    event.renderer.line(x, fy, z + 1.0, x + 1.0, fy, z + 1.0, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.WEST, blockSet)) {
                    event.renderer.line(x, fy, z, x, fy, z + 1.0, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.EAST, blockSet)) {
                    event.renderer.line(x + 1.0, fy, z, x + 1.0, fy, z + 1.0, lineColor);
                }
                break;
            case NORTH:
            case SOUTH:
                double fz = side == Direction.SOUTH ? z + 1.0 : z;
                if (shouldDrawEdge(pos, side, Direction.UP, blockSet)) {
                    event.renderer.line(x, y + 1.0, fz, x + 1.0, y + 1.0, fz, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.DOWN, blockSet)) {
                    event.renderer.line(x, y, fz, x + 1.0, y, fz, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.WEST, blockSet)) {
                    event.renderer.line(x, y, fz, x, y + 1.0, fz, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.EAST, blockSet)) {
                    event.renderer.line(x + 1.0, y, fz, x + 1.0, y + 1.0, fz, lineColor);
                }
                break;
            case WEST:
            case EAST:
                double fx = side == Direction.EAST ? x + 1.0 : x;
                if (shouldDrawEdge(pos, side, Direction.UP, blockSet)) {
                    event.renderer.line(fx, y + 1.0, z, fx, y + 1.0, z + 1.0, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.DOWN, blockSet)) {
                    event.renderer.line(fx, y, z, fx, y, z + 1.0, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.NORTH, blockSet)) {
                    event.renderer.line(fx, y, z, fx, y + 1.0, z, lineColor);
                }

                if (shouldDrawEdge(pos, side, Direction.SOUTH, blockSet)) {
                    event.renderer.line(fx, y, z + 1.0, fx, y + 1.0, z + 1.0, lineColor);
                }
        }
    }

    public static void renderFace(Render3DEvent event, BlockPos pos, Direction side, Color sideColor) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        switch (side) {
            case UP:
                event.renderer.quad(x, y + 1.0, z, x + 1.0, y + 1.0, z, x + 1.0, y + 1.0, z + 1.0, x, y + 1.0, z + 1.0, sideColor);
                break;
            case DOWN:
                event.renderer.quad(x, y, z, x, y, z + 1.0, x + 1.0, y, z + 1.0, x + 1.0, y, z, sideColor);
                break;
            case NORTH:
                event.renderer.quad(x, y, z, x + 1.0, y, z, x + 1.0, y + 1.0, z, x, y + 1.0, z, sideColor);
                break;
            case SOUTH:
                event.renderer.quad(x, y, z + 1.0, x, y + 1.0, z + 1.0, x + 1.0, y + 1.0, z + 1.0, x + 1.0, y, z + 1.0, sideColor);
                break;
            case WEST:
                event.renderer.quad(x, y, z, x, y + 1.0, z, x, y + 1.0, z + 1.0, x, y, z + 1.0, sideColor);
                break;
            case EAST:
                event.renderer.quad(x + 1.0, y, z, x + 1.0, y, z + 1.0, x + 1.0, y + 1.0, z + 1.0, x + 1.0, y + 1.0, z, sideColor);
        }
    }

    public static boolean shouldDrawEdge(BlockPos pos, Direction face, Direction edgeDir, Set<BlockPos> blockSet) {
        return !blockSet.contains(pos.offset(edgeDir)) || blockSet.contains(pos.offset(face).offset(edgeDir));
    }
}
