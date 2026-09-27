package com.ghaith.ironhud.plane.models

/**
 * Flight-deck glazing for each aircraft family, as [Pane]s in (u, deg) coordinates of the builder's
 * glazing zone (see [Pane]). Airliner decks come from [deck], which covers the classic layouts: a
 * windscreen each side, one or two side windows, optional eyebrow and chin windows. Canopies,
 * bubbles and helicopter noses are drawn pane by pane.
 */
object Windshields {

    /** A pane with corners given as (u, deg) round the section (see [Gp.Deg]). */
    private fun p(
        vararg c: Pair<Double, Double>, mirror: Boolean = true, closed: Boolean = true, essential: Boolean = false,
        round: Double = 0.02, frame: Double = 0.012,
    ) = Pane(c.map { (u, d) -> Gp.Deg(u, d) }, mirror, closed, essential, if (closed) round else 0.0, frame = if (closed) frame else 0.0)

    /** How the last side window ends. */
    enum class Rear {
        /** Straight rear edge. */
        SQUARE,
        /** Airbus: the top rear corner cut off at an angle. */
        NOTCH,
        /** 737: the rear edge leans forward, the window narrowing towards the top. */
        TAPER,
        /** 757/767/777, bizjets: a well-rounded rear edge. */
        ROUND,
        /** 787: the sill and header close in to a rounded point. */
        SWEPT,
    }

    /**
     * A jet or turboprop flight deck as seen on its 3-view plans, in fuselage heights, s aft of the
     * windscreen's foot on the centreline and heights relative to it, all scaled by [k]:
     * - with [foot] set, the nose crest is shaped for it ([Cab]): radome up to the foot, [foot] aft of
     *   the nose tip and [anchor] above the centreline, then the flat windscreen, [wsLen] long and
     *   [wsRise] high, then the roof, full height [roof] aft of the tip; without, the foot sits where
     *   the builder's crest reaches [anchor] and the windscreen's head at [wsLen];
     * - windscreen from the centre post ([centre] half-width) out to the A-post, which runs from
     *   ([aBot], [low]) up to ([aTop], [head]);
     * - side windows back to [ends], separated by [post]-wide posts leaning [lean] as much as the
     *   A-post; the sill starts at [sill] and climbs [rise] per unit aft (the Boeing "V"), the header
     *   falls [drop] per unit aft;
     * - optional eyebrow windows above the side windows, chin windows below the windscreen, parked
     *   wipers, and a [mask] outline round the lot.
     */
    fun deck(
        label: String? = null,
        foot: Double? = null,
        anchor: Double = 0.13,
        k: Double = 1.0,
        wsLen: Double = 0.13,
        wsRise: Double = 0.17,
        roof: Double = 1.3,
        radome: Double = 0.3,
        aBot: Double = 0.12,
        low: Double = -0.03,
        aTop: Double = 0.2,
        head: Double = 0.14,
        ends: List<Double> = listOf(0.33, 0.5),
        sill: Double = low,
        rise: Double = 0.0,
        drop: Double = 0.03,
        post: Double = 0.022,
        centre: Double = 0.011,
        lean: Double = 0.4,
        rear: Rear = Rear.SQUARE,
        eyebrows: Boolean = false,
        chin: Boolean = false,
        wipers: Boolean = true,
        mask: Boolean = false,
        round: Double = 0.012,
        frame: Double = 0.006,
    ): Windshield {
        fun sd(s: Double, dz: Double) = Gp.Side(s * k, anchor + dz * k)
        fun pl(s: Double, x: Double) = Gp.Plan(s * k, x * k)
        val slant = aTop - aBot
        fun sillAt(s: Double) = sill + rise * (s - aBot)
        fun headAt(s: Double) = head - drop * (s - aTop)
        fun pane(vararg c: Gp, radii: List<Double>? = null, essential: Boolean = true) =
            Pane(c.toList(), essential = essential, round = round * k, radii = radii?.map { it * k }, frame = frame * k)

        val panes = ArrayList<Pane>()
        panes += pane(pl(0.0, centre), sd(aBot, low), sd(aTop, head), pl(wsLen, centre))
        // Side windows: bottom front, bottom rear, top rear, top front.
        var front = aBot + post
        var frontTop = aTop + post
        ends.forEachIndexed { i, end ->
            val last = i == ends.lastIndex
            val bf = sd(front, sillAt(front))
            val tf = sd(frontTop, headAt(frontTop))
            val br = sd(end, sillAt(end))
            val endTop = when {
                !last -> end + slant * lean
                rear == Rear.TAPER -> end - 0.05
                rear == Rear.SWEPT -> end - 0.02
                else -> end
            }
            val tr = sd(endTop, headAt(endTop))
            val h = headAt(end) - sillAt(end)
            panes += when {
                last && rear == Rear.NOTCH -> {
                    val cut = h * 0.35
                    pane(bf, br, sd(end, headAt(end) - cut), sd(end - cut * 1.2, headAt(end - cut * 1.2)), tf)
                }
                last && rear == Rear.ROUND -> pane(bf, br, tr, tf, radii = listOf(round, h * 0.45, h * 0.45, round))
                last && rear == Rear.SWEPT -> pane(bf, br, tr, tf, radii = listOf(round, h * 0.48, h * 0.48, round))
                else -> pane(bf, br, tr, tf)
            }
            if (eyebrows) {
                // On the roof just above the side window, following its header.
                val a = frontTop + 0.015
                val z = endTop - 0.02
                fun up(s: Double, deg: Double) = Gp.Side(s * k, anchor + headAt(s) * k, deg)
                panes += pane(up(a, 7.0), up(z, 7.0), up(z + 0.02, 19.0), up(a + 0.02, 19.0), essential = false)
            }
            front = end + post
            frontTop = end + slant * lean + post
        }
        if (chin) panes += pane(sd(0.0, low - 0.06), sd(aBot - 0.02, low - 0.04), sd(aBot - 0.04, low - 0.18), sd(0.01, low - 0.19), essential = false)

        val outline = if (!mask) null else {
            val m = 0.03
            val end = ends.last()
            Pane(listOf(
                pl(-m, 0.0), sd(aBot - m * 0.6, low - m), sd(end, sillAt(end) - m), sd(end + 0.1, sillAt(end) + 0.02),
                sd(end + 0.02, headAt(end) + m * 0.6), sd(aTop, head + m), pl(wsLen + m, 0.0),
            ), closed = false)
        }
        val count = 2 * (1 + ends.size)
        val text = label ?: buildString {
            append("$count-PANE FLIGHT DECK")
            if (eyebrows) append(" + EYEBROW WINDOWS")
            if (chin) append(" + CHIN WINDOWS")
        }
        val cab = foot?.let { Cab(it, anchor, it + wsLen * k, anchor + wsRise * k, roof, radome) }
        return Windshield(text, panes, outline, if (wipers) listOf(Wiper(0, 0, 0.8, 0.2, 0.02 * k)) else emptyList(), anchor, cab)
    }

