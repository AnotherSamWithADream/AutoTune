package com.autotune.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interpolates benchmark data points to find optimal setting values.
 * Supports piecewise linear interpolation and monotonic cubic spline fitting.
 */
public class CurveInterpolator {

    /**
     * A single benchmark data point: x is the setting value, y is the measured FPS (or metric).
     */
    public record DataPoint(double x, double y) implements Comparable<DataPoint> {
        @Override
        public int compareTo(DataPoint other) {
            return Double.compare(this.x, other.x);
        }
    }

    // [CODE-REVIEW-FIX] M-001: Added sortedCopy() to sort data once instead of re-sorting
    // on every call to interpolate(), evaluateAt(), etc. Callers that invoke multiple
    // methods on the same dataset should pre-sort with sortedCopy() and pass the result.

    /**
     * Returns a new list of data points sorted by x-value ascending.
     * Use this to pre-sort data once before calling multiple interpolation methods.
     *
     * @param points the unsorted data points
     * @return a new sorted list (the original is not modified)
     */
    public static List<DataPoint> sortedCopy(List<DataPoint> points) {
        if (points == null || points.size() <= 1) {
            return points != null ? new ArrayList<>(points) : new ArrayList<>();
        }
        List<DataPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(DataPoint::x));
        return sorted;
    }

    /**
     * Returns true if the given list is already sorted by x-value ascending.
     */
    private static boolean isSorted(List<DataPoint> points) {
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).x() < points.get(i - 1).x()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a sorted view of the data, avoiding a copy if already sorted.
     */
    private static List<DataPoint> ensureSorted(List<DataPoint> points) {
        if (isSorted(points)) {
            return points;
        }
        List<DataPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(DataPoint::x));
        return sorted;
    }

    /**
     * Finds the x value where the interpolated curve reaches targetY,
     * using piecewise linear interpolation.
     * If no exact crossing exists, returns the x of the closest point.
     *
     * @param points  benchmark data points sorted or unsorted (will be sorted internally)
     * @param targetY the target y value (e.g., target FPS)
     * @return the interpolated x value where y = targetY
     */
    public static double interpolate(List<DataPoint> points, double targetY) {
        if (points == null || points.isEmpty()) return 0.0;
        if (points.size() == 1) return points.getFirst().x();

        // [CODE-REVIEW-FIX] M-001: Use ensureSorted to avoid re-sorting already-sorted data
        List<DataPoint> sorted = ensureSorted(points);

        // Check if targetY is outside the range of measured y values
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double xAtMinY = sorted.getFirst().x(), xAtMaxY = sorted.getFirst().x();
        for (DataPoint p : sorted) {
            if (p.y() < minY) { minY = p.y(); xAtMinY = p.x(); }
            if (p.y() > maxY) { maxY = p.y(); xAtMaxY = p.x(); }
        }
        if (targetY <= minY) return xAtMinY;
        if (targetY >= maxY) return xAtMaxY;

        // Walk segments to find where we cross targetY
        double bestX = sorted.getFirst().x();
        double bestDist = Math.abs(sorted.getFirst().y() - targetY);

        for (int i = 0; i < sorted.size() - 1; i++) {
            DataPoint a = sorted.get(i);
            DataPoint b = sorted.get(i + 1);

            double distA = Math.abs(a.y() - targetY);
            double distB = Math.abs(b.y() - targetY);
            if (distA < bestDist) { bestDist = distA; bestX = a.x(); }
            if (distB < bestDist) { bestDist = distB; bestX = b.x(); }

            // Check if this segment crosses targetY
            if ((a.y() <= targetY && b.y() >= targetY) || (a.y() >= targetY && b.y() <= targetY)) {
                if (Math.abs(b.y() - a.y()) < 1e-10) {
                    // Flat segment, take midpoint
                    return (a.x() + b.x()) / 2.0;
                }
                // Linear interpolation within this segment
                double t = (targetY - a.y()) / (b.y() - a.y());
                return a.x() + t * (b.x() - a.x());
            }
        }

        return bestX;
    }

    /**
     * Evaluates the piecewise linear interpolation at a given x value.
     *
     * @param points data points (will be sorted internally)
     * @param x      the x value to evaluate at
     * @return the interpolated y value
     */
    public static double evaluateAt(List<DataPoint> points, double x) {
        if (points == null || points.isEmpty()) return 0.0;
        if (points.size() == 1) return points.getFirst().y();

        // [CODE-REVIEW-FIX] M-001: Use ensureSorted to avoid re-sorting already-sorted data
        List<DataPoint> sorted = ensureSorted(points);

        // Clamp to range
        if (x <= sorted.getFirst().x()) return sorted.getFirst().y();
        if (x >= sorted.getLast().x()) return sorted.getLast().y();

        // Find the enclosing segment
        for (int i = 0; i < sorted.size() - 1; i++) {
            DataPoint a = sorted.get(i);
            DataPoint b = sorted.get(i + 1);
            if (x >= a.x() && x <= b.x()) {
                double dx = b.x() - a.x();
                if (Math.abs(dx) < 1e-10) return a.y();
                double t = (x - a.x()) / dx;
                return a.y() + t * (b.y() - a.y());
            }
        }

        return sorted.getLast().y();
    }

    /**
     * Fits a monotonic Fritsch-Carlson spline to the data points and returns
     * a dense set of interpolated points suitable for smooth rendering or lookup.
     * The spline is guaranteed to be monotonic between data points.
     *
     * @param points the raw data points (at least 2 required)
     * @return a list of densely sampled DataPoints along the monotonic spline
     */
    public static List<DataPoint> fitMonotonicSpline(List<DataPoint> points) {
        if (points == null || points.size() < 2) {
            return points != null ? new ArrayList<>(points) : new ArrayList<>();
        }

        // [CODE-REVIEW-FIX] M-001: Use ensureSorted to avoid re-sorting already-sorted data
        List<DataPoint> sorted = ensureSorted(points);
        int n = sorted.size();

        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = sorted.get(i).x();
            ys[i] = sorted.get(i).y();
        }

        // Step 1: Compute secants (deltas)
        double[] dx = new double[n - 1];
        double[] dy = new double[n - 1];
        double[] m = new double[n - 1]; // secant slopes
        for (int i = 0; i < n - 1; i++) {
            dx[i] = xs[i + 1] - xs[i];
            dy[i] = ys[i + 1] - ys[i];
            m[i] = dx[i] != 0 ? dy[i] / dx[i] : 0;
        }

        // Step 2: Compute tangent slopes (Fritsch-Carlson method)
        double[] tangents = new double[n];
        tangents[0] = m[0];
        tangents[n - 1] = m[n - 2];
        // [CODE-REVIEW-FIX] Uses arithmetic mean (Catmull-Rom variant) rather than harmonic mean
        // (canonical Fritsch-Carlson). Both produce valid monotonic splines after the enforcement step.
        for (int i = 1; i < n - 1; i++) {
            if (m[i - 1] * m[i] <= 0) {
                tangents[i] = 0;
            } else {
                tangents[i] = (m[i - 1] + m[i]) / 2.0;
            }
        }

        // Step 3: Monotonicity enforcement
        for (int i = 0; i < n - 1; i++) {
            if (Math.abs(m[i]) < 1e-10) {
                tangents[i] = 0;
                tangents[i + 1] = 0;
            } else {
                double alpha = tangents[i] / m[i];
                double beta = tangents[i + 1] / m[i];
                double s = alpha * alpha + beta * beta;
                if (s > 9) {
                    double tau = 3.0 / Math.sqrt(s);
                    tangents[i] = tau * alpha * m[i];
                    tangents[i + 1] = tau * beta * m[i];
                }
            }
        }

        // Step 4: Generate dense output using Hermite basis
        List<DataPoint> result = new ArrayList<>();
        int samplesPerSegment = 20;

        for (int i = 0; i < n - 1; i++) {
            double h = dx[i];
            for (int j = 0; j < samplesPerSegment; j++) {
                double t = (double) j / samplesPerSegment;
                double x = xs[i] + t * h;

                // Hermite basis functions
                double h00 = (1 + 2 * t) * (1 - t) * (1 - t);
                double h10 = t * (1 - t) * (1 - t);
                double h01 = t * t * (3 - 2 * t);
                double h11 = t * t * (t - 1);

                double y = h00 * ys[i] + h10 * h * tangents[i]
                         + h01 * ys[i + 1] + h11 * h * tangents[i + 1];

                result.add(new DataPoint(x, y));
            }
        }
        // Add the last point
        result.add(new DataPoint(xs[n - 1], ys[n - 1]));

        return result;
    }

    /**
     * Evaluates the monotonic spline at a specific x value using Hermite interpolation.
     *
     * @param points original data points (at least 2)
     * @param x      the x value to evaluate
     * @return the spline-interpolated y value
     */
    public static double evaluateSplineAt(List<DataPoint> points, double x) {
        if (points == null || points.size() < 2) {
            return points != null && !points.isEmpty() ? points.getFirst().y() : 0.0;
        }

        // [CODE-REVIEW-FIX] M-001: Use ensureSorted to avoid re-sorting already-sorted data
        List<DataPoint> sorted = ensureSorted(points);
        int n = sorted.size();

        if (x <= sorted.getFirst().x()) return sorted.getFirst().y();
        if (x >= sorted.get(n - 1).x()) return sorted.get(n - 1).y();

        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = sorted.get(i).x();
            ys[i] = sorted.get(i).y();
        }

        // Compute secant slopes
        double[] dxArr = new double[n - 1];
        double[] m = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            dxArr[i] = xs[i + 1] - xs[i];
            double dyVal = ys[i + 1] - ys[i];
            m[i] = dxArr[i] != 0 ? dyVal / dxArr[i] : 0;
        }

        // Compute tangents
        double[] tangents = new double[n];
        tangents[0] = m[0];
        tangents[n - 1] = m[n - 2];
        for (int i = 1; i < n - 1; i++) {
            if (m[i - 1] * m[i] <= 0) {
                tangents[i] = 0;
            } else {
                tangents[i] = (m[i - 1] + m[i]) / 2.0;
            }
        }

        // Monotonicity enforcement
        for (int i = 0; i < n - 1; i++) {
            if (Math.abs(m[i]) < 1e-10) {
                tangents[i] = 0;
                tangents[i + 1] = 0;
            } else {
                double alpha = tangents[i] / m[i];
                double beta = tangents[i + 1] / m[i];
                double s = alpha * alpha + beta * beta;
                if (s > 9) {
                    double tau = 3.0 / Math.sqrt(s);
                    tangents[i] = tau * alpha * m[i];
                    tangents[i + 1] = tau * beta * m[i];
                }
            }
        }

        // Find the segment containing x
        int seg = 0;
        for (int i = 0; i < n - 1; i++) {
            if (x >= xs[i] && x <= xs[i + 1]) {
                seg = i;
                break;
            }
        }

        double h = dxArr[seg];
        if (Math.abs(h) < 1e-10) return ys[seg];
        double t = (x - xs[seg]) / h;

        double h00 = (1 + 2 * t) * (1 - t) * (1 - t);
        double h10 = t * (1 - t) * (1 - t);
        double h01 = t * t * (3 - 2 * t);
        double h11 = t * t * (t - 1);

        return h00 * ys[seg] + h10 * h * tangents[seg]
             + h01 * ys[seg + 1] + h11 * h * tangents[seg + 1];
    }
}
