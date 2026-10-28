package musheor.modules.automation.highway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import musheor.utils.HighwayUtils;

public class HighwayRouter {
    public static HighwayRouter.Route findRoute(double px, double pz, double gx, double gz) {
        HighwayUtils.HorizontalPos player = new HighwayUtils.HorizontalPos(px, pz);
        HighwayNetwork.Highway playerHighway = findNearestHighway(px, pz);
        if (playerHighway == null) {
            return null;
        } else {
            HighwayRouter.SnapResult goalSnap = snapToHighway(gx, gz);
            if (goalSnap == null) {
                return null;
            } else {
                HighwayUtils.HorizontalPos goalPos = goalSnap.point;
                HighwayNetwork.Highway goalHighway = goalSnap.highway;
                if (playerHighway.equals(goalHighway)) {
                    return new HighwayRouter.Route(List.of(new HighwayRouter.Leg(player, goalPos, playerHighway, HighwayNetwork.distance(player, goalPos))));
                } else {
                    HighwayUtils.HorizontalPos intersection = findBestIntersection(playerHighway, goalHighway, player, goalPos);
                    if (intersection != null) {
                        HighwayRouter.Leg leg1 = new HighwayRouter.Leg(player, intersection, playerHighway, HighwayNetwork.distance(player, intersection));
                        HighwayRouter.Leg leg2 = new HighwayRouter.Leg(intersection, goalPos, goalHighway, HighwayNetwork.distance(intersection, goalPos));
                        return new HighwayRouter.Route(List.of(leg1, leg2));
                    } else {
                        return findGraphRoute(player, playerHighway, goalPos, goalHighway);
                    }
                }
            }
        }
    }

    private static HighwayNetwork.Highway findNearestHighway(double x, double z) {
        HighwayNetwork.Highway best = null;
        double bestDist = Double.MAX_VALUE;
        HighwayUtils.HorizontalPos pos = new HighwayUtils.HorizontalPos(x, z);

        for (HighwayNetwork.Highway hw : HighwayNetwork.HIGHWAYS) {
            double d = HighwayNetwork.distance(hw.closestPoint(x, z), pos);
            if (d < bestDist) {
                bestDist = d;
                best = hw;
            }
        }

        return bestDist <= 150.0 ? best : null;
    }

    private static HighwayRouter.SnapResult snapToHighway(double x, double z) {
        HighwayNetwork.Highway best = null;
        HighwayUtils.HorizontalPos bestPoint = null;
        double bestDist = Double.MAX_VALUE;
        HighwayUtils.HorizontalPos pos = new HighwayUtils.HorizontalPos(x, z);

        for (HighwayNetwork.Highway hw : HighwayNetwork.HIGHWAYS) {
            HighwayUtils.HorizontalPos nearest = hw.closestPoint(x, z);
            double d = HighwayNetwork.distance(nearest, pos);
            if (d < bestDist) {
                bestDist = d;
                bestPoint = nearest;
                best = hw;
            }
        }

        return best != null ? new HighwayRouter.SnapResult(bestPoint, best) : null;
    }

    private static HighwayUtils.HorizontalPos findBestIntersection(
        HighwayNetwork.Highway hwA, HighwayNetwork.Highway hwB, HighwayUtils.HorizontalPos player, HighwayUtils.HorizontalPos goal
    ) {
        List<HighwayUtils.HorizontalPos> candidates = new ArrayList<>();
        HighwayUtils.HorizontalPos direct = HighwayNetwork.intersect(hwA, hwB);
        if (direct != null && hwA.containsPoint(direct.x(), direct.z(), 1.0) && hwB.containsPoint(direct.x(), direct.z(), 1.0)) {
            candidates.add(direct);
        }

        for (HighwayNetwork.Intersection node : HighwayNetwork.INTERSECTIONS) {
            if (hwA.containsPoint(node.pos().x(), node.pos().z(), 1.0) && hwB.containsPoint(node.pos().x(), node.pos().z(), 1.0)) {
                candidates.add(node.pos());
            }
        }

        if (candidates.isEmpty()) {
            return null;
        } else {
            HighwayUtils.HorizontalPos best = null;
            double bestCost = Double.MAX_VALUE;

            for (HighwayUtils.HorizontalPos candidate : candidates) {
                double cost = HighwayNetwork.distance(player, candidate) + HighwayNetwork.distance(candidate, goal);
                double directDist = HighwayNetwork.distance(player, goal);
                if (!(cost > directDist * 1.5) && cost < bestCost) {
                    bestCost = cost;
                    best = candidate;
                }
            }

            return best;
        }
    }

    private static HighwayRouter.Route findGraphRoute(
        HighwayUtils.HorizontalPos player, HighwayNetwork.Highway playerHighway, HighwayUtils.HorizontalPos goal, HighwayNetwork.Highway goalHighway
    ) {
        int startNode = findStartNode(playerHighway, player, goal);
        int goalNode = findNearestNode(goalHighway, goal);
        if (startNode < 0 || goalNode < 0) {
            return null;
        } else if (startNode == goalNode) {
            return HighwayRouter.Route.empty();
        } else {
            Map<Integer, List<HighwayNetwork.Edge>> adj = HighwayNetwork.buildAdjacency();
            int n = HighwayNetwork.INTERSECTIONS.size();
            double[] dist = new double[n];
            int[] prev = new int[n];
            HighwayNetwork.Edge[] prevEdge = new HighwayNetwork.Edge[n];
            boolean[] visited = new boolean[n];
            Arrays.fill(dist, Double.MAX_VALUE);
            Arrays.fill(prev, -1);
            dist[startNode] = 0.0;
            PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> Double.longBitsToDouble(a[0])));
            pq.offer(new long[]{Double.doubleToLongBits(0.0), startNode});

