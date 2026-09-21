package com.dnk.wallpaperlyrics

import android.content.Context
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

/**
 * Central parameter holder for live visual tuning in debug builds.
 * Initialized to production defaults so release behavior is completely identical.
 */
object Tuning {

    const val PREFS_NAME = "debug_tuning_overrides"

    const val GROUP_WORD_MOTION = "Word Motion Curves"
    const val GROUP_BACKGROUND_COLOUR = "Background Colour"
    const val GROUP_SHADER_MOTION = "Shader & Background Motion"
    const val GROUP_LYRIC_TIMING = "Lyric Timing Windows"
    const val GROUP_INSTRUMENTAL_DOTS = "Instrumental Dots"

    class Tunable(
        val key: String,
        val label: String,
        val description: String,
        val group: String,
        val min: Float,
        val max: Float,
        val defaultValue: Float,
        val isInteger: Boolean = false,
        val inverted: Boolean = false
    ) {
        var value: Float = defaultValue

        val isModified: Boolean
            get() = Math.abs(value - defaultValue) > 0.0001f

        fun reset() {
            value = defaultValue
        }
    }

    // Group 1: Word motion curves
    val WORD_SCALE_START = Tunable("wordScaleStart", "Scale start", "Size an unsung word sits at, matching an inactive line.", GROUP_WORD_MOTION, 0.80f, 1.20f, 0.95f)
    val WORD_SCALE_PEAK = Tunable("wordScalePeak", "Scale peak", "Biggest size a word reaches on its swell.", GROUP_WORD_MOTION, 1.00f, 1.30f, 1.00f)
    val WORD_SCALE_SETTLE = Tunable("wordScaleSettle", "Scale settle", "Size a word settles at once it has been sung, and stays at.", GROUP_WORD_MOTION, 0.90f, 1.20f, 0.98f)
    val WORD_SCALE_PEAK_POS = Tunable("wordScalePeakPosition", "Scale peak position", "Where in the word's motion the swell peaks. 0.6 is 60 percent through.", GROUP_WORD_MOTION, 0.10f, 0.90f, 0.60f)
    val WORD_SCALE_EASE_IN_FRACTION = Tunable("wordScaleEaseInFraction", "Scale ease-in fraction", "How much of the rise is spent dipping in before the swell. Higher is a slower, softer dip.", GROUP_WORD_MOTION, 0.05f, 0.50f, 0.20f)
    val RIPPLE_EASE_IN_FRACTION = Tunable("rippleEaseInFraction", "Ripple ease-in fraction", "Fades the letter ripple in and out at the ends of a word so it does not start or stop with a jolt. Higher fades more and costs the first and last letters some emphasis.", GROUP_WORD_MOTION, 0.05f, 0.50f, 0.15f)
    val WORD_LIFT_PEAK_FRACTION = Tunable("wordLiftPeakFraction", "Lift peak fraction", "How far a word lifts off the line, as a fraction of text size.", GROUP_WORD_MOTION, 0.00f, 0.20f, 0.05f)
    val WORD_LIFT_PEAK_POS = Tunable("wordLiftPeakPosition", "Lift peak position", "Where in the word's motion the lift peaks.", GROUP_WORD_MOTION, 0.10f, 0.90f, 0.58f)
    val WORD_GLOW_RISE_END = Tunable("wordGlowRiseEnd", "Glow rise end", "Point where the glow has finished fading in.", GROUP_WORD_MOTION, 0.05f, 0.50f, 0.20f)
    val WORD_GLOW_HOLD_END = Tunable("wordGlowHoldEnd", "Glow hold end", "Point where the glow starts fading out. It holds at full between the two.", GROUP_WORD_MOTION, 0.30f, 0.90f, 0.55f)
    val WORD_GLOW_ALPHA_MULT = Tunable("wordGlowAlphaMultiplier", "Glow alpha multiplier", "Glow strength. 0 turns the glow off.", GROUP_WORD_MOTION, 0f, 255f, 0f)
    val HELD_WORD_MIN_DURATION_MS = Tunable("heldWordMinDurationMs", "Held word threshold (ms)", "A word sung at least this long animates letter by letter. Shorter words swell as one block.", GROUP_WORD_MOTION, 200f, 3000f, 575f, isInteger = true)
    val HELD_WORD_LETTER_SCALE_PEAK = Tunable("heldWordLetterScalePeak", "Held letter scale peak", "Biggest size a single letter reaches as the ripple passes over it.", GROUP_WORD_MOTION, 1.00f, 1.40f, 1.04f)
    val LETTER_FALLOFF_POWER = Tunable("letterFalloffPower", "Letter falloff power", "How tightly the ripple hugs the letter being sung. Higher leaves the neighbours almost still.", GROUP_WORD_MOTION, 1f, 6f, 3f, isInteger = true)
    val GLOW_BLUR_RADIUS_FRACTION = Tunable("glowBlurRadiusFraction", "Glow blur radius fraction", "Width of the glow blur, as a fraction of text size.", GROUP_WORD_MOTION, 0.02f, 0.30f, 0.10f)
    val WORD_MOTION_MIN_DURATION_MS = Tunable("wordMotionMinDurationMs", "Motion min duration (ms)", "Words sung this fast get the smallest motion. Nothing shrinks further below it.", GROUP_WORD_MOTION, 50f, 400f, 150f, isInteger = true)
    val WORD_MOTION_MAX_DURATION_MS = Tunable("wordMotionMaxDurationMs", "Motion max duration (ms)", "Words sung this long or longer get the full motion.", GROUP_WORD_MOTION, 300f, 1000f, 500f, isInteger = true)
    val WORD_MOTION_MIN_AMPLITUDE = Tunable("wordMotionMinAmplitude", "Motion min amplitude", "How much motion the fastest words keep. 0.4 is 40 percent of the full swell.", GROUP_WORD_MOTION, 0.10f, 1.00f, 0.40f)
    val WORD_MOTION_DURATION_FLOOR_MS = Tunable("wordMotionDurationFloorMs", "Motion duration floor (ms)", "Shortest motion window a word can get, however briefly it is sung.", GROUP_WORD_MOTION, 100f, 800f, 480f, isInteger = true)
    val WORD_RISE_DURATION_MS = Tunable("wordRiseDurationMs", "Word rise duration (ms)", "Time from the start of motion to the top of the swell. Fixed in ms, so tempo does not squash it.", GROUP_WORD_MOTION, 80f, 800f, 220f, isInteger = true)
    val WORD_LEAD_IN_MS = Tunable("wordLeadInMs", "Word lead-in (ms)", "How early motion starts before the word is sung, so the peak lands on the beat.", GROUP_WORD_MOTION, 0f, 300f, 100f, isInteger = true)
    val WORD_SETTLE_DURATION_MS = Tunable("wordSettleDurationMs", "Word settle duration (ms)", "Time the word takes to fall from its peak back to normal size.", GROUP_WORD_MOTION, 150f, 1200f, 420f, isInteger = true)
    val WORD_SPACING = Tunable("wordSpacing", "Word spacing", "Width of the gap between words, as a multiple of a normal space.", GROUP_WORD_MOTION, 1.00f, 3.00f, 1.15f)

