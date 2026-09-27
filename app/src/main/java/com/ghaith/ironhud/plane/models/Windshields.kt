package com.ghaith.ironhud.plane.models

/**
 * Flight-deck glazing for each aircraft family, as [Pane]s in (u, deg) coordinates of the builder's
 * glazing zone (see [Pane]). Airliner decks come from [deck], which covers the classic layouts: a
 * windscreen each side, one or two side windows, optional eyebrow and chin windows. Canopies,
 * bubbles and helicopter noses are drawn pane by pane.
 */
object Windshields {

    private fun p(vararg c: Pair<Double, Double>, mirror: Boolean = true, closed: Boolean = true, essential: Boolean = false) =
        Pane(c.toList(), mirror, closed, essential)

    /**
     * A jet or turboprop flight deck. Windscreen from u 0 to [front] between the centre post
     * ([post]°) and the outer post ([low]° at the bottom); side windows from [sideStart] to
     * [sideEnd] between [sideLow]° and [sideHigh]°, split at [split] unless [single].
     */
    fun deck(
        label: String? = null,
        front: Double = 0.40,
        low: Double = 48.0,
        post: Double = 86.0,
        sideStart: Double = 0.45,
        split: Double = 0.70,
        sideEnd: Double = 0.94,
        sideLow: Double = 27.0,
        sideHigh: Double = 54.0,
        single: Boolean = false,
        rounded: Boolean = false,
        notch: Boolean = false,
        eyebrows: Boolean = false,
        chin: Boolean = false,
    ): Windshield {
        val panes = ArrayList<Pane>()
        panes += p(0.0 to post, 0.0 to low, front to low + 6, front to post, essential = true)
        if (single) {
            panes += if (rounded) {
                p(sideStart to sideHigh, sideStart to sideLow, sideEnd - 0.08 to sideLow - 2, sideEnd to sideLow + 8,
                    sideEnd - 0.04 to sideHigh - 12, sideEnd - 0.14 to sideHigh - 4, essential = true)
            } else {
                p(sideStart to sideHigh, sideStart to sideLow, sideEnd to sideLow - 2, sideEnd to sideHigh - 6, essential = true)
            }
        } else {
            panes += p(sideStart to sideHigh, sideStart to sideLow, split to sideLow - 1, split to sideHigh - 3, essential = true)
            val s2 = split + 0.04
            panes += when {
                notch -> p(s2 to sideHigh - 4, s2 to sideLow - 1, sideEnd to sideLow, sideEnd to sideLow + 10, sideEnd - 0.08 to sideHigh - 4)
                rounded -> p(s2 to sideHigh - 4, s2 to sideLow - 1, sideEnd - 0.05 to sideLow, sideEnd to sideLow + 8, sideEnd - 0.06 to sideHigh - 10)
                else -> p(s2 to sideHigh - 4, s2 to sideLow - 1, sideEnd to sideLow + 2, sideEnd - 0.06 to sideHigh - 10)
            }
        }
        if (eyebrows) {
            panes += p(sideStart + 0.02 to sideHigh + 15, sideStart + 0.02 to sideHigh + 7, split - 0.03 to sideHigh + 5, split - 0.03 to sideHigh + 11)
            panes += p(split + 0.06 to sideHigh + 9, split + 0.06 to sideHigh + 3, sideEnd - 0.12 to sideHigh, sideEnd - 0.12 to sideHigh + 5)
        }
        if (chin) panes += p(0.06 to -6.0, 0.06 to -38.0, 0.36 to -34.0, 0.36 to -4.0)
        val count = 2 * (1 + if (single) 1 else 2)
        val text = label ?: buildString {
            append("$count-PANE FLIGHT DECK")
            if (eyebrows) append(" + EYEBROW WINDOWS")
            if (chin) append(" + CHIN WINDOWS")
        }
        return Windshield(text, panes)
    }

    // ---- Airliners ------------------------------------------------------------------------------

    /** A300/A310/A320/A330/A340/A380: flat panes, the rear side window with Airbus's cut corner. */
    val AIRBUS_6 = deck(front = 0.40, low = 50.0, sideStart = 0.44, split = 0.69, sideEnd = 0.93, sideLow = 30.0, sideHigh = 55.0, notch = true)

    /** Belugas: the flight deck sits low at the front, below the cargo lobe. */
    val AIRBUS_BELUGA = deck(front = 0.40, low = 16.0, post = 58.0, sideStart = 0.44, split = 0.69, sideEnd = 0.93, sideLow = -2.0, sideHigh = 24.0, notch = true)

    /** A220: big windscreen and one large side window. */
    val A220_4 = deck(front = 0.42, low = 46.0, sideStart = 0.47, sideEnd = 0.9, sideLow = 26.0, sideHigh = 57.0, single = true, rounded = true)