            while (!pq.isEmpty()) {
                long[] top = pq.poll();
                int curr = (int)top[1];
                double d = Double.longBitsToDouble(top[0]);
                if (!visited[curr]) {
                    visited[curr] = true;
                    if (curr == goalNode) {
                        break;
                    }

                    for (HighwayNetwork.Edge edge : adj.getOrDefault(curr, List.of())) {
                        double nd = d + edge.distance();
                        if (nd < dist[edge.to()]) {
                            dist[edge.to()] = nd;
                            prev[edge.to()] = curr;
                            prevEdge[edge.to()] = edge;
                            pq.offer(new long[]{Double.doubleToLongBits(nd), edge.to()});
                        }
                    }
                }
            }

            if (dist[goalNode] == Double.MAX_VALUE) {
                return null;
            } else {
                List<HighwayRouter.Leg> legs = new ArrayList<>();
                int cur = goalNode;

                while (prev[cur] != -1) {
                    int from = prev[cur];
                    HighwayNetwork.Edge edgex = prevEdge[cur];
                    legs.add(
                        new HighwayRouter.Leg(
                            HighwayNetwork.INTERSECTIONS.get(from).pos(), HighwayNetwork.INTERSECTIONS.get(cur).pos(), edgex.highway(), edgex.distance()
                        )
                    );
                    cur = from;
                }

                Collections.reverse(legs);
                HighwayUtils.HorizontalPos startPos = HighwayNetwork.INTERSECTIONS.get(startNode).pos();
                if (HighwayNetwork.distance(player, startPos) > 10.0) {
                    legs.add(0, new HighwayRouter.Leg(player, startPos, playerHighway, HighwayNetwork.distance(player, startPos)));
                }

                HighwayUtils.HorizontalPos lastPos = legs.isEmpty() ? player : legs.get(legs.size() - 1).to();
                if (HighwayNetwork.distance(lastPos, goal) > 10.0) {
                    legs.add(new HighwayRouter.Leg(lastPos, goal, goalHighway, HighwayNetwork.distance(lastPos, goal)));
                }

                return new HighwayRouter.Route(legs);
            }
        }
    }

    private static int findStartNode(HighwayNetwork.Highway hw, HighwayUtils.HorizontalPos from, HighwayUtils.HorizontalPos goal) {
        List<Integer> candidates = HighwayNetwork.intersectionsOn(hw);
        int best = -1;
        double bestCost = Double.MAX_VALUE;

        for (int idx : candidates) {
            HighwayUtils.HorizontalPos p = HighwayNetwork.INTERSECTIONS.get(idx).pos();
            double cost = HighwayNetwork.distance(from, p) + HighwayNetwork.distance(p, goal);
            if (cost < bestCost) {
                bestCost = cost;
                best = idx;
            }
        }

        return best;
    }

    private static int findNearestNode(HighwayNetwork.Highway hw, HighwayUtils.HorizontalPos target) {
        List<Integer> candidates = HighwayNetwork.intersectionsOn(hw);
        int best = -1;
        double bestDist = Double.MAX_VALUE;

        for (int idx : candidates) {
            double d = HighwayNetwork.distance(target, HighwayNetwork.INTERSECTIONS.get(idx).pos());
            if (d < bestDist) {
                bestDist = d;
                best = idx;
            }
        }

        return best;
    }

    public record Leg(HighwayUtils.HorizontalPos from, HighwayUtils.HorizontalPos to, HighwayNetwork.Highway highway, double distance) {
    }

    public static class Route {
        public final List<HighwayRouter.Leg> legs;
        public final double totalDistance;
        public final List<HighwayUtils.HorizontalPos> waypoints;

        private Route(List<HighwayRouter.Leg> legs) {
            this.legs = Collections.unmodifiableList(mergeCollinearLegs(legs));
            this.totalDistance = this.legs.stream().mapToDouble(HighwayRouter.Leg::distance).sum();
            List<HighwayUtils.HorizontalPos> wp = new ArrayList<>();
            if (!this.legs.isEmpty()) {
                wp.add(this.legs.get(0).from());
            }

            for (HighwayRouter.Leg leg : this.legs) {
                wp.add(leg.to());
            }

            this.waypoints = Collections.unmodifiableList(wp);
        }

        public static HighwayRouter.Route empty() {
            return new HighwayRouter.Route(List.of());
        }

        private static List<HighwayRouter.Leg> mergeCollinearLegs(List<HighwayRouter.Leg> legs) {
            if (legs.size() < 2) {
                return new ArrayList<>(legs);
            } else {
                List<HighwayRouter.Leg> out = new ArrayList<>();
                HighwayRouter.Leg cur = legs.get(0);

                for (int i = 1; i < legs.size(); i++) {
                    HighwayRouter.Leg next = legs.get(i);
                    if (cur.highway().equals(next.highway()) && sameDirection(cur, next)) {
                        cur = new HighwayRouter.Leg(cur.from(), next.to(), cur.highway(), cur.distance() + next.distance());
                    } else {
                        out.add(cur);
                        cur = next;
                    }
                }

                out.add(cur);
                return out;
            }
        }

        private static boolean sameDirection(HighwayRouter.Leg a, HighwayRouter.Leg b) {
            double ax = a.to().x() - a.from().x();
            double az = a.to().z() - a.from().z();
            double bx = b.to().x() - b.from().x();
            double bz = b.to().z() - b.from().z();
            return ax * bx + az * bz > 0.0;
        }
    }

    private record SnapResult(HighwayUtils.HorizontalPos point, HighwayNetwork.Highway highway) {
    }
}
