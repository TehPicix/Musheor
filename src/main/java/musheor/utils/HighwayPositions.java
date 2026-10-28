package musheor.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import musheor.modules.automation.HighwayBuilder;
import musheor.utils.highway.HighwayDetector;
import musheor.utils.highway.HighwayState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

public class HighwayPositions {
    @NotNull
    public static BlockPos[] getCardinalFloor(int f, int b, boolean leftRail, boolean rightRail) {
        HighwayState state = HighwayState.getInstance();
        boolean placeRails = HighwayBuilder.shouldPlaceRails();
        int width = HighwayBuilder.getPavementWidth();
        HighwayUtils.HighwayDirection direction = HighwayBuilder.getDirection();
        int playerX = MeteorClient.mc.player.getBlockX();
        int playerY = state.getAnchorY();
        int playerZ = MeteorClient.mc.player.getBlockZ();
        int dx;
        int dz;
        int start;
        int end;
        int step;
        int sideSign;
        switch (direction) {
            case NORTH:
                dx = 0;
                dz = 1;
                start = b;
                end = -f;
                step = -1;
                sideSign = 1;
                playerX = state.getStartX();
                break;
            case SOUTH:
                dx = 0;
                dz = 1;
                start = -b;
                end = f;
                step = 1;
                sideSign = -1;
                playerX = state.getStartX();
                break;
            case EAST:
                dx = 1;
                dz = 0;
                start = -b;
                end = f;
                step = 1;
                sideSign = 1;
                playerZ = state.getStartZ();
                break;
            case WEST:
                dx = 1;
                dz = 0;
                start = b;
                end = -f;
                step = -1;
                sideSign = -1;
                playerZ = state.getStartZ();
                break;
            default:
                return new BlockPos[0];
        }

        int leftBound;
        int rightBound;
        if (width % 2 == 0) {
            leftBound = width / 2;
            rightBound = leftBound - 1;
        } else {
            leftBound = rightBound = (width - 1) / 2;
        }

        List<BlockPos> positions = new ArrayList<>();

        for (int i = start; i != end + step; i += step) {
            for (int j = -leftBound; j <= rightBound; j++) {
                int x = playerX + dx * i + dz * j * sideSign;
                int z = playerZ + dz * i + dx * j * sideSign;
                positions.add(new BlockPos(x, playerY - 1, z));
            }

            if (placeRails) {
                int leftRailX = playerX + dx * i + dz * (-leftBound - 1) * sideSign;
                int leftRailZ = playerZ + dz * i + dx * (-leftBound - 1) * sideSign;
                int rightRailX = playerX + dx * i + dz * (rightBound + 1) * sideSign;
                int rightRailZ = playerZ + dz * i + dx * (rightBound + 1) * sideSign;
                if (leftRail) {
                    positions.add(new BlockPos(leftRailX, playerY, leftRailZ));
                }

                if (rightRail) {
                    positions.add(new BlockPos(rightRailX, playerY, rightRailZ));
                }
            }
        }

        positions.sort(Comparator.comparingDouble(p -> MeteorClient.mc.player.squaredDistanceTo(Vec3d.of(p))));
        return positions.toArray(new BlockPos[0]);
    }

    public static BlockPos[] getCardinalClearArea(int f, int b) {
        HighwayState state = HighwayState.getInstance();
        int width = HighwayBuilder.getPavementWidth();
        int height = 2;
        if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Tunnel) {
            height = 3;
        }

