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
        const currentStation = rawStationData.find(s => s.state === "current");
        if (currentStation && currentStation.status) {
            etaText = currentStation.status;
        } else {
            const nextStation = rawStationData.find(s => s.state === "pending");
            if (nextStation && nextStation.status) {
                etaText = nextStation.status;
            } else if (rawStationData.length > 0) {
                etaText = rawStationData[rawStationData.length - 1].status || etaText;
            }
        }

        // Extract Last Updated time
        let lastUpdatedWebsiteMs = null;
        try {
            const updateTimeLocator = page.locator('.train-update__time').first();
            if (await updateTimeLocator.isVisible()) {
                const updateTimeText = await updateTimeLocator.innerText();
                // Example text: "Last Updated: 11 Jun 2026 08:57, (Disclaimer..."
                const match = updateTimeText.match(/Last Updated:\s*([\d]{1,2}\s+[a-zA-Z]{3}\s+[\d]{4}\s+\d{2}:\d{2})/i);
                if (match && match[1]) {
                    lastUpdatedWebsiteMs = new Date(match[1]).getTime();
                }
            }
        } catch (e) {
            console.log("Last updated time not found.");
        }

        const extractedData = {
            train_number: trainNumber,
            eta_string: etaText.replace(/\n/g, ' ').trim().replace(/right time/ig, 'On time'),
            station_sequence: stationSequence,
            timestamp_fetched: Date.now(),
            last_updated_website_ms: lastUpdatedWebsiteMs
        };

        if (stationSequence.length === 0) {
            throw new Error("No stations extracted from primary source");
        }

        console.log("✅ Scrape Successful. Summary:");
        console.log(`- Stations Found: ${stationSequence.length}`);
        console.log(`- Current Status: ${extractedData.eta_string}`);
        
        return extractedData;

    } catch (error) {
        console.error("⚠️ Primary scraping failed:", error.message);
        console.log("🔄 Triggering backup scraper (Fallback)...");
        return await scrapeTrainTelemetryFallback(trainNumber, page);
    } finally {
        // CRITICAL: Always close the browser to prevent memory leaks
        await browser.close();
    }
}

async function scrapeTrainTelemetryFallback(trainNumber, page) {
    try {
        const fallbackUrl = `https://erail.in/train-running-status/${trainNumber}`;
        console.log(`🌐 Navigating to backup source: ${fallbackUrl}...`);
        await page.goto(fallbackUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
        await page.waitForTimeout(4000);

        // Extract station table from erail fallback
        const rawStationData = await page.evaluate(() => {
            const rows = document.querySelectorAll('table tr');
            const results = [];
            rows.forEach((row, idx) => {
                const cells = row.querySelectorAll('td');
                if (cells.length >= 4) {
                    const stnName = cells[1]?.innerText.trim() || cells[0]?.innerText.trim();
                    const arr = cells[2]?.innerText.trim() || "";
                    const dep = cells[3]?.innerText.trim() || "";
                    if (stnName && stnName.length > 2 && !stnName.toLowerCase().includes('station')) {
                        results.push({
                            station_name: stnName,
                            sequence_index: results.length + 1,
                            arrival: arr || "Source",
                            departure: dep || "Destination",
                            status: "On time",
                            state: "pending"
                        });
                    }
                }
            });
            return results;
        });

        if (rawStationData.length === 0) {
            console.error("❌ Fallback scraper returned 0 stations.");
            return null;
        }

        const mappedSequenceRaw = await Promise.all(rawStationData.map(async item => {
            const resolved = await resolveStationData(item.station_name);
            return {
                ...item,
                station_code: resolved.code,
                latitude: resolved.lat,
                longitude: resolved.lon
            };
        }));

        const mappedSequence = mappedSequenceRaw.filter(s => s.latitude !== null && s.longitude !== null);

        const stationSequence = mappedSequence.map((station, index) => {
            const { state, ...cleanStation } = station;
            return {
                ...cleanStation,
                has_departed: false
            };
        });

        console.log(`✅ Fallback Scrape Successful (${stationSequence.length} stations found).`);
        return {
            train_number: trainNumber,
            eta_string: "Live status via backup feed",
            station_sequence: stationSequence,
            timestamp_fetched: Date.now(),
            last_updated_website_ms: Date.now()
        };
    } catch (err) {
        console.error("❌ Fallback scraping completely failed:", err.message);
        return null;
    }
}

async function scrapeTrainsBetweenStations(source, destination) {
    console.log(`🔍 Searching trains between ${source} and ${destination}...`);
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext();
    const page = await context.newPage();

    await page.route('**/*', (route) => {
        const resourceType = route.request().resourceType();
        if (['image', 'stylesheet', 'font', 'media'].includes(resourceType)) {
            route.abort();
        } else {
            route.continue();
        }
    });

    try {
        const url = `https://erail.in/trains-between-stations/${source}/${destination}`;
        console.log(`🌐 Navigating to ${url}...`);
        try {
            await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 });
        } catch (navErr) {
            console.log("⚠️ Navigation timeout exceeded, attempting to read DOM anyway...");
        }

        const btn = page.locator('input[type="button"][value="Get Trains"], button:has-text("Get Trains")').first();
        if (await btn.isVisible()) {
            await btn.click();
        }
        await page.waitForTimeout(5000);

        let trains = [];
        for (let attempt = 1; attempt <= 3; attempt++) {
            trains = await page.evaluate(() => {
                const results = [];
                const divs = document.querySelectorAll('div.OneTrain');
                divs.forEach(d => {
                    const text = d.innerText || "";
                    const lines = text.split('\n').map(l => l.trim()).filter(Boolean);
                    const trainNumMatch = text.match(/\b(\d{5})\b/);
                    if (trainNumMatch && lines.length >= 2) {
                        const trainNum = trainNumMatch[1];
                        const trainName = lines[0].replace(trainNum, '').trim();
                        // Try to find time formats
                        const times = lines.filter(l => /^\d{2}:\d{2}$/.test(l) || /\d+\.\d+\s*hr/i.test(l));
                        results.push({
                            train_number: trainNum,
                            train_name: trainName || `Train ${trainNum}`,
                            departure: times[0] || "Sch. Dep",
                            arrival: times[1] || "Sch. Arr"
                        });
                    }
                });
                return results;
            });
            if (trains.length > 0) break;
            if (attempt < 3) {
                console.log(`⏳ Attempt ${attempt} yielded 0 trains, waiting 3 seconds before retrying...`);
                await page.waitForTimeout(3000);
            }
        }

        console.log(`✅ Extracted ${trains.length} trains.`);
        return trains;
    } catch (err) {
        console.error("❌ Train search failed:", err.message);
        return [];
    } finally {
        await browser.close();
    }
}

module.exports = { scrapeTrainTelemetry, scrapeTrainsBetweenStations };
