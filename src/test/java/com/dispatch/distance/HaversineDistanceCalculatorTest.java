package com.dispatch.distance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void samePointIsZeroDistance() {
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.6139, 77.2090)).isZero();
    }

    @Test
    void oneDegreeOfLongitudeAtTheEquatorIsAbout111Km() {
        assertThat(calculator.distanceKm(0, 0, 0, 1)).isCloseTo(111.195, within(0.01));
    }

    @Test
    void isSymmetric() {
        double ab = calculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        double ba = calculator.distanceKm(13.0827, 80.2707, 12.9716, 77.5946);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void bangaloreToChennaiIsAbout290Km() {
        assertThat(calculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707))
                .isCloseTo(290.2, within(1.0));
    }

    @Test
    void delhiLandmarksMatchKnownDistances() {
        // Connaught Place -> Karol Bagh ~14.4 km, -> Sector 18 Noida ~19.8 km, -> Cyber Hub ~24.7 km
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.7041, 77.1025)).isCloseTo(14.4, within(0.2));
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.5355, 77.3910)).isCloseTo(19.8, within(0.2));
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.4595, 77.0266)).isCloseTo(24.7, within(0.2));
    }

    @Test
    void antipodalPointsDoNotProduceNaN() {
        double half = calculator.distanceKm(0, 0, 0, 180);
        assertThat(half).isFinite().isCloseTo(Math.PI * 6371.0088, within(0.5));
        assertThat(calculator.distanceKm(90, 0, -90, 0)).isFinite();
    }
}
