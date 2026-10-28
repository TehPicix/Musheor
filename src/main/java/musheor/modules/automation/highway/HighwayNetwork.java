package musheor.modules.automation.highway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import musheor.utils.HighwayUtils;
import musheor.utils.system.HighwayNetworkManager;

public class HighwayNetwork {
    public static final List<HighwayNetwork.Highway> HIGHWAYS = buildHighways();
    public static final List<HighwayNetwork.Intersection> INTERSECTIONS = buildIntersections(HIGHWAYS);
    public static final List<HighwayNetwork.Edge> EDGES = buildEdges(HIGHWAYS, INTERSECTIONS);

    public static double[] getRingDistances() {
        return HighwayNetworkManager.getInstance().getRingDistances("2b2t");
    }

    public static double[] getDiamondDistances() {
        return HighwayNetworkManager.getInstance().getDiamondDistances("2b2t");
    }

    private static List<HighwayNetwork.Highway> buildHighways() {
        List<HighwayNetwork.Highway> h = new ArrayList<>();
        h.add(HighwayNetwork.Highway.xAxis());
        h.add(HighwayNetwork.Highway.zAxis());
        h.add(HighwayNetwork.Highway.diagonalXEqualsZ());
        h.add(HighwayNetwork.Highway.diagonalXEqualsNegZ());

        for (double d : getRingDistances()) {
            h.add(HighwayNetwork.Highway.ringRoadZ(d));
            h.add(HighwayNetwork.Highway.ringRoadZ(-d));
            h.add(HighwayNetwork.Highway.ringRoadX(d));
            h.add(HighwayNetwork.Highway.ringRoadX(-d));
        }

        for (double d : getDiamondDistances()) {
            h.add(HighwayNetwork.Highway.diamond(true, d));
            h.add(HighwayNetwork.Highway.diamond(true, -d));
            h.add(HighwayNetwork.Highway.diamond(false, d));
            h.add(HighwayNetwork.Highway.diamond(false, -d));
        }

        for (int i = 1; i <= 10; i++) {
            double coord = i * 5000.0 + 0.5;
            h.add(HighwayNetwork.Highway.gridZ(coord));
            h.add(HighwayNetwork.Highway.gridZ(-coord));
            h.add(HighwayNetwork.Highway.gridX(coord));
            h.add(HighwayNetwork.Highway.gridX(-coord));
        }

        return Collections.unmodifiableList(h);
    }

    private static List<HighwayNetwork.Intersection> buildIntersections(List<HighwayNetwork.Highway> highways) {
        Map<String, List<HighwayNetwork.Highway>> byPos = new LinkedHashMap<>();

        for (int i = 0; i < highways.size(); i++) {
            for (int j = i + 1; j < highways.size(); j++) {
                HighwayNetwork.Highway a = highways.get(i);
                HighwayNetwork.Highway b = highways.get(j);
                HighwayUtils.HorizontalPos pt = intersect(a, b);
                if (pt != null && a.containsPoint(pt.x(), pt.z(), 1.0) && b.containsPoint(pt.x(), pt.z(), 1.0)) {
                    String key = toKey(pt);
                    byPos.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
                    byPos.get(key).add(b);
                }
            }
        }

        List<HighwayNetwork.Intersection> nodes = new ArrayList<>();

        for (Entry<String, List<HighwayNetwork.Highway>> entry : byPos.entrySet()) {
            HighwayUtils.HorizontalPos pos = parseKey(entry.getKey());
            List<HighwayNetwork.Highway> unique = entry.getValue().stream().distinct().toList();
            nodes.add(new HighwayNetwork.Intersection(pos, unique));
        }

        return Collections.unmodifiableList(nodes);
    }

