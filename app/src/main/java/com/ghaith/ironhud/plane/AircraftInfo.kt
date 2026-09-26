package com.ghaith.ironhud.plane

import com.ghaith.ironhud.plane.models.AircraftTypes
import java.util.Locale

/** Human names for ICAO type designators, airline callsign prefixes and ADS-B categories. */
object AircraftInfo {

    fun typeName(a: Aircraft): String? =
        AircraftTypes.byCode(a.typeCode)?.name ?: a.description?.let(::titleCase) ?: a.typeCode

    /** The airline for an ICAO-style callsign ("RJA123" → Royal Jordanian), else the registered operator. */
    fun airline(a: Aircraft): String? {
        val cs = a.callsign?.uppercase(Locale.US)
        if (cs != null && cs.length >= 4 && cs.take(3).all { it in 'A'..'Z' } && cs[3].isDigit()) {
            AIRLINES[cs.take(3)]?.let { return it }
        }
        return a.operator?.let(::titleCase)
    }

    fun categoryName(code: String?): String? = code?.let { CATEGORIES[it.uppercase(Locale.US)] }

    private fun titleCase(s: String): String =
        s.lowercase(Locale.US).split(' ').joinToString(" ") { w ->
            if (w.any { it.isDigit() } || w.length <= 2) w.uppercase(Locale.US)
            else w.replaceFirstChar { it.titlecase(Locale.US) }
        }

    private val CATEGORIES = mapOf(
        "A1" to "LIGHT", "A2" to "SMALL", "A3" to "LARGE", "A4" to "LARGE · HIGH VORTEX", "A5" to "HEAVY",
        "A6" to "HIGH PERFORMANCE", "A7" to "ROTORCRAFT", "B1" to "GLIDER", "B2" to "BALLOON",
        "B3" to "PARACHUTIST", "B4" to "ULTRALIGHT", "B6" to "DRONE", "B7" to "SPACE VEHICLE",
    )

    private val AIRLINES = mapOf(
        "RJA" to "Royal Jordanian", "JAV" to "Jordan Aviation", "UAE" to "Emirates", "QTR" to "Qatar Airways",
        "ETD" to "Etihad Airways", "SVA" to "Saudia", "FDB" to "flydubai", "ABY" to "Air Arabia", "KAC" to "Kuwait Airways",
        "GFA" to "Gulf Air", "OMA" to "Oman Air", "MEA" to "Middle East Airlines", "MSR" to "EgyptAir", "NAS" to "flynas",
        "FAD" to "flyadeal", "RSI" to "Riyadh Air", "IAW" to "Iraqi Airways", "IRA" to "Iran Air", "SYR" to "Syrian Air",
        "ELY" to "El Al", "THY" to "Turkish Airlines", "PGT" to "Pegasus Airlines", "SXS" to "SunExpress", "AJT" to "AJet",
        "BAW" to "British Airways", "SHT" to "British Airways", "VIR" to "Virgin Atlantic", "EZY" to "easyJet",
        "EJU" to "easyJet Europe", "RYR" to "Ryanair", "WZZ" to "Wizz Air", "WMT" to "Wizz Air Malta", "WUK" to "Wizz Air UK",
        "DLH" to "Lufthansa", "EWG" to "Eurowings", "CFG" to "Condor", "AFR" to "Air France", "KLM" to "KLM",
        "IBE" to "Iberia", "VLG" to "Vueling", "ITY" to "ITA Airways", "SWR" to "Swiss", "AUA" to "Austrian Airlines",
        "BEL" to "Brussels Airlines", "SAS" to "Scandinavian Airlines", "FIN" to "Finnair", "LOT" to "LOT Polish Airlines",
        "TAP" to "TAP Air Portugal", "AEE" to "Aegean Airlines", "ICE" to "Icelandair", "NOZ" to "Norwegian",
        "TOM" to "TUI Airways", "EXS" to "Jet2", "AFL" to "Aeroflot", "UAL" to "United Airlines", "DAL" to "Delta Air Lines",
        "AAL" to "American Airlines", "SWA" to "Southwest Airlines", "JBU" to "JetBlue", "ASA" to "Alaska Airlines",
        "ACA" to "Air Canada", "WJA" to "WestJet", "AMX" to "Aeroméxico", "LAN" to "LATAM", "TAM" to "LATAM Brasil",
        "AVA" to "Avianca", "GLO" to "Gol", "AZU" to "Azul", "QFA" to "Qantas", "ANZ" to "Air New Zealand",
        "SIA" to "Singapore Airlines", "CPA" to "Cathay Pacific", "JAL" to "Japan Airlines", "ANA" to "All Nippon Airways",
        "KAL" to "Korean Air", "AAR" to "Asiana Airlines", "CCA" to "Air China", "CES" to "China Eastern",
        "CSN" to "China Southern", "EVA" to "EVA Air", "CAL" to "China Airlines", "THA" to "Thai Airways",
        "MAS" to "Malaysia Airlines", "GIA" to "Garuda Indonesia", "PAL" to "Philippine Airlines", "AIC" to "Air India",
        "AXB" to "Air India Express", "IGO" to "IndiGo", "PIA" to "Pakistan International", "ALK" to "SriLankan Airlines",
        "UZB" to "Uzbekistan Airways", "KZR" to "Air Astana", "ETH" to "Ethiopian Airlines", "KQA" to "Kenya Airways",
        "SAA" to "South African Airways", "RAM" to "Royal Air Maroc", "TAR" to "Tunisair", "DAH" to "Air Algérie",
        "FDX" to "FedEx", "UPS" to "UPS Airlines", "GTI" to "Atlas Air", "CLX" to "Cargolux", "BCS" to "DHL (EAT Leipzig)",
        "DHK" to "DHL Air UK", "RRR" to "Royal Air Force", "RCH" to "US Air Force (AMC)", "NATO" to "NATO",
    )
}