    // Group 2: Background colour
    val CHROMA_EXPONENT = Tunable("bg_saturation", "Chroma exponent", "Overall colour strength of the background. Drag right for more saturated.", GROUP_BACKGROUND_COLOUR, AuroraRenderer.MIN_CHROMA_EXPONENT, AuroraRenderer.MAX_CHROMA_EXPONENT, AuroraRenderer.DEFAULT_CHROMA_EXPONENT, inverted = true)
    val LINEAR_BOOST = Tunable("linearBoost", "Linear chroma boost", "Cap on how hard near grey pixels are pushed. Low keeps whites and greys neutral, high lets them take on a tint.", GROUP_BACKGROUND_COLOUR, 1.0f, 10.0f, AuroraRenderer.DEFAULT_LINEAR_BOOST)
    val BACKGROUND_DEPTH = Tunable("backgroundDepth", "Background depth", "Darkens the most colourful areas so the background has some depth.", GROUP_BACKGROUND_COLOUR, 0.0f, 0.50f, AuroraRenderer.DEFAULT_BACKGROUND_DEPTH)
    val DEPTH_GATE_LOW = Tunable("depthGateLow", "Depth gate low", "How colourful a pixel has to be before that darkening starts.", GROUP_BACKGROUND_COLOUR, 0.0f, 1.0f, AuroraRenderer.DEFAULT_DEPTH_GATE_LOW)
    val DEPTH_GATE_HIGH = Tunable("depthGateHigh", "Depth gate high", "How colourful a pixel has to be to get the full darkening.", GROUP_BACKGROUND_COLOUR, 0.0f, 1.0f, AuroraRenderer.DEFAULT_DEPTH_GATE_HIGH)
    val GAMUT_CAP_FRACTION = Tunable("gamutCapFraction", "Gamut cap fraction", "Ceiling on colour, as a fraction of the most the screen can show. Below 1 leaves headroom so strong colours do not clip.", GROUP_BACKGROUND_COLOUR, 0.80f, 1.00f, AuroraRenderer.DEFAULT_GAMUT_CAP_FRACTION)
    val LIGHTNESS_CAP_KNEE = Tunable("lightnessCapKnee", "Lightness cap knee", "Brightness at which bright covers start being eased down.", GROUP_BACKGROUND_COLOUR, 0.40f, 0.80f, AuroraRenderer.LIGHTNESS_CAP_KNEE)
    val LIGHTNESS_CAP_CEILING = Tunable("lightnessCapCeiling", "Lightness cap ceiling", "Brightest the background is allowed to get, so white lyrics stay readable.", GROUP_BACKGROUND_COLOUR, 0.65f, 0.95f, AuroraRenderer.LIGHTNESS_CAP_CEILING)
    val LIGHTNESS_CAP_STRENGTH = Tunable("lightnessCapStrength", "Lightness cap strength", "How firmly that brightness cap is applied. 0 turns it off.", GROUP_BACKGROUND_COLOUR, 0.0f, 1.0f, AuroraRenderer.LIGHTNESS_CAP_STRENGTH)
    val SHADER_DITHER_AMPLITUDE = Tunable("shaderDitherAmplitude", "Shader dither amplitude", "Noise added just before the picture drops to 8 bit, to break up banding in smooth gradients.", GROUP_BACKGROUND_COLOUR, 0.0f, 0.05f, AuroraRenderer.DEFAULT_DITHER_AMPLITUDE)

