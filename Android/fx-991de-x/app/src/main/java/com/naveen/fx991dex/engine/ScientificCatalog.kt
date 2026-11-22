package com.naveen.fx991dex.engine

data class ScientificConstant(
    val code: String,
    val symbol: String,
    val nameDe: String,
    val value: Double,
    val unit: String,
    val category: String
)

data class UnitConversion(
    val id: String,
    val label: String,
    val category: String,
    val convert: (Double) -> Double
)

/**
 * Authentic German fx-991DE X ClassWiz CONST (SHIFT+7) and CONV (SHIFT+8) libraries.
 */
object ScientificCatalog {

    val constantCategories = listOf(
        "1: Universell",
        "2: Elektromagn.",
        "3: Atom & Kern",
        "4: Physik.-Chem.",
        "5: Übernommene"
    )

    val constants: List<ScientificConstant> = listOf(
        // 1: Universell
        ScientificConstant("h", "h", "Planck-Konstante", 6.62607015e-34, "J·s", "1: Universell"),
        ScientificConstant("ħ", "ħ", "Red. Planck-Konst.", 1.054571817e-34, "J·s", "1: Universell"),
        ScientificConstant("c₀", "c₀", "Lichtgeschwindigk.", 299792458.0, "m/s", "1: Universell"),
        ScientificConstant("ε₀", "ε₀", "Elektr. Feldkonst.", 8.8541878128e-12, "F/m", "1: Universell"),
        ScientificConstant("μ₀", "μ₀", "Magn. Feldkonst.", 1.25663706212e-6, "N/A²", "1: Universell"),
        ScientificConstant("Z₀", "Z₀", "Wellenwiderstand", 376.730313668, "Ω", "1: Universell"),
        ScientificConstant("G", "G", "Gravitationskonst.", 6.67430e-11, "m³/(kg·s²)", "1: Universell"),

        // 2: Elektromagnetisch
        ScientificConstant("e", "e₀", "Elementarladung", 1.602176634e-19, "C", "2: Elektromagn."),
        ScientificConstant("Φ₀", "Φ₀", "Magn. Flussquant", 2.067833848e-15, "Wb", "2: Elektromagn."),
        ScientificConstant("G₀", "G₀", "Leitwertquant", 7.748091729e-5, "S", "2: Elektromagn."),
        ScientificConstant("μB", "μB", "Bohr-Magneton", 9.2740100783e-24, "J/T", "2: Elektromagn."),
        ScientificConstant("μN", "μN", "Kernmagneton", 5.0507837461e-27, "J/T", "2: Elektromagn."),

        // 3: Atom & Kern
        ScientificConstant("mp", "mp", "Protonenmasse", 1.67262192369e-27, "kg", "3: Atom & Kern"),
        ScientificConstant("mn", "mn", "Neutronenmasse", 1.67492749804e-27, "kg", "3: Atom & Kern"),
        ScientificConstant("me", "me", "Elektronenmasse", 9.1093837015e-31, "kg", "3: Atom & Kern"),
        ScientificConstant("a₀", "a₀", "Bohr-Radius", 5.29177210903e-11, "m", "3: Atom & Kern"),
        ScientificConstant("R∞", "R∞", "Rydberg-Konstante", 1.097373156816e7, "1/m", "3: Atom & Kern"),
        ScientificConstant("α", "α", "Feinstrukturkonst.", 7.2973525693e-3, "", "3: Atom & Kern"),

        // 4: Physikalisch-Chemisch
        ScientificConstant("u", "u", "Atomare Masseneinh.", 1.66053906660e-27, "kg", "4: Physik.-Chem."),
        ScientificConstant("F", "F", "Faraday-Konstante", 96485.33212, "C/mol", "4: Physik.-Chem."),
        ScientificConstant("NA", "NA", "Avogadro-Konstante", 6.02214076e23, "1/mol", "4: Physik.-Chem."),
        ScientificConstant("k", "k", "Boltzmann-Konst.", 1.380649e-23, "J/K", "4: Physik.-Chem."),
        ScientificConstant("Vm", "Vm", "Molares Volumen", 0.02241396954, "m³/mol", "4: Physik.-Chem."),
        ScientificConstant("R", "R", "Univers. Gaskonst.", 8.314462618, "J/(mol·K)", "4: Physik.-Chem."),
        ScientificConstant("σ", "σ", "Stefan-Boltzmann", 5.670374419e-8, "W/(m²·K⁴)", "4: Physik.-Chem."),

        // 5: Übernommene
        ScientificConstant("g", "g", "Normfallbeschl.", 9.80665, "m/s²", "5: Übernommene"),
        ScientificConstant("atm", "atm", "Normdruck", 101325.0, "Pa", "5: Übernommene"),
        ScientificConstant("t", "t₀", "Celsius-Nullpunkt", 273.15, "K", "5: Übernommene")
    )