    /** A350: four curved panes framed by the black "mask". */
    val A350_4_MASK = Windshield(
        "4-PANE CURVED WINDSHIELD · MASK",
        listOf(
            p(0.0 to 87.0, 0.0 to 42.0, 0.44 to 47.0, 0.44 to 87.0, essential = true),
            p(0.48 to 60.0, 0.48 to 26.0, 0.82 to 24.0, 0.92 to 34.0, 0.9 to 46.0, 0.78 to 56.0, essential = true),
        ),
        mask = p(-0.06 to 90.0, -0.06 to 36.0, 0.46 to 18.0, 0.9 to 18.0, 1.0 to 34.0, 0.96 to 54.0, 0.7 to 66.0, 0.5 to 90.0),
    )

    /** 757/767/777/747: six flat panes, no eyebrows. */
    val BOEING_6 = deck(front = 0.42, low = 44.0, sideStart = 0.46, split = 0.72, sideEnd = 0.95, sideLow = 26.0, sideHigh = 56.0)

    /** 737NG/MAX. */
    val B737_6 = deck(front = 0.38, low = 46.0, sideStart = 0.42, split = 0.66, sideEnd = 0.9, sideLow = 29.0, sideHigh = 55.0)

    /** 737-200/Classic: with the overhead "eyebrow" windows. */
    val B737_EYEBROW = deck(front = 0.38, low = 46.0, sideStart = 0.42, split = 0.66, sideEnd = 0.9, sideLow = 29.0, sideHigh = 55.0, eyebrows = true)

    /** 707/727/KC-135/E-3. */
    val B707_EYEBROW = deck(front = 0.40, low = 46.0, sideStart = 0.44, split = 0.68, sideEnd = 0.92, sideLow = 28.0, sideHigh = 54.0, eyebrows = true)

    /** 787: four large panes, the side window swept up at the back. */
    val B787_4 = Windshield(
        "4-PANE FLIGHT DECK",
        listOf(
            p(0.0 to 87.0, 0.0 to 40.0, 0.46 to 45.0, 0.46 to 87.0, essential = true),
            p(0.5 to 60.0, 0.5 to 22.0, 0.88 to 20.0, 0.98 to 32.0, 0.9 to 50.0, 0.74 to 58.0, essential = true),
        ),
    )

    val DOUGLAS_EYEBROW = deck(front = 0.38, low = 46.0, sideStart = 0.42, split = 0.67, sideEnd = 0.9, sideLow = 28.0, sideHigh = 54.0, eyebrows = true)
    val DOUGLAS_6 = deck(front = 0.38, low = 46.0, sideStart = 0.42, split = 0.67, sideEnd = 0.9, sideLow = 28.0, sideHigh = 54.0)
    val DC10_EYEBROW = deck(front = 0.42, low = 44.0, sideStart = 0.46, split = 0.71, sideEnd = 0.94, sideLow = 27.0, sideHigh = 56.0, eyebrows = true)
    val MD11_6 = deck(front = 0.42, low = 44.0, sideStart = 0.46, split = 0.71, sideEnd = 0.94, sideLow = 27.0, sideHigh = 56.0)
    val L1011_6 = deck(front = 0.42, low = 46.0, sideStart = 0.46, split = 0.72, sideEnd = 0.95, sideLow = 28.0, sideHigh = 57.0, rounded = true)

    /** E-Jets: windscreen plus one big side window, which is also the crew's emergency exit. */
    val EJET_4 = deck(front = 0.44, low = 44.0, sideStart = 0.49, sideEnd = 0.9, sideLow = 25.0, sideHigh = 56.0, single = true)
    val ERJ_6 = deck(front = 0.36, low = 48.0, sideStart = 0.4, split = 0.64, sideEnd = 0.86, sideLow = 28.0, sideHigh = 52.0)
    val CRJ_6 = deck(front = 0.36, low = 46.0, sideStart = 0.4, split = 0.64, sideEnd = 0.88, sideLow = 28.0, sideHigh = 52.0, rounded = true)
    val FOKKER_6 = deck(front = 0.38, low = 46.0, sideStart = 0.42, split = 0.67, sideEnd = 0.9, sideLow = 28.0, sideHigh = 54.0)
    val BAE146_6 = deck(front = 0.4, low = 44.0, sideStart = 0.44, split = 0.69, sideEnd = 0.93, sideLow = 27.0, sideHigh = 56.0)
    val ATR_6 = deck(front = 0.4, low = 42.0, sideStart = 0.44, split = 0.7, sideEnd = 0.94, sideLow = 24.0, sideHigh = 54.0, rounded = true)
    val DASH8_6 = deck(front = 0.4, low = 44.0, sideStart = 0.44, split = 0.7, sideEnd = 0.94, sideLow = 25.0, sideHigh = 55.0)
    val TURBOPROP_4 = deck(front = 0.4, low = 42.0, sideStart = 0.45, sideEnd = 0.86, sideLow = 24.0, sideHigh = 54.0, single = true, rounded = true)
    val TU154_6 = deck(front = 0.4, low = 44.0, sideStart = 0.44, split = 0.7, sideEnd = 0.94, sideLow = 26.0, sideHigh = 55.0, eyebrows = true)
    val ARJ_6 = deck(front = 0.38, low = 46.0, sideStart = 0.42, split = 0.67, sideEnd = 0.9, sideLow = 28.0, sideHigh = 54.0)
    val COMAC_4 = deck(label = "4-PANE CURVED WINDSHIELD", front = 0.44, low = 44.0, sideStart = 0.48, sideEnd = 0.92, sideLow = 26.0, sideHigh = 57.0, single = true, rounded = true)
    val AN124_6 = deck(front = 0.42, low = 42.0, sideStart = 0.46, split = 0.72, sideEnd = 0.95, sideLow = 24.0, sideHigh = 56.0, chin = true)

