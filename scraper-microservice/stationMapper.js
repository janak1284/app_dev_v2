// stationMapper.js
require('dotenv').config({ path: __dirname + '/.env' });
const fs = require('fs');
const path = require('path');
const { createClient } = require('@supabase/supabase-js');

// Initialize Supabase for coordinate lookups
const supabase = createClient(
    process.env.SUPABASE_URL, 
    process.env.SUPABASE_KEY
);

// 1. The in-memory Hash Map and Normalized List
const stationDictionary = {};
const normalizedStationList = [];

// Station Fallbacks for common routes and demo modes
const FALLBACKS = {
  "ms": { code: "MS", lat: 13.0827, lon: 80.2707 },
  "chennai": { code: "MS", lat: 13.0827, lon: 80.2707 },
  "chennai egmore": { code: "MS", lat: 13.0827, lon: 80.2707 },
  "chennai central": { code: "MAS", lat: 13.0827, lon: 80.2707 },
  "mumbai": { code: "MMCT", lat: 18.9696, lon: 72.8193 },
  "mumbai central": { code: "MMCT", lat: 18.9696, lon: 72.8193 },
  "delhi": { code: "NDLS", lat: 28.6429, lon: 77.2191 },
  "new delhi": { code: "NDLS", lat: 28.6429, lon: 77.2191 },
  "bangalore": { code: "SBC", lat: 12.9784, lon: 77.5697 },
  "bengaluru": { code: "SBC", lat: 12.9784, lon: 77.5697 },
  "hyderabad": { code: "HYB", lat: 17.3924, lon: 78.4688 },
  "kolkata": { code: "HWH", lat: 22.5853, lon: 88.3432 },
  "howrah": { code: "HWH", lat: 22.5853, lon: 88.3432 },
  "villupuram": { code: "VM", lat: 11.9401, lon: 79.4861 },
  "vridhachalam": { code: "VRI", lat: 11.5176, lon: 79.3251 },
  "trichy": { code: "TPJ", lat: 10.7860, lon: 78.6991 },
  "tiruchirappalli": { code: "TPJ", lat: 10.7860, lon: 78.6991 },
  "tiruchchirappalli": { code: "TPJ", lat: 10.7860, lon: 78.6991 },
  "tiruchchirapali": { code: "TPJ", lat: 10.7860, lon: 78.6991 },
  "ponmlai gld rck": { code: "GOC", lat: 10.791837, lon: 78.709755 },
  "pudukkottai": { code: "PDKT", lat: 10.3725, lon: 78.8019 },
  "karaikkuidi": { code: "KKDI", lat: 10.0747, lon: 78.7854 },
  "prayagrajcheoki": { code: "PCOI", lat: 25.3770586, lon: 81.8671292 },
  "dildarnagar jn": { code: "DLN", lat: 25.4194, lon: 83.6683 },
  "ara jn": { code: "ARA", lat: 25.5645, lon: 84.6641 }
};

/**
 * Advanced String Normalization for Indian Railway Stations
 * Strips out operational noise words and common abbreviations.
 */
function normalizeStationName(name) {
    if (!name) return "";
    let stripped = name
        .toLowerCase()
        .replace(/\b(jn|junction|terminal|term|cantt|cnt|central|ctc|halt|hltr|gld|rck|road|rd)\b/g, "")
        .replace(/[^a-z0-9]/g, "") // Remove spaces and special characters for phonetic compression
        .trim();
        
    // If stripping removed everything (e.g., station is literally named "HALT" or "ROAD"),
    // return the compressed original name instead to avoid empty strings.
    if (stripped === "") {
        return name.toLowerCase().replace(/[^a-z0-9]/g, "").trim();
    }
    
    return stripped;
}

/**
 * Levenshtein Distance Algorithm to calculate edit distance between two strings
 */
function getLevenshteinDistance(str1, str2) {
    const track = Array(str2.length + 1).fill(null).map(() => Array(str1.length + 1).fill(null));
    for (let i = 0; i <= str1.length; i += 1) track[0][i] = i;
    for (let j = 0; j <= str2.length; j += 1) track[j][0] = j;
    
    for (let j = 1; j <= str2.length; j += 1) {
        for (let i = 1; i <= str1.length; i += 1) {
            const indicator = str1[i - 1] === str2[j - 1] ? 0 : 1;
            track[j][i] = Math.min(
                track[j][i - 1] + 1, // deletion
                track[j - 1][i] + 1, // insertion
                track[j - 1][i - 1] + indicator // substitution
            );
        }
    }
    return track[str2.length][str1.length];
}

/**
 * Calculates string similarity percentage (0 to 1)
 */
function getStringSimilarity(str1, str2) {
    const maxLength = Math.max(str1.length, str2.length);
    if (maxLength === 0) return 1.0;
    return (maxLength - getLevenshteinDistance(str1, str2)) / maxLength;
}

// 2. Compile the dictionary ONCE when the server boots
function initializeMapper() {
    try {
        console.log("🗄️ Compiling Station Dictionary from GeoJSON...");
        
        const filePath = path.join(__dirname, 'stations.json');
        if (!fs.existsSync(filePath)) {
            console.error(`❌ stations.json not found at ${filePath}.`);
            return;
        }

        const rawData = fs.readFileSync(filePath, 'utf8');
        const geoJson = JSON.parse(rawData);

        if (geoJson.features && Array.isArray(geoJson.features)) {
            geoJson.features.forEach(feature => {
                if (feature.properties && feature.properties.name && feature.properties.code) {
                    const name = feature.properties.name;
                    const code = feature.properties.code;
                    const coords = feature.geometry?.coordinates;
                    const entry = {
                        name: name,
                        normalizedName: normalizeStationName(name),
                        code: code,
                        lat: coords ? coords[1] : null,
                        lon: coords ? coords[0] : null
                    };
                    
                    stationDictionary[name.toLowerCase()] = entry;
                    normalizedStationList.push(entry);
                }
            });
            console.log(`✅ Loaded ${normalizedStationList.length} stations into memory.`);
        } else {
            console.warn("⚠️ GeoJSON structure is unexpected.");
        }

    } catch (error) {
        console.error("❌ Failed to load stations.json:", error.message);
    }
}