    val constantMap: Map<String, Double> = constants.associate { it.symbol to it.value }

    val conversionCategories = listOf(
        "1: Länge",
        "2: Fläche",
        "3: Volumen",
        "4: Masse",
        "5: Geschwindigk.",
        "6: Druck",
        "7: Energie",
        "8: Leistung",
        "9: Temperatur"
    )

    val conversions: List<UnitConversion> = listOf(
        // Länge
        UnitConversion("in_cm", "in ▸ cm", "1: Länge") { it * 2.54 },
        UnitConversion("cm_in", "cm ▸ in", "1: Länge") { it / 2.54 },
        UnitConversion("ft_m", "ft ▸ m", "1: Länge") { it * 0.3048 },
        UnitConversion("m_ft", "m ▸ ft", "1: Länge") { it / 0.3048 },
        UnitConversion("yd_m", "yd ▸ m", "1: Länge") { it * 0.9144 },
        UnitConversion("m_yd", "m ▸ yd", "1: Länge") { it / 0.9144 },
        UnitConversion("mile_km", "mile ▸ km", "1: Länge") { it * 1.609344 },
        UnitConversion("km_mile", "km ▸ mile", "1: Länge") { it / 1.609344 },
        UnitConversion("nmile_m", "n mile ▸ m", "1: Länge") { it * 1852.0 },
        UnitConversion("m_nmile", "m ▸ n mile", "1: Länge") { it / 1852.0 },

        // Fläche
        UnitConversion("acre_m2", "acre ▸ m²", "2: Fläche") { it * 4046.8564224 },
        UnitConversion("m2_acre", "m² ▸ acre", "2: Fläche") { it / 4046.8564224 },

        // Volumen
        UnitConversion("galUS_L", "gal(US) ▸ L", "3: Volumen") { it * 3.785411784 },
        UnitConversion("L_galUS", "L ▸ gal(US)", "3: Volumen") { it / 3.785411784 },
        UnitConversion("galUK_L", "gal(UK) ▸ L", "3: Volumen") { it * 4.54609 },
        UnitConversion("L_galUK", "L ▸ gal(UK)", "3: Volumen") { it / 4.54609 },

        // Masse
        UnitConversion("oz_g", "oz ▸ g", "4: Masse") { it * 28.349523125 },
        UnitConversion("g_oz", "g ▸ oz", "4: Masse") { it / 28.349523125 },
        UnitConversion("lb_kg", "lb ▸ kg", "4: Masse") { it * 0.45359237 },
        UnitConversion("kg_lb", "kg ▸ lb", "4: Masse") { it / 0.45359237 },

        // Geschwindigkeit
        UnitConversion("kmh_ms", "km/h ▸ m/s", "5: Geschwindigk.") { it / 3.6 },
        UnitConversion("ms_kmh", "m/s ▸ km/h", "5: Geschwindigk.") { it * 3.6 },

        // Druck
        UnitConversion("atm_Pa", "atm ▸ Pa", "6: Druck") { it * 101325.0 },
        UnitConversion("Pa_atm", "Pa ▸ atm", "6: Druck") { it / 101325.0 },
        UnitConversion("mmHg_Pa", "mmHg ▸ Pa", "6: Druck") { it * 133.3223684 },
        UnitConversion("Pa_mmHg", "Pa ▸ mmHg", "6: Druck") { it / 133.3223684 },
        UnitConversion("bar_Pa", "bar ▸ Pa", "6: Druck") { it * 100000.0 },
        UnitConversion("Pa_bar", "Pa ▸ bar", "6: Druck") { it / 100000.0 },

        // Energie
        UnitConversion("cal_J", "cal ▸ J", "7: Energie") { it * 4.1868 },
        UnitConversion("J_cal", "J ▸ cal", "7: Energie") { it / 4.1868 },
        UnitConversion("kWh_J", "kWh ▸ J", "7: Energie") { it * 3.6e6 },
        UnitConversion("J_kWh", "J ▸ kWh", "7: Energie") { it / 3.6e6 },

        // Leistung
        UnitConversion("PS_kW", "PS ▸ kW", "8: Leistung") { it * 0.73549875 },
        UnitConversion("kW_PS", "kW ▸ PS", "8: Leistung") { it / 0.73549875 },
        UnitConversion("hp_kW", "hp ▸ kW", "8: Leistung") { it * 0.745699872 },
        UnitConversion("kW_hp", "kW ▸ hp", "8: Leistung") { it / 0.745699872 },

        // Temperatur
        UnitConversion("F_C", "°F ▸ °C", "9: Temperatur") { (it - 32.0) * 5.0 / 9.0 },
        UnitConversion("C_F", "°C ▸ °F", "9: Temperatur") { it * 9.0 / 5.0 + 32.0 }
    )
}