    // Group 3: Shader and background motion
    val VIGNETTE_STRENGTH = Tunable("vignetteStrength", "Vignette strength", "How much the corners of the screen are darkened.", GROUP_SHADER_MOTION, 0.0f, 1.0f, 0.30f)
    val SHADER_INTENSITY = Tunable("shaderIntensity", "Shader intensity", "How far the flow warps the cover art. 0 leaves it still and sharp.", GROUP_SHADER_MOTION, 0.0f, 2.0f, 1.0f)
    val EDGE_FALLOFF_BAND = Tunable("edgeFalloffBand", "Edge falloff band", "Not wired to anything right now.", GROUP_SHADER_MOTION, 0.05f, 0.50f, 0.22f)
    val CROSSFADE_RATE = Tunable("crossfadeRate", "Crossfade rate", "Speed of the crossfade when the track changes. 2 is twice as fast.", GROUP_SHADER_MOTION, 0.1f, 5.0f, 1.0f)

    // Group 4: Lyric timing windows
    val WORD_OVERLAP_MS = Tunable("wordOverlapMs", "Word overlap (ms)", "Extra time a word keeps sweeping after it has been sung, so words overlap a little.", GROUP_LYRIC_TIMING, 0f, 200f, 50f, isInteger = true)
    val WORD_MIN_ANIMATION_MS = Tunable("wordMinAnimationMs", "Word min animation (ms)", "Shortest sweep a word can get, however briefly it is sung.", GROUP_LYRIC_TIMING, 50f, 500f, 200f, isInteger = true)
    val WORD_MOTION_TRAIL_FRACTION = Tunable("wordMotionTrailFraction", "Motion trail fraction", "Trail of motion kept after the sweep ends, as a fraction of the word's length.", GROUP_LYRIC_TIMING, 0.0f, 1.0f, 0.35f)
    val WORD_MOTION_TRAIL_MIN_MS = Tunable("wordMotionTrailMinMs", "Motion trail min (ms)", "Shortest trail a word can get.", GROUP_LYRIC_TIMING, 0f, 500f, 120f, isInteger = true)
    val WORD_MOTION_TRAIL_MAX_MS = Tunable("wordMotionTrailMaxMs", "Motion trail max (ms)", "Longest trail a word can get.", GROUP_LYRIC_TIMING, 50f, 1000f, 250f, isInteger = true)
    val PRE_ROLL_MAX_LIFT = Tunable("preRollMaxLift", "Pre-roll max lift", "Extra brightness a line gains while it waits for its first word.", GROUP_LYRIC_TIMING, 0f, 150f, 50f, isInteger = true)
    val PRE_ROLL_SETTLE_MS = Tunable("preRollSettleMs", "Pre-roll settle (ms)", "Time that extra brightness takes to fade once the first word lands.", GROUP_LYRIC_TIMING, 0f, 500f, 150f, isInteger = true)
    val BASE_GLIDE_MS = Tunable("baseGlideMs", "Base glide (ms)", "Base time a line takes to glide to its new position.", GROUP_LYRIC_TIMING, 50f, 600f, 200f)
    val REFERENCE_DISTANCE_PX = Tunable("referenceDistancePx", "Reference distance (px)", "Distance treated as a normal glide. Longer moves take proportionally more time.", GROUP_LYRIC_TIMING, 50f, 500f, 158f)

