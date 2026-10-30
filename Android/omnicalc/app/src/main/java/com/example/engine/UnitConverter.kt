package com.example.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

data class UnitDefinition(
    val id: String,
    val name: String,
    val symbol: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
)

enum class UnitCategory(
    val id: String,
    val title: String,
    val description: String
) {
    LENGTH("length", "Length", "Meters, feet, miles, etc."),
    MASS("mass", "Weight & Mass", "Kilograms, pounds, ounces, etc."),
    TEMPERATURE("temperature", "Temperature", "Celsius, Fahrenheit, Kelvin"),
    AREA("area", "Area", "Acres, square meters, sq feet, etc."),
    VOLUME("volume", "Volume", "Liters, gallons, cups, mL, etc."),
    SPEED("speed", "Speed", "km/h, mph, m/s, knots"),
    DIGITAL("digital", "Digital Data", "Bytes, MB, GB, TB, etc."),
    TIME("time", "Time", "Seconds, minutes, hours, days, etc."),
    ENERGY("energy", "Energy", "Joules, calories, kWh, BTU"),
    PRESSURE("pressure", "Pressure", "Bar, PSI, Pascal, atm, mmHg"),
    FUEL("fuel", "Fuel Economy", "MPG, L/100km, km/L")
}

object UnitConverter {

    fun getUnitsForCategory(category: UnitCategory): List<UnitDefinition> {
        return when (category) {
            UnitCategory.LENGTH -> listOf(
                linearUnit("m", "Meter", "m", 1.0),
                linearUnit("km", "Kilometer", "km", 1000.0),
                linearUnit("cm", "Centimeter", "cm", 0.01),
                linearUnit("mm", "Millimeter", "mm", 0.001),
                linearUnit("um", "Micrometer", "µm", 1e-6),
                linearUnit("nm", "Nanometer", "nm", 1e-9),
                linearUnit("mi", "Mile", "mi", 1609.344),
                linearUnit("yd", "Yard", "yd", 0.9144),
                linearUnit("ft", "Foot", "ft", 0.3048),
                linearUnit("in", "Inch", "in", 0.0254),
                linearUnit("nmi", "Nautical Mile", "NM", 1852.0)
            )

            UnitCategory.MASS -> listOf(
                linearUnit("kg", "Kilogram", "kg", 1.0),
                linearUnit("g", "Gram", "g", 0.001),
                linearUnit("mg", "Milligram", "mg", 1e-6),
                linearUnit("t", "Metric Ton", "t", 1000.0),
                linearUnit("lb", "Pound", "lb", 0.45359237),
                linearUnit("oz", "Ounce", "oz", 0.028349523125),
                linearUnit("st", "Stone", "st", 6.35029318),
                linearUnit("ct", "Carat", "ct", 0.0002)
            )

            UnitCategory.TEMPERATURE -> listOf(
                UnitDefinition(
                    id = "c",
                    name = "Celsius",
                    symbol = "°C",
                    toBase = { it },
                    fromBase = { it }
                ),
                UnitDefinition(
                    id = "f",
                    name = "Fahrenheit",
                    symbol = "°F",
                    toBase = { (it - 32.0) * (5.0 / 9.0) },
                    fromBase = { it * (9.0 / 5.0) + 32.0 }
                ),
                UnitDefinition(
                    id = "k",
                    name = "Kelvin",
                    symbol = "K",
                    toBase = { it - 273.15 },
                    fromBase = { it + 273.15 }
                )
            )

            UnitCategory.AREA -> listOf(
                linearUnit("sq_m", "Square Meter", "m²", 1.0),
                linearUnit("sq_km", "Square Kilometer", "km²", 1e6),
                linearUnit("sq_cm", "Square Centimeter", "cm²", 1e-4),
                linearUnit("ha", "Hectare", "ha", 10000.0),
                linearUnit("ac", "Acre", "ac", 4046.8564224),
                linearUnit("sq_ft", "Square Foot", "ft²", 0.09290304),
                linearUnit("sq_in", "Square Inch", "in²", 0.00064516),
                linearUnit("sq_yd", "Square Yard", "yd²", 0.83612736),
                linearUnit("sq_mi", "Square Mile", "mi²", 2589988.110336)
            )

            UnitCategory.VOLUME -> listOf(
                linearUnit("l", "Liter", "L", 1.0),
                linearUnit("ml", "Milliliter", "mL", 0.001),
                linearUnit("cu_m", "Cubic Meter", "m³", 1000.0),
                linearUnit("cu_cm", "Cubic Centimeter", "cm³", 0.001),
                linearUnit("gal_us", "Gallon (US)", "gal", 3.785411784),
                linearUnit("qt_us", "Quart (US)", "qt", 0.946352946),
                linearUnit("pt_us", "Pint (US)", "pt", 0.473176473),
                linearUnit("cup_us", "Cup (US)", "cup", 0.2365882365),
                linearUnit("fl_oz_us", "Fluid Ounce (US)", "fl oz", 0.0295735295625),
                linearUnit("tbsp_us", "Tablespoon (US)", "tbsp", 0.01478676478125),
                linearUnit("tsp_us", "Teaspoon (US)", "tsp", 0.00492892159375),
                linearUnit("gal_uk", "Gallon (UK)", "imp gal", 4.54609)
            )

            UnitCategory.SPEED -> listOf(
                linearUnit("m_s", "Meter / second", "m/s", 1.0),
                linearUnit("km_h", "Kilometer / hour", "km/h", 1.0 / 3.6),
                linearUnit("mph", "Mile / hour", "mph", 0.44704),
                linearUnit("kn", "Knot", "kn", 0.514444),
                linearUnit("ft_s", "Foot / second", "ft/s", 0.3048)
            )

            UnitCategory.DIGITAL -> listOf(
                linearUnit("b", "Bit", "b", 0.125),
                linearUnit("B", "Byte", "B", 1.0),
                linearUnit("KB", "Kilobyte (KB)", "KB", 1000.0),
                linearUnit("MB", "Megabyte (MB)", "MB", 1e6),
                linearUnit("GB", "Gigabyte (GB)", "GB", 1e9),
                linearUnit("TB", "Terabyte (TB)", "TB", 1e12),
                linearUnit("PB", "Petabyte (PB)", "PB", 1e15),
                linearUnit("KiB", "Kibibyte (KiB)", "KiB", 1024.0),
                linearUnit("MiB", "Mebibyte (MiB)", "MiB", 1048576.0),
                linearUnit("GiB", "Gibibyte (GiB)", "GiB", 1073741824.0)
            )

            UnitCategory.TIME -> listOf(
                linearUnit("s", "Second", "s", 1.0),
                linearUnit("ms", "Millisecond", "ms", 0.001),
                linearUnit("us", "Microsecond", "µs", 1e-6),
                linearUnit("min", "Minute", "min", 60.0),
                linearUnit("h", "Hour", "h", 3600.0),
                linearUnit("d", "Day", "d", 86400.0),
                linearUnit("wk", "Week", "wk", 604800.0),
                linearUnit("yr", "Year (365d)", "yr", 31536000.0)
            )

            UnitCategory.ENERGY -> listOf(
                linearUnit("j", "Joule", "J", 1.0),
                linearUnit("kj", "Kilojoule", "kJ", 1000.0),
                linearUnit("cal", "Calorie", "cal", 4.184),
                linearUnit("kcal", "Kilocalorie", "kcal", 4184.0),
                linearUnit("wh", "Watt-hour", "Wh", 3600.0),
                linearUnit("kwh", "Kilowatt-hour", "kWh", 3.6e6),
                linearUnit("ev", "Electronvolt", "eV", 1.602176634e-19),
                linearUnit("btu", "BTU", "BTU", 1055.06)
            )

            UnitCategory.PRESSURE -> listOf(
                linearUnit("pa", "Pascal", "Pa", 1.0),
                linearUnit("kpa", "Kilopascal", "kPa", 1000.0),
                linearUnit("mpa", "Megapascal", "MPa", 1e6),
                linearUnit("bar", "Bar", "bar", 100000.0),
                linearUnit("mbar", "Millibar", "mbar", 100.0),
                linearUnit("psi", "Pound/sq inch", "psi", 6894.757293168),
                linearUnit("atm", "Atmosphere", "atm", 101325.0),
                linearUnit("torr", "Torr (mmHg)", "mmHg", 133.322)
            )

            UnitCategory.FUEL -> listOf(
                UnitDefinition(
                    id = "l_100km",
                    name = "L/100km",
                    symbol = "L/100km",
                    toBase = { it },
                    fromBase = { it }
                ),
                UnitDefinition(
                    id = "mpg_us",
                    name = "MPG (US)",
                    symbol = "mpg US",
                    toBase = { if (it > 0) 235.214583 / it else 0.0 },
                    fromBase = { if (it > 0) 235.214583 / it else 0.0 }
                ),
                UnitDefinition(
                    id = "mpg_imp",
                    name = "MPG (Imperial)",
                    symbol = "mpg Imp",
                    toBase = { if (it > 0) 282.481 / it else 0.0 },
                    fromBase = { if (it > 0) 282.481 / it else 0.0 }
                ),
                UnitDefinition(
                    id = "km_l",
                    name = "km/L",
                    symbol = "km/L",
                    toBase = { if (it > 0) 100.0 / it else 0.0 },
                    fromBase = { if (it > 0) 100.0 / it else 0.0 }
                )
            )
        }
    }

