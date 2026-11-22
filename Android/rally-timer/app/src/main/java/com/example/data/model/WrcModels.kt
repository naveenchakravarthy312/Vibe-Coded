package com.example.data.model

data class Rally(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val surface: String,
    val stages: List<StagePreset>
)

data class StagePreset(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val surfaceDetail: String,
    val isEpicStage: Boolean = false,
    val isSuperSpecialStage: Boolean = false
)

object RallyPresets {
    val RALLIES = listOf(
        Rally(
            id = "monte_carlo",
            name = "Rallye Monte-Carlo",
            country = "Monaco",
            flag = "🇲🇨",
            surface = "Tarmac / Snow / Ice",
            stages = listOf(
                StagePreset("mc_1", "Agnières en Devoluy - Le Motty", 10.3, "Snow & Mountain Passes"),
                StagePreset("mc_2", "La Bollène-Vésubie - Peïra Cava", 18.4, "Col de Turini Mountain Pass", isEpicStage = true),
                StagePreset("mc_3", "Aspres les Corps - Chaillol", 8.8, "Technical Mountain Descent"),
                StagePreset("mc_4", "Luceram - Col St Roch", 5.6, "Dry / Icy Tarmac", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "sweden",
            name = "Rally Sweden",
            country = "Sweden",
            flag = "🇸🇪",
            surface = "Snow & Frozen Gravel",
            stages = listOf(
                StagePreset("swe_1", "Torsby", 8.4, "High Speed Snow Banks"),
                StagePreset("swe_2", "Vargåsen", 11.2, "Colin's Crest Big Jumps"),
                StagePreset("swe_3", "Knon", 14.1, "Fast Forest Glades", isEpicStage = true),
                StagePreset("swe_4", "Karlstad", 3.8, "Super Special Snow Arena", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "mexico",
            name = "Rally Guanajuato México",
            country = "Mexico",
            flag = "🇲🇽",
            surface = "High Altitude Rough Gravel",
            stages = listOf(
                StagePreset("mex_1", "Media Luna", 11.5, "Rocky Mountain Trails"),
                StagePreset("mex_2", "Ibarrilla", 6.8, "Watersplash & Technical Dust"),
                StagePreset("mex_3", "El Chocolate", 18.2, "Oxygen-Deprived Mountain Pass", isEpicStage = true),
                StagePreset("mex_4", "Autódromo de León", 2.3, "Twin Track Autodrome Arena", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "corsica",
            name = "Tour de Corse",
            country = "France",
            flag = "🇫🇷",
            surface = "Mountain Tarmac",
            stages = listOf(
                StagePreset("cor_1", "Pietrosella - Albitreccia", 7.9, "Cliffside Twisty Asphalt"),
                StagePreset("cor_2", "Novella", 10.4, "Narrow Mountain Ribbon"),
                StagePreset("cor_3", "La Porta - Valle di Rostino", 11.8, "Tight Hairpin Sequences"),
                StagePreset("cor_4", "Antisanti - Poggio di Nazza", 19.4, "Non-stop Technical Asphalt", isEpicStage = true)
            )
        ),
        Rally(
            id = "argentina",
            name = "YPF Rally Argentina",
            country = "Argentina",
            flag = "🇦🇷",
            surface = "Rough Gravel & Boulders",
            stages = listOf(
                StagePreset("arg_1", "Amboy - Santa Monica", 9.1, "Fast Flowing Dirt Tracks"),
                StagePreset("arg_2", "El Condor - Copina", 10.6, "Lunar Landscape & Hanging Bridges"),
                StagePreset("arg_3", "Los Gigantes - Cantera El Condor", 17.8, "Brutal High Rocky Plateau", isEpicStage = true),
                StagePreset("arg_4", "Parque Tematico", 3.4, "Head-to-Head Theme Park Arena", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "portugal",
            name = "Vodafone Rally de Portugal",
            country = "Portugal",
            flag = "🇵🇹",
            surface = "Sandy & Rocky Gravel",
            stages = listOf(
                StagePreset("por_1", "Viana do Castelo", 11.1, "Fast Coastline Hills"),
                StagePreset("por_2", "Fafe", 14.5, "Pedra Sentada Jump & Ruts"),
                StagePreset("por_3", "Amarante", 18.0, "Marathon Ridge Crests", isEpicStage = true),
                StagePreset("por_4", "Lousada", 3.3, "Rallycross Twin Stadium Track", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "sardinia",
            name = "Rally Italia Sardegna",
            country = "Italy",
            flag = "🇮🇹",
            surface = "Narrow Dust Gravel",
            stages = listOf(
                StagePreset("ita_1", "Olmedo Monte Baranta", 8.2, "Coastline Dust Path"),
                StagePreset("ita_2", "Monte Lerno", 16.8, "Micky's Jump & Rough Mountain", isEpicStage = true),
                StagePreset("ita_3", "Monti di Ala", 12.0, "Narrow Forest Sweep"),
                StagePreset("ita_4", "Ittiri Arena", 2.0, "Arena Special Stage", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "poland",
            name = "ORLEN 74th Rally Poland",
            country = "Poland",
            flag = "🇵🇱",
            surface = "Fast Soft Sand & Grass",
            stages = listOf(
                StagePreset("pol_1", "Chmielewo", 9.5, "High-Speed Open Fields"),
                StagePreset("pol_2", "Baranowo", 12.4, "Sandy Ruts & Fast Sweepers"),
                StagePreset("pol_3", "Kruklanki", 23.0, "Longest Endurance Stage in Game", isEpicStage = true),
                StagePreset("pol_4", "Mikolajki Arena", 2.5, "Twin Track Stadium Sprint", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "finland",
            name = "Neste Rally Finland",
            country = "Finland",
            flag = "🇫🇮",
            surface = "Hard Gravel & Big Crests",
            stages = listOf(
                StagePreset("fin_1", "Ouninpohja", 15.3, "Rollercoaster Forest Crests", isEpicStage = true),
                StagePreset("fin_2", "Horkka", 12.1, "Blind Crests & High Speed Jumps"),
                StagePreset("fin_3", "Päijälä", 8.9, "Narrow Pine Forest Slalom"),
                StagePreset("fin_4", "Harju", 2.3, "Asphalt & City Park Super Special", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "germany",
            name = "ADAC Rallye Deutschland",
            country = "Germany",
            flag = "🇩🇪",
            surface = "Vineyards & Military Concrete",
            stages = listOf(
                StagePreset("ger_1", "Freisen-Westrich", 9.8, "Fast Country Asphalt & Fields"),
                StagePreset("ger_2", "Grafschaft", 16.9, "Moselle Vineyards Hairpins", isEpicStage = true),
                StagePreset("ger_3", "Mittelmosel", 7.4, "Tight Slopes & Vineyard Walls"),
                StagePreset("ger_4", "Arena Panzerplatte", 2.8, "Concrete Military Tank Sprints", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "spain",
            name = "RallyRACC Catalunya-Costa Daurada",
            country = "Spain",
            flag = "🇪🇸",
            surface = "Smooth Circuit Tarmac & Gravel",
            stages = listOf(
                StagePreset("esp_1", "Bot", 10.2, "Mixed Surface Country Ribbon"),
                StagePreset("esp_2", "La Figuera", 14.1, "Racetrack Quality Asphalt Curves"),
                StagePreset("esp_3", "El Montmell", 18.5, "Continuous High Speed Sweepers", isEpicStage = true),
                StagePreset("esp_4", "Barcelona", 3.2, "Montjuïc Urban Super Special", isSuperSpecialStage = true)
            )
        ),
        Rally(
            id = "wales",
            name = "Dayinsure Wales Rally GB",
            country = "United Kingdom",
            flag = "🇬🇧",
            surface = "Forest Mud, Slate & Rain",
            stages = listOf(
                StagePreset("gb_1", "Hafren", 13.8, "Slick Mud Logging Trails"),
                StagePreset("gb_2", "Great Orme", 6.2, "Coastal Asphalt Road Ribbon"),
                StagePreset("gb_3", "Hafren 2", 11.4, "Dense Pine Forest Sprints"),
                StagePreset("gb_4", "Dyfi", 17.6, "Low Grip Wet Slate Marathon", isEpicStage = true)
            )
        ),
        Rally(
            id = "australia",
            name = "Kennards Hire Rally Australia",
            country = "Australia",
            flag = "🇦🇺",
            surface = "Hard Packed Ball-Bearing Gravel",
            stages = listOf(
                StagePreset("aus_1", "Baker's Creek", 9.4, "Fast Grazing Plains"),
                StagePreset("aus_2", "Nambucca", 19.8, "Stunning Ridge Lines Marathon", isEpicStage = true),
                StagePreset("aus_3", "Valla 16", 12.3, "Dense Eucalyptus Forest"),
                StagePreset("aus_4", "Coffs", 1.8, "Jetty Super Special Stage", isSuperSpecialStage = true)
            )
        )
    )

    val WEATHER_OPTIONS = listOf(
        "Clear / Sunny" to "☀️",
        "Overcast / Cloudy" to "⛅",
        "Fog / Mist" to "🌫️",
        "Rain" to "🌧️",
        "Heavy Rain" to "⛈️",
        "Snow / Ice" to "❄️",
        "Dusk / Sunset" to "🌅",
        "Night" to "🌙"
    )

    val CAMERA_OPTIONS = listOf(
        "Chase Far" to "📹",
        "Chase Close" to "🏎️",
        "Bonnet / Hood" to "🏁",
        "Bumper" to "⚡",
        "Cockpit / Dash" to "💺",
        "Roof" to "🚁"
    )

    val CAR_CLASSES = listOf(
        "WRC (Premier)" to listOf(
            "Ford Fiesta WRC",
            "Hyundai i20 Coupe WRC",
            "Toyota Yaris WRC",
            "Citroën C3 WRC"
        ),
        "WRC 2 (R5)" to listOf(
            "Škoda Fabia R5",
            "Ford Fiesta R5",
            "Peugeot 208 T16 R5"
        ),
        "Junior WRC" to listOf(
            "Ford Fiesta R2"
        ),
        "Custom / Historic" to listOf(
            "Classic 4WD Turbo",
            "Rally Spec 4WD"
        )
    )

    val TRANSMISSIONS = listOf("Sequential", "Manual H-Pattern", "Automatic")
}

// Backward-compatibility alias
typealias WrcPresets = RallyPresets

object TimeFormatter {
    fun formatMillis(millis: Long): String {
        if (millis <= 0) return "00:00.000"
        val minutes = millis / 60000
        val remainingMillis = millis % 60000
        val seconds = remainingMillis / 1000
        val ms = remainingMillis % 1000
        return "%02d:%02d.%03d".format(minutes, seconds, ms)
    }

    fun parseToMillis(minutes: Int, seconds: Int, millis: Int): Long {
        return (minutes.coerceAtLeast(0) * 60000L) +
                (seconds.coerceIn(0, 59) * 1000L) +
                millis.coerceIn(0, 999)
    }

    fun formatDelta(deltaMillis: Long): String {
        if (deltaMillis == 0L) return "+00.000s"
        val sign = if (deltaMillis > 0) "+" else "-"
        val absMillis = Math.abs(deltaMillis)
        val minutes = absMillis / 60000
        val remMillis = absMillis % 60000
        val seconds = remMillis / 1000
        val ms = remMillis % 1000
        return if (minutes > 0) {
            "$sign%02d:%02d.%03d".format(minutes, seconds, ms)
        } else {
            "$sign%02d.%03ds".format(seconds, ms)
        }
    }
}