    // Group 5: Instrumental dots
    val DOT_SCALE_PEAK = Tunable("dotScalePeak", "Dot scale peak", "Biggest a dot gets as its turn comes round.", GROUP_INSTRUMENTAL_DOTS, 1.00f, 1.40f, 1.25f)
    val DOT_LIFT_FRACTION = Tunable("dotLiftFraction", "Dot lift fraction", "How far a dot lifts, as a fraction of its radius.", GROUP_INSTRUMENTAL_DOTS, 0.00f, 0.50f, 0.40f)
    val DOT_COUNT = Tunable("dotCount", "Dot count", "How many dots show during an instrumental break.", GROUP_INSTRUMENTAL_DOTS, 1f, 8f, 3f, isInteger = true)
    val DOT_OVERLAP = Tunable("dotOverlap", "Dot overlap", "How much neighbouring dots share their turn, in percent. 0 is strictly one at a time.", GROUP_INSTRUMENTAL_DOTS, 0f, 100f, 35.00f)

    val allParams: List<Tunable> = listOf(
        WORD_SCALE_START,
        WORD_SCALE_PEAK,
        WORD_SCALE_SETTLE,
        WORD_SCALE_PEAK_POS,
        WORD_SCALE_EASE_IN_FRACTION,
        RIPPLE_EASE_IN_FRACTION,
        WORD_LIFT_PEAK_FRACTION,
        WORD_LIFT_PEAK_POS,
        WORD_GLOW_RISE_END,
        WORD_GLOW_HOLD_END,
        WORD_GLOW_ALPHA_MULT,
        HELD_WORD_MIN_DURATION_MS,
        HELD_WORD_LETTER_SCALE_PEAK,
        LETTER_FALLOFF_POWER,
        GLOW_BLUR_RADIUS_FRACTION,
        WORD_MOTION_MIN_DURATION_MS,
        WORD_MOTION_MAX_DURATION_MS,
        WORD_MOTION_MIN_AMPLITUDE,
        WORD_MOTION_DURATION_FLOOR_MS,
        WORD_RISE_DURATION_MS,
        WORD_LEAD_IN_MS,
        WORD_SETTLE_DURATION_MS,
        WORD_SPACING,
        CHROMA_EXPONENT,
        LINEAR_BOOST,
        BACKGROUND_DEPTH,
        DEPTH_GATE_LOW,
        DEPTH_GATE_HIGH,
        GAMUT_CAP_FRACTION,
        LIGHTNESS_CAP_KNEE,
        LIGHTNESS_CAP_CEILING,
        LIGHTNESS_CAP_STRENGTH,
        SHADER_DITHER_AMPLITUDE,
        VIGNETTE_STRENGTH,
        SHADER_INTENSITY,
        EDGE_FALLOFF_BAND,
        CROSSFADE_RATE,
        WORD_OVERLAP_MS,
        WORD_MIN_ANIMATION_MS,
        WORD_MOTION_TRAIL_FRACTION,
        WORD_MOTION_TRAIL_MIN_MS,
        WORD_MOTION_TRAIL_MAX_MS,
        PRE_ROLL_MAX_LIFT,
        PRE_ROLL_SETTLE_MS,
        BASE_GLIDE_MS,
        REFERENCE_DISTANCE_PX,
        DOT_SCALE_PEAK,
        DOT_LIFT_FRACTION,
        DOT_COUNT,
        DOT_OVERLAP
    )

