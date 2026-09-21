package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TuningTest {

    @Before
    fun setUp() {
        Tuning.resetAll()
    }

    @org.junit.After
    fun tearDown() {
        Tuning.resetAll()
    }

    // 1. Every parameter's default in Tuning equals the constant it replaced. Assert per parameter.
    @Test
    fun testWordScaleStartDefaultEqualsHardcodedConstant() {
        assertEquals(0.95f, Tuning.WORD_SCALE_START.defaultValue, 0.0001f)
        assertEquals(0.95f, Tuning.wordScaleStart, 0.0001f)
    }

    @Test
    fun testWordScalePeakDefaultEqualsHardcodedConstant() {
        assertEquals(1.00f, Tuning.WORD_SCALE_PEAK.defaultValue, 0.0001f)
        assertEquals(1.00f, Tuning.wordScalePeak, 0.0001f)
    }

    @Test
    fun testWordScalePeakPositionDefaultEqualsHardcodedConstant() {
        assertEquals(0.60f, Tuning.WORD_SCALE_PEAK_POS.defaultValue, 0.0001f)
        assertEquals(0.60f, Tuning.wordScalePeakPosition, 0.0001f)
    }

    @Test
    fun testWordLiftPeakFractionDefaultEqualsHardcodedConstant() {
        assertEquals(0.05f, Tuning.WORD_LIFT_PEAK_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.05f, Tuning.wordLiftPeakFraction, 0.0001f)
    }

    @Test
    fun testWordLiftPeakPositionDefaultEqualsHardcodedConstant() {
        assertEquals(0.58f, Tuning.WORD_LIFT_PEAK_POS.defaultValue, 0.0001f)
        assertEquals(0.58f, Tuning.wordLiftPeakPosition, 0.0001f)
    }

    @Test
    fun testWordGlowRiseEndDefaultEqualsHardcodedConstant() {
        assertEquals(0.20f, Tuning.WORD_GLOW_RISE_END.defaultValue, 0.0001f)
        assertEquals(0.20f, Tuning.wordGlowRiseEnd, 0.0001f)
    }

    @Test
    fun testWordGlowHoldEndDefaultEqualsHardcodedConstant() {
        assertEquals(0.55f, Tuning.WORD_GLOW_HOLD_END.defaultValue, 0.0001f)
        assertEquals(0.55f, Tuning.wordGlowHoldEnd, 0.0001f)
    }

    @Test
    fun testWordGlowAlphaMultiplierDefaultEqualsHardcodedConstant() {
        assertEquals(0f, Tuning.WORD_GLOW_ALPHA_MULT.defaultValue, 0.0001f)
        assertEquals(0f, Tuning.wordGlowAlphaMultiplier, 0.0001f)
    }

    @Test
    fun testHeldWordThresholdMsDefaultEqualsHardcodedConstant() {
        assertEquals(575f, Tuning.HELD_WORD_MIN_DURATION_MS.defaultValue, 0.0001f)
        assertEquals(575L, Tuning.heldWordMinDurationMs)
    }

    @Test
    fun testHeldWordLetterScalePeakDefaultEqualsHardcodedConstant() {
        assertEquals(1.04f, Tuning.HELD_WORD_LETTER_SCALE_PEAK.defaultValue, 0.0001f)
        assertEquals(1.04f, Tuning.heldWordLetterScalePeak, 0.0001f)
    }

    @Test
    fun testLetterFalloffPowerDefaultEqualsHardcodedConstant() {
        assertEquals(3f, Tuning.LETTER_FALLOFF_POWER.defaultValue, 0.0001f)
        assertEquals(3f, Tuning.letterFalloffPower, 0.0001f)
    }

    @Test
    fun testGlowBlurRadiusFractionDefaultEqualsHardcodedConstant() {
        assertEquals(0.10f, Tuning.GLOW_BLUR_RADIUS_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.10f, Tuning.glowBlurRadiusFraction, 0.0001f)
    }

    // 2. Every parameter's default lies inside its own declared minimum and maximum.
    @Test
    fun testEveryParameterDefaultLiesInsideMinMaxBounds() {
        for (param in Tuning.allParams) {
            assertTrue(
                "Parameter ${param.key} default ${param.defaultValue} must be >= min ${param.min}",
                param.defaultValue >= param.min
            )
            assertTrue(
                "Parameter ${param.key} default ${param.defaultValue} must be <= max ${param.max}",
                param.defaultValue <= param.max
            )
            assertTrue(
                "Parameter ${param.key} min ${param.min} must be strictly less than max ${param.max}",
                param.min < param.max
            )
        }
    }

    // 4. The clipboard dump carries only modified values and round-trips.
    @Test
    fun testClipboardDumpCarriesOnlyModifiedValuesAndRoundTrips() {
        val defaultExport = Tuning.exportKotlin()
        assertTrue("A dump with nothing changed must say so", defaultExport.contains("No values changed"))
        assertEquals(0, Tuning.parseKotlinExport(defaultExport).size)

        Tuning.WORD_SCALE_PEAK.value = 1.12f
        Tuning.HELD_WORD_MIN_DURATION_MS.value = 1250f
        val modifiedExport = Tuning.exportKotlin()

        val parsed = Tuning.parseKotlinExport(modifiedExport)
        assertEquals("Only the two changed values belong in the dump", 2, parsed.size)
        assertEquals(1.12f, parsed["wordScalePeak"] ?: Float.NaN, 0.0001f)
        assertEquals(1250f, parsed["heldWordMinDurationMs"] ?: Float.NaN, 0.0001f)
        assertTrue("The default belongs in the comment", modifiedExport.contains("// default 1.000f"))

        for (param in Tuning.allParams.filter { !it.isModified }) {
            assertFalse(
                "Unmodified ${param.key} must not appear",
                modifiedExport.contains("val ${param.key}:")
            )
        }
    }

    // 5. Reset restores defaults for every parameter in a group.
    @Test
    fun testResetGroupRestoresDefaultsForEveryParameterInGroup() {
        Tuning.WORD_SCALE_START.value = 0.85f
        Tuning.WORD_SCALE_PEAK.value = 1.25f
        Tuning.HELD_WORD_MIN_DURATION_MS.value = 500f

        assertTrue(Tuning.WORD_SCALE_START.isModified)
        assertTrue(Tuning.WORD_SCALE_PEAK.isModified)
        assertTrue(Tuning.HELD_WORD_MIN_DURATION_MS.isModified)

        Tuning.resetGroup(Tuning.GROUP_WORD_MOTION)

        for (param in Tuning.allParams.filter { it.group == Tuning.GROUP_WORD_MOTION }) {
            assertEquals("Param ${param.key} must be restored to default", param.defaultValue, param.value, 0.0001f)
            assertFalse("Param ${param.key} should not be modified after reset", param.isModified)
        }
    }

    // 6. Slider position to value mapping is correct at both endpoints and a midpoint.
    @Test
    fun testSliderMappingStandardNonInverted() {
        val min = 0.80f
        val max = 1.20f

        val valAt0 = Tuning.progressToValue(0, min, max, inverted = false)
        assertEquals(0.80f, valAt0, 0.0001f)

        val valAt1000 = Tuning.progressToValue(1000, min, max, inverted = false)
        assertEquals(1.20f, valAt1000, 0.0001f)

        val valAt500 = Tuning.progressToValue(500, min, max, inverted = false)
        assertEquals(1.00f, valAt500, 0.0001f)

        assertEquals(0, Tuning.valueToProgress(0.80f, min, max, inverted = false))
        assertEquals(1000, Tuning.valueToProgress(1.20f, min, max, inverted = false))
        assertEquals(500, Tuning.valueToProgress(1.00f, min, max, inverted = false))
    }

    @Test
    fun testSliderMappingInverted() {
        val min = 10f
        val max = 100f

        val valAt0 = Tuning.progressToValue(0, min, max, inverted = true)
        assertEquals(100f, valAt0, 0.0001f)

        val valAt1000 = Tuning.progressToValue(1000, min, max, inverted = true)
        assertEquals(10f, valAt1000, 0.0001f)

        val valAt500 = Tuning.progressToValue(500, min, max, inverted = true)
        assertEquals(55f, valAt500, 0.0001f)

        assertEquals(0, Tuning.valueToProgress(100f, min, max, inverted = true))
        assertEquals(1000, Tuning.valueToProgress(10f, min, max, inverted = true))
        assertEquals(500, Tuning.valueToProgress(55f, min, max, inverted = true))
    }

    @Test
    fun testSliderMappingInteger() {
        val min = 200f
        val max = 2000f

        val valAt0 = Tuning.progressToValue(0, min, max, isInteger = true)
        assertEquals(200f, valAt0, 0.0001f)

        val valAt1000 = Tuning.progressToValue(1000, min, max, isInteger = true)
        assertEquals(2000f, valAt1000, 0.0001f)

        val valAt500 = Tuning.progressToValue(500, min, max, isInteger = true)
        assertEquals(1100f, valAt500, 0.0001f)
    }

    // Wave B tests: Background colour parameters
    @Test
    fun testChromaExponentDefaultEqualsHardcodedConstant() {
        assertEquals(0.30f, Tuning.CHROMA_EXPONENT.defaultValue, 0.0001f)
        assertEquals(0.30f, Tuning.chromaExponent, 0.0001f)
        assertEquals("bg_saturation", Tuning.CHROMA_EXPONENT.key)
        assertTrue(Tuning.CHROMA_EXPONENT.inverted)
    }

    @Test
    fun testGamutCapFractionDefaultEqualsHardcodedConstant() {
        assertEquals(0.98f, Tuning.GAMUT_CAP_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.98f, Tuning.gamutCapFraction, 0.0001f)
    }

    @Test
    fun testLightnessCapKneeDefaultEqualsHardcodedConstant() {
        assertEquals(0.62f, Tuning.LIGHTNESS_CAP_KNEE.defaultValue, 0.0001f)
        assertEquals(0.62f, Tuning.lightnessCapKnee, 0.0001f)
    }

    @Test
    fun testLightnessCapCeilingDefaultEqualsHardcodedConstant() {
        assertEquals(0.76f, Tuning.LIGHTNESS_CAP_CEILING.defaultValue, 0.0001f)
        assertEquals(0.76f, Tuning.lightnessCapCeiling, 0.0001f)
    }

    @Test
    fun testLightnessCapStrengthDefaultEqualsHardcodedConstant() {
        assertEquals(0.50f, Tuning.LIGHTNESS_CAP_STRENGTH.defaultValue, 0.0001f)
        assertEquals(0.50f, Tuning.lightnessCapStrength, 0.0001f)
    }

    @Test
    fun testShaderDitherAmplitudeDefaultEqualsHardcodedConstant() {
        assertEquals(0.0118f, Tuning.SHADER_DITHER_AMPLITUDE.defaultValue, 0.0001f)
        assertEquals(0.0118f, Tuning.shaderDitherAmplitude, 0.0001f)
    }

    @Test
    fun testLinearBoostDefaultEqualsHardcodedConstant() {
        assertEquals(4.5f, Tuning.LINEAR_BOOST.defaultValue, 0.0001f)
        assertEquals(4.5f, Tuning.linearBoost, 0.0001f)
    }

    @Test
    fun testBackgroundDepthDefaultEqualsHardcodedConstant() {
        assertEquals(0.23f, Tuning.BACKGROUND_DEPTH.defaultValue, 0.0001f)
        assertEquals(0.23f, Tuning.backgroundDepth, 0.0001f)
    }

    @Test
    fun testDepthGateLowDefaultEqualsHardcodedConstant() {
        assertEquals(0.55f, Tuning.DEPTH_GATE_LOW.defaultValue, 0.0001f)
        assertEquals(0.55f, Tuning.depthGateLow, 0.0001f)
    }

    @Test
    fun testDepthGateHighDefaultEqualsHardcodedConstant() {
        assertEquals(0.85f, Tuning.DEPTH_GATE_HIGH.defaultValue, 0.0001f)
        assertEquals(0.85f, Tuning.depthGateHigh, 0.0001f)
    }

    // Wave B tests: Shader and background motion parameters
    @Test
    fun testVignetteStrengthDefaultEqualsHardcodedConstant() {
        assertEquals(0.30f, Tuning.VIGNETTE_STRENGTH.defaultValue, 0.0001f)
        assertEquals(0.30f, Tuning.vignetteStrength, 0.0001f)
    }

    @Test
    fun testShaderIntensityDefaultEqualsHardcodedConstant() {
        assertEquals(1.0f, Tuning.SHADER_INTENSITY.defaultValue, 0.0001f)
        assertEquals(1.0f, Tuning.shaderIntensity, 0.0001f)
    }

    @Test
    fun testEdgeFalloffBandDefaultEqualsHardcodedConstant() {
        assertEquals(0.22f, Tuning.EDGE_FALLOFF_BAND.defaultValue, 0.0001f)
        assertEquals(0.22f, Tuning.edgeFalloffBand, 0.0001f)
    }

    @Test
    fun testCrossfadeRateDefaultEqualsHardcodedConstant() {
        assertEquals(1.0f, Tuning.CROSSFADE_RATE.defaultValue, 0.0001f)
        assertEquals(1.0f, Tuning.crossfadeRate, 0.0001f)
    }

    // Wave B tests: Lyric timing windows parameters
    @Test
    fun testWordOverlapMsDefaultEqualsHardcodedConstant() {
        assertEquals(50f, Tuning.WORD_OVERLAP_MS.defaultValue, 0.0001f)
        assertEquals(50L, Tuning.wordOverlapMs)
    }

    @Test
    fun testWordMinAnimationMsDefaultEqualsHardcodedConstant() {
        assertEquals(200f, Tuning.WORD_MIN_ANIMATION_MS.defaultValue, 0.0001f)
        assertEquals(200L, Tuning.wordMinAnimationMs)
    }

    @Test
    fun testWordMotionTrailFractionDefaultEqualsHardcodedConstant() {
        assertEquals(0.35f, Tuning.WORD_MOTION_TRAIL_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.35f, Tuning.wordMotionTrailFraction, 0.0001f)
    }

    @Test
    fun testWordMotionTrailMinMsDefaultEqualsHardcodedConstant() {
        assertEquals(120f, Tuning.WORD_MOTION_TRAIL_MIN_MS.defaultValue, 0.0001f)
        assertEquals(120L, Tuning.wordMotionTrailMinMs)
    }

    @Test
    fun testWordMotionTrailMaxMsDefaultEqualsHardcodedConstant() {
        assertEquals(250f, Tuning.WORD_MOTION_TRAIL_MAX_MS.defaultValue, 0.0001f)
        assertEquals(250L, Tuning.wordMotionTrailMaxMs)
    }

    @Test
    fun testPreRollMaxLiftDefaultEqualsHardcodedConstant() {
        assertEquals(50f, Tuning.PRE_ROLL_MAX_LIFT.defaultValue, 0.0001f)
        assertEquals(50, Tuning.preRollMaxLift)
    }

    @Test
    fun testPreRollSettleMsDefaultEqualsHardcodedConstant() {
        assertEquals(150f, Tuning.PRE_ROLL_SETTLE_MS.defaultValue, 0.0001f)
        assertEquals(150L, Tuning.preRollSettleMs)
    }

    @Test
    fun testBaseGlideMsDefaultEqualsHardcodedConstant() {
        assertEquals(200f, Tuning.BASE_GLIDE_MS.defaultValue, 0.0001f)
        assertEquals(200f, Tuning.baseGlideMs, 0.0001f)
    }

    @Test
    fun testReferenceDistancePxDefaultEqualsHardcodedConstant() {
        assertEquals(158f, Tuning.REFERENCE_DISTANCE_PX.defaultValue, 0.0001f)
        assertEquals(158f, Tuning.referenceDistancePx, 0.0001f)
    }

    @Test
    fun testAllGroupsPresent() {
        assertEquals(5, Tuning.groups.size)
        assertTrue(Tuning.groups.contains(Tuning.GROUP_WORD_MOTION))
        assertTrue(Tuning.groups.contains(Tuning.GROUP_BACKGROUND_COLOUR))
        assertTrue(Tuning.groups.contains(Tuning.GROUP_SHADER_MOTION))
        assertTrue(Tuning.groups.contains(Tuning.GROUP_LYRIC_TIMING))
        assertTrue(Tuning.groups.contains(Tuning.GROUP_INSTRUMENTAL_DOTS))
    }

    @Test
    fun testResetGroupForEveryWaveBGroup() {
        for (group in listOf(Tuning.GROUP_BACKGROUND_COLOUR, Tuning.GROUP_SHADER_MOTION, Tuning.GROUP_LYRIC_TIMING)) {
            val params = Tuning.allParams.filter { it.group == group }
            for (p in params) {
                p.value = p.max
            }
            Tuning.resetGroup(group)
            for (p in params) {
                assertEquals("Param ${p.key} in $group must reset to default", p.defaultValue, p.value, 0.0001f)
                assertFalse("Param ${p.key} in $group must not be modified", p.isModified)
            }
        }
    }

    @Test
    fun testSaturationTunablePropertiesAndInversion() {
        val sat = Tuning.CHROMA_EXPONENT
        assertEquals("bg_saturation", sat.key)
        assertEquals(0.15f, sat.min, 0.0001f)
        assertEquals(1.00f, sat.max, 0.0001f)
        assertEquals(0.30f, sat.defaultValue, 0.0001f)
        assertTrue("Chroma exponent slider must be inverted so dragging right increases saturation", sat.inverted)
    }

    @Test
    fun testSaturationPreferenceAbsenceFallback() {
        val map = mutableMapOf<String, Float>()
        // When absent from preferences, falls back to Tuning.chromaExponent
        val fallbackDefault = map["bg_saturation"] ?: Tuning.chromaExponent
        assertEquals(0.30f, fallbackDefault, 0.0001f)

        // When Tuning is modified, fallback updates
        Tuning.chromaExponent = 0.65f
        val fallbackModified = map["bg_saturation"] ?: Tuning.chromaExponent
        assertEquals(0.65f, fallbackModified, 0.0001f)

        // When preference is stored, stored value takes precedence
        map["bg_saturation"] = 0.90f
        val prefValue = map["bg_saturation"] ?: Tuning.chromaExponent
        assertEquals(0.90f, prefValue, 0.0001f)
    }

    @Test
    fun testSyllableAnimatorGlideDurationUsesTuningDefaults() {
        val defaultGlide = SyllableAnimator.glideDurationMs(158f)
        assertEquals(200f, defaultGlide, 0.001f)

        Tuning.baseGlideMs = 300f
        val modifiedGlide = SyllableAnimator.glideDurationMs(158f)
        assertEquals(300f, modifiedGlide, 0.001f)
    }

    @Test
    fun testSyllableAnimatorExtendedWordEndUsesTuningDefaults() {
        // start=1000, end=1100, lineEnd=5000: duration=100, overlap=50 -> 150 < minAnim(200) -> 1200
        val defaultEnd = SyllableAnimator.getExtendedWordEnd(1000L, 1100L, 5000L)
        assertEquals(1200L, defaultEnd)

        Tuning.wordMinAnimationMs = 400L
        val modifiedEnd = SyllableAnimator.getExtendedWordEnd(1000L, 1100L, 5000L)
        assertEquals(1400L, modifiedEnd)
    }

    @Test
    fun testSyllableAnimatorMotionWordEndUsesTuningDefaults() {
        // start=1000, end=1300, lineEnd=5000. sweepEnd=1350. trail = (350 * 0.35) = 122.5 -> 122 clamped to [120, 250] -> 1472
        // With wordMotionDurationFloorMs = 480: floored = 1480, max(1472, 1480) = 1480
        val defaultMotionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1300L, 5000L)
        assertEquals(1480L, defaultMotionEnd)

        Tuning.wordMotionTrailMinMs = 180L
        val modifiedMotionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1300L, 5000L)
        assertEquals(1350L + 180L, modifiedMotionEnd)
    }

    @Test
    fun testSyllableAnimatorPreRollInactiveAlphaUsesTuningDefaults() {
        // pre-roll gap = 1400 - 1000 = 400. At pos=1200, progress = 0.5.
        // alpha = 80 + (50 * 0.5) = 105
        val defaultAlpha = SyllableAnimator.getPreRollInactiveAlpha(1200L, 1000L, 1400L)
        assertEquals(105, defaultAlpha)

        Tuning.preRollMaxLift = 100
        val modifiedAlpha = SyllableAnimator.getPreRollInactiveAlpha(1200L, 1000L, 1400L)
        assertEquals(130, modifiedAlpha)
    }

    @Test
    fun testWordMotionMinDurationMsDefaultEqualsHardcodedConstant() {
        assertEquals(150f, Tuning.WORD_MOTION_MIN_DURATION_MS.defaultValue, 0.0001f)
        assertEquals(150L, Tuning.wordMotionMinDurationMs)
        assertEquals(50f, Tuning.WORD_MOTION_MIN_DURATION_MS.min, 0.0001f)
        assertEquals(400f, Tuning.WORD_MOTION_MIN_DURATION_MS.max, 0.0001f)
        assertTrue(Tuning.WORD_MOTION_MIN_DURATION_MS.isInteger)
    }

    @Test
    fun testWordMotionMaxDurationMsDefaultEqualsHardcodedConstant() {
        assertEquals(500f, Tuning.WORD_MOTION_MAX_DURATION_MS.defaultValue, 0.0001f)
        assertEquals(500L, Tuning.wordMotionMaxDurationMs)
        assertEquals(300f, Tuning.WORD_MOTION_MAX_DURATION_MS.min, 0.0001f)
        assertEquals(1000f, Tuning.WORD_MOTION_MAX_DURATION_MS.max, 0.0001f)
        assertTrue(Tuning.WORD_MOTION_MAX_DURATION_MS.isInteger)
    }

    @Test
    fun testWordMotionMinAmplitudeDefaultEqualsHardcodedConstant() {
        assertEquals(0.40f, Tuning.WORD_MOTION_MIN_AMPLITUDE.defaultValue, 0.0001f)
        assertEquals(0.40f, Tuning.wordMotionMinAmplitude, 0.0001f)
        assertEquals(0.10f, Tuning.WORD_MOTION_MIN_AMPLITUDE.min, 0.0001f)
        assertEquals(1.00f, Tuning.WORD_MOTION_MIN_AMPLITUDE.max, 0.0001f)
        assertFalse(Tuning.WORD_MOTION_MIN_AMPLITUDE.isInteger)
    }

    @Test
    fun testWordMotionDurationFloorMsDefaultEqualsHardcodedConstant() {
        assertEquals(480f, Tuning.WORD_MOTION_DURATION_FLOOR_MS.defaultValue, 0.0001f)
        assertEquals(480L, Tuning.wordMotionDurationFloorMs)
        assertEquals(100f, Tuning.WORD_MOTION_DURATION_FLOOR_MS.min, 0.0001f)
        assertEquals(800f, Tuning.WORD_MOTION_DURATION_FLOOR_MS.max, 0.0001f)
        assertTrue(Tuning.WORD_MOTION_DURATION_FLOOR_MS.isInteger)
    }

    @Test
    fun testWordRiseDurationMsDefaultEqualsSpecification() {
        assertEquals(220f, Tuning.WORD_RISE_DURATION_MS.defaultValue, 0.0001f)
        assertEquals(220L, Tuning.wordRiseDurationMs)
        assertEquals(80f, Tuning.WORD_RISE_DURATION_MS.min, 0.0001f)
        assertEquals(800f, Tuning.WORD_RISE_DURATION_MS.max, 0.0001f)
        assertTrue(Tuning.WORD_RISE_DURATION_MS.isInteger)
    }

    @Test
    fun testWordLeadInMsDefaultEqualsSpecification() {
        assertEquals(100f, Tuning.WORD_LEAD_IN_MS.defaultValue, 0.0001f)
        assertEquals(100L, Tuning.wordLeadInMs)
        assertEquals(0f, Tuning.WORD_LEAD_IN_MS.min, 0.0001f)
        assertEquals(300f, Tuning.WORD_LEAD_IN_MS.max, 0.0001f)
        assertTrue(Tuning.WORD_LEAD_IN_MS.isInteger)
    }

    @Test
    fun testWordScaleEaseInFractionDefaultEqualsSpecification() {
        assertEquals(0.20f, Tuning.WORD_SCALE_EASE_IN_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.20f, Tuning.wordScaleEaseInFraction, 0.0001f)
        assertEquals(0.05f, Tuning.WORD_SCALE_EASE_IN_FRACTION.min, 0.0001f)
        assertEquals(0.50f, Tuning.WORD_SCALE_EASE_IN_FRACTION.max, 0.0001f)
        assertFalse(Tuning.WORD_SCALE_EASE_IN_FRACTION.isInteger)
    }

    @Test
    fun testWordSettleDurationMsDefaultEqualsSpecification() {
        assertEquals(420f, Tuning.WORD_SETTLE_DURATION_MS.defaultValue, 0.0001f)
        assertEquals(420L, Tuning.wordSettleDurationMs)
        assertEquals(150f, Tuning.WORD_SETTLE_DURATION_MS.min, 0.0001f)
        assertEquals(1200f, Tuning.WORD_SETTLE_DURATION_MS.max, 0.0001f)
        assertTrue(Tuning.WORD_SETTLE_DURATION_MS.isInteger)
    }

    @Test
    fun testRippleEaseInFractionTunable() {
        assertEquals(0.15f, Tuning.RIPPLE_EASE_IN_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.15f, Tuning.rippleEaseInFraction, 0.0001f)
        assertEquals(0.05f, Tuning.RIPPLE_EASE_IN_FRACTION.min, 0.0001f)
        assertEquals(0.50f, Tuning.RIPPLE_EASE_IN_FRACTION.max, 0.0001f)
        assertFalse(Tuning.RIPPLE_EASE_IN_FRACTION.isInteger)
    }

    @Test
    fun testDotScalePeakTunable() {
        assertEquals(1.25f, Tuning.DOT_SCALE_PEAK.defaultValue, 0.0001f)
        assertEquals(1.25f, Tuning.dotScalePeak, 0.0001f)
        assertEquals(1.00f, Tuning.DOT_SCALE_PEAK.min, 0.0001f)
        assertEquals(1.40f, Tuning.DOT_SCALE_PEAK.max, 0.0001f)
        assertFalse(Tuning.DOT_SCALE_PEAK.isInteger)
    }

    @Test
    fun testDotLiftFractionTunable() {
        assertEquals(0.40f, Tuning.DOT_LIFT_FRACTION.defaultValue, 0.0001f)
        assertEquals(0.40f, Tuning.dotLiftFraction, 0.0001f)
        assertEquals(0.00f, Tuning.DOT_LIFT_FRACTION.min, 0.0001f)
        assertEquals(0.50f, Tuning.DOT_LIFT_FRACTION.max, 0.0001f)
        assertFalse(Tuning.DOT_LIFT_FRACTION.isInteger)
    }

    @Test
    fun testDotCountTunable() {
        assertEquals(3f, Tuning.DOT_COUNT.defaultValue, 0.0001f)
        assertEquals(3, Tuning.dotCount)
        assertEquals(1f, Tuning.DOT_COUNT.min, 0.0001f)
        assertEquals(8f, Tuning.DOT_COUNT.max, 0.0001f)
        assertTrue(Tuning.DOT_COUNT.isInteger)
    }

    @Test
    fun testDotOverlapTunable() {
        assertEquals(35.00f, Tuning.DOT_OVERLAP.defaultValue, 0.0001f)
        assertEquals(35.00f, Tuning.dotOverlap, 0.0001f)
        assertEquals(0f, Tuning.DOT_OVERLAP.min, 0.0001f)
        assertEquals(100f, Tuning.DOT_OVERLAP.max, 0.0001f)
        assertEquals("dotOverlap", Tuning.DOT_OVERLAP.key)
        assertEquals("Dot overlap", Tuning.DOT_OVERLAP.label)
        assertEquals(Tuning.GROUP_INSTRUMENTAL_DOTS, Tuning.DOT_OVERLAP.group)
        assertFalse(Tuning.DOT_OVERLAP.isInteger)
        assertTrue(Tuning.DOT_OVERLAP.defaultValue >= Tuning.DOT_OVERLAP.min)
        assertTrue(Tuning.DOT_OVERLAP.defaultValue <= Tuning.DOT_OVERLAP.max)
    }

    @Test
    fun testResetGroupForInstrumentalDots() {
        Tuning.DOT_SCALE_PEAK.value = 1.35f
        Tuning.DOT_LIFT_FRACTION.value = 0.45f
        Tuning.DOT_COUNT.value = 6f
        Tuning.DOT_OVERLAP.value = 75f

        assertTrue(Tuning.DOT_SCALE_PEAK.isModified)
        assertTrue(Tuning.DOT_LIFT_FRACTION.isModified)
        assertTrue(Tuning.DOT_COUNT.isModified)
        assertTrue(Tuning.DOT_OVERLAP.isModified)

        Tuning.resetGroup(Tuning.GROUP_INSTRUMENTAL_DOTS)

        assertEquals(1.25f, Tuning.DOT_SCALE_PEAK.value, 0.0001f)
        assertEquals(0.40f, Tuning.DOT_LIFT_FRACTION.value, 0.0001f)
        assertEquals(3f, Tuning.DOT_COUNT.value, 0.0001f)
        assertEquals(35.00f, Tuning.DOT_OVERLAP.value, 0.0001f)
        assertFalse(Tuning.DOT_SCALE_PEAK.isModified)
        assertFalse(Tuning.DOT_LIFT_FRACTION.isModified)
        assertFalse(Tuning.DOT_COUNT.isModified)
        assertFalse(Tuning.DOT_OVERLAP.isModified)
    }

    @Test
    fun testWordSpacingTunable() {
        assertEquals(1.15f, Tuning.WORD_SPACING.defaultValue, 0.0001f)
        assertEquals(1.15f, Tuning.wordSpacing, 0.0001f)
        assertEquals(1.00f, Tuning.WORD_SPACING.min, 0.0001f)
        assertEquals(3.00f, Tuning.WORD_SPACING.max, 0.0001f)
        assertTrue(Tuning.WORD_SPACING.min < Tuning.WORD_SPACING.max)
        assertFalse(Tuning.WORD_SPACING.isInteger)
        assertEquals("wordSpacing", Tuning.WORD_SPACING.key)
        assertEquals("Word spacing", Tuning.WORD_SPACING.label)
        assertEquals(Tuning.GROUP_WORD_MOTION, Tuning.WORD_SPACING.group)
    }
}