    // ---- Airliners ------------------------------------------------------------------------------

    /** A318-A321: flat windscreens, a level sill, and the rear window's top corner cut off. */
    val AIRBUS_6 = deck(radome = 0.2, foot = 0.48, anchor = 0.11, wsLen = 0.2, wsRise = 0.18, aBot = 0.17, low = 0.0, aTop = 0.27, head = 0.18,
        ends = listOf(0.39, 0.54), drop = 0.05, lean = 0.3, rear = Rear.NOTCH)

    /** A300/A310/A330/A340: the same flight deck on a bigger nose. */
    val AIRBUS_WIDE_6 = deck(radome = 0.15, foot = 0.44, anchor = 0.13, k = 0.75, wsLen = 0.2, wsRise = 0.18, roof = 1.2, aBot = 0.17, low = 0.0,
        aTop = 0.27, head = 0.18, ends = listOf(0.39, 0.54), drop = 0.05, lean = 0.3, rear = Rear.NOTCH)

    /** A380: the flight deck sits between the two decks, low on a very tall nose. */
    val A380_6 = deck(anchor = 0.02, k = 0.56, wsLen = 0.22, aBot = 0.17, low = 0.0, aTop = 0.27, head = 0.18, ends = listOf(0.39, 0.54),
        drop = 0.05, lean = 0.3, rear = Rear.NOTCH)

    /** Belugas: the flight deck sits low at the front, below the cargo lobe. */
    val AIRBUS_BELUGA = Windshield(
        "6-PANE FLIGHT DECK",
        listOf(
            p(0.0 to 58.0, 0.0 to 16.0, 0.4 to 22.0, 0.4 to 58.0, essential = true),
            p(0.44 to 24.0, 0.44 to -2.0, 0.69 to -3.0, 0.69 to 21.0, essential = true),
            p(0.73 to 20.0, 0.73 to -3.0, 0.93 to -2.0, 0.93 to 8.0, 0.85 to 20.0, essential = true),
        ),
    )

    /** A220: a big windscreen and one large side window, rounded at the back. */
    val A220_4 = deck(radome = 0.25, foot = 0.5, anchor = 0.1, wsLen = 0.21, wsRise = 0.19, aBot = 0.18, low = 0.0, aTop = 0.28, head = 0.19,
        ends = listOf(0.5), rise = 0.03, drop = 0.1, rear = Rear.ROUND)

