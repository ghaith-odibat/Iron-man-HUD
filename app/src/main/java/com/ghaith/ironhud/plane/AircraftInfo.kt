package com.ghaith.ironhud.plane

import java.util.Locale

/** Human names for ICAO type designators, airline callsign prefixes and ADS-B categories. */
object AircraftInfo {

    fun typeName(a: Aircraft): String? =
        a.typeCode?.let { TYPES[it.uppercase(Locale.US)] } ?: a.description?.let(::titleCase) ?: a.typeCode

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

    private val TYPES = mapOf(
        "A318" to "Airbus A318", "A319" to "Airbus A319", "A320" to "Airbus A320", "A321" to "Airbus A321",
        "A19N" to "Airbus A319neo", "A20N" to "Airbus A320neo", "A21N" to "Airbus A321neo",
        "A306" to "Airbus A300-600", "A310" to "Airbus A310", "A332" to "Airbus A330-200", "A333" to "Airbus A330-300",
        "A337" to "Airbus BelugaXL", "A338" to "Airbus A330-800neo", "A339" to "Airbus A330-900neo",
        "A342" to "Airbus A340-200", "A343" to "Airbus A340-300", "A345" to "Airbus A340-500", "A346" to "Airbus A340-600",
        "A359" to "Airbus A350-900", "A35K" to "Airbus A350-1000", "A388" to "Airbus A380-800",
        "BCS1" to "Airbus A220-100", "BCS3" to "Airbus A220-300", "A400" to "Airbus A400M Atlas",
        "B712" to "Boeing 717", "B733" to "Boeing 737-300", "B734" to "Boeing 737-400", "B735" to "Boeing 737-500",
        "B736" to "Boeing 737-600", "B737" to "Boeing 737-700", "B738" to "Boeing 737-800", "B739" to "Boeing 737-900",
        "B37M" to "Boeing 737 MAX 7", "B38M" to "Boeing 737 MAX 8", "B39M" to "Boeing 737 MAX 9", "B3XM" to "Boeing 737 MAX 10",
        "B752" to "Boeing 757-200", "B753" to "Boeing 757-300", "B762" to "Boeing 767-200", "B763" to "Boeing 767-300",
        "B764" to "Boeing 767-400", "B772" to "Boeing 777-200", "B77L" to "Boeing 777-200LR", "B773" to "Boeing 777-300",
        "B77W" to "Boeing 777-300ER", "B778" to "Boeing 777-8", "B779" to "Boeing 777-9", "B788" to "Boeing 787-8",
        "B789" to "Boeing 787-9", "B78X" to "Boeing 787-10", "B744" to "Boeing 747-400", "B748" to "Boeing 747-8",
        "MD11" to "McDonnell Douglas MD-11", "MD82" to "McDonnell Douglas MD-82", "MD83" to "McDonnell Douglas MD-83",
        "MD88" to "McDonnell Douglas MD-88", "DC10" to "McDonnell Douglas DC-10",
        "E170" to "Embraer E170", "E75L" to "Embraer E175", "E75S" to "Embraer E175", "E190" to "Embraer E190",
        "E195" to "Embraer E195", "E290" to "Embraer E190-E2", "E295" to "Embraer E195-E2", "E135" to "Embraer ERJ-135",
        "E145" to "Embraer ERJ-145", "CRJ2" to "Bombardier CRJ200", "CRJ7" to "Bombardier CRJ700",
        "CRJ9" to "Bombardier CRJ900", "CRJX" to "Bombardier CRJ1000", "AT43" to "ATR 42-300", "AT45" to "ATR 42-500",
        "AT46" to "ATR 42-600", "AT72" to "ATR 72", "AT75" to "ATR 72-500", "AT76" to "ATR 72-600",
        "DH8A" to "De Havilland Dash 8-100", "DH8C" to "De Havilland Dash 8-300", "DH8D" to "De Havilland Dash 8 Q400",
        "SF34" to "Saab 340", "B190" to "Beechcraft 1900", "SU95" to "Sukhoi Superjet 100", "C919" to "COMAC C919",
        "AJ27" to "COMAC ARJ21", "C172" to "Cessna 172 Skyhawk", "C152" to "Cessna 152", "C182" to "Cessna 182 Skylane",
        "C208" to "Cessna 208 Caravan", "P28A" to "Piper PA-28 Cherokee", "SR22" to "Cirrus SR22", "DA40" to "Diamond DA40",
        "DA42" to "Diamond DA42", "PC12" to "Pilatus PC-12", "PC24" to "Pilatus PC-24", "TBM9" to "Daher TBM 900",
        "BE20" to "Beechcraft King Air 200", "BE36" to "Beechcraft Bonanza", "GLF4" to "Gulfstream IV",
        "GLF5" to "Gulfstream V", "GLF6" to "Gulfstream G650", "GLEX" to "Bombardier Global Express",
        "GL5T" to "Bombardier Global 5000", "GL7T" to "Bombardier Global 7500", "CL35" to "Bombardier Challenger 350",
        "CL60" to "Bombardier Challenger 600", "C56X" to "Cessna Citation Excel", "C68A" to "Cessna Citation Latitude",
        "C700" to "Cessna Citation Longitude", "C750" to "Cessna Citation X", "E50P" to "Embraer Phenom 100",
        "E55P" to "Embraer Phenom 300", "F2TH" to "Dassault Falcon 2000", "FA7X" to "Dassault Falcon 7X",
        "FA8X" to "Dassault Falcon 8X", "LJ45" to "Learjet 45", "LJ75" to "Learjet 75", "H25B" to "Hawker 800",
        "C130" to "Lockheed C-130 Hercules", "C30J" to "Lockheed C-130J Super Hercules", "C17" to "Boeing C-17 Globemaster III",
        "K35R" to "Boeing KC-135 Stratotanker", "A124" to "Antonov An-124 Ruslan", "IL76" to "Ilyushin Il-76",
        "F16" to "General Dynamics F-16", "EUFI" to "Eurofighter Typhoon", "EC35" to "Airbus H135", "EC45" to "Airbus H145",
        "AS50" to "Airbus H125 Écureuil", "A139" to "Leonardo AW139", "A169" to "Leonardo AW169", "B06" to "Bell 206",
        "B407" to "Bell 407", "R44" to "Robinson R44", "R22" to "Robinson R22", "S76" to "Sikorsky S-76",
        "H60" to "Sikorsky UH-60 Black Hawk", "H47" to "Boeing CH-47 Chinook",
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