/**
 * Permanent Station-to-Code Resolution Engine
 */
async function resolveStationData(rawScrapedName) {
    if (!rawScrapedName) return { code: "UNKNOWN", lat: null, lon: null };

    // Step -1: Code Extraction from name (e.g., "Tiruchchirappalli Jn (TPJ)" or "CHENNAI - MS")
    const codeMatch = rawScrapedName.match(/\(([A-Z0-9]{2,6})\)/) || rawScrapedName.match(/\s-\s([A-Z0-9]{2,6})$/);
    if (codeMatch) {
        const extractedCode = codeMatch[1].toUpperCase();
        // Look up this code in our dictionary
        const entry = normalizedStationList.find(e => e.code === extractedCode);
        if (entry) return entry;
        
        // If not in local dict, it might be valid but missing coords. 
        // We'll try to find it by code in Supabase later.
    }

    const scrapedNormal = normalizeStationName(rawScrapedName);

    // Step 0: Station Fallbacks (High Priority)
    const fallbackKey = rawScrapedName.toLowerCase().replace(/\s+/g, ' ').trim();
    if (FALLBACKS[scrapedNormal]) return FALLBACKS[scrapedNormal];
    if (FALLBACKS[fallbackKey]) return FALLBACKS[fallbackKey];

    // Step 1: Strict Normal Match
    for (const entry of normalizedStationList) {
        if (entry.normalizedName === scrapedNormal || entry.code === scrapedNormal.toUpperCase()) {
            return entry;
        }
    }

    // Step 2: Substring/Token Containment Match
    // (Removed because it aggressively matches false positives like "agra" in "prayagrajcheoki")
    // for (const entry of normalizedStationList) {
    //     if (entry.normalizedName.length < 4) continue;
    //     if (scrapedNormal.length > 3 && (scrapedNormal.includes(entry.normalizedName) || entry.normalizedName.includes(scrapedNormal))) {
    //         return entry;
    //     }
    // }

    // Step 3: Fuzzy Levenshtein Distance Match
    let bestMatch = null;
    let highestSimilarity = 0;
    const SIM_THRESHOLD = 0.75; // Increased threshold for better accuracy

    for (const entry of normalizedStationList) {
        const similarity = getStringSimilarity(scrapedNormal, entry.normalizedName);
        if (similarity > highestSimilarity) {
            highestSimilarity = similarity;
            bestMatch = entry;
        }
    }

    if (highestSimilarity >= SIM_THRESHOLD && bestMatch) {
        console.log(`🎯 Fuzzy Match Success: "${rawScrapedName}" matched to [${bestMatch.code}] (Confidence: ${(highestSimilarity * 100).toFixed(1)}%)`);
        return bestMatch;
    }

    // Step 4: Supabase Fallback (By Code or Name)
    try {
        const searchCode = codeMatch ? codeMatch[1].toUpperCase() : null;
        let query = supabase.from('stations').select('station_name, station_code, latitude, longitude');
        
        if (searchCode) {
            query = query.eq('station_code', searchCode);
        } else {
            query = query.ilike('station_name', `%${scrapedNormal}%`);
        }
        
        const { data } = await query.limit(1);

        if (data && data.length > 0) {
            return {
                code: data[0].station_code,
                lat: data[0].latitude,
                lon: data[0].longitude
            };
        }
    } catch (e) {
        console.error(`❌ Supabase lookup failed for ${rawScrapedName}:`, e.message);
    }

    // Step 5: Absolute Fallback
    console.error(`🚨 Fatal Mapping Failure: Unable to resolve code for "${rawScrapedName}"`);
    const finalCode = codeMatch ? codeMatch[1].toUpperCase() : rawScrapedName.toUpperCase().replace(/[^A-Z]/g, "").substring(0, 5);
    return {
        code: finalCode || "HALT",
        lat: null,
        lon: null
    };
}

/**
 * Lightning-fast station autocomplete search across all 8,989 stations
 */
function searchStations(query) {
    if (!query || query.trim().length < 1) return [];
    const q = query.trim().toLowerCase();
    const qNorm = normalizeStationName(q);
    
    const exactMatches = [];
    const partialMatches = [];
    
    for (const entry of normalizedStationList) {
        if (entry.code.toLowerCase() === q || entry.name.toLowerCase().startsWith(q)) {
            exactMatches.push(entry);
        } else if (entry.name.toLowerCase().includes(q) || (qNorm.length >= 2 && entry.normalizedName.includes(qNorm))) {
            partialMatches.push(entry);
        }
        if (exactMatches.length >= 15) break;
    }
    
    const results = [...exactMatches, ...partialMatches].slice(0, 15);
    return results.map(r => ({
        name: r.name,
        code: r.code,
        lat: r.lat,
        lon: r.lon
    }));
}

// Deprecated alias for legacy support
function getStationCode(rawName) {
    const normal = normalizeStationName(rawName);
    const match = normalizedStationList.find(e => e.normalizedName === normal);
    return match ? match.code : rawName.toUpperCase().replace(/[^A-Z]/g, "").substring(0, 5);
}

// Execute the initialization immediately
initializeMapper();

module.exports = {
    resolveStationData,
    getStationCode,
    searchStations,
    FALLBACKS
};
