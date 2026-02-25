package org.uedalab.clijplugin;

import java.util.List;

public final class Pca3DUtils {

    private Pca3DUtils() {
    }

    public static LineFitResult fitLine(final List<double[]> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 points.");
        }
        final double[] centroid = computeCentroid(points);
        final double[][] covariance = computeCovariance(points, centroid);
        final EigenResult eigen = jacobiEigenSymmetric3x3(covariance);
        final int principalIndex = largestEigenvalueIndex(eigen.values);
        final double[] direction = normalize(new double[]{
                eigen.vectors[0][principalIndex],
                eigen.vectors[1][principalIndex],
                eigen.vectors[2][principalIndex]
        }, new double[]{1.0, 0.0, 0.0});
        final double rms = computeLineRmsDistance(points, centroid, direction);
        return new LineFitResult(centroid, direction, rms);
    }

    public static PlaneFitResult fitPlane(final List<double[]> points) {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("Need at least 3 points.");
        }
        final double[] centroid = computeCentroid(points);
        final double[][] covariance = computeCovariance(points, centroid);
        final EigenResult eigen = jacobiEigenSymmetric3x3(covariance);
        final int normalIndex = smallestEigenvalueIndex(eigen.values);
        final double[] normal = normalize(new double[]{
                eigen.vectors[0][normalIndex],
                eigen.vectors[1][normalIndex],
                eigen.vectors[2][normalIndex]
        }, new double[]{0.0, 0.0, 1.0});
        final DistanceStats stats = computePlaneDistanceStats(points, centroid, normal);
        return new PlaneFitResult(centroid, normal, stats.rms, stats.max);
    }

    public static double[] normalize(final double[] v, final double[] fallback) {
        final double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (!Double.isFinite(norm) || norm <= 1e-15) {
            return new double[]{fallback[0], fallback[1], fallback[2]};
        }
        return new double[]{v[0] / norm, v[1] / norm, v[2] / norm};
    }

    private static double[] computeCentroid(final List<double[]> points) {
        final double[] c = new double[3];
        for (double[] p : points) {
            c[0] += p[0];
            c[1] += p[1];
            c[2] += p[2];
        }
        final double invN = 1.0 / points.size();
        c[0] *= invN;
        c[1] *= invN;
        c[2] *= invN;
        return c;
    }

    private static double[][] computeCovariance(final List<double[]> points, final double[] c) {
        final double[][] s = new double[3][3];
        for (double[] p : points) {
            final double dx = p[0] - c[0];
            final double dy = p[1] - c[1];
            final double dz = p[2] - c[2];
            s[0][0] += dx * dx;
            s[0][1] += dx * dy;
            s[0][2] += dx * dz;
            s[1][1] += dy * dy;
            s[1][2] += dy * dz;
            s[2][2] += dz * dz;
        }
        final double invN = 1.0 / points.size();
        s[0][0] *= invN;
        s[0][1] *= invN;
        s[0][2] *= invN;
        s[1][1] *= invN;
        s[1][2] *= invN;
        s[2][2] *= invN;
        s[1][0] = s[0][1];
        s[2][0] = s[0][2];
        s[2][1] = s[1][2];
        return s;
    }

    private static EigenResult jacobiEigenSymmetric3x3(final double[][] matrix) {
        final double[][] a = new double[3][3];
        final double[][] v = new double[3][3];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, 3);
            v[i][0] = i == 0 ? 1.0 : 0.0;
            v[i][1] = i == 1 ? 1.0 : 0.0;
            v[i][2] = i == 2 ? 1.0 : 0.0;
        }

        for (int iter = 0; iter < 50; iter++) {
            int p = 0;
            int q = 1;
            double max = Math.abs(a[0][1]);
            if (Math.abs(a[0][2]) > max) {
                max = Math.abs(a[0][2]);
                p = 0;
                q = 2;
            }
            if (Math.abs(a[1][2]) > max) {
                max = Math.abs(a[1][2]);
                p = 1;
                q = 2;
            }
            if (max < 1e-12) {
                break;
            }

            final double app = a[p][p];
            final double aqq = a[q][q];
            final double apq = a[p][q];
            final double phi = 0.5 * Math.atan2(2.0 * apq, aqq - app);
            final double c = Math.cos(phi);
            final double s = Math.sin(phi);

            for (int i = 0; i < 3; i++) {
                if (i == p || i == q) {
                    continue;
                }
                final double aip = a[i][p];
                final double aiq = a[i][q];
                final double newAip = c * aip - s * aiq;
                final double newAiq = s * aip + c * aiq;
                a[i][p] = newAip;
                a[p][i] = newAip;
                a[i][q] = newAiq;
                a[q][i] = newAiq;
            }

            final double newApp = c * c * app - 2.0 * s * c * apq + s * s * aqq;
            final double newAqq = s * s * app + 2.0 * s * c * apq + c * c * aqq;
            a[p][p] = newApp;
            a[q][q] = newAqq;
            a[p][q] = 0.0;
            a[q][p] = 0.0;

            for (int i = 0; i < 3; i++) {
                final double vip = v[i][p];
                final double viq = v[i][q];
                v[i][p] = c * vip - s * viq;
                v[i][q] = s * vip + c * viq;
            }
        }

        return new EigenResult(new double[]{a[0][0], a[1][1], a[2][2]}, v);
    }

    private static int largestEigenvalueIndex(final double[] values) {
        int idx = 0;
        if (values[1] > values[idx]) {
            idx = 1;
        }
        if (values[2] > values[idx]) {
            idx = 2;
        }
        return idx;
    }

    private static int smallestEigenvalueIndex(final double[] values) {
        int idx = 0;
        if (values[1] < values[idx]) {
            idx = 1;
        }
        if (values[2] < values[idx]) {
            idx = 2;
        }
        return idx;
    }

    private static double computeLineRmsDistance(final List<double[]> points, final double[] c, final double[] d) {
        double sumSq = 0.0;
        for (double[] p : points) {
            final double vx = p[0] - c[0];
            final double vy = p[1] - c[1];
            final double vz = p[2] - c[2];
            final double t = vx * d[0] + vy * d[1] + vz * d[2];
            final double rx = vx - t * d[0];
            final double ry = vy - t * d[1];
            final double rz = vz - t * d[2];
            sumSq += rx * rx + ry * ry + rz * rz;
        }
        return Math.sqrt(sumSq / points.size());
    }

    private static DistanceStats computePlaneDistanceStats(final List<double[]> points, final double[] c, final double[] n) {
        double sumSq = 0.0;
        double max = 0.0;
        for (double[] p : points) {
            final double vx = p[0] - c[0];
            final double vy = p[1] - c[1];
            final double vz = p[2] - c[2];
            final double dist = Math.abs(vx * n[0] + vy * n[1] + vz * n[2]);
            sumSq += dist * dist;
            if (dist > max) {
                max = dist;
            }
        }
        return new DistanceStats(Math.sqrt(sumSq / points.size()), max);
    }

    private static final class EigenResult {
        private final double[] values;
        private final double[][] vectors;

        private EigenResult(final double[] values, final double[][] vectors) {
            this.values = values;
            this.vectors = vectors;
        }
    }

    private static final class DistanceStats {
        private final double rms;
        private final double max;

        private DistanceStats(final double rms, final double max) {
            this.rms = rms;
            this.max = max;
        }
    }

    public static final class LineFitResult {
        public final double[] centroid;
        public final double[] direction;
        public final double rmsDist;

        private LineFitResult(final double[] centroid, final double[] direction, final double rmsDist) {
            this.centroid = centroid;
            this.direction = direction;
            this.rmsDist = rmsDist;
        }
    }

    public static final class PlaneFitResult {
        public final double[] centroid;
        public final double[] normal;
        public final double rmsDist;
        public final double maxDist;

        private PlaneFitResult(final double[] centroid, final double[] normal, final double rmsDist, final double maxDist) {
            this.centroid = centroid;
            this.normal = normal;
            this.rmsDist = rmsDist;
            this.maxDist = maxDist;
        }
    }
}
