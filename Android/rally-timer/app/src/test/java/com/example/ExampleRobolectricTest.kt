package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rally Timer", appName)
    }

    @Test
    fun `time formatter correctly parses and formats rally times`() {
        val millis = TimeFormatter.parseToMillis(3, 42, 854)
        val formatted = TimeFormatter.formatMillis(millis)
        assertEquals("03:42.854", formatted)
    }

    @Test
    fun `time formatter formats delta times with sign and seconds`() {
        val deltaPositive = TimeFormatter.formatDelta(1250)
        assertEquals("+01.250s", deltaPositive)

        val deltaNegative = TimeFormatter.formatDelta(-530)
        assertEquals("-00.530s", deltaNegative)

        val deltaZero = TimeFormatter.formatDelta(0L)
        assertEquals("+00.000s", deltaZero)

        val deltaOverMinute = TimeFormatter.formatDelta(65000L)
        assertEquals("+01:05.000", deltaOverMinute)

        val deltaNegativeMinute = TimeFormatter.formatDelta(-75200L)
        assertEquals("-01:15.200", deltaNegativeMinute)
    }

    @Test
    fun `theme modes include system, light, dark, and amoled`() {
        val modes = com.example.ui.theme.AppThemeMode.entries.map { it.name }
        assertEquals(listOf("SYSTEM", "LIGHT", "DARK", "AMOLED"), modes)
    }

    @Test
    fun `wrc 7 stage list contains exactly 13 locations and 52 total stages`() {
        val rallies = com.example.data.model.RallyPresets.RALLIES
        assertEquals(13, rallies.size)
        val totalStages = rallies.sumOf { it.stages.size }
        assertEquals(52, totalStages)

        // Verify Monte Carlo stages
        val mc = rallies.first { it.id == "monte_carlo" }
        assertEquals(
            listOf(
                "Agnières en Devoluy - Le Motty",
                "La Bollène-Vésubie - Peïra Cava",
                "Aspres les Corps - Chaillol",
                "Luceram - Col St Roch"
            ),
            mc.stages.map { it.name }
        )

        // Verify Sweden stages
        val swe = rallies.first { it.id == "sweden" }
        assertEquals(
            listOf("Torsby", "Vargåsen", "Knon", "Karlstad"),
            swe.stages.map { it.name }
        )

        // Verify Mexico stages
        val mex = rallies.first { it.id == "mexico" }
        assertEquals(
            listOf("Media Luna", "Ibarrilla", "El Chocolate", "Autódromo de León"),
            mex.stages.map { it.name }
        )

        // Verify Tour de Corse stages
        val cor = rallies.first { it.id == "corsica" }
        assertEquals(
            listOf(
                "Pietrosella - Albitreccia",
                "Novella",
                "La Porta - Valle di Rostino",
                "Antisanti - Poggio di Nazza"
            ),
            cor.stages.map { it.name }
        )

        // Verify Argentina stages
        val arg = rallies.first { it.id == "argentina" }
        assertEquals(
            listOf(
                "Amboy - Santa Monica",
                "El Condor - Copina",
                "Los Gigantes - Cantera El Condor",
                "Parque Tematico"
            ),
            arg.stages.map { it.name }
        )

        // Verify Portugal stages
        val por = rallies.first { it.id == "portugal" }
        assertEquals(
            listOf("Viana do Castelo", "Fafe", "Amarante", "Lousada"),
            por.stages.map { it.name }
        )

        // Verify Sardegna stages
        val ita = rallies.first { it.id == "sardinia" }
        assertEquals(
            listOf("Olmedo Monte Baranta", "Monte Lerno", "Monti di Ala", "Ittiri Arena"),
            ita.stages.map { it.name }
        )

        // Verify Poland stages
        val pol = rallies.first { it.id == "poland" }
        assertEquals(
            listOf("Chmielewo", "Baranowo", "Kruklanki", "Mikolajki Arena"),
            pol.stages.map { it.name }
        )

        // Verify Finland stages
        val fin = rallies.first { it.id == "finland" }
        assertEquals(
            listOf("Ouninpohja", "Horkka", "Päijälä", "Harju"),
            fin.stages.map { it.name }
        )

        // Verify Germany stages
        val ger = rallies.first { it.id == "germany" }
        assertEquals(
            listOf("Freisen-Westrich", "Grafschaft", "Mittelmosel", "Arena Panzerplatte"),
            ger.stages.map { it.name }
        )

        // Verify Spain stages
        val esp = rallies.first { it.id == "spain" }
        assertEquals(
            listOf("Bot", "La Figuera", "El Montmell", "Barcelona"),
            esp.stages.map { it.name }
        )

        // Verify Wales stages
        val gb = rallies.first { it.id == "wales" }
        assertEquals(
            listOf("Hafren", "Great Orme", "Hafren 2", "Dyfi"),
            gb.stages.map { it.name }
        )

        // Verify Australia stages
        val aus = rallies.first { it.id == "australia" }
        assertEquals(
            listOf("Baker's Creek", "Nambucca", "Valla 16", "Coffs"),
            aus.stages.map { it.name }
        )

        // Verify every rally has an Epic Stage
        rallies.forEach { rally ->
            val epicStages = rally.stages.filter { it.isEpicStage }
            assertEquals("Rally ${rally.name} should have 1 Epic Stage", 1, epicStages.size)
        }
    }

    @Test
    fun `stage session config correctly initializes with default active rally and stage`() {
        val config = com.example.data.preferences.StageSessionConfig()
        assertEquals("monte_carlo", config.rallyId)
        assertEquals("Agnières en Devoluy - Le Motty", config.stageName)
        assertEquals("🇲🇨", config.flag)
        assertEquals(true, config.stickyEnabled)
    }

    @Test
    fun `stage session correctly identifies epic and super special stages`() {
        val epicConfig = com.example.data.preferences.StageSessionConfig(
            rallyId = "poland",
            stageName = "Kruklanki"
        )
        assertEquals(true, epicConfig.isEpicStage)
        assertEquals(false, epicConfig.isSuperSpecialStage)

        val sssConfig = com.example.data.preferences.StageSessionConfig(
            rallyId = "poland",
            stageName = "Mikolajki Arena"
        )
        assertEquals(false, sssConfig.isEpicStage)
        assertEquals(true, sssConfig.isSuperSpecialStage)
    }
}