    /** A350: six curved panes inside the black "Zorro" mask, the rear window small and rounded. */
    val A350_6_MASK = deck(
        label = "6-PANE CURVED WINDSHIELD · MASK", foot = 0.55, anchor = 0.11, k = 0.8, wsLen = 0.26, wsRise = 0.22, roof = 1.45,
        radome = 0.4, aBot = 0.21, low = 0.0, aTop = 0.31, head = 0.2, ends = listOf(0.46, 0.58), rise = 0.05, drop = 0.22,
        lean = 0.3, rear = Rear.ROUND, mask = true, round = 0.02,
    )

    /** 737NG/MAX: the Boeing "V" (sill dipping to the A-post, then climbing aft) and the rear window narrowing to the top. */
    val B737_6 = deck(foot = 0.55, anchor = 0.07, wsLen = 0.2, wsRise = 0.22, roof = 1.45, radome = 0.35,
        aBot = 0.18, low = -0.01, aTop = 0.28, head = 0.21, ends = listOf(0.4, 0.55), rise = 0.2, drop = 0.18, lean = 0.8, rear = Rear.TAPER)

    /** 737-100/200/Classic: with the eyebrow windows above the side windows. */
    val B737_EYEBROW = deck(foot = 0.55, anchor = 0.07, wsLen = 0.2, wsRise = 0.22, roof = 1.45, radome = 0.35,
        aBot = 0.18, low = -0.01, aTop = 0.28, head = 0.21, ends = listOf(0.4, 0.55), rise = 0.2, drop = 0.18, lean = 0.8, rear = Rear.TAPER, eyebrows = true)

    /** 707/727/KC-135/E-3: the 737's ancestors, eyebrows and all. */
    val B707_EYEBROW = deck(foot = 0.55, anchor = 0.07, wsLen = 0.2, wsRise = 0.22, roof = 1.45, radome = 0.35,
        aBot = 0.18, low = -0.01, aTop = 0.28, head = 0.21, ends = listOf(0.4, 0.55), rise = 0.2, drop = 0.18, lean = 0.8, rear = Rear.TAPER, eyebrows = true)

    /** 757: the pointed nose, six panes in a strong V, the rear window rounded. */
    val B757_6 = deck(foot = 0.56, anchor = 0.07, wsLen = 0.2, wsRise = 0.22, roof = 1.5, radome = 0.4,
        aBot = 0.18, low = -0.01, aTop = 0.28, head = 0.21, ends = listOf(0.4, 0.55), rise = 0.18, drop = 0.16, lean = 0.7, rear = Rear.ROUND)

    /** 767: the same flight deck as the 757 on a wider nose. */
    val B767_6 = deck(radome = 0.25, foot = 0.5, anchor = 0.1, k = 0.8, wsLen = 0.2, wsRise = 0.22, roof = 1.35,
        aBot = 0.18, low = -0.01, aTop = 0.28, head = 0.21, ends = listOf(0.4, 0.55), rise = 0.18, drop = 0.16, lean = 0.7, rear = Rear.ROUND)

    /** 777: six panes on the round nose, a gentler V, the rear window rounded. */
    val B777_6 = deck(foot = 0.45, anchor = 0.12, k = 0.72, wsLen = 0.2, wsRise = 0.21, roof = 1.25, radome = 0.15,
        aBot = 0.18, low = 0.0, aTop = 0.28, head = 0.2, ends = listOf(0.4, 0.55), rise = 0.12, drop = 0.12, lean = 0.6, rear = Rear.ROUND)

    /** 747: on the front of the upper deck. */
    val B747_6 = deck(anchor = 0.27, k = 0.66, wsLen = 0.22, aBot = 0.18, low = -0.01, aTop = 0.27, head = 0.15, ends = listOf(0.4, 0.55),
        rise = 0.12, drop = 0.12, lean = 0.6, rear = Rear.ROUND)

    /** 747 Dreamlifter: the flight deck in front of the swollen cargo lobe. */
    val B747_LCF = deck(anchor = 0.2, k = 0.66, wsLen = 0.24, aBot = 0.18, low = -0.01, aTop = 0.26, head = 0.12, ends = listOf(0.4, 0.55),
        rise = 0.12, drop = 0.12, lean = 0.6, rear = Rear.ROUND)

    /** 747-100/200/300/SP: plus the eyebrow windows. */
    val B747_EYEBROW = deck(anchor = 0.27, k = 0.66, wsLen = 0.22, aBot = 0.18, low = -0.01, aTop = 0.27, head = 0.15, ends = listOf(0.4, 0.55),
        rise = 0.12, drop = 0.12, lean = 0.6, rear = Rear.ROUND, eyebrows = true)

    /** 787: four big panes on the long smooth nose; the side windows taper aft to a rounded point. */
    val B787_4 = deck(foot = 0.62, anchor = 0.09, k = 0.8, wsLen = 0.3, wsRise = 0.24, roof = 1.6, radome = 0.5,
        aBot = 0.24, low = -0.01, aTop = 0.36, head = 0.21, ends = listOf(0.6), rise = 0.2, drop = 0.5, lean = 0.6, rear = Rear.SWEPT,
        round = 0.02)

