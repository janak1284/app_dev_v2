// stationMapper.js
const fs = require('fs');
const path = require('path');

// 1. The in-memory Hash Map
const stationDictionary = {};

// 2. Compile the dictionary ONCE when the server boots
function initializeMapper() {
    try {
        console.log("🗄️ Compiling Station Dictionary from GeoJSON...");
        
        // Read the local GeoJSON file
        const filePath = path.join(__dirname, 'stations.json');
        
        if (!fs.existsSync(filePath)) {
            console.error(`❌ stations.json not found at ${filePath}. Creating a mock file for environment stability.`);
            // Create a small mock if it doesn't exist just to prevent crash, 
            // though user says they added it.
            return;
        }

        const rawData = fs.readFileSync(filePath, 'utf8');
        const geoJson = JSON.parse(rawData);

        // Loop through all features and map name -> code
        if (geoJson.features && Array.isArray(geoJson.features)) {
            geoJson.features.forEach(feature => {
                if (feature.properties && feature.properties.name && feature.properties.code) {
                    // Store the name in lowercase for easy matching
                    const cleanName = feature.properties.name.toLowerCase().trim();
                    stationDictionary[cleanName] = feature.properties.code;
                }
            });
            console.log(`✅ Loaded ${Object.keys(stationDictionary).length} stations into memory.`);
        } else {
            console.warn("⚠️ GeoJSON structure is unexpected (missing features array).");
        }

    } catch (error) {
        console.error("❌ Failed to load stations.json. Ensure the file exists.", error.message);
    }
}

// 3. The Extraction Logic (Preserves string cleaning for ConfirmTkt's quirks)
function getStationCode(rawName) {
    if (!rawName) return "UNKNOWN";

    // Clean ConfirmTkt's string to match the strict GeoJSON names
    let cleanName = rawName.toLowerCase()
        .replace(/\bjn\b/g, '')
        .replace(/\bjunction\b/g, '')
        .replace(/\bcantt\b/g, '')
        .trim();

    // Instant O(1) lookup in our generated dictionary
    const code = stationDictionary[cleanName];

    if (code) {
        return code;
    } else {
        // Fallback: If no match, try to extract from brackets if present
        const match = rawName.match(/\((.*?)\)/);
        if (match && match[1]) {
            return match[1].trim().toUpperCase();
        }
        
        console.warn(`⚠️ No exact match for "${rawName}". Sending cleaned name.`);
        return cleanName.toUpperCase();
    }
}

// Execute the initialization immediately when this file is imported
initializeMapper();

module.exports = { getStationCode };
