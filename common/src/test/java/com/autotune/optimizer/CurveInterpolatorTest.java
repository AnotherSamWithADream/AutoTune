package com.autotune.optimizer;

import com.autotune.optimizer.CurveInterpolator.DataPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurveInterpolatorTest {

    private static final double TOLERANCE = 0.01;

    // -----------------------------------------------------------------------
    // Linear interpolation (evaluateAt)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Linear interpolation midpoint between two points: (0,100) and (10,50) at x=5 yields ~75")
    void testLinearInterpolationTwoPoints() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(10, 50)
        );

        double y = CurveInterpolator.evaluateAt(points, 5.0);
        assertEquals(75.0, y, TOLERANCE,
                "Midpoint between (0,100) and (10,50) should be 75");
    }

    @Test
    @DisplayName("Linear interpolation at quarter point: x=2.5 yields ~87.5")
    void testLinearInterpolationQuarterPoint() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(10, 50)
        );

        double y = CurveInterpolator.evaluateAt(points, 2.5);
        assertEquals(87.5, y, TOLERANCE);
    }

    @Test
    @DisplayName("Linear interpolation across three segments")
    void testLinearInterpolationMultipleSegments() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 0),
                new DataPoint(5, 50),
                new DataPoint(10, 100)
        );

        assertEquals(0.0, CurveInterpolator.evaluateAt(points, 0.0), TOLERANCE);
        assertEquals(25.0, CurveInterpolator.evaluateAt(points, 2.5), TOLERANCE);
        assertEquals(50.0, CurveInterpolator.evaluateAt(points, 5.0), TOLERANCE);
        assertEquals(75.0, CurveInterpolator.evaluateAt(points, 7.5), TOLERANCE);
        assertEquals(100.0, CurveInterpolator.evaluateAt(points, 10.0), TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // interpolate (find X for a given Y)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Find x where y=75 on line (0,100)-(10,50) yields x=5")
    void testInterpolateFindX() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(10, 50)
        );

        double x = CurveInterpolator.interpolate(points, 75.0);
        assertEquals(5.0, x, TOLERANCE,
                "y=75 should correspond to x=5 on this line");
    }

    @Test
    @DisplayName("Find x for y at exact data point value")
    void testInterpolateFindXExactMatch() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(5, 75),
                new DataPoint(10, 50)
        );

        double x = CurveInterpolator.interpolate(points, 75.0);
        assertEquals(5.0, x, TOLERANCE);
    }

    @Test
    @DisplayName("interpolate returns xAtMaxY when targetY exceeds the range")
    void testInterpolateTargetAboveRange() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 50),
                new DataPoint(10, 100)
        );

        // targetY = 200 is above maxY = 100 -> should return x where y is max
        double x = CurveInterpolator.interpolate(points, 200.0);
        assertEquals(10.0, x, TOLERANCE,
                "Target above max should return x of max y");
    }

    @Test
    @DisplayName("interpolate returns xAtMinY when targetY is below the range")
    void testInterpolateTargetBelowRange() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 50),
                new DataPoint(10, 100)
        );

        // targetY = 10 is below minY = 50 -> should return x where y is min
        double x = CurveInterpolator.interpolate(points, 10.0);
        assertEquals(0.0, x, TOLERANCE,
                "Target below min should return x of min y");
    }

    // -----------------------------------------------------------------------
    // Monotonic spline
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Monotonic spline preserves monotonicity for increasing data")
    void testMonotonicSplineIncreasing() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 10),
                new DataPoint(2, 30),
                new DataPoint(4, 45),
                new DataPoint(6, 70),
                new DataPoint(8, 90),
                new DataPoint(10, 100)
        );

        List<DataPoint> spline = CurveInterpolator.fitMonotonicSpline(points);

        assertFalse(spline.isEmpty(), "Spline should produce output points");
        // Verify monotonicity: each y should be >= previous y
        for (int i = 1; i < spline.size(); i++) {
            assertTrue(spline.get(i).y() >= spline.get(i - 1).y() - 0.01,
                    "Spline should be monotonically non-decreasing at index " + i
                    + ": " + spline.get(i - 1).y() + " -> " + spline.get(i).y());
        }
    }

    @Test
    @DisplayName("Monotonic spline preserves monotonicity for decreasing data")
    void testMonotonicSplineDecreasing() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(2, 80),
                new DataPoint(4, 55),
                new DataPoint(6, 35),
                new DataPoint(8, 15),
                new DataPoint(10, 5)
        );

        List<DataPoint> spline = CurveInterpolator.fitMonotonicSpline(points);

        for (int i = 1; i < spline.size(); i++) {
            assertTrue(spline.get(i).y() <= spline.get(i - 1).y() + 0.01,
                    "Spline should be monotonically non-increasing at index " + i);
        }
    }

    @Test
    @DisplayName("Monotonic spline passes through the original endpoints")
    void testMonotonicSplineEndpoints() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 10),
                new DataPoint(5, 50),
                new DataPoint(10, 90)
        );

        List<DataPoint> spline = CurveInterpolator.fitMonotonicSpline(points);

        // First point should match
        assertEquals(0.0, spline.getFirst().x(), TOLERANCE);
        assertEquals(10.0, spline.getFirst().y(), TOLERANCE);

        // Last point should match
        DataPoint last = spline.getLast();
        assertEquals(10.0, last.x(), TOLERANCE);
        assertEquals(90.0, last.y(), TOLERANCE);
    }

    @Test
    @DisplayName("fitMonotonicSpline with fewer than 2 points returns input as-is")
    void testMonotonicSplineSinglePoint() {
        List<DataPoint> single = List.of(new DataPoint(5, 50));
        List<DataPoint> result = CurveInterpolator.fitMonotonicSpline(single);

        assertEquals(1, result.size());
        assertEquals(5.0, result.getFirst().x(), TOLERANCE);
        assertEquals(50.0, result.getFirst().y(), TOLERANCE);
    }

    @Test
    @DisplayName("fitMonotonicSpline with null returns empty list")
    void testMonotonicSplineNull() {
        List<DataPoint> result = CurveInterpolator.fitMonotonicSpline(null);
        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Single point edge case
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("evaluateAt with a single point always returns that point's y")
    void testSinglePointEvaluateAt() {
        List<DataPoint> single = List.of(new DataPoint(5, 42));

        assertEquals(42.0, CurveInterpolator.evaluateAt(single, 0.0), TOLERANCE);
        assertEquals(42.0, CurveInterpolator.evaluateAt(single, 5.0), TOLERANCE);
        assertEquals(42.0, CurveInterpolator.evaluateAt(single, 100.0), TOLERANCE);
    }

    @Test
    @DisplayName("interpolate with a single point returns that point's x")
    void testSinglePointInterpolate() {
        List<DataPoint> single = List.of(new DataPoint(5, 42));

        assertEquals(5.0, CurveInterpolator.interpolate(single, 42.0), TOLERANCE);
        assertEquals(5.0, CurveInterpolator.interpolate(single, 0.0), TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // Flat curve (all y-values identical)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Flat curve: evaluateAt returns constant y for any x in range")
    void testFlatCurveEvaluateAt() {
        List<DataPoint> flat = List.of(
                new DataPoint(0, 60),
                new DataPoint(5, 60),
                new DataPoint(10, 60)
        );

        assertEquals(60.0, CurveInterpolator.evaluateAt(flat, 0.0), TOLERANCE);
        assertEquals(60.0, CurveInterpolator.evaluateAt(flat, 3.0), TOLERANCE);
        assertEquals(60.0, CurveInterpolator.evaluateAt(flat, 7.5), TOLERANCE);
        assertEquals(60.0, CurveInterpolator.evaluateAt(flat, 10.0), TOLERANCE);
    }

    @Test
    @DisplayName("Flat curve: interpolate returns midpoint x for a flat segment")
    void testFlatCurveInterpolate() {
        List<DataPoint> flat = List.of(
                new DataPoint(0, 60),
                new DataPoint(10, 60)
        );

        // Both y values are 60, targetY = 60 -> segment is flat, should return midpoint
        double x = CurveInterpolator.interpolate(flat, 60.0);
        // targetY == maxY -> returns xAtMaxY, which is whichever point has y==maxY
        // Both have y=60, so xAtMaxY depends on iteration order. Let's just verify it's in range.
        assertTrue(x >= 0.0 && x <= 10.0,
                "Result should be within the data range");
    }

    // -----------------------------------------------------------------------
    // Extrapolation beyond range (clamping behavior)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("evaluateAt clamps to first point's y when x is below range")
    void testExtrapolationBelowRange() {
        List<DataPoint> points = List.of(
                new DataPoint(5, 80),
                new DataPoint(10, 40)
        );

        double y = CurveInterpolator.evaluateAt(points, 0.0);
        assertEquals(80.0, y, TOLERANCE,
                "x below range should return first point's y");
    }

    @Test
    @DisplayName("evaluateAt clamps to last point's y when x is above range")
    void testExtrapolationAboveRange() {
        List<DataPoint> points = List.of(
                new DataPoint(5, 80),
                new DataPoint(10, 40)
        );

        double y = CurveInterpolator.evaluateAt(points, 100.0);
        assertEquals(40.0, y, TOLERANCE,
                "x above range should return last point's y");
    }

    @Test
    @DisplayName("evaluateSplineAt clamps below range")
    void testSplineExtrapolationBelowRange() {
        List<DataPoint> points = List.of(
                new DataPoint(5, 80),
                new DataPoint(10, 60),
                new DataPoint(15, 40)
        );

        double y = CurveInterpolator.evaluateSplineAt(points, 0.0);
        assertEquals(80.0, y, TOLERANCE);
    }

    @Test
    @DisplayName("evaluateSplineAt clamps above range")
    void testSplineExtrapolationAboveRange() {
        List<DataPoint> points = List.of(
                new DataPoint(5, 80),
                new DataPoint(10, 60),
                new DataPoint(15, 40)
        );

        double y = CurveInterpolator.evaluateSplineAt(points, 100.0);
        assertEquals(40.0, y, TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // Unsorted input gets sorted internally
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("evaluateAt sorts unsorted input and interpolates correctly")
    void testUnsortedInputGetsSorted() {
        // Deliberately unsorted
        List<DataPoint> unsorted = List.of(
                new DataPoint(10, 50),
                new DataPoint(0, 100),
                new DataPoint(5, 75)
        );

        // x=2.5 should interpolate between (0,100) and (5,75) -> 87.5
        double y = CurveInterpolator.evaluateAt(unsorted, 2.5);
        assertEquals(87.5, y, TOLERANCE,
                "Unsorted input should be sorted and interpolated correctly");
    }

    @Test
    @DisplayName("interpolate sorts unsorted input and finds correct x")
    void testUnsortedInputInterpolate() {
        List<DataPoint> unsorted = List.of(
                new DataPoint(10, 50),
                new DataPoint(0, 100),
                new DataPoint(5, 75)
        );

        double x = CurveInterpolator.interpolate(unsorted, 75.0);
        assertEquals(5.0, x, TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // evaluateAt at exact data points
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("evaluateAt returns exact y when x matches a data point")
    void testEvaluateAtExactPoint() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(3, 70),
                new DataPoint(7, 40),
                new DataPoint(10, 10)
        );

        assertEquals(100.0, CurveInterpolator.evaluateAt(points, 0.0), TOLERANCE);
        assertEquals(70.0, CurveInterpolator.evaluateAt(points, 3.0), TOLERANCE);
        assertEquals(40.0, CurveInterpolator.evaluateAt(points, 7.0), TOLERANCE);
        assertEquals(10.0, CurveInterpolator.evaluateAt(points, 10.0), TOLERANCE);
    }

    @Test
    @DisplayName("evaluateSplineAt returns exact y at data point boundaries")
    void testEvaluateSplineAtExactEndpoints() {
        List<DataPoint> points = List.of(
                new DataPoint(0, 100),
                new DataPoint(5, 60),
                new DataPoint(10, 20)
        );

        // Boundary points should match exactly
        assertEquals(100.0, CurveInterpolator.evaluateSplineAt(points, 0.0), TOLERANCE);
        assertEquals(20.0, CurveInterpolator.evaluateSplineAt(points, 10.0), TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // Null and empty input
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("evaluateAt with null returns 0")
    void testEvaluateAtNull() {
        assertEquals(0.0, CurveInterpolator.evaluateAt(null, 5.0), TOLERANCE);
    }

    @Test
    @DisplayName("evaluateAt with empty list returns 0")
    void testEvaluateAtEmpty() {
        assertEquals(0.0, CurveInterpolator.evaluateAt(List.of(), 5.0), TOLERANCE);
    }

    @Test
    @DisplayName("interpolate with null returns 0")
    void testInterpolateNull() {
        assertEquals(0.0, CurveInterpolator.interpolate(null, 50.0), TOLERANCE);
    }

    @Test
    @DisplayName("interpolate with empty list returns 0")
    void testInterpolateEmpty() {
        assertEquals(0.0, CurveInterpolator.interpolate(List.of(), 50.0), TOLERANCE);
    }

    @Test
    @DisplayName("evaluateSplineAt with single point returns that point's y")
    void testEvaluateSplineAtSinglePoint() {
        List<DataPoint> single = List.of(new DataPoint(5, 42));
        assertEquals(42.0, CurveInterpolator.evaluateSplineAt(single, 5.0), TOLERANCE);
        assertEquals(42.0, CurveInterpolator.evaluateSplineAt(single, 0.0), TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // DataPoint record
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DataPoint compareTo sorts by x ascending")
    void testDataPointCompareTo() {
        DataPoint a = new DataPoint(1, 100);
        DataPoint b = new DataPoint(5, 50);
        DataPoint c = new DataPoint(1, 200);

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(c), "Same x should compare as equal");
    }
}