    /** DC-9/MD-80: squarish panes with eyebrow windows. */
    val DOUGLAS_EYEBROW = deck(foot = 0.5, anchor = 0.11, wsLen = 0.13, roof = 1.4, aBot = 0.12, low = -0.04, aTop = 0.2, head = 0.15,
        ends = listOf(0.34, 0.5), rise = 0.03, drop = 0.03, lean = 0.5, eyebrows = true)

    /** MD-90/717. */
    val DOUGLAS_6 = deck(foot = 0.5, anchor = 0.11, wsLen = 0.13, roof = 1.4, aBot = 0.12, low = -0.04, aTop = 0.2, head = 0.15,
        ends = listOf(0.34, 0.5), rise = 0.03, drop = 0.03, lean = 0.5)

    val DC10_EYEBROW = deck(foot = 0.42, anchor = 0.14, k = 0.75, roof = 1.25, aBot = 0.12, low = -0.04, aTop = 0.2, head = 0.15,
        ends = listOf(0.34, 0.5), rise = 0.03, drop = 0.03, lean = 0.5, eyebrows = true)
    val MD11_6 = deck(foot = 0.42, anchor = 0.14, k = 0.75, roof = 1.25, aBot = 0.12, low = -0.04, aTop = 0.2, head = 0.15,
        ends = listOf(0.34, 0.5), rise = 0.03, drop = 0.03, lean = 0.5)
    val L1011_6 = deck(foot = 0.42, anchor = 0.14, k = 0.75, roof = 1.25, aBot = 0.12, low = -0.04, aTop = 0.2, head = 0.15,
        ends = listOf(0.35, 0.52), rise = 0.05, drop = 0.06, lean = 0.5, rear = Rear.ROUND)

    /** E-Jets: windscreen plus one big side window each side, which is also the crew's emergency exit. */
    val EJET_4 = deck(foot = 0.5, anchor = 0.12, k = 1.1, wsLen = 0.14, roof = 1.4, aBot = 0.13, aTop = 0.21, head = 0.15, ends = listOf(0.44),
        rise = 0.05, drop = 0.1, rear = Rear.ROUND)

    /** ERJ 135/145: four panes on the long pointed nose. */
    val ERJ_4 = deck(foot = 0.75, anchor = 0.1, k = 1.15, wsLen = 0.14, roof = 1.9, radome = 0.6, aBot = 0.13, aTop = 0.21, head = 0.15,
        ends = listOf(0.42), rise = 0.05, drop = 0.1, rear = Rear.ROUND)

    /** CRJ/Challenger 600: windscreen, a side window and a small rounded rear window. */
    val CRJ_6 = deck(foot = 0.6, anchor = 0.1, k = 1.15, wsLen = 0.14, roof = 1.6, radome = 0.5, aBot = 0.13, aTop = 0.21, head = 0.15,
        ends = listOf(0.33, 0.46), rise = 0.06, drop = 0.12, rear = Rear.ROUND)

    val FOKKER_6 = deck(foot = 0.5, anchor = 0.11, k = 1.05, roof = 1.4, ends = listOf(0.34, 0.5), rise = 0.02, drop = 0.04)
    val BAE146_6 = deck(foot = 0.42, anchor = 0.12, k = 1.0, roof = 1.2, radome = 0.2, ends = listOf(0.35, 0.52), rise = 0.02, drop = 0.04)
    val ATR_6 = deck(foot = 0.5, anchor = 0.1, k = 1.15, roof = 1.45, ends = listOf(0.33, 0.47), rise = 0.05, drop = 0.1, rear = Rear.ROUND)
    val DASH8_6 = deck(foot = 0.5, anchor = 0.1, k = 1.1, roof = 1.45, ends = listOf(0.34, 0.49), rise = 0.03, drop = 0.07)

    /** Saab 340/2000, Jetstream, Brasília, Beech 1900, Dornier: windscreen and one side window. */
    val TURBOPROP_4 = deck(foot = 0.55, anchor = 0.1, k = 1.2, roof = 1.5, ends = listOf(0.42), rise = 0.05, drop = 0.1, rear = Rear.ROUND)

    val TU154_6 = deck(foot = 0.5, anchor = 0.1, roof = 1.4, low = -0.04, head = 0.14, ends = listOf(0.33, 0.48), rise = 0.02, drop = 0.04,
        lean = 0.5, eyebrows = true)
    val ARJ_6 = deck(foot = 0.5, anchor = 0.11, k = 1.05, roof = 1.4, ends = listOf(0.34, 0.5), rise = 0.02, drop = 0.04)

