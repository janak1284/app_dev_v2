package com.janak.location.alarm.ui.components

data class StationItem(
    val name: String,
    val code: String,
    val city: String
)

object StationDictionary {
    val COMMON_STATIONS = listOf(
        // Tamil Nadu & Puducherry
        StationItem("CHENNAI CENTRAL", "MAS", "Chennai"),
        StationItem("CHENNAI EGMORE", "MS", "Chennai"),
        StationItem("TAMBARAM", "TBM", "Chennai"),
        StationItem("CHENGALPATTU JN", "CGL", "Chengalpattu"),
        StationItem("VILLUPURAM JN", "VM", "Villupuram"),
        StationItem("VRIDHACHALAM JN", "VRI", "Vridhachalam"),
        StationItem("TIRUCHCHIRAPPALLI JN", "TPJ", "Trichy"),
        StationItem("PONMLAI GLD RCK", "GOC", "Trichy"),
        StationItem("THANJAVUR JN", "TJ", "Thanjavur"),
        StationItem("KUMBAKONAM", "KMU", "Kumbakonam"),
        StationItem("MAYILADUTURAI JN", "MV", "Mayiladuthurai"),
        StationItem("NAGAPPATTINAM", "NGT", "Nagapattinam"),
        StationItem("KARAIKKUDI JN", "KKDI", "Karaikudi"),
        StationItem("PUDUKKOTTAI", "PDKT", "Pudukkottai"),
        StationItem("MADURAI JN", "MDU", "Madurai"),
        StationItem("DINDIGUL JN", "DG", "Dindigul"),
        StationItem("VIRUDUNAGAR JN", "VPT", "Virudhunagar"),
        StationItem("TIRUNELVELI JN", "TEN", "Tirunelveli"),
        StationItem("TUTICORIN", "TN", "Thoothukudi"),
        StationItem("RAMESWARAM", "RMM", "Rameswaram"),
        StationItem("SALEM JN", "SA", "Salem"),
        StationItem("ERODE JN", "ED", "Erode"),
        StationItem("TIRUPPUR", "TUP", "Tiruppur"),
        StationItem("COIMBATORE JN", "CBE", "Coimbatore"),
        StationItem("METTUPALAYAM", "MTP", "Coimbatore"),
        StationItem("KATPADI JN", "KPD", "Vellore"),
        StationItem("JOLARPETTAI JN", "JTJ", "Jolarpettai"),
        StationItem("ARAKKONAM JN", "AJJ", "Arakkonam"),
        StationItem("HOSUR", "HSRA", "Hosur"),
        StationItem("PUDUCHERRY", "PDY", "Puducherry"),

        // Karnataka
        StationItem("KSR BENGALURU", "SBC", "Bangalore"),
        StationItem("YESVANTPUR JN", "YPR", "Bangalore"),
        StationItem("BENGALURU CANT", "BNC", "Bangalore"),
        StationItem("KRISHNARAJAPURM", "KJM", "Bangalore"),
        StationItem("MYSURU JN", "MYS", "Mysore"),
        StationItem("HUBBALLI JN", "UBL", "Hubli"),
        StationItem("DHARWAD", "DWR", "Dharwad"),
        StationItem("BELAGAVI", "BGM", "Belgaum"),
        StationItem("MANGALURU CNTL", "MAQ", "Mangalore"),
        StationItem("MANGALURU JN", "MAJN", "Mangalore"),
        StationItem("KALABURAGI", "KLBG", "Gulbarga"),
        StationItem("RAICHUR", "RC", "Raichur"),
        StationItem("BALLARI JN", "BAY", "Bellary"),
        StationItem("DAVANGERE", "DVG", "Davangere"),
        StationItem("SHIVAMOGGA TOWN", "SMET", "Shimoga"),
        StationItem("HASSAN", "HAS", "Hassan"),
        StationItem("UDUPI", "UD", "Udupi"),

        // Kerala
        StationItem("TRIVANDRUM CNTL", "TVC", "Trivandrum"),
        StationItem("KOLLAM JN", "QLN", "Kollam"),
        StationItem("ALAPPUZHA", "ALLP", "Alleppey"),
        StationItem("KOTTAYAM", "KTYM", "Kottayam"),
        StationItem("ERNAKULAM JN", "ERS", "Kochi"),
        StationItem("ERNAKULAM TOWN", "ERN", "Kochi"),
        StationItem("THRISSUR", "TCR", "Thrissur"),
        StationItem("PALAKKAD JN", "PGT", "Palakkad"),
        StationItem("SHORANUR JN", "SRR", "Shoranur"),
        StationItem("KOZHIKODE", "CLT", "Calicut"),
        StationItem("KANNUR", "CAN", "Kannur"),
        StationItem("KASARAGOD", "KGQ", "Kasaragod"),

        // Andhra Pradesh & Telangana
        StationItem("SECUNDERABAD JN", "SC", "Hyderabad"),
        StationItem("HYDERABAD DECAN", "HYB", "Hyderabad"),
        StationItem("KACHEGUDA", "KCG", "Hyderabad"),
        StationItem("KAZIPET JN", "KZJ", "Warangal"),
        StationItem("WARANGAL", "WL", "Warangal"),
        StationItem("KHAMMAM", "KMT", "Khammam"),
        StationItem("NIZAMABAD", "NZB", "Nizamabad"),
        StationItem("VIJAYAWADA JN", "BZA", "Vijayawada"),
        StationItem("GUNTUR JN", "GNT", "Guntur"),
        StationItem("TENALI JN", "TEL", "Tenali"),
        StationItem("NELLORE", "NLR", "Nellore"),
        StationItem("ONGOLE", "OGL", "Ongole"),
        StationItem("TIRUPATI", "TPTY", "Tirupati"),
        StationItem("RENIGUNTA JN", "RU", "Tirupati"),
        StationItem("GUDUR JN", "GDR", "Gudur"),
        StationItem("VISAKHAPATNAM", "VSKP", "Visakhapatnam"),
        StationItem("RAJAHMUNDRY", "RJY", "Rajahmundry"),
        StationItem("KAKINADA TOWN", "CCT", "Kakinada"),
        StationItem("ELURU", "EE", "Eluru"),
        StationItem("KURNOOL CITY", "KRNT", "Kurnool"),
        StationItem("ANANTAPUR", "ATP", "Anantapur"),
        StationItem("CUDDAPAH JN", "HX", "Kadapa"),

        // Maharashtra
        StationItem("MUMBAI CENTRAL", "MMCT", "Mumbai"),
        StationItem("CHHATRAPATI SHIVAJI MAHARAJ TERMINUS", "CSTM", "Mumbai"),
        StationItem("DADAR", "DDR", "Mumbai"),
        StationItem("LOKMANYATILAK T", "LTT", "Mumbai"),
        StationItem("BANDRA TERMINUS", "BDTS", "Mumbai"),
        StationItem("BORIVALI", "BVI", "Mumbai"),
        StationItem("THANE", "TNA", "Thane"),
        StationItem("KALYAN JN", "KYN", "Kalyan"),
        StationItem("PUNE JN", "PUNE", "Pune"),
        StationItem("LONAVALA", "LNL", "Lonavala"),
        StationItem("SOLAPUR JN", "SUR", "Solapur"),
        StationItem("CHHATRAPATI SHAHU MAHARAJ TERMINUS", "KOP", "Kolhapur"),
        StationItem("NASIK ROAD", "NK", "Nashik"),
        StationItem("MANMAD JN", "MMR", "Manmad"),
        StationItem("BHUSAVAL JN", "BSL", "Bhusaval"),
        StationItem("JALGAON JN", "JL", "Jalgaon"),
        StationItem("AKOLA JN", "AK", "Akola"),
        StationItem("AMRAVATI", "AMI", "Amravati"),
        StationItem("NAGPUR", "NGP", "Nagpur"),
        StationItem("WARDHA JN", "WR", "Wardha"),
        StationItem("NANDED", "NED", "Nanded"),
        StationItem("AURANGABAD", "AWB", "Aurangabad"),

        // Gujarat
        StationItem("AHMEDABAD JN", "ADI", "Ahmedabad"),
        StationItem("VADODARA JN", "BRC", "Vadodara"),
        StationItem("SURAT", "ST", "Surat"),
        StationItem("RAJKOT JN", "RJT", "Rajkot"),
        StationItem("JAMNAGAR", "JAM", "Jamnagar"),
        StationItem("BHAVNAGAR TRMUS", "BVC", "Bhavnagar"),
        StationItem("BHUJ", "BHUJ", "Bhuj"),
        StationItem("GANDHIDHAM BG", "GIMB", "Gandhidham"),
        StationItem("VAPI", "VAPI", "Vapi"),
        StationItem("BHARUCH JN", "BH", "Bharuch"),
        StationItem("PALANPUR JN", "PNU", "Palanpur"),

        // Rajasthan
        StationItem("JAIPUR", "JP", "Jaipur"),
        StationItem("AJMER JN", "AII", "Ajmer"),
        StationItem("JODHPUR JN", "JU", "Jodhpur"),
        StationItem("UDAIPUR CITY", "UDZ", "Udaipur"),
        StationItem("BIKANER JN", "BKN", "Bikaner"),
        StationItem("KOTA JN", "KOTA", "Kota"),
        StationItem("ABU ROAD", "ABR", "Mount Abu"),
        StationItem("ALWAR", "AWR", "Alwar"),
        StationItem("BHARATPUR JN", "BTE", "Bharatpur"),

        // Delhi & NCR
        StationItem("NEW DELHI", "NDLS", "Delhi"),
        StationItem("DELHI", "DLI", "Delhi"),
        StationItem("HAZRAT NIZAMUDDIN", "NZM", "Delhi"),
        StationItem("ANAND VIHAR TRM", "ANVT", "Delhi"),
        StationItem("DELHI SARAI ROHILLA", "DEE", "Delhi"),
        StationItem("GHAZIABAD", "GZB", "Ghaziabad"),
        StationItem("FARIDABAD", "FDB", "Faridabad"),
        StationItem("GURGAON", "GGN", "Gurugram"),

        // Uttar Pradesh
        StationItem("LUCKNOW NR", "LKO", "Lucknow"),
        StationItem("LUCKNOW NE", "LJN", "Lucknow"),
        StationItem("KANPUR CENTRAL", "CNB", "Kanpur"),
        StationItem("PRAYAGRAJ JN", "PRYJ", "Prayagraj"),
        StationItem("PRAYAGRAJ CHHEOKI", "PCOI", "Prayagraj"),
        StationItem("VARANASI JN", "BSB", "Varanasi"),
        StationItem("PT DEEN DAYAL UPADHYAY JN", "DDU", "Mughalsarai"),
        StationItem("AYODHYA DHAM JN", "AY", "Ayodhya"),
        StationItem("AYODHYA CANTT", "AYC", "Ayodhya"),
        StationItem("GORAKHPUR JN", "GKP", "Gorakhpur"),
        StationItem("AGRA CANTT", "AGC", "Agra"),
        StationItem("AGRA FORT", "AF", "Agra"),
        StationItem("MATHURA JN", "MTJ", "Mathura"),
        StationItem("VGLB JHANSI JN", "VGLJ", "Jhansi"),
        StationItem("ALIGARH JN", "ALJN", "Aligarh"),
        StationItem("MEERUT CITY", "MTC", "Meerut"),
        StationItem("BAREILLY", "BE", "Bareilly"),
        StationItem("MORADABAD", "MB", "Moradabad"),
        StationItem("SAHARANPUR", "SRE", "Saharanpur"),

        // Bihar & Jharkhand
        StationItem("PATNA JN", "PNBE", "Patna"),
        StationItem("RAJENDRA NAGAR", "RJPB", "Patna"),
        StationItem("DANAPUR", "DNR", "Patna"),
        StationItem("PATLIPUTRA", "PPTA", "Patna"),
        StationItem("GAYA JN", "GAYA", "Gaya"),
        StationItem("MUZAFFARPUR JN", "MFP", "Muzaffarpur"),
        StationItem("DARBHANGA JN", "DBG", "Darbhanga"),
        StationItem("BHAGALPUR", "BGP", "Bhagalpur"),
        StationItem("CHHAPRA", "CPR", "Chhapra"),
        StationItem("KATIHAR JN", "KIR", "Katihar"),
        StationItem("SAMASTIPUR JN", "SPJ", "Samastipur"),
        StationItem("ARA JN", "ARA", "Ara"),
        StationItem("BUXAR", "BXR", "Buxar"),
        StationItem("DHANBAD JN", "DHN", "Dhanbad"),
        StationItem("RANCHI", "RNC", "Ranchi"),
        StationItem("TATANAGAR JN", "TATA", "Jamshedpur"),
        StationItem("JASIDIH JN", "JSME", "Deoghar"),
        StationItem("BOKARO STL CITY", "BKSC", "Bokaro"),

        // West Bengal & Odisha
        StationItem("HOWRAH JN", "HWH", "Kolkata"),
        StationItem("SEALDAH", "SDAH", "Kolkata"),
        StationItem("KOLKATA", "KOAA", "Kolkata"),
        StationItem("SHALIMAR", "SHM", "Kolkata"),
        StationItem("ASANSOL JN", "ASN", "Asansol"),
        StationItem("DURGAPUR", "DGR", "Durgapur"),
        StationItem("KHARAGPUR JN", "KGP", "Kharagpur"),
        StationItem("SILIGURI JN", "SGUJ", "Siliguri"),
        StationItem("NEW JALPAIGURI", "NJP", "Siliguri"),
        StationItem("MALDA TOWN", "MLDT", "Malda"),
        StationItem("BARDHAMAN JN", "BWN", "Bardhaman"),
        StationItem("BHUBANESWAR", "BBS", "Bhubaneswar"),
        StationItem("CUTTACK", "CTC", "Cuttack"),
        StationItem("PURI", "PURI", "Puri"),
        StationItem("ROURKELA", "ROU", "Rourkela"),
        StationItem("SAMBALPUR", "SBP", "Sambalpur"),
        StationItem("BALASORE", "BLS", "Balasore"),
        StationItem("BRAHMAPUR", "BAM", "Berhampur"),

        // Madhya Pradesh & Chhattisgarh
        StationItem("BHOPAL JN", "BPL", "Bhopal"),
        StationItem("RANI KAMALAPATI", "RKMP", "Bhopal"),
        StationItem("INDORE JN BG", "INDB", "Indore"),
        StationItem("UJJAIN JN", "UJN", "Ujjain"),
        StationItem("GWALIOR", "GWL", "Gwalior"),
        StationItem("JABALPUR", "JBP", "Jabalpur"),
        StationItem("KATNI", "KTE", "Katni"),
        StationItem("SATNA", "STA", "Satna"),
        StationItem("REWA", "REWA", "Rewa"),
        StationItem("ITARSI JN", "ET", "Itarsi"),
        StationItem("KHANDWA", "KNW", "Khandwa"),
        StationItem("RAIPUR JN", "RPR", "Raipur"),
        StationItem("BILASPUR JN", "BSP", "Bilaspur"),
        StationItem("DURG", "DURG", "Durg"),

        // Punjab, Haryana, J&K, Uttarakhand, Assam
        StationItem("AMRITSAR JN", "ASR", "Amritsar"),
        StationItem("LUDHIANA JN", "LDH", "Ludhiana"),
        StationItem("JALANDHAR CITY", "JUC", "Jalandhar"),
        StationItem("AMBALA CANT JN", "UMB", "Ambala"),
        StationItem("CHANDIGARH", "CDG", "Chandigarh"),
        StationItem("KALKA", "KLK", "Kalka"),
        StationItem("JAMMU TAWI", "JAT", "Jammu"),
        StationItem("SHMATA VD KATRA", "SVDK", "Katra"),
        StationItem("DEHRADUN", "DDN", "Dehradun"),
        StationItem("HARIDWAR JN", "HW", "Haridwar"),
        StationItem("ROORKEE", "RK", "Roorkee"),
        StationItem("GUWAHATI", "GHY", "Guwahati"),
        StationItem("KAMAKHYA", "KYQ", "Guwahati"),
        StationItem("DIBRUGARH", "DBRG", "Dibrugarh"),
        StationItem("LUMDING JN", "LMG", "Lumding"),
        StationItem("SILCHAR", "SCL", "Silchar")
    )

    fun search(query: String): List<StationItem> {
        if (query.trim().isEmpty()) return emptyList()
        val q = query.trim().lowercase()
        
        val exact = COMMON_STATIONS.filter { 
            it.code.lowercase() == q || 
            it.name.lowercase().startsWith(q) || 
            it.city.lowercase().startsWith(q) 
        }
        val partial = COMMON_STATIONS.filter { 
            !exact.contains(it) && (
                it.name.lowercase().contains(q) || 
                it.city.lowercase().contains(q) || 
                it.code.lowercase().contains(q)
            ) 
        }
        return (exact + partial).take(6)
    }
}
