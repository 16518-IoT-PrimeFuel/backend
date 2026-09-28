package com.primefuel.fulltank.platform.shared.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolumeTest {

    @Test
    void rejectsNegativeAndNonFiniteAmounts() {
        assertThatThrownBy(() -> Volume.litres(-1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Volume.litres(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Volume.litres(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsBetweenUnits() {
        var oneGallon = Volume.gallons(1.0);
        assertThat(oneGallon.convertedTo(Unit.LITRE).amount())
                .isCloseTo(3.785411784, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(Volume.litres(3.785411784).convertedTo(Unit.GALLON).amount())
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void unknownUnitCodesDefaultToLitres() {
        assertThat(Unit.fromCode("GAL")).isEqualTo(Unit.GALLON);
        assertThat(Unit.fromCode(null)).isEqualTo(Unit.LITRE);
        assertThat(Unit.fromCode("whatever")).isEqualTo(Unit.LITRE);
    }
}