    /** C919: four large curved panes. */
    val COMAC_4 = deck(label = "4-PANE CURVED WINDSHIELD", foot = 0.5, anchor = 0.12, wsLen = 0.16, roof = 1.4, aBot = 0.15, aTop = 0.24,
        head = 0.16, ends = listOf(0.48), rise = 0.08, drop = 0.14, rear = Rear.ROUND, round = 0.02)

    val AN124_6 = deck(anchor = 0.1, k = 0.55, ends = listOf(0.34, 0.5), rise = 0.02, drop = 0.03, chin = true)

    /** Il-76: the glazed navigator's station in the nose, below and ahead of the flight deck. */
    val IL_GLAZED_NOSE = deck(label = "6-PANE FLIGHT DECK + GLAZED NAVIGATOR NOSE", foot = 0.62, anchor = 0.1, k = 0.75, roof = 1.4, radome = 0.3,
        ends = listOf(0.34, 0.5), rise = 0.02, drop = 0.03).let { d ->
        fun nav(vararg c: Pair<Double, Double>, mirror: Boolean = true) =
            Pane(c.map { (u, deg) -> Gp.Deg(u, deg) }, mirror = mirror, essential = true, round = 0.01, frame = 0.005)
        Windshield(d.label, d.panes + listOf(
            nav(-0.34 to -4.0, -0.34 to -40.0, -0.2 to -46.0, -0.2 to -2.0),
            nav(-0.17 to -2.0, -0.17 to -46.0, -0.03 to -50.0, -0.03 to 0.0),
            nav(-0.36 to -48.0, -0.36 to -90.0, -0.22 to -90.0, -0.22 to -52.0),
        ), d.mask, d.wipers, d.anchor, d.cab)
    }

    // ---- Military transports ---------------------------------------------------------------------

    /** C-130: windscreens, side windows, eyebrows overhead and chin windows by the pilots' feet. */
    val C130_MULTI = deck(foot = 0.3, anchor = 0.08, k = 0.85, wsLen = 0.12, wsRise = 0.2, roof = 1.0, radome = 0.15,
        aBot = 0.12, low = -0.06, aTop = 0.19, head = 0.17, ends = listOf(0.34, 0.5), drop = 0.03, eyebrows = true, chin = true)
    val C17_CHIN = deck(foot = 0.36, anchor = 0.1, k = 0.62, roof = 1.05, radome = 0.25, ends = listOf(0.34, 0.5), chin = true)
    val A400M_6 = deck(foot = 0.34, anchor = 0.1, k = 0.72, roof = 1.05, radome = 0.25, ends = listOf(0.34, 0.5), chin = true, rear = Rear.NOTCH)
    val P3_6 = deck(foot = 0.5, anchor = 0.1, roof = 1.4, ends = listOf(0.33, 0.49), rise = 0.03, drop = 0.04, eyebrows = true)
    val V22_CHIN = deck(foot = 0.45, anchor = 0.06, k = 0.9, wsRise = 0.2, roof = 1.2, low = -0.07, head = 0.16, ends = listOf(0.44),
        drop = 0.06, chin = true, rear = Rear.ROUND)
    val B52_6 = deck(foot = 0.5, anchor = 0.1, roof = 1.4, ends = listOf(0.31, 0.45), rise = 0.03, drop = 0.04, eyebrows = true)
    val AN_6 = deck(foot = 0.45, anchor = 0.1, k = 0.9, roof = 1.3, ends = listOf(0.34, 0.5), rise = 0.02, drop = 0.03)

    // ---- Business jets -------------------------------------------------------------------------------