    val groups: List<String> = listOf(
        GROUP_WORD_MOTION,
        GROUP_BACKGROUND_COLOUR,
        GROUP_SHADER_MOTION,
        GROUP_LYRIC_TIMING,
        GROUP_INSTRUMENTAL_DOTS
    )

    // Direct accessors for fast call-sites: Group 1 (Word motion curves)
    var wordScaleStart: Float
        get() = WORD_SCALE_START.value
        set(v) { WORD_SCALE_START.value = v }

    var wordScalePeak: Float
        get() = WORD_SCALE_PEAK.value
        set(v) { WORD_SCALE_PEAK.value = v }

    var wordScaleSettle: Float
        get() = WORD_SCALE_SETTLE.value
        set(v) { WORD_SCALE_SETTLE.value = v }

    var wordScalePeakPosition: Float
        get() = WORD_SCALE_PEAK_POS.value
        set(v) { WORD_SCALE_PEAK_POS.value = v }

    var wordScaleEaseInFraction: Float
        get() = WORD_SCALE_EASE_IN_FRACTION.value
        set(v) { WORD_SCALE_EASE_IN_FRACTION.value = v }

    var rippleEaseInFraction: Float
        get() = RIPPLE_EASE_IN_FRACTION.value
        set(v) { RIPPLE_EASE_IN_FRACTION.value = v }

    var wordLiftPeakFraction: Float
        get() = WORD_LIFT_PEAK_FRACTION.value
        set(v) { WORD_LIFT_PEAK_FRACTION.value = v }

    var wordLiftPeakPosition: Float
        get() = WORD_LIFT_PEAK_POS.value
        set(v) { WORD_LIFT_PEAK_POS.value = v }

    var wordGlowRiseEnd: Float
        get() = WORD_GLOW_RISE_END.value
        set(v) { WORD_GLOW_RISE_END.value = v }

    var wordGlowHoldEnd: Float
        get() = WORD_GLOW_HOLD_END.value
        set(v) { WORD_GLOW_HOLD_END.value = v }

    var wordGlowAlphaMultiplier: Float
        get() = WORD_GLOW_ALPHA_MULT.value
        set(v) { WORD_GLOW_ALPHA_MULT.value = v }

    var heldWordMinDurationMs: Long
        get() = HELD_WORD_MIN_DURATION_MS.value.toLong()
        set(v) { HELD_WORD_MIN_DURATION_MS.value = v.toFloat() }

    var heldWordLetterScalePeak: Float
        get() = HELD_WORD_LETTER_SCALE_PEAK.value
        set(v) { HELD_WORD_LETTER_SCALE_PEAK.value = v }

    var letterFalloffPower: Float
        get() = LETTER_FALLOFF_POWER.value
        set(v) { LETTER_FALLOFF_POWER.value = v }

    var glowBlurRadiusFraction: Float
        get() = GLOW_BLUR_RADIUS_FRACTION.value
        set(v) { GLOW_BLUR_RADIUS_FRACTION.value = v }

    var wordMotionMinDurationMs: Long
        get() = WORD_MOTION_MIN_DURATION_MS.value.toLong()
        set(v) { WORD_MOTION_MIN_DURATION_MS.value = v.toFloat() }

    var wordMotionMaxDurationMs: Long
        get() = WORD_MOTION_MAX_DURATION_MS.value.toLong()
        set(v) { WORD_MOTION_MAX_DURATION_MS.value = v.toFloat() }

    var wordMotionMinAmplitude: Float
        get() = WORD_MOTION_MIN_AMPLITUDE.value
        set(v) { WORD_MOTION_MIN_AMPLITUDE.value = v }

    var wordMotionDurationFloorMs: Long
        get() = WORD_MOTION_DURATION_FLOOR_MS.value.toLong()
        set(v) { WORD_MOTION_DURATION_FLOOR_MS.value = v.toFloat() }

    var wordRiseDurationMs: Long
        get() = WORD_RISE_DURATION_MS.value.toLong()
        set(v) { WORD_RISE_DURATION_MS.value = v.toFloat() }

    var wordLeadInMs: Long
        get() = WORD_LEAD_IN_MS.value.toLong()
        set(v) { WORD_LEAD_IN_MS.value = v.toFloat() }

    var wordSettleDurationMs: Long
        get() = WORD_SETTLE_DURATION_MS.value.toLong()
        set(v) { WORD_SETTLE_DURATION_MS.value = v.toFloat() }