    private fun linearUnit(id: String, name: String, symbol: String, ratioToBase: Double): UnitDefinition {
        return UnitDefinition(
            id = id,
            name = name,
            symbol = symbol,
            toBase = { it * ratioToBase },
            fromBase = { it / ratioToBase }
        )
    }

    /**
     * Converts a value from one unit to another within the same category.
     */
    fun convert(value: Double, fromUnit: UnitDefinition, toUnit: UnitDefinition): Double {
        if (fromUnit.id == toUnit.id) return value
        val baseVal = fromUnit.toBase(value)
        return toUnit.fromBase(baseVal)
    }

    /**
     * Formats conversion numbers cleanly.
     */
    fun formatUnitValue(value: Double): String {
        if (value.isNaN()) return "0"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        if (abs(value) < 1e-12) return "0"

        return try {
            val bd = BigDecimal(value.toString())
            if (abs(value) >= 1e10 || (abs(value) < 1e-4 && abs(value) > 0.0)) {
                val symbols = DecimalFormatSymbols(Locale.US)
                val sciFormat = DecimalFormat("0.#####E0", symbols)
                sciFormat.format(value)
            } else {
                val rounded = bd.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                rounded.toPlainString()
            }
        } catch (e: Exception) {
            val symbols = DecimalFormatSymbols(Locale.US)
            val df = DecimalFormat("#.######", symbols)
            df.format(value)
        }
    }
}
