// scraper.js
const { chromium } = require('playwright-extra');
const stealth = require('puppeteer-extra-plugin-stealth')();
const { resolveStationData } = require('./stationMapper');

// Inject the stealth plugin to mask the headless browser fingerprint
chromium.use(stealth);

async function scrapeTrainTelemetry(trainNumber) {
    console.log(`🚂 Initializing tracking sequence for Train: ${trainNumber}...`);

    // Launch Chromium in headless mode
    const browser = await chromium.launch({ headless: true });
    
    // Create a new browser context (acts like a fresh incognito window)
    const context = await browser.newContext();
    const page = await context.newPage();

    // RAM OPTIMIZATION: Intercept and block heavy, unnecessary network requests
    await page.route('**/*', (route) => {
        const request = route.request();
        const resourceType = request.resourceType();
        // Block images, fonts, stylesheets, and media to speed up scraping
        if (['image', 'stylesheet', 'font', 'media'].includes(resourceType)) {
            route.abort();
        } else {
            route.continue();
        }
    });

    try {
        // -----------------------------------------------------------
        // THE EXTRACTION ZONE (ConfirmTkt)
        // -----------------------------------------------------------
        const targetUrl = `https://www.confirmtkt.com/train-running-status/${trainNumber}`;
        console.log(`🌐 Navigating to ${targetUrl}...`);
        
        // Go to the site and wait for the DOM to settle
        await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });

        // Wait for the main timeline container to appear
        try {
            await page.waitForSelector('.running-status', { timeout: 15000 });
        } catch (e) {
            console.log("⚠️ Main container '.running-status' not found. Checking for alternative selectors...");
        }

        // 1. Extract raw data from the page using visual indicators (SVGs and classes)
        const rawStationData = await page.$$eval('.rs__station-row', (rows) => {
            return rows.map((row, index) => {
                const stationNameText = row.querySelector('.rs__station-name')?.innerText.trim() || "";
                const columns = row.querySelectorAll('div[class^="col-xs-"]');
                let arrivalTime = columns[2]?.innerText.trim() || "";
                let departureTime = columns[3]?.innerText.trim() || "";
                const delayStatus = row.querySelector('.rs__station-delay')?.innerText.trim() || "";

                if (index === 0 && arrivalTime === "") arrivalTime = "Source";
                if (index === rows.length - 1 && departureTime === "") departureTime = "Destination";

                // Visual status detection (more reliable than text parsing)
                // The green checkmark indicates the station has been departed
                const hasCheckmark = row.querySelector('svg.bi-check-circle') !== null;
                // The blinking circle indicates the train is currently at or approaching this station
                const isBlinking = row.querySelector('.circle.blink') !== null;

                let state = "pending";
                if (hasCheckmark) state = "passed";
                else if (isBlinking) state = "current";

                return {
                    station_name: stationNameText,
                    sequence_index: index + 1,
                    arrival: arrivalTime,
                    departure: departureTime,
                    status: delayStatus,
                    state: state // internal flag for cascading logic
                };
            });
        });

        // 2. Map standardized station data using the dictionary + Supabase
        const mappedSequenceRaw = await Promise.all(rawStationData.map(async item => {
            const resolved = await resolveStationData(item.station_name);
            return {
                ...item,
                station_code: resolved.code,
                latitude: resolved.lat,
                longitude: resolved.lon
            };
        }));

        // Filter out stations that resolved to placeholder codes and have no coordinates
        // This removes "HALT" entries and other technical stops that break routing
        const mappedSequence = mappedSequenceRaw.filter(s => s.latitude !== null && s.longitude !== null);

        if (mappedSequence.length < mappedSequenceRaw.length) {
            console.log(`🧹 Filtered out ${mappedSequenceRaw.length - mappedSequence.length} stations with missing coordinates.`);
        }

        // 3. Smart Filtering: Determine which stations have already been passed
        // Apply the visual heuristic
        let lastDepartedIndex = -1;
        mappedSequence.forEach((station, index) => {
            if (station.state === "passed") {
                lastDepartedIndex = index;
            }
        });

        const stationSequence = mappedSequence.map((station, index) => {
            const { state, ...cleanStation } = station; // Remove internal state flag
            return {
                ...cleanStation,
                has_departed: index <= lastDepartedIndex
            };
        });

        // Extract the Overall Live Status (ETA)
        let etaText = "Data unavailable";
        try {
            // Check for a global status or the first 'Current' status in the timeline
            const statusLocator = page.locator('.rs__station-delay').first();
            if (await statusLocator.isVisible()) {
                etaText = await statusLocator.innerText();
            }
        } catch (e) {
            console.log("Global status/delay not found.");
        }

        const extractedData = {
            train_number: trainNumber,
            eta_string: etaText.replace(/\n/g, ' ').trim(),
            station_sequence: stationSequence,
            timestamp_fetched: Date.now()
        };

        console.log("✅ Scrape Successful. Summary:");
        console.log(`- Stations Found: ${stationSequence.length}`);
        console.log(`- Current Status: ${extractedData.eta_string}`);
        
        return extractedData;

    } catch (error) {
        console.error("❌ Scraping failed:", error.message);
        return null;
    } finally {
        // CRITICAL: Always close the browser to prevent memory leaks
        await browser.close();
    }
}

module.exports = { scrapeTrainTelemetry };