    var wordSpacing: Float
        get() = WORD_SPACING.value
        set(v) { WORD_SPACING.value = v }

    // Direct accessors: Group 2 (Background colour)
    var chromaExponent: Float
        get() = CHROMA_EXPONENT.value
        set(v) { CHROMA_EXPONENT.value = v }

    var linearBoost: Float
        get() = LINEAR_BOOST.value
        set(v) { LINEAR_BOOST.value = v }

    var backgroundDepth: Float
        get() = BACKGROUND_DEPTH.value
        set(v) { BACKGROUND_DEPTH.value = v }

    var depthGateLow: Float
        get() = DEPTH_GATE_LOW.value
        set(v) { DEPTH_GATE_LOW.value = v }

    var depthGateHigh: Float
        get() = DEPTH_GATE_HIGH.value
        set(v) { DEPTH_GATE_HIGH.value = v }

    var gamutCapFraction: Float
        get() = GAMUT_CAP_FRACTION.value
        set(v) { GAMUT_CAP_FRACTION.value = v }

    var lightnessCapKnee: Float
        get() = LIGHTNESS_CAP_KNEE.value
        set(v) { LIGHTNESS_CAP_KNEE.value = v }

    var lightnessCapCeiling: Float
        get() = LIGHTNESS_CAP_CEILING.value
        set(v) { LIGHTNESS_CAP_CEILING.value = v }

    var lightnessCapStrength: Float
        get() = LIGHTNESS_CAP_STRENGTH.value
        set(v) { LIGHTNESS_CAP_STRENGTH.value = v }

    var shaderDitherAmplitude: Float
        get() = SHADER_DITHER_AMPLITUDE.value
        set(v) { SHADER_DITHER_AMPLITUDE.value = v }

    // Direct accessors: Group 3 (Shader and background motion)
    var vignetteStrength: Float
        get() = VIGNETTE_STRENGTH.value
        set(v) { VIGNETTE_STRENGTH.value = v }

    var shaderIntensity: Float
        get() = SHADER_INTENSITY.value
        set(v) { SHADER_INTENSITY.value = v }

    var edgeFalloffBand: Float
        get() = EDGE_FALLOFF_BAND.value
        set(v) { EDGE_FALLOFF_BAND.value = v }

    var crossfadeRate: Float
        get() = CROSSFADE_RATE.value
        set(v) { CROSSFADE_RATE.value = v }

    // Direct accessors: Group 4 (Lyric timing windows)
    var wordOverlapMs: Long
        get() = WORD_OVERLAP_MS.value.toLong()
        set(v) { WORD_OVERLAP_MS.value = v.toFloat() }

    var wordMinAnimationMs: Long
        get() = WORD_MIN_ANIMATION_MS.value.toLong()
        set(v) { WORD_MIN_ANIMATION_MS.value = v.toFloat() }

    var wordMotionTrailFraction: Float
        get() = WORD_MOTION_TRAIL_FRACTION.value
        set(v) { WORD_MOTION_TRAIL_FRACTION.value = v }

    var wordMotionTrailMinMs: Long
        get() = WORD_MOTION_TRAIL_MIN_MS.value.toLong()
        set(v) { WORD_MOTION_TRAIL_MIN_MS.value = v.toFloat() }

    var wordMotionTrailMaxMs: Long
        get() = WORD_MOTION_TRAIL_MAX_MS.value.toLong()
        set(v) { WORD_MOTION_TRAIL_MAX_MS.value = v.toFloat() }

    var preRollMaxLift: Int
        get() = PRE_ROLL_MAX_LIFT.value.toInt()
        set(v) { PRE_ROLL_MAX_LIFT.value = v.toFloat() }

    var preRollSettleMs: Long
        get() = PRE_ROLL_SETTLE_MS.value.toLong()
        set(v) { PRE_ROLL_SETTLE_MS.value = v.toFloat() }

    var baseGlideMs: Float
        get() = BASE_GLIDE_MS.value
        set(v) { BASE_GLIDE_MS.value = v }

    var referenceDistancePx: Float
        get() = REFERENCE_DISTANCE_PX.value
        set(v) { REFERENCE_DISTANCE_PX.value = v }

    // Direct accessors: Group 5 (Instrumental dots)
    var dotScalePeak: Float
        get() = DOT_SCALE_PEAK.value
        set(v) { DOT_SCALE_PEAK.value = v }