    /** Gulfstreams: a wide windscreen and one broad side window with a rounded back. */
    val GULFSTREAM_4 = deck(foot = 0.95, anchor = 0.08, k = 1.3, wsLen = 0.14, roof = 2.0, radome = 0.6, aTop = 0.21, head = 0.15,
        ends = listOf(0.44), rise = 0.05, drop = 0.1, rear = Rear.ROUND)
    val GLOBAL_4 = deck(foot = 0.95, anchor = 0.08, k = 1.25, wsLen = 0.14, roof = 2.0, radome = 0.6, aTop = 0.21, head = 0.15,
        ends = listOf(0.42), rise = 0.04, drop = 0.1, rear = Rear.ROUND)
    val CITATION_4 = deck(foot = 1.0, anchor = 0.06, k = 1.4, wsLen = 0.14, roof = 2.1, radome = 0.6, aTop = 0.21, head = 0.15,
        ends = listOf(0.4), rise = 0.05, drop = 0.12, rear = Rear.ROUND)
    val LEARJET_4 = deck(foot = 1.2, anchor = 0.06, k = 1.35, wsLen = 0.14, roof = 2.3, radome = 0.7, aTop = 0.21, head = 0.15,
        ends = listOf(0.36), rise = 0.06, drop = 0.14, rear = Rear.ROUND)
    val FALCON_6 = deck(foot = 0.95, anchor = 0.07, k = 1.3, wsLen = 0.14, roof = 2.0, radome = 0.6, aTop = 0.21, head = 0.15,
        ends = listOf(0.33, 0.45), rise = 0.04, drop = 0.1, rear = Rear.ROUND)
    val PHENOM_4 = deck(foot = 0.95, anchor = 0.06, k = 1.4, wsLen = 0.17, wsRise = 0.2, roof = 2.0, radome = 0.55, aBot = 0.14, aTop = 0.23,
        head = 0.16, ends = listOf(0.44), rise = 0.08, drop = 0.14, rear = Rear.ROUND, round = 0.02)
    val HONDAJET_4 = deck(foot = 1.0, anchor = 0.06, k = 1.4, wsLen = 0.17, wsRise = 0.2, roof = 2.0, radome = 0.55, aBot = 0.14, aTop = 0.23,
        head = 0.16, ends = listOf(0.44), rise = 0.08, drop = 0.14, rear = Rear.ROUND, round = 0.02)
    val PC24_4 = deck(foot = 0.9, anchor = 0.07, k = 1.35, wsLen = 0.14, roof = 1.9, radome = 0.55, aTop = 0.21, head = 0.15,
        ends = listOf(0.43), rise = 0.04, drop = 0.1, rear = Rear.ROUND)
    val AVANTI_4 = deck(foot = 1.0, anchor = 0.06, k = 1.35, wsLen = 0.16, roof = 2.0, radome = 0.6, aBot = 0.14, aTop = 0.23, head = 0.16,
        ends = listOf(0.42), rise = 0.06, drop = 0.12, rear = Rear.ROUND, round = 0.02)

    /** Vision Jet: one big curved windshield and door windows. */
    val VISIONJET = Windshield(
        "ONE-PIECE WINDSHIELD",
        listOf(
            p(0.02 to 24.0, 0.44 to 40.0, 0.44 to 140.0, 0.02 to 156.0, mirror = false, essential = true),
            p(0.5 to 58.0, 0.5 to 20.0, 0.9 to 22.0, 0.84 to 52.0),
        ),
    )

    // ---- Light aircraft (zones set by `light()`, see there) --------------------------------------------

    /** Cessna singles, Beaver, Kodiak, Caravan: one-piece windshield up to the wing, door and rear side windows, rear window. */
    val GA_HIGH = Windshield(
        "ONE-PIECE WINDSHIELD",
        listOf(
            p(0.06 to 24.0, 0.27 to 38.0, 0.27 to 142.0, 0.06 to 156.0, mirror = false, essential = true),
            p(0.3 to 62.0, 0.3 to 14.0, 0.56 to 12.0, 0.56 to 64.0, essential = true),
            p(0.6 to 60.0, 0.6 to 16.0, 0.84 to 26.0, 0.78 to 58.0),
            p(0.88 to 58.0, 0.98 to 52.0, 0.98 to 128.0, 0.88 to 122.0, mirror = false),
        ),
    )

    /** Pipers, Bonanzas, Mooneys, Robin: one-piece windshield and side windows above the wing. */
    val GA_LOW = Windshield(
        "ONE-PIECE WINDSHIELD",
        listOf(
            p(0.05 to 20.0, 0.3 to 40.0, 0.3 to 140.0, 0.05 to 160.0, mirror = false, essential = true),
            p(0.32 to 70.0, 0.32 to 20.0, 0.54 to 18.0, 0.54 to 70.0, essential = true),
            p(0.57 to 64.0, 0.57 to 20.0, 0.8 to 28.0, 0.72 to 56.0),
        ),
    )

    /** Light twins: windshield, pilot door window and two cabin windows. */
    val GA_TWIN = Windshield(
        "ONE-PIECE WINDSHIELD",
        listOf(
            p(0.04 to 22.0, 0.28 to 40.0, 0.28 to 140.0, 0.04 to 158.0, mirror = false, essential = true),
            p(0.31 to 66.0, 0.31 to 18.0, 0.5 to 16.0, 0.5 to 66.0, essential = true),
            p(0.54 to 62.0, 0.54 to 18.0, 0.7 to 18.0, 0.7 to 60.0),
            p(0.74 to 58.0, 0.74 to 20.0, 0.9 to 26.0, 0.86 to 52.0),
        ),
    )

    /** Cirrus: wrap-around windshield and large gull-wing door windows. */
    val CIRRUS_WRAP = Windshield(
        "WRAP-AROUND WINDSHIELD",
        listOf(
            p(0.04 to 14.0, 0.3 to 34.0, 0.3 to 146.0, 0.04 to 166.0, mirror = false, essential = true),
            p(0.32 to 74.0, 0.32 to 14.0, 0.62 to 14.0, 0.58 to 66.0, essential = true),
            p(0.66 to 58.0, 0.66 to 20.0, 0.82 to 26.0, 0.78 to 50.0),
        ),
    )

