package com.example

import com.example.engine.CalculatorEngine
import com.example.engine.UnitCategory
import com.example.engine.UnitConverter
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCalculatorBasicArithmetic() {
        val res1 = CalculatorEngine.evaluate("2 + 2")
        assertTrue(res1 is CalculatorEngine.EvalResult.Success)
        assertEquals("4", (res1 as CalculatorEngine.EvalResult.Success).formatted)

        val res2 = CalculatorEngine.evaluate("10 - 4.5 * 2")
        assertTrue(res2 is CalculatorEngine.EvalResult.Success)
        assertEquals("1", (res2 as CalculatorEngine.EvalResult.Success).formatted)

        val res3 = CalculatorEngine.evaluate("(12 + 8) / 4")
        assertTrue(res3 is CalculatorEngine.EvalResult.Success)
        assertEquals("5", (res3 as CalculatorEngine.EvalResult.Success).formatted)
    }

    @Test
    fun testCalculatorScientificFunctions() {
        val sqrtRes = CalculatorEngine.evaluate("sqrt(144)")
        assertTrue(sqrtRes is CalculatorEngine.EvalResult.Success)
        assertEquals("12", (sqrtRes as CalculatorEngine.EvalResult.Success).formatted)

        val powRes = CalculatorEngine.evaluate("2 ^ 8")
        assertTrue(powRes is CalculatorEngine.EvalResult.Success)
        assertEquals("256", (powRes as CalculatorEngine.EvalResult.Success).formatted)

        val sinRes = CalculatorEngine.evaluate("sin(90)", CalculatorEngine.AngleMode.DEG)
        assertTrue(sinRes is CalculatorEngine.EvalResult.Success)
        assertEquals("1", (sinRes as CalculatorEngine.EvalResult.Success).formatted)

        val implicitRes = CalculatorEngine.evaluate("5 * sin(30) + 2(3)", CalculatorEngine.AngleMode.DEG)
        assertTrue(implicitRes is CalculatorEngine.EvalResult.Success)
        assertEquals("8.5", (implicitRes as CalculatorEngine.EvalResult.Success).formatted)

        val sciNotationRes = CalculatorEngine.evaluate("1E12 + 5")
        assertTrue(sciNotationRes is CalculatorEngine.EvalResult.Success)
        assertEquals(1.000000000005E12, (sciNotationRes as CalculatorEngine.EvalResult.Success).value, 1.0)

        val percentRes = CalculatorEngine.evaluate("200 * 15%")
        assertTrue(percentRes is CalculatorEngine.EvalResult.Success)
        assertEquals("30", (percentRes as CalculatorEngine.EvalResult.Success).formatted)

        val trigPowerRes = CalculatorEngine.evaluate("sin(30)^2", CalculatorEngine.AngleMode.DEG)
        assertTrue(trigPowerRes is CalculatorEngine.EvalResult.Success)
        assertEquals("0.25", (trigPowerRes as CalculatorEngine.EvalResult.Success).formatted)
    }

    @Test
    fun testCalculatorDivisionByZero() {
        val res = CalculatorEngine.evaluate("42 / 0")
        assertTrue(res is CalculatorEngine.EvalResult.Error)
        assertEquals("Cannot divide by zero", (res as CalculatorEngine.EvalResult.Error).message)
    }

    @Test
    fun testUnitConverterLength() {
        val lengthUnits = UnitConverter.getUnitsForCategory(UnitCategory.LENGTH)
        val km = lengthUnits.first { it.id == "km" }
        val m = lengthUnits.first { it.id == "m" }

        val converted = UnitConverter.convert(5.0, km, m)
        assertEquals(5000.0, converted, 0.001)
    }

    @Test
    fun testUnitConverterTemperature() {
        val tempUnits = UnitConverter.getUnitsForCategory(UnitCategory.TEMPERATURE)
        val c = tempUnits.first { it.id == "c" }
        val f = tempUnits.first { it.id == "f" }
        val k = tempUnits.first { it.id == "k" }

        // 100°C should be 212°F
        val toF = UnitConverter.convert(100.0, c, f)
        assertEquals(212.0, toF, 0.001)

        // 0°C should be 273.15 K
        val toK = UnitConverter.convert(0.0, c, k)
        assertEquals(273.15, toK, 0.001)
    }

    @Test
    fun testUnitConverterMass() {
        val massUnits = UnitConverter.getUnitsForCategory(UnitCategory.MASS)
        val kg = massUnits.first { it.id == "kg" }
        val g = massUnits.first { it.id == "g" }

        val converted = UnitConverter.convert(2.5, kg, g)
        assertEquals(2500.0, converted, 0.001)
    }
}