    private static List<HighwayNetwork.Edge> buildEdges(List<HighwayNetwork.Highway> highways, List<HighwayNetwork.Intersection> nodes) {
        List<HighwayNetwork.Edge> edges = new ArrayList<>();

        for (HighwayNetwork.Highway hw : highways) {
            List<Integer> onHw = new ArrayList<>();

            for (int i = 0; i < nodes.size(); i++) {
                HighwayUtils.HorizontalPos p = nodes.get(i).pos();
                if (hw.containsPoint(p.x(), p.z(), 1.0)) {
                    onHw.add(i);
                }
            }

            onHw.sort((a, b) -> {
                HighwayUtils.HorizontalPos pa = nodes.get(a).pos();
                HighwayUtils.HorizontalPos pb = nodes.get(b).pos();
                double ca = coordAlong(hw, pa);
                double cb = coordAlong(hw, pb);
                return Double.compare(ca, cb);
            });

            for (int k = 0; k < onHw.size() - 1; k++) {
                int from = onHw.get(k);
                int to = onHw.get(k + 1);
                HighwayUtils.HorizontalPos pf = nodes.get(from).pos();
                HighwayUtils.HorizontalPos pt = nodes.get(to).pos();
                double dist = distance(pf, pt);
                edges.add(new HighwayNetwork.Edge(from, to, dist, hw));
                edges.add(new HighwayNetwork.Edge(to, from, dist, hw));
            }
        }

        return Collections.unmodifiableList(edges);
    }

    static HighwayUtils.HorizontalPos intersect(HighwayNetwork.Highway a, HighwayNetwork.Highway b) {
        double[] la = lineCoefficients(a);
        double[] lb = lineCoefficients(b);
        if (la != null && lb != null) {
            double det = la[0] * lb[1] - lb[0] * la[1];
            if (Math.abs(det) < 1.0E-9) {
                return null;
            } else {
                double x = (la[2] * lb[1] - lb[2] * la[1]) / det;
                double z = (la[0] * lb[2] - lb[0] * la[2]) / det;
                return new HighwayUtils.HorizontalPos(x, z);
            }
        } else {
            return null;
        }
    }

    private static double[] lineCoefficients(HighwayNetwork.Highway hw) {
        return switch (hw.type()) {
            case CARDINAL, RING_ROAD, GRID -> hw.isHorizontal() ? new double[]{0.0, 1.0, hw.constCoord()} : new double[]{1.0, 0.0, hw.constCoord()};
            case DIAGONAL -> hw.isHorizontal() ? new double[]{1.0, 1.0, hw.constCoord()} : new double[]{1.0, -1.0, hw.constCoord()};
            case DIAMOND -> hw.isHorizontal() ? new double[]{1.0, 1.0, hw.constCoord()} : new double[]{1.0, -1.0, hw.constCoord()};
        };
    }

    private static double coordAlong(HighwayNetwork.Highway hw, HighwayUtils.HorizontalPos p) {
        return hw.isDiagonal() ? p.x() : (hw.isHorizontal() ? p.x() : p.z());
    }

