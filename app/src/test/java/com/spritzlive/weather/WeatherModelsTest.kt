package com.spritzlive.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic tests for the WMO code mapping and wind compass — no device needed. */
class WeatherModelsTest {

    @Test
    fun weatherCodes_mapToExpectedConditions() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromCode(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromCode(2))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromCode(48))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromCode(65))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromCode(75))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromCode(95))
    }

    @Test
    fun unknownCode_fallsBackToUnknown() {
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromCode(1234))
    }

    @Test
    fun windCompass_coversAllEightPoints() {
        assertEquals("N", windCompass(0))
        assertEquals("N", windCompass(360))
        assertEquals("E", windCompass(90))
        assertEquals("S", windCompass(180))
        assertEquals("W", windCompass(270))
        assertEquals("NE", windCompass(45))
        assertEquals("N", windCompass(350))   // wraps back to N near 360
    }

    @Test
    fun tempUnit_otherTogglesBothWays() {
        assertEquals(TempUnit.FAHRENHEIT, TempUnit.CELSIUS.other())
        assertEquals(TempUnit.CELSIUS, TempUnit.FAHRENHEIT.other())
    }
}