    /** Diamond: one big tip-up bubble canopy with a frame behind the windshield. */
    val DIAMOND_BUBBLE = Windshield(
        "BUBBLE CANOPY",
        listOf(
            p(0.04 to 18.0, 0.36 to 10.0, 0.7 to 22.0, 0.7 to 158.0, 0.36 to 170.0, 0.04 to 162.0, mirror = false, essential = true),
            p(0.3 to 12.0, 0.3 to 90.0, 0.3 to 168.0, mirror = false, closed = false),
        ),
    )

    /** Single turboprops (PC-12, TBM, M600): two-piece windshield and cockpit side windows. */
    val TURBOPROP_SINGLE = Windshield(
        "2-PIECE WINDSHIELD",
        listOf(
            p(0.05 to 88.0, 0.05 to 26.0, 0.3 to 40.0, 0.3 to 88.0, essential = true),
            p(0.33 to 66.0, 0.33 to 20.0, 0.5 to 18.0, 0.5 to 64.0, essential = true),
            p(0.55 to 60.0, 0.55 to 22.0, 0.66 to 22.0, 0.66 to 60.0),
            p(0.72 to 58.0, 0.72 to 24.0, 0.84 to 26.0, 0.84 to 54.0),
        ),
    )

    val TWIN_OTTER = Windshield(
        "MULTI-PANE WINDSHIELD",
        listOf(
            p(0.05 to 88.0, 0.05 to 30.0, 0.28 to 40.0, 0.28 to 88.0, essential = true),
            p(0.3 to 64.0, 0.3 to 16.0, 0.46 to 14.0, 0.46 to 62.0, essential = true),
            p(0.52 to 58.0, 0.52 to 24.0, 0.62 to 24.0, 0.62 to 58.0),
            p(0.68 to 58.0, 0.68 to 24.0, 0.78 to 24.0, 0.78 to 58.0),
            p(0.84 to 56.0, 0.84 to 26.0, 0.94 to 28.0, 0.94 to 54.0),
        ),
    )

    /** An-2: a greenhouse of small flat panes. */
    val AN2_GREENHOUSE = Windshield(
        "MULTI-PANE GREENHOUSE",
        listOf(
            p(0.05 to 88.0, 0.05 to 56.0, 0.24 to 60.0, 0.24 to 88.0, essential = true),
            p(0.05 to 54.0, 0.05 to 24.0, 0.24 to 26.0, 0.24 to 58.0, essential = true),
            p(0.27 to 60.0, 0.27 to 20.0, 0.42 to 18.0, 0.42 to 60.0),
        ),
    )

    val AEROBATIC_BUBBLE = Windshield(
        "BUBBLE CANOPY",
        listOf(
            p(0.18 to 30.0, 0.5 to 24.0, 0.62 to 36.0, 0.62 to 144.0, 0.5 to 156.0, 0.18 to 150.0, mirror = false, essential = true),
            p(0.28 to 26.0, 0.28 to 90.0, 0.28 to 154.0, mirror = false, closed = false),
        ),
    )

    /** Tiger Moth: two open cockpits, each behind a small windscreen. */
    val OPEN_COCKPITS = Windshield(
        "OPEN COCKPITS · WINDSCREENS",
        listOf(
            p(0.3 to 64.0, 0.3 to 116.0, 0.34 to 110.0, 0.34 to 70.0, mirror = false, essential = true),
            p(0.56 to 64.0, 0.56 to 116.0, 0.6 to 110.0, 0.6 to 70.0, mirror = false, essential = true),
        ),
    )

    // ---- Canopies (zone = the canopy pod) ------------------------------------------------------------

    private val SILL = p(0.04 to 8.0, 0.5 to 5.0, 0.96 to 8.0, closed = false, essential = true)

    val CANOPY_FRAMELESS = Windshield("FRAMELESS BUBBLE CANOPY", listOf(SILL))
    val CANOPY_FRAMED = Windshield(
        "FRAMED CANOPY",
        listOf(SILL, p(0.3 to 6.0, 0.3 to 90.0, 0.3 to 174.0, mirror = false, closed = false, essential = true)),
    )
    val TANDEM_CANOPY = Windshield(
        "TANDEM CANOPY",
        listOf(
            SILL,
            p(0.24 to 6.0, 0.24 to 90.0, 0.24 to 174.0, mirror = false, closed = false, essential = true),
            p(0.58 to 5.0, 0.58 to 90.0, 0.58 to 175.0, mirror = false, closed = false, essential = true),
        ),
    )
    val GLIDER_CANOPY = Windshield("ONE-PIECE CANOPY", listOf(SILL))

    // ---- Helicopters (zone from the nose back over the cabin, see `heli()`) ------------------------------