    public static double distance(HighwayUtils.HorizontalPos a, HighwayUtils.HorizontalPos b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String toKey(HighwayUtils.HorizontalPos p) {
        return Math.round(p.x()) + "," + Math.round(p.z());
    }

    private static HighwayUtils.HorizontalPos parseKey(String key) {
        String[] parts = key.split(",");
        return new HighwayUtils.HorizontalPos(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }

    public static List<Integer> intersectionsOn(HighwayNetwork.Highway hw) {
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < INTERSECTIONS.size(); i++) {
            HighwayUtils.HorizontalPos p = INTERSECTIONS.get(i).pos();
            if (hw.containsPoint(p.x(), p.z(), 1.0)) {
                result.add(i);
            }
        }

        return result;
    }

    public static Map<Integer, List<HighwayNetwork.Edge>> buildAdjacency() {
        Map<Integer, List<HighwayNetwork.Edge>> adj = new HashMap<>();

        for (int i = 0; i < INTERSECTIONS.size(); i++) {
            adj.put(i, new ArrayList<>());
        }

        for (HighwayNetwork.Edge e : EDGES) {
            adj.get(e.from()).add(e);
        }

        return adj;
    }

    public record Edge(int from, int to, double distance, HighwayNetwork.Highway highway) {
    }

    public record Highway(String name, HighwayNetwork.HighwayType type, boolean isHorizontal, double constCoord, double minCoord, double maxCoord) {
        static HighwayNetwork.Highway xAxis() {
            return new HighwayNetwork.Highway("+X/-X axis (Z=0)", HighwayNetwork.HighwayType.CARDINAL, true, 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        }

        static HighwayNetwork.Highway zAxis() {
            return new HighwayNetwork.Highway("+Z/-Z axis (X=0)", HighwayNetwork.HighwayType.CARDINAL, false, 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        }

        static HighwayNetwork.Highway diagonalXEqualsZ() {
            return new HighwayNetwork.Highway("diagonal (X=Z)", HighwayNetwork.HighwayType.DIAGONAL, false, 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        }

        static HighwayNetwork.Highway diagonalXEqualsNegZ() {
            return new HighwayNetwork.Highway("diagonal (X=-Z)", HighwayNetwork.HighwayType.DIAGONAL, true, 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        }

        static HighwayNetwork.Highway ringRoadZ(double coord) {
            double limit = Math.abs(coord);
            return new HighwayNetwork.Highway(String.format("ring road Z=%.0f", coord), HighwayNetwork.HighwayType.RING_ROAD, true, coord, -limit, limit);
        }

        static HighwayNetwork.Highway ringRoadX(double coord) {
            double limit = Math.abs(coord);
            return new HighwayNetwork.Highway(String.format("ring road X=%.0f", coord), HighwayNetwork.HighwayType.RING_ROAD, false, coord, -limit, limit);
        }

        static HighwayNetwork.Highway diamond(boolean isSum, double K) {
            double minX = Math.min(0.0, K);
            double maxX = Math.max(0.0, K);
            String eq = isSum ? String.format("diamond X+Z=%.0f", K) : String.format("diamond X-Z=%.0f", K);
            return new HighwayNetwork.Highway(eq, HighwayNetwork.HighwayType.DIAMOND, isSum, K, minX, maxX);
        }

        static HighwayNetwork.Highway gridZ(double coord) {
            return new HighwayNetwork.Highway(String.format("grid Z=%.1f", coord), HighwayNetwork.HighwayType.GRID, true, coord, -50000.5, 50000.5);
        }

        static HighwayNetwork.Highway gridX(double coord) {
            return new HighwayNetwork.Highway(String.format("grid X=%.1f", coord), HighwayNetwork.HighwayType.GRID, false, coord, -50000.5, 50000.5);
        }

        boolean isDiagonal() {
            return this.type == HighwayNetwork.HighwayType.DIAGONAL || this.type == HighwayNetwork.HighwayType.DIAMOND;
        }

        boolean containsPoint(double x, double z, double tol) {
            return switch (this.type) {
                case CARDINAL, RING_ROAD, GRID -> this.isHorizontal
                    ? Math.abs(z - this.constCoord) <= tol && x >= this.minCoord - tol && x <= this.maxCoord + tol
                    : Math.abs(x - this.constCoord) <= tol && z >= this.minCoord - tol && z <= this.maxCoord + tol;
                case DIAGONAL -> {
                    double val = this.isHorizontal ? x + z : x - z;
                    yield Math.abs(val - this.constCoord) <= tol;
                }
                case DIAMOND -> {
                    double val = this.isHorizontal ? x + z : x - z;
                    yield Math.abs(val - this.constCoord) <= tol && x >= this.minCoord - tol && x <= this.maxCoord + tol;
                }
            };
        }

        double clampCoord(double coord) {
            return Math.max(this.minCoord, Math.min(this.maxCoord, coord));
        }

        HighwayUtils.HorizontalPos closestPoint(double x, double z) {
            return switch (this.type) {
                case CARDINAL, RING_ROAD, GRID -> {
                    if (this.isHorizontal) {
                        double cx = this.clampCoord(x);
                        yield new HighwayUtils.HorizontalPos(cx, this.constCoord);
                    } else {
                        double cz = this.clampCoord(z);
                        yield new HighwayUtils.HorizontalPos(this.constCoord, cz);
                    }
                }
                case DIAGONAL -> {
                    if (this.isHorizontal) {
                        double px = (x - z) / 2.0;
                        double pz = -px;
                        yield new HighwayUtils.HorizontalPos(px, pz);
                    } else {
                        double px = (x + z) / 2.0;
                        yield new HighwayUtils.HorizontalPos(px, px);
                    }
                }
                case DIAMOND -> {
                    boolean isSum = this.isHorizontal;
                    double cx = isSum ? (x - z + this.constCoord) / 2.0 : (x + z + this.constCoord) / 2.0;
                    cx = this.clampCoord(cx);
                    double cz = isSum ? this.constCoord - cx : cx - this.constCoord;
                    yield new HighwayUtils.HorizontalPos(cx, cz);
                }
            };
        }
    }

    public static enum HighwayType {
        CARDINAL,
        DIAGONAL,
        RING_ROAD,
        DIAMOND,
        GRID;
    }

    public record Intersection(HighwayUtils.HorizontalPos pos, List<HighwayNetwork.Highway> highways) {
    }
}