        HighwayUtils.HighwayDirection direction = HighwayBuilder.getDirection();
        int playerX = MeteorClient.mc.player.getBlockX();
        int playerY = state.getAnchorY();
        int playerZ = MeteorClient.mc.player.getBlockZ();
        int dx;
        int dz;
        int start;
        int end;
        int step;
        int sideSign;
        switch (direction) {
            case NORTH:
                dx = 0;
                dz = 1;
                start = b;
                end = -f;
                step = -1;
                sideSign = 1;
                playerX = state.getStartX();
                break;
            case SOUTH:
                dx = 0;
                dz = 1;
                start = -b;
                end = f;
                step = 1;
                sideSign = -1;
                playerX = state.getStartX();
                break;
            case EAST:
                dx = 1;
                dz = 0;
                start = -b;
                end = f;
                step = 1;
                sideSign = 1;
                playerZ = state.getStartZ();
                break;
            case WEST:
                dx = 1;
                dz = 0;
                start = b;
                end = -f;
                step = -1;
                sideSign = -1;
                playerZ = state.getStartZ();
                break;
            default:
                return new BlockPos[0];
        }

        int leftBound;
        int rightBound;
        if (width % 2 == 0) {
            leftBound = width / 2;
            rightBound = leftBound - 1;
        } else {
            leftBound = rightBound = (width - 1) / 2;
        }

        List<BlockPos> positions = new ArrayList<>();

        for (int i = start; i != end + step; i += step) {
            for (int j = 0; j <= height; j++) {
                for (int k = -leftBound; k <= rightBound; k++) {
                    int x = playerX + dx * i + dz * k * sideSign;
                    int z = playerZ + dz * i + dx * k * sideSign;
                    positions.add(new BlockPos(x, playerY + j, z));
                }

                if (HighwayBuilder.shouldMineAboveRails() && j > 0) {
                    int leftRailX = playerX + dx * i + dz * (-leftBound - 1) * sideSign;
                    int leftRailZ = playerZ + dz * i + dx * (-leftBound - 1) * sideSign;
                    int rightRailX = playerX + dx * i + dz * (rightBound + 1) * sideSign;
                    int rightRailZ = playerZ + dz * i + dx * (rightBound + 1) * sideSign;
                    positions.add(new BlockPos(leftRailX, playerY + j, leftRailZ));
                    positions.add(new BlockPos(rightRailX, playerY + j, rightRailZ));
                }
            }
        }