    /** Il-76: the glazed navigator's nose below and ahead of the flight deck. */
    val IL_GLAZED_NOSE = Windshield(
        "6-PANE FLIGHT DECK + GLAZED NAVIGATOR NOSE",
        deck(front = 0.4, low = 44.0, sideStart = 0.44, split = 0.7, sideEnd = 0.94, sideLow = 26.0, sideHigh = 55.0).panes + listOf(
            p(-0.72 to -8.0, -0.72 to -48.0, -0.46 to -52.0, -0.46 to -6.0, essential = true),
            p(-0.42 to -4.0, -0.42 to -50.0, -0.18 to -52.0, -0.18 to -2.0),
            p(-0.78 to -50.0, -0.78 to -90.0, -0.78 to -130.0, mirror = false, closed = false),
        ),
    )

    // ---- Military transports ---------------------------------------------------------------------

    val C130_MULTI = deck(front = 0.44, low = 36.0, sideStart = 0.48, split = 0.72, sideEnd = 0.95, sideLow = 18.0, sideHigh = 52.0, eyebrows = true, chin = true)
    val C17_CHIN = deck(front = 0.42, low = 40.0, sideStart = 0.46, split = 0.72, sideEnd = 0.95, sideLow = 24.0, sideHigh = 54.0, chin = true)
    val A400M_6 = deck(front = 0.42, low = 40.0, sideStart = 0.46, split = 0.72, sideEnd = 0.95, sideLow = 22.0, sideHigh = 54.0, chin = true)
    val P3_6 = deck(front = 0.42, low = 42.0, sideStart = 0.46, split = 0.7, sideEnd = 0.93, sideLow = 26.0, sideHigh = 55.0, eyebrows = true)
    val V22_CHIN = deck(front = 0.44, low = 30.0, sideStart = 0.48, sideEnd = 0.92, sideLow = 10.0, sideHigh = 52.0, single = true, chin = true)
    val B52_6 = deck(front = 0.42, low = 44.0, sideStart = 0.46, split = 0.7, sideEnd = 0.93, sideLow = 26.0, sideHigh = 55.0, eyebrows = true)
    val AN_6 = deck(front = 0.42, low = 40.0, sideStart = 0.46, split = 0.72, sideEnd = 0.95, sideLow = 22.0, sideHigh = 55.0)

    // ---- Business jets -------------------------------------------------------------------------------

    /** Gulfstreams: wide windscreen and a broad side window with a rounded back. */
    val GULFSTREAM_4 = deck(front = 0.44, low = 42.0, sideStart = 0.49, sideEnd = 0.9, sideLow = 24.0, sideHigh = 56.0, single = true, rounded = true)
    val GLOBAL_4 = deck(front = 0.44, low = 42.0, sideStart = 0.49, sideEnd = 0.88, sideLow = 26.0, sideHigh = 55.0, single = true)
    val CITATION_4 = deck(front = 0.42, low = 44.0, sideStart = 0.47, sideEnd = 0.8, sideLow = 26.0, sideHigh = 52.0, single = true, rounded = true)
    val LEARJET_4 = deck(front = 0.4, low = 46.0, sideStart = 0.45, sideEnd = 0.74, sideLow = 30.0, sideHigh = 52.0, single = true)
    val FALCON_6 = deck(front = 0.42, low = 44.0, sideStart = 0.46, split = 0.7, sideEnd = 0.9, sideLow = 26.0, sideHigh = 54.0, rounded = true)
    val PHENOM_4 = deck(front = 0.44, low = 40.0, sideStart = 0.49, sideEnd = 0.84, sideLow = 22.0, sideHigh = 54.0, single = true, rounded = true)
    val HONDAJET_4 = deck(front = 0.46, low = 40.0, sideStart = 0.5, sideEnd = 0.84, sideLow = 22.0, sideHigh = 54.0, single = true, rounded = true)
    val PC24_4 = deck(front = 0.44, low = 42.0, sideStart = 0.48, sideEnd = 0.86, sideLow = 24.0, sideHigh = 54.0, single = true)
    val AVANTI_4 = deck(front = 0.44, low = 40.0, sideStart = 0.48, sideEnd = 0.84, sideLow = 22.0, sideHigh = 54.0, single = true, rounded = true)

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
