package musheor.utils;

public final class BouncePitchPolicy {
    private static final int HEIGHT_STEPS = (int)Math.round(250.0) + 1;
    private static final int VELOCITY_STEPS = (int)Math.round(150.0) + 1;
    private final double gravity;
    private final double jumpVelocity;
    private final float[] valueTable = new float[HEIGHT_STEPS * VELOCITY_STEPS];
    private double averageReward;

    private BouncePitchPolicy(double gravity, double jumpVelocity) {
        this.gravity = gravity;
        this.jumpVelocity = jumpVelocity;
    }

    public static BouncePitchPolicy solve(double gravity, double jumpVelocity) {
        BouncePitchPolicy opt = new BouncePitchPolicy(gravity, jumpVelocity);
        opt.averageReward = opt.estimateFixedPitchRewardRate(72.4);

        for (int i = 0; i < 4; i++) {
            opt.runValueIteration();
            double next = opt.estimatePolicyRewardRate();
            if (Math.abs(next - opt.averageReward) < 1.0E-7) {
                opt.averageReward = next;
                break;
            }

            opt.averageReward = next;
        }

        return opt;
    }

    public float getBestPitch(double heightAboveGround, double vy, boolean onGround) {
        double y = Math.max(0.0, heightAboveGround);
        if (onGround) {
            y = 0.0;
            vy = Math.max(this.jumpVelocity, vy);
        }

        double bestPitch = 90.0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (double p = 90.0; p >= 0.0; p -= 0.5) {
            double score = this.computeActionValue(y, vy, cosSquared(p));
            if (score > bestScore) {
                bestScore = score;
                bestPitch = p;
            }
        }

        return (float)bestPitch;
    }

    private void runValueIteration() {
        int actions = (int)Math.round(45.0) + 1;
        double[] cos2 = new double[actions];

        for (int a = 0; a < actions; a++) {
            cos2[a] = cosSquared(90.0 - a * 2.0);
        }

        float[] next = new float[this.valueTable.length];

        for (int iter = 0; iter < 200; iter++) {
            double maxDelta = 0.0;

            for (int iy = 0; iy < HEIGHT_STEPS; iy++) {
                double y = iy * 0.01;

                for (int iv = 0; iv < VELOCITY_STEPS; iv++) {
                    double vy = -1.0 + iv * 0.01;
                    double best = Double.NEGATIVE_INFINITY;

                    for (int a = 0; a < actions; a++) {
                        double s = this.computeActionValue(y, vy, cos2[a]);
                        if (s > best) {
                            best = s;
                        }
                    }

                    int idx = iy * VELOCITY_STEPS + iv;
                    next[idx] = (float)best;
                    maxDelta = Math.max(maxDelta, Math.abs(best - this.valueTable[idx]));
                }
            }

            System.arraycopy(next, 0, this.valueTable, 0, this.valueTable.length);
            if (maxDelta < 1.0E-7) {
                break;
            }
        }
    }

    private double computeActionValue(double y, double vy, double h) {
        double mid = vy + this.gravity * (-1.0 + h * 0.75);
        double lift = mid < 0.0 ? mid * -0.1 * h : 0.0;
        double vyNext = (mid + lift) * 0.98F;
        double yNext = y + vyNext;
        double reward = 0.9 * lift - this.averageReward;
        return yNext <= 0.0 ? reward : reward + this.interpolateValue(yNext, vyNext);
    }

    private static double cosSquared(double pitch) {
        double c = Math.cos((float)pitch * (float) (Math.PI / 180.0));
        return c * c;
    }

    private double interpolateValue(double y, double vy) {
        double fy = Math.min(Math.max(y / 0.01, 0.0), HEIGHT_STEPS - 1.0001);
        double fv = Math.min(Math.max((vy - -1.0) / 0.01, 0.0), VELOCITY_STEPS - 1.0001);
        int iy = (int)fy;
        int iv = (int)fv;
        double ty = fy - iy;
        double tv = fv - iv;
        int i = iy * VELOCITY_STEPS + iv;
        double a = this.valueTable[i] + (this.valueTable[i + 1] - this.valueTable[i]) * tv;
        double b = this.valueTable[i + VELOCITY_STEPS] + (this.valueTable[i + VELOCITY_STEPS + 1] - this.valueTable[i + VELOCITY_STEPS]) * tv;
        return a + (b - a) * ty;
    }

    private double estimatePolicyRewardRate() {
        double y = 0.0;
        double vy = 0.0;
        double boost = 0.2;
        boolean ground = true;

        for (int t = 1; t < 400; t++) {
            double p = this.getBestPitch(y, vy, ground);
            if (ground) {
                vy = Math.max(this.jumpVelocity, vy);
                ground = false;
            }

            double[] s = this.simulateTick(vy, p);
            vy = s[0];
            boost += 0.9 * s[1];
            y += vy;
            if (y <= 0.0) {
                return boost / t;
            }
        }

        return this.averageReward;
    }

    private double estimateFixedPitchRewardRate(double pitch) {
        double y = 0.0;
        double vy = this.jumpVelocity;
        double boost = 0.2;

        for (int t = 1; t < 400; t++) {
            double[] s = this.simulateTick(vy, pitch);
            vy = s[0];
            boost += 0.9 * s[1];
            y += vy;
            if (y <= 0.0) {
                return boost / t;
            }
        }

        return 0.0;
    }

    private double[] simulateTick(double vy, double pitch) {
        double h = cosSquared(pitch);
        double mid = vy + this.gravity * (-1.0 + h * 0.75);
        double lift = mid < 0.0 ? mid * -0.1 * h : 0.0;
        return new double[]{(mid + lift) * 0.98F, lift};
    }
}