    var dotLiftFraction: Float
        get() = DOT_LIFT_FRACTION.value
        set(v) { DOT_LIFT_FRACTION.value = v }

    var dotCount: Int
        get() = DOT_COUNT.value.toInt()
        set(v) { DOT_COUNT.value = v.toFloat() }

    var dotOverlap: Float
        get() = DOT_OVERLAP.value
        set(v) { DOT_OVERLAP.value = v }

    fun resetGroup(groupName: String) {
        for (param in allParams) {
            if (param.group == groupName) {
                param.reset()
            }
        }
    }

    fun resetAll() {
        for (param in allParams) {
            param.reset()
        }
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        for (param in allParams) {
            if (prefs.contains(param.key)) {
                param.value = prefs.getFloat(param.key, param.defaultValue)
            } else if (param.key == "bg_saturation" && settingsPrefs.contains("bg_saturation")) {
                param.value = settingsPrefs.getFloat("bg_saturation", param.defaultValue)
            }
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (param in allParams) {
            if (param.isModified) {
                editor.putFloat(param.key, param.value)
            } else {
                editor.remove(param.key)
            }
        }
        editor.apply()
        if (CHROMA_EXPONENT.isModified) {
            settingsPrefs.edit().putFloat("bg_saturation", CHROMA_EXPONENT.value).apply()
        } else {
            settingsPrefs.edit().remove("bg_saturation").apply()
        }
    }

    private fun formatFloat(v: Float): String {
        return if (Math.abs(v - (v * 1000).roundToInt() / 1000f) > 0.0001f) {
            String.format(Locale.US, "%.4ff", v)
        } else {
            String.format(Locale.US, "%.3ff", v)
        }
    }

    fun exportKotlin(): String {
        val sb = StringBuilder()
        sb.append("// Debug Tuning Dump\n")
        val modified = allParams.filter { it.isModified }
        if (modified.isEmpty()) {
            sb.append("// No values changed from defaults\n")
            return sb.toString()
        }
        for (groupName in groups) {
            val groupParams = modified.filter { it.group == groupName }
            if (groupParams.isEmpty()) continue
            sb.append("// Group: ").append(groupName).append("\n")
            for (p in groupParams) {
                val isIntType = p.key == "preRollMaxLift" || p.key == "dotCount"
                val typeStr = if (isIntType) "Int" else if (p.isInteger) "Long" else "Float"
                val valStr = if (isIntType) {
                    "${p.value.toInt()}"
                } else if (p.isInteger) {
                    "${p.value.toLong()}L"
                } else {
                    formatFloat(p.value)
                }
                val defStr = if (isIntType) {
                    "${p.defaultValue.toInt()}"
                } else if (p.isInteger) {
                    "${p.defaultValue.toLong()}L"
                } else {
                    formatFloat(p.defaultValue)
                }
                sb.append("val ").append(p.key).append(": ").append(typeStr).append(" = ").append(valStr)
                    .append(" // default ").append(defStr).append("\n")
            }
        }
        return sb.toString()
    }

    fun parseKotlinExport(code: String): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        val regex = Pattern.compile("val\\s+([a-zA-Z0-9_]+):\\s*(?:Float|Long|Int)\\s*=\\s*([0-9\\.\\-]+)[fFLl]?")
        for (line in code.lines()) {
            val matcher = regex.matcher(line.trim())
            if (matcher.find()) {
                val key = matcher.group(1) ?: continue
                val rawVal = matcher.group(2) ?: continue
                val floatVal = rawVal.toFloatOrNull() ?: continue
                result[key] = floatVal
            }
        }
        return result
    }

    fun progressToValue(progress: Int, min: Float, max: Float, inverted: Boolean = false, isInteger: Boolean = false): Float {
        val ratio = progress.coerceIn(0, 1000) / 1000f
        val raw = if (inverted) {
            max - (max - min) * ratio
        } else {
            min + (max - min) * ratio
        }
        return if (isInteger) {
            raw.roundToInt().toFloat()
        } else {
            raw
        }
    }

    fun valueToProgress(value: Float, min: Float, max: Float, inverted: Boolean = false): Int {
        val clamped = value.coerceIn(min, max)
        val span = max - min
        if (span <= 0f) return 0
        val ratio = if (inverted) {
            (max - clamped) / span
        } else {
            (clamped - min) / span
        }
        return (ratio * 1000f).roundToInt().coerceIn(0, 1000)
    }
}
