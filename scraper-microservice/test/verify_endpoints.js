// test/verify_endpoints.js
const axios = require('axios');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

// Set port to 7865 to avoid conflicting with anything running on default port 7860
process.env.PORT = 7865;
const PORT = 7865;
const BASE_URL = `http://localhost:${PORT}`;

// Require server.js to boot the Express app
console.log("🚂 Starting Scraper Microservice on test port 7865...");
require('../server.js');

const { createClient } = require('@supabase/supabase-js');
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_KEY);

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runTests() {
    console.log("\n==================================================");
    console.log("🔬 STARTING MICROSERVICE ENDPOINT VERIFICATION");
    console.log("==================================================\n");

    // Wait 2 seconds for server to boot and initialize station dictionary
    await sleep(2000);

    let passed = 0;
    let failed = 0;

    function report(testName, success, details) {
        if (success) {
            console.log(`✅ [PASS] ${testName}`);
            if (details) console.log(`   └─ ${details}`);
            passed++;
        } else {
            console.error(`❌ [FAIL] ${testName}`);
            if (details) console.error(`   └─ Error/Details: ${JSON.stringify(details)}`);
            failed++;
        }
    }

    // ------------------------------------------------------------------------
    // 1. GET /api/v4/train/route/cache
    // ------------------------------------------------------------------------
    console.log("--- 1. Testing GET /api/v4/train/route/cache ---");
    try {
        await axios.get(`${BASE_URL}/api/v4/train/route/cache`);
        report("GET /api/v4/train/route/cache (Missing param validation)", false, "Expected 400 but got success");
    } catch (err) {
        if (err.response && err.response.status === 400) {
            report("GET /api/v4/train/route/cache (Missing param validation)", true, `Returned status 400: ${err.response.data.error}`);
        } else {
            report("GET /api/v4/train/route/cache (Missing param validation)", false, err.message);
        }
    }

    try {
        await axios.get(`${BASE_URL}/api/v4/train/route/cache?segment_key=NON_EXISTENT_TEST_KEY_999`);
        report("GET /api/v4/train/route/cache (Cache miss handling)", false, "Expected 404 but got success");
    } catch (err) {
        if (err.response && err.response.status === 404) {
            report("GET /api/v4/train/route/cache (Cache miss handling)", true, `Returned status 404: ${err.response.data.error}`);
        } else {
            report("GET /api/v4/train/route/cache (Cache miss handling)", false, err.message);
        }
    }

    // ------------------------------------------------------------------------
    // 2. POST /api/v4/train/route/cache
    // ------------------------------------------------------------------------
    console.log("\n--- 2. Testing POST /api/v4/train/route/cache ---");
    try {
        await axios.post(`${BASE_URL}/api/v4/train/route/cache`, { segment_key: "TEST-INCOMPLETE" });
        report("POST /api/v4/train/route/cache (Incomplete data validation)", false, "Expected 400 but got success");
    } catch (err) {
        if (err.response && err.response.status === 400) {
            report("POST /api/v4/train/route/cache (Incomplete data validation)", true, `Returned status 400: ${err.response.data.error}`);
        } else {
            report("POST /api/v4/train/route/cache (Incomplete data validation)", false, err.message);
        }
    }

    const testSegmentKey = "TEST-SEGMENT-MS-TPJ-VERIFY";
    const testPayload = {
        segment_key: testSegmentKey,
        polyline: [[80.2707, 13.0827], [79.4861, 11.9401], [78.6991, 10.7860]],
        distance: 310500,
        duration: 15000
    };

    try {
        const res = await axios.post(`${BASE_URL}/api/v4/train/route/cache`, testPayload);
        if (res.status === 200 && res.data.success) {
            report("POST /api/v4/train/route/cache (Save segment to cloud cache)", true, "Successfully saved to Supabase");
        } else {
            report("POST /api/v4/train/route/cache (Save segment to cloud cache)", false, res.data);
        }
    } catch (err) {
        report("POST /api/v4/train/route/cache (Save segment to cloud cache)", false, err.message);
    }

    // Verify Read Back
    try {
        const res = await axios.get(`${BASE_URL}/api/v4/train/route/cache?segment_key=${testSegmentKey}`);
        if (res.status === 200 && res.data.cache_hit && res.data.distance === 310500) {
            report("GET /api/v4/train/route/cache (Read back saved segment)", true, `Cache hit verified! Points: ${res.data.points.length}, Distance: ${res.data.distance}m`);
        } else {
            report("GET /api/v4/train/route/cache (Read back saved segment)", false, res.data);
        }
    } catch (err) {
        report("GET /api/v4/train/route/cache (Read back saved segment)", false, err.message);
    }

    // Clean up test segment from Supabase
    try {
        await supabase.from('railway_track_cache').delete().eq('segment_key', testSegmentKey);
        console.log(`   🧹 Cleaned up test segment "${testSegmentKey}" from Supabase.`);
    } catch (e) {
        console.warn("   ⚠️ Could not clean up test segment:", e.message);
    }

    // ------------------------------------------------------------------------
    // 3. POST /api/v4/stations/correct
    // ------------------------------------------------------------------------
    console.log("\n--- 3. Testing POST /api/v4/stations/correct ---");
    try {
        await axios.post(`${BASE_URL}/api/v4/stations/correct`, { latitude: 13.0, longitude: 80.0 });
        report("POST /api/v4/stations/correct (Missing station_code validation)", false, "Expected 400 but got success");
    } catch (err) {
        if (err.response && err.response.status === 400) {
            report("POST /api/v4/stations/correct (Missing station_code validation)", true, `Returned status 400: ${err.response.data.error}`);
        } else {
            report("POST /api/v4/stations/correct (Missing station_code validation)", false, err.message);
        }
    }

    try {
        const res = await axios.post(`${BASE_URL}/api/v4/stations/correct`, {
            station_code: "MS",
            latitude: 13.0827,
            longitude: 80.2707
        });
        if (res.status === 200 && res.data.success) {
            report("POST /api/v4/stations/correct (Global station healing & cache invalidation)", true, "Successfully updated station MS in Supabase and invalidated stale cache");
        } else {
            report("POST /api/v4/stations/correct (Global station healing & cache invalidation)", false, res.data);
        }
    } catch (err) {
        report("POST /api/v4/stations/correct (Global station healing & cache invalidation)", false, err.message);
    }

    // ------------------------------------------------------------------------
    // 4. GET /api/v4/trains/search
    // ------------------------------------------------------------------------
    console.log("\n--- 4. Testing GET /api/v4/trains/search ---");
    try {
        await axios.get(`${BASE_URL}/api/v4/trains/search?source=MS`);
        report("GET /api/v4/trains/search (Missing param validation)", false, "Expected 400 but got success");
    } catch (err) {
        if (err.response && err.response.status === 400) {
            report("GET /api/v4/trains/search (Missing param validation)", true, `Returned status 400: ${err.response.data.error}`);
        } else {
            report("GET /api/v4/trains/search (Missing param validation)", false, err.message);
        }
    }

    try {
        console.log("   ⏳ Scraping live train schedules between MS (Chennai) and TPJ (Trichy)... (this may take ~5-15s)");
        const startTime = Date.now();
        const res = await axios.get(`${BASE_URL}/api/v4/trains/search?source=MS&destination=TPJ`, { timeout: 45000 });
        const durationSec = ((Date.now() - startTime) / 1000).toFixed(1);
        if (res.status === 200 && res.data.success && Array.isArray(res.data.trains)) {
            const sampleTrain = res.data.trains.length > 0 ? `${res.data.trains[0].train_number} (${res.data.trains[0].train_name})` : "None found";
            report("GET /api/v4/trains/search (Live train scraping via erail.in)", true, `Found ${res.data.count} trains in ${durationSec}s. Sample: ${sampleTrain}`);
        } else {
            report("GET /api/v4/trains/search (Live train scraping via erail.in)", false, res.data);
        }
    } catch (err) {
        report("GET /api/v4/trains/search (Live train scraping via erail.in)", false, err.message);
    }

    // ------------------------------------------------------------------------
    // 5. GET /api/v4/train/track
    // ------------------------------------------------------------------------
    console.log("\n--- 5. Testing GET /api/v4/train/track ---");
    try {
        await axios.get(`${BASE_URL}/api/v4/train/track`);
        report("GET /api/v4/train/track (Missing train_number validation)", false, "Expected 400 but got success");
    } catch (err) {
        if (err.response && err.response.status === 400) {
            report("GET /api/v4/train/track (Missing train_number validation)", true, `Returned status 400: ${err.response.data.error}`);
        } else {
            report("GET /api/v4/train/track (Missing train_number validation)", false, err.message);
        }
    }

    try {
        console.log("   ⏳ Fetching live train telemetry for Train 12605 (Pallavan Express)... (checking cache/scraping)");
        const startTime = Date.now();
        // Use ttl_mins=0 to force a fresh scrape and Supabase upsert verification
        const res = await axios.get(`${BASE_URL}/api/v4/train/track?train_number=12605&ttl_mins=0`, { timeout: 60000 });
        const durationSec = ((Date.now() - startTime) / 1000).toFixed(1);
        if (res.status === 200 && res.data.train_number === "12605" && Array.isArray(res.data.station_sequence)) {
            const stnCount = res.data.station_sequence.length;
            const statusStr = res.data.eta_string;
            const isCached = res.data.cache_hit ? "HIT (From Supabase Cache)" : "MISS (Freshly Scraped)";
            report("GET /api/v4/train/track (Live telemetry & station sequence)", true, `Status: "${statusStr}" | Stations: ${stnCount} | Cache: ${isCached} (${durationSec}s)`);
            
            // Test Cache Hit immediately after
            const cacheTestStart = Date.now();
            const cacheRes = await axios.get(`${BASE_URL}/api/v4/train/track?train_number=12605`);
            const cacheDurationMs = Date.now() - cacheTestStart;
            if (cacheRes.status === 200 && cacheRes.data.cache_hit) {
                report("GET /api/v4/train/track (Verify cloud caching TTL)", true, `Instant Cache Hit verified in ${cacheDurationMs}ms!`);
            } else {
                report("GET /api/v4/train/track (Verify cloud caching TTL)", false, `Expected cache_hit=true but got ${cacheRes.data.cache_hit}`);
            }
        } else {
            report("GET /api/v4/train/track (Live telemetry & station sequence)", false, res.data);
        }
    } catch (err) {
        report("GET /api/v4/train/track (Live telemetry & station sequence)", false, err.message);
    }

    console.log("\n==================================================");
    console.log(`📊 TEST SUMMARY: ${passed} PASSED | ${failed} FAILED`);
    console.log("==================================================\n");

    process.exit(failed > 0 ? 1 : 0);
}

runTests().catch(err => {
    console.error("Fatal test runner error:", err);
    process.exit(1);
});