        positions.sort(Comparator.comparingDouble(p -> MeteorClient.mc.player.squaredDistanceTo(Vec3d.of(p))));
        return positions.toArray(new BlockPos[0]);
    }

    public static BlockPos[] getCardinalCeiling(int f, int b, boolean leftRail, boolean rightRail) {
        HighwayState state = HighwayState.getInstance();
        boolean placeRails = HighwayBuilder.shouldPlaceRails();
        int width = HighwayBuilder.getPavementWidth();

        assert MeteorClient.mc.player != null;

        int playerX = MeteorClient.mc.player.getBlockX();
        int playerY = MeteorClient.mc.player.getBlockY();
        int playerZ = MeteorClient.mc.player.getBlockZ();
        int ceilingY = playerY + 3;
        int leftBound;
        int rightBound;
        if (width % 2 == 0) {
            leftBound = width / 2;
            rightBound = leftBound - 1;
        } else {
            leftBound = rightBound = (width - 1) / 2;
        }

        HighwayUtils.HighwayDirection direction = HighwayBuilder.getDirection();
        int dx;
        int dz;
        int start;
        int end;
        int step;
        int sideSign;
        switch (direction) {
            case NORTH:
                dx = 0;
                dz = 1;
                start = b;
                end = -f;
                step = -1;
                sideSign = 1;
                playerX = state.getStartX();
                break;
            case SOUTH:
                dx = 0;
                dz = 1;
                start = -b;
                end = f;
                step = 1;
                sideSign = -1;
                playerX = state.getStartX();
                break;
            case EAST:
                dx = 1;
                dz = 0;
                start = -b;
                end = f;
                step = 1;
                sideSign = 1;
                playerZ = state.getStartZ();
                break;
            case WEST:
                dx = 1;
                dz = 0;
                start = b;
                end = -f;
                step = -1;
                sideSign = -1;
                playerZ = state.getStartZ();
                break;
            default:
                return new BlockPos[0];
        }

        List<BlockPos> positions = new ArrayList<>();

        for (int i = start; i != end + step; i += step) {
            for (int j = -leftBound; j <= rightBound; j++) {
                int x = playerX + dx * i + dz * j * sideSign;
                int z = playerZ + dz * i + dx * j * sideSign;
                positions.add(new BlockPos(x, ceilingY, z));
            }

            if (placeRails) {
                if (leftRail) {
                    int x = playerX + dx * i + dz * (-leftBound - 1) * sideSign;
                    int z = playerZ + dz * i + dx * (-leftBound - 1) * sideSign;
                    positions.add(new BlockPos(x, ceilingY, z));
                }

                if (rightRail) {
                    int x = playerX + dx * i + dz * (rightBound + 1) * sideSign;
                    int z = playerZ + dz * i + dx * (rightBound + 1) * sideSign;
                    positions.add(new BlockPos(x, ceilingY, z));
                }
            }
        }

        positions.sort(Comparator.comparingDouble(p -> MeteorClient.mc.player.squaredDistanceTo(Vec3d.of(p))));
        return positions.toArray(new BlockPos[0]);
    }

    private static void addDiagonalStrip(int vMin, int vMax, int uMin, int uMax, int K, boolean isNESW, int y, List<BlockPos> out) {
        for (int v = vMin; v <= vMax; v++) {
            for (int u = uMin; u <= uMax; u++) {
                if ((u + K + v & 1) == 0) {
                    int x = u + K + v >> 1;
                    int z = isNESW ? u + K - v >> 1 : v - u - K >> 1;
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    private static int[] computeDiagonalParams(HighwayState state, int f, int b) {
        HighwayUtils.HighwayDirection dir = HighwayBuilder.getDirection();
        if (dir != HighwayUtils.HighwayDirection.NORTH_EAST
            && dir != HighwayUtils.HighwayDirection.SOUTH_WEST
            && dir != HighwayUtils.HighwayDirection.NORTH_WEST
            && dir != HighwayUtils.HighwayDirection.SOUTH_EAST) {
            return null;
        } else {
            int px = state.getAnchorX();
            int pz = state.getAnchorZ();
            boolean isNESW = dir == HighwayUtils.HighwayDirection.NORTH_EAST || dir == HighwayUtils.HighwayDirection.SOUTH_WEST;
            HighwayDetector.DetectedHighway detected = state.getDetectedHighway();
            int K = detected != null ? (int)detected.axisValue : (isNESW ? px + pz : px - pz);
            int HW = (HighwayBuilder.getPavementWidth() - 1) / 2;
            int lpx = MeteorClient.mc.player.getBlockX();
            int lpz = MeteorClient.mc.player.getBlockZ();
            int vPlayer = isNESW ? lpx - lpz : lpx + lpz;
            boolean vInc = dir == HighwayUtils.HighwayDirection.NORTH_EAST || dir == HighwayUtils.HighwayDirection.SOUTH_EAST;
            int vMin = vInc ? vPlayer - 2 * b : vPlayer - 2 * f;
            int vMax = vInc ? vPlayer + 2 * f : vPlayer + 2 * b;
            return new int[]{K, HW, vMin, vMax, state.getAnchorY(), isNESW ? 1 : 0};
        }
    }

    public static BlockPos[] getDiagonalFloor(int f, int b, boolean leftRail, boolean rightRail) {
        HighwayState state = HighwayState.getInstance();

        assert MeteorClient.mc.player != null;

        if (state.getAnchorX() == null) {
            state.setAnchorX(MeteorClient.mc.player.getBlockX());
        }

        if (state.getAnchorY() == null) {
            state.setAnchorY(MeteorClient.mc.player.getBlockY());
        }

        if (state.getAnchorZ() == null) {
            state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
        }

        int[] s = computeDiagonalParams(state, f, b);
        if (s == null) {
            return new BlockPos[0];
        } else {
            int K = s[0];
            int HW = s[1];
            int vMin = s[2];
            int vMax = s[3];
            int py = s[4];
            boolean isNESW = s[5] == 1;
            List<BlockPos> positions = new ArrayList<>();
            addDiagonalStrip(vMin, vMax, -HW, HW, K, isNESW, py - 1, positions);
            if (HighwayBuilder.shouldPlaceRails()) {
                int leftU = isNESW ? HW + 1 : -(HW + 1);
                int rightU = isNESW ? -(HW + 1) : HW + 1;
                if (leftRail) {
                    addDiagonalStrip(vMin, vMax, leftU, leftU, K, isNESW, py, positions);
                }

                if (rightRail) {
                    addDiagonalStrip(vMin, vMax, rightU, rightU, K, isNESW, py, positions);
                }
            }

            positions.sort(Comparator.comparingDouble(p -> MeteorClient.mc.player.squaredDistanceTo(Vec3d.of(p))));
            return positions.toArray(new BlockPos[0]);
        }
    }

    public static BlockPos[] getDiagonalCeiling(int f, int b, boolean leftRail, boolean rightRail) {
        HighwayState state = HighwayState.getInstance();

        assert MeteorClient.mc.player != null;

        if (state.getAnchorX() == null) {
            state.setAnchorX(MeteorClient.mc.player.getBlockX());
        }

        if (state.getAnchorY() == null) {
            state.setAnchorY(MeteorClient.mc.player.getBlockY());
        }

        if (state.getAnchorZ() == null) {
            state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
        }

        int[] s = computeDiagonalParams(state, f, b);
        if (s == null) {
            return new BlockPos[0];
        } else {
            int K = s[0];
            int HW = s[1];
            int vMin = s[2];
            int vMax = s[3];
            int py = s[4];
            boolean isNESW = s[5] == 1;
            int ceilingY = py + 3;
            List<BlockPos> positions = new ArrayList<>();
            addDiagonalStrip(vMin, vMax, -HW, HW, K, isNESW, ceilingY, positions);
            if (HighwayBuilder.shouldPlaceRails()) {
                int leftU = isNESW ? HW + 1 : -(HW + 1);
                int rightU = isNESW ? -(HW + 1) : HW + 1;
                if (leftRail) {
                    addDiagonalStrip(vMin, vMax, leftU, leftU, K, isNESW, ceilingY, positions);
                }

                if (rightRail) {
                    addDiagonalStrip(vMin, vMax, rightU, rightU, K, isNESW, ceilingY, positions);
                }
            }

            positions.sort(Comparator.comparingDouble(p -> MeteorClient.mc.player.squaredDistanceTo(Vec3d.of(p))));
            return positions.toArray(new BlockPos[0]);
        }
    }

    public static BlockPos[] getDiagonalClearArea(int f, int b) {
        HighwayState state = HighwayState.getInstance();
        int height = 2;
        if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Tunnel) {
            height = 3;
        }

        if (state.getAnchorX() == null) {
            state.setAnchorX(MeteorClient.mc.player.getBlockX());
        }

        if (state.getAnchorY() == null) {
            state.setAnchorY(MeteorClient.mc.player.getBlockY());
        }

        if (state.getAnchorZ() == null) {
            state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
        }

        int[] s = computeDiagonalParams(state, f, b);
        if (s == null) {
            return new BlockPos[0];
        } else {
            int K = s[0];
            int HW = s[1];
            int vMin = s[2];
            int vMax = s[3];
            int py = s[4];
            boolean isNESW = s[5] == 1;
            List<BlockPos> positions = new ArrayList<>();

            for (int j = 0; j <= height; j++) {
                addDiagonalStrip(vMin, vMax, -HW, HW, K, isNESW, py + j, positions);
                if (HighwayBuilder.shouldMineAboveRails() && j > 0) {
                    addDiagonalStrip(vMin, vMax, -(HW + 1), -(HW + 1), K, isNESW, py + j, positions);
                    addDiagonalStrip(vMin, vMax, HW + 1, HW + 1, K, isNESW, py + j, positions);
                }
            }

            positions.sort(Comparator.comparingDouble(p -> MeteorClient.mc.player.squaredDistanceTo(Vec3d.of(p))));
            return positions.toArray(new BlockPos[0]);
        }
    }

    public static BlockPos[] getCardinalAreaBehind() {
        HighwayState state = HighwayState.getInstance();
        int width = HighwayBuilder.getPavementWidth();
        int leftBound = 0;
        int rightBound = 0;
        if (HighwayBuilder.getDirection() == null) {
            return new BlockPos[0];
        } else {
            assert MeteorClient.mc.player != null;

            if (state.getAnchorX() == null) {
                state.setAnchorX(MeteorClient.mc.player.getBlockX());
            }

            if (state.getAnchorY() == null) {
                state.setAnchorY(MeteorClient.mc.player.getBlockY());
            }

            if (state.getAnchorZ() == null) {
                state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
            }

            if (width % 2 == 0) {
                leftBound = width / 2 + 1;
            }

            rightBound = leftBound - 1;
            if (width % 2 != 0) {
                leftBound = rightBound = (width - 1) / 2 + 1;
            }

            int height = 2;
            int length = 4;

            return switch (HighwayBuilder.getDirection()) {
                case NORTH -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = length; i > 0; i--) {
                            for (int k = 0; k < height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() - leftBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                                positions.add(new BlockPos(state.getAnchorX() + rightBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    for (int i = length; i > 0; i--) {
                        for (int j = -leftBound + 1; j < rightBound; j++) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + j, state.getAnchorY() + k, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case SOUTH -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = -length; i < 0; i++) {
                            for (int k = 0; k < height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + leftBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                                positions.add(new BlockPos(state.getAnchorX() - rightBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    for (int i = -length; i < 0; i++) {
                        for (int j = leftBound - 1; j > -rightBound; j--) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + j, state.getAnchorY() + k, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case EAST -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = -length; i < 0; i++) {
                            for (int k = 0; k < height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() - leftBound));
                                positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + rightBound));
                            }
                        }
                    }

                    for (int i = -length; i < 0; i++) {
                        for (int j = -leftBound + 1; j < rightBound; j++) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k, state.getAnchorZ() + j));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case WEST -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = length; i > 0; i--) {
                            for (int k = 0; k < height; k++) {
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, state.getAnchorZ() + leftBound));
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, state.getAnchorZ() - rightBound));
                            }
                        }
                    }

                    for (int i = length; i > 0; i--) {
                        for (int j = leftBound - 1; j > -rightBound; j--) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k, state.getAnchorZ() + j));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                default -> new BlockPos[0];
            };
        }
    }

    public static BlockPos[] getCardinalFloorBehindAnchor() {
        HighwayState state = HighwayState.getInstance();
        int width = HighwayBuilder.getPavementWidth();
        int leftBound = 0;
        int rightBound = 0;
        if (HighwayBuilder.getDirection() == null) {
            return new BlockPos[0];
        } else {
            assert MeteorClient.mc.player != null;

            if (state.getAnchorX() == null) {
                state.setAnchorX(MeteorClient.mc.player.getBlockX());
            }

            if (state.getAnchorY() == null) {
                state.setAnchorY(MeteorClient.mc.player.getBlockY());
            }

            if (state.getAnchorY() == null) {
                state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
            }

            if (width % 2 == 0) {
                leftBound = width / 2;
            }

            rightBound = leftBound - 1;
            if (width % 2 != 0) {
                leftBound = rightBound = (width - 1) / 2;
            }
            return switch (HighwayBuilder.getDirection()) {
                case NORTH -> {
                    List<BlockPos> positions = new ArrayList<>();

                    for (int i = 4; i <= 7; i++) {
                        int z = state.getAnchorZ() + i;

                        for (int j = -leftBound; j < rightBound; j++) {
                            positions.add(new BlockPos(state.getAnchorX() + j, state.getAnchorY() - 1, z));
                        }

                        if (HighwayBuilder.shouldPlaceRails()) {
                            positions.add(new BlockPos(state.getAnchorX() - leftBound - 1, state.getAnchorY(), z));
                            positions.add(new BlockPos(state.getAnchorX() + rightBound + 1, state.getAnchorY(), z));
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case SOUTH -> {
                    List<BlockPos> positions = new ArrayList<>();

                    for (int i = 4; i <= 7; i++) {
                        int z = state.getAnchorZ() - i;

                        for (int j = leftBound; j > -rightBound; j--) {
                            positions.add(new BlockPos(state.getAnchorX() + j, state.getAnchorY() - 1, z));
                        }

                        if (HighwayBuilder.shouldPlaceRails()) {
                            positions.add(new BlockPos(state.getAnchorX() + leftBound + 1, state.getAnchorY(), z));
                            positions.add(new BlockPos(state.getAnchorX() - rightBound - 1, state.getAnchorY(), z));
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case EAST -> {
                    List<BlockPos> positions = new ArrayList<>();

                    for (int i = 4; i <= 7; i++) {
                        int x = state.getAnchorX() - i;

                        for (int j = -leftBound; j < rightBound; j++) {
                            positions.add(new BlockPos(x, state.getAnchorY() - 1, state.getAnchorZ() + j));
                        }

                        if (HighwayBuilder.shouldPlaceRails()) {
                            positions.add(new BlockPos(x, state.getAnchorY(), state.getAnchorZ() - leftBound - 1));
                            positions.add(new BlockPos(x, state.getAnchorY(), state.getAnchorZ() + rightBound + 1));
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case WEST -> {
                    List<BlockPos> positions = new ArrayList<>();

                    for (int i = 4; i <= 7; i++) {
                        int x = state.getAnchorX() + i;

                        for (int j = leftBound; j > -rightBound; j--) {
                            positions.add(new BlockPos(x, state.getAnchorY() - 1, state.getAnchorZ() + j));
                        }

                        if (HighwayBuilder.shouldPlaceRails()) {
                            positions.add(new BlockPos(x, state.getAnchorY(), state.getAnchorZ() + leftBound + 1));
                            positions.add(new BlockPos(x, state.getAnchorY(), state.getAnchorZ() - rightBound - 1));
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                default -> new BlockPos[0];
            };
        }
    }

    public static BlockPos[] getCardinalLavaScanArea() {
        HighwayState state = HighwayState.getInstance();
        int width = HighwayBuilder.getPavementWidth();
        int leftBound = 0;
        int rightBound = 0;
        if (HighwayBuilder.getDirection() == null) {
            return new BlockPos[0];
        } else {
            assert MeteorClient.mc.player != null;

            if (state.getAnchorX() == null) {
                state.setAnchorX(MeteorClient.mc.player.getBlockX());
            }

            if (state.getAnchorY() == null) {
                state.setAnchorY(MeteorClient.mc.player.getBlockY());
            }

            if (state.getAnchorZ() == null) {
                state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
            }

            if (width % 2 == 0) {
                leftBound = width / 2 + 1;
            }

            rightBound = leftBound - 1;
            if (width % 2 != 0) {
                leftBound = rightBound = (width - 1) / 2 + 1;
            }

            int height = 0;
            if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Pave) {
                height = 3;
            }

            if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Tunnel) {
                height = 4;
            }
            return switch (HighwayBuilder.getDirection()) {
                case NORTH -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = 0; i > -10; i--) {
                            for (int k = 0; k < height; k++) {
                                positions.add(
                                    new BlockPos(state.getAnchorX() - leftBound - 1, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i)
                                );
                                positions.add(
                                    new BlockPos(state.getAnchorX() + rightBound + 1, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i)
                                );
                                positions.add(new BlockPos(state.getAnchorX() - leftBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                                positions.add(new BlockPos(state.getAnchorX() + rightBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    for (int i = 0; i >= -10; i--) {
                        for (int j = -leftBound; j < rightBound; j++) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + j, state.getAnchorY() + k, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case SOUTH -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = 10; i >= 0; i--) {
                            for (int k = 0; k < height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + leftBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                                positions.add(new BlockPos(state.getAnchorX() - rightBound, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i));
                                positions.add(
                                    new BlockPos(state.getAnchorX() + leftBound + 1, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i)
                                );
                                positions.add(
                                    new BlockPos(state.getAnchorX() - rightBound - 1, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + i)
                                );
                            }
                        }
                    }

                    for (int i = 10; i >= 0; i--) {
                        for (int j = leftBound; j > -rightBound; j--) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(state.getAnchorX() + j, state.getAnchorY() + k, MeteorClient.mc.player.getBlockZ() + i));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case EAST -> {
                    List<BlockPos> positions = new ArrayList<>();

                    for (int i = 10; i >= 0; i--) {
                        for (int k = 0; k < height; k++) {
                            positions.add(
                                new BlockPos(
                                    MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() - leftBound - 1
                                )
                            );
                            positions.add(
                                new BlockPos(
                                    MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + rightBound + 1
                                )
                            );
                            positions.add(
                                new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() - leftBound)
                            );
                            positions.add(
                                new BlockPos(
                                    MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, MeteorClient.mc.player.getBlockZ() + rightBound
                                )
                            );
                        }
                    }

                    for (int i = 10; i >= 0; i--) {
                        for (int j = -leftBound + 1; j < rightBound; j++) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k, state.getAnchorZ() + j));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                case WEST -> {
                    List<BlockPos> positions = new ArrayList<>();
                    if (HighwayBuilder.shouldMineAboveRails()) {
                        for (int i = -10; i <= 0; i++) {
                            for (int k = 0; k < height; k++) {
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, state.getAnchorZ() + leftBound));
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, state.getAnchorZ() - rightBound));
                                positions.add(
                                    new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, state.getAnchorZ() + leftBound + 1)
                                );
                                positions.add(
                                    new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k + 1, state.getAnchorZ() - rightBound - 1)
                                );
                            }
                        }
                    }

                    for (int i = -10; i <= 0; i++) {
                        for (int j = leftBound; j > -rightBound; j--) {
                            for (int k = 0; k <= height; k++) {
                                positions.add(new BlockPos(MeteorClient.mc.player.getBlockX() + i, state.getAnchorY() + k, state.getAnchorZ() + j));
                            }
                        }
                    }

                    yield positions.toArray(new BlockPos[0]);
                }
                default -> new BlockPos[0];
            };
        }
    }

    public static BlockPos[] getDiagonalLavaScanArea() {
        HighwayState state = HighwayState.getInstance();
        int width = HighwayBuilder.getPavementWidth();
        int leftBound = 0;
        int rightBound = 0;
        int height = 0;
        if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Pave) {
            height = 3;
        }

        if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Tunnel) {
            height = 4;
        }

        if (width % 2 == 0) {
            leftBound = width / 2;
        }

        rightBound = leftBound - 1;
        if (width % 2 != 0) {
            leftBound = rightBound = (width - 1) / 2;
        }

        assert MeteorClient.mc.player != null;

        if (state.getAnchorX() == null) {
            state.setAnchorX(MeteorClient.mc.player.getBlockX());
        }

        if (state.getAnchorY() == null) {
            state.setAnchorY(MeteorClient.mc.player.getBlockY());
        }

        if (state.getAnchorZ() == null) {
            state.setAnchorZ(MeteorClient.mc.player.getBlockZ());
        }

        HighwayUtils.HighwayDirection direction = HighwayBuilder.getDirection();

        return switch (direction) {
            case NORTH_WEST -> {
                List<BlockPos> positions = new ArrayList<>();

                for (int i = -8; i <= -3; i++) {
                    for (int j = 0; j <= height; j++) {
                        for (int left = leftBound; left > 0; left--) {
                            positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + j, state.getAnchorZ() + left + i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + j, state.getAnchorZ() + leftBound + 1 + i));
                            positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + j, state.getAnchorZ() + leftBound + 2 + i));
                        }

                        for (int right = rightBound; right >= 0; right--) {
                            positions.add(new BlockPos(state.getAnchorX() + right + i, state.getAnchorY() + j, state.getAnchorZ() + i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() + rightBound + 1 + i, state.getAnchorY() + j, state.getAnchorZ() + i));
                            positions.add(new BlockPos(state.getAnchorX() + rightBound + 2 + i, state.getAnchorY() + j, state.getAnchorZ() + i));
                        }
                    }
                }

                yield positions.toArray(new BlockPos[0]);
            }
            case NORTH_EAST -> {
                List<BlockPos> positions = new ArrayList<>();

                for (int i = -8; i <= -3; i++) {
                    for (int j = 0; j <= height; j++) {
                        for (int left = leftBound; left > 0; left--) {
                            positions.add(new BlockPos(state.getAnchorX() - i, state.getAnchorY() + j, state.getAnchorZ() + left + i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() - i, state.getAnchorY() + j, state.getAnchorZ() + leftBound + 1 + i));
                            positions.add(new BlockPos(state.getAnchorX() - i, state.getAnchorY() + j, state.getAnchorZ() + leftBound + 2 + i));
                        }

                        for (int right = rightBound; right >= 0; right--) {
                            positions.add(new BlockPos(state.getAnchorX() - right - i, state.getAnchorY() + j, state.getAnchorZ() + i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() - rightBound - 1 - i, state.getAnchorY() + j, state.getAnchorZ() + i));
                            positions.add(new BlockPos(state.getAnchorX() - rightBound - 2 - i, state.getAnchorY() + j, state.getAnchorZ() + i));
                        }
                    }
                }

                yield positions.toArray(new BlockPos[0]);
            }
            case SOUTH_EAST -> {
                List<BlockPos> positions = new ArrayList<>();

                for (int i = -8; i <= -3; i++) {
                    for (int j = 0; j <= height; j++) {
                        for (int left = leftBound; left > 0; left--) {
                            positions.add(new BlockPos(state.getAnchorX() - i, state.getAnchorY() + j, state.getAnchorZ() - left - i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() - i, state.getAnchorY() + j, state.getAnchorZ() - leftBound - 1 - i));
                            positions.add(new BlockPos(state.getAnchorX() - i, state.getAnchorY() + j, state.getAnchorZ() - leftBound - 2 - i));
                        }

                        for (int right = rightBound; right >= 0; right--) {
                            positions.add(new BlockPos(state.getAnchorX() - right - i, state.getAnchorY() + j, state.getAnchorZ() - i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() - rightBound - 1 - i, state.getAnchorY() + j, state.getAnchorZ() - i));
                            positions.add(new BlockPos(state.getAnchorX() - rightBound - 2 - i, state.getAnchorY() + j, state.getAnchorZ() - i));
                        }
                    }
                }

                yield positions.toArray(new BlockPos[0]);
            }
            case SOUTH_WEST -> {
                List<BlockPos> positions = new ArrayList<>();

                for (int i = -8; i <= -3; i++) {
                    for (int j = 0; j <= height; j++) {
                        for (int left = leftBound; left > 0; left--) {
                            positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + j, state.getAnchorZ() - left - i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + j, state.getAnchorZ() - leftBound - 1 - i));
                            positions.add(new BlockPos(state.getAnchorX() + i, state.getAnchorY() + j, state.getAnchorZ() - leftBound - 2 - i));
                        }

                        for (int right = rightBound; right >= 0; right--) {
                            positions.add(new BlockPos(state.getAnchorX() + right + i, state.getAnchorY() + j, state.getAnchorZ() - i));
                        }

                        if (j > 0) {
                            positions.add(new BlockPos(state.getAnchorX() + rightBound + 1 + i, state.getAnchorY() + j, state.getAnchorZ() - i));
                            positions.add(new BlockPos(state.getAnchorX() + rightBound + 2 + i, state.getAnchorY() + j, state.getAnchorZ() - i));
                        }
                    }
                }

                yield positions.toArray(new BlockPos[0]);
            }
            default -> new BlockPos[0];
        };
    }
}