    /** Robinsons: a two-piece bubble from the chin to the roof. */
    val HELI_BUBBLE = Windshield(
        "BUBBLE CANOPY",
        listOf(
            p(0.02 to 88.0, 0.02 to -36.0, 0.22 to -34.0, 0.3 to 20.0, 0.34 to 68.0, 0.3 to 88.0, essential = true),
            p(0.4 to 58.0, 0.4 to -20.0, 0.62 to -16.0, 0.62 to 54.0),
        ),
    )

    /** Bell 206/407/505/429: two windscreens, roof and chin windows, door windows. */
    val HELI_JETRANGER = Windshield(
        "GLAZED NOSE + CHIN WINDOWS",
        listOf(
            p(0.06 to 88.0, 0.06 to 18.0, 0.3 to 26.0, 0.3 to 88.0, essential = true),
            p(0.04 to -6.0, 0.04 to -44.0, 0.2 to -40.0, 0.2 to -4.0),
            p(0.34 to 84.0, 0.34 to 66.0, 0.48 to 66.0, 0.48 to 84.0),
            p(0.4 to 56.0, 0.4 to -12.0, 0.62 to -10.0, 0.62 to 54.0, essential = true),
        ),
    )

    /** Écureuil / H130: a big curved one-piece bubble each side and chin windows. */
    val HELI_ECUREUIL = Windshield(
        "BUBBLE WINDSHIELD + CHIN WINDOWS",
        listOf(
            p(0.02 to 88.0, 0.02 to -18.0, 0.3 to -8.0, 0.36 to 60.0, 0.3 to 88.0, essential = true),
            p(0.03 to -24.0, 0.03 to -56.0, 0.18 to -52.0, 0.18 to -26.0),
            p(0.42 to 56.0, 0.42 to -14.0, 0.64 to -10.0, 0.64 to 54.0),
        ),
    )

    /** H135/H145/BK117/H155/Dauphin/H160: large windscreen, chin windows, big door windows. */
    val HELI_EC135 = Windshield(
        "GLAZED NOSE + CHIN WINDOWS",
        listOf(
            p(0.03 to 88.0, 0.03 to 14.0, 0.3 to 22.0, 0.3 to 88.0, essential = true),
            p(0.03 to 0.0, 0.03 to -48.0, 0.22 to -44.0, 0.22 to 4.0),
            p(0.34 to 60.0, 0.34 to -18.0, 0.6 to -16.0, 0.6 to 58.0, essential = true),
            p(0.64 to 52.0, 0.64 to -8.0, 0.82 to -6.0, 0.82 to 50.0),
        ),
    )

    /** Medium and heavy utility helicopters: two windscreens, cockpit side and chin windows, cabin windows. */
    val HELI_UTILITY = Windshield(
        "2-PANE WINDSHIELD + CHIN WINDOWS",
        listOf(
            p(0.06 to 88.0, 0.06 to 24.0, 0.28 to 32.0, 0.28 to 88.0, essential = true),
            p(0.3 to 60.0, 0.3 to -6.0, 0.44 to -4.0, 0.44 to 56.0, essential = true),
            p(0.04 to 12.0, 0.04 to -44.0, 0.2 to -40.0, 0.2 to 16.0),
            p(0.52 to 46.0, 0.52 to 6.0, 0.62 to 6.0, 0.62 to 46.0),
            p(0.68 to 46.0, 0.68 to 6.0, 0.78 to 6.0, 0.78 to 46.0),
        ),
    )

    /** Apache / Mi-24: stepped tandem cockpits of flat armoured panes. */
    val HELI_ATTACK = Windshield(
        "STEPPED TANDEM CANOPY",
        listOf(
            p(0.08 to 88.0, 0.08 to 22.0, 0.3 to 30.0, 0.3 to 88.0, essential = true),
            p(0.34 to 88.0, 0.34 to 26.0, 0.58 to 32.0, 0.58 to 88.0, essential = true),
            p(0.3 to 20.0, 0.3 to 88.0, 0.3 to 160.0, mirror = false, closed = false),
        ),
    )

    /** Chinook: a wide multi-pane front, side and chin windows. */
    val HELI_CHINOOK = Windshield(
        "MULTI-PANE FRONT + CHIN WINDOWS",
        listOf(
            p(0.03 to 88.0, 0.03 to 30.0, 0.28 to 38.0, 0.28 to 88.0, essential = true),
            p(0.03 to 26.0, 0.03 to -8.0, 0.28 to -6.0, 0.28 to 34.0, essential = true),
            p(0.02 to -12.0, 0.02 to -46.0, 0.2 to -42.0, 0.2 to -10.0),
            p(0.34 to 56.0, 0.34 to 4.0, 0.6 to 4.0, 0.6 to 54.0),
        ),
    )
}
