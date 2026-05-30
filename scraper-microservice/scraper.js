// scraper.js
const { chromium } = require('playwright-extra');
const stealth = require('puppeteer-extra-plugin-stealth')();

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

        // Wait for the main timeline container to appear so we know the data loaded
        // Note: You may need to tweak this class name if ConfirmTkt updates their UI
        try {
            await page.waitForSelector('.rs-timeline-info', { timeout: 15000 });
        } catch (e) {
            console.log("⚠️ Timeline container not found. The site might have updated or the train number is invalid.");
        }

        // 1. Extract the Station Sequence
        // We look for all elements containing the station codes (usually in brackets like "(MS)")
        const stationElements = await page.locator('.rs-station-name').allInnerTexts();
        
        let sequenceArray = [];
        stationElements.forEach((text, index) => {
            // Use regex to extract just the station code from a string like "Chennai Egmore (MS)"
            const match = text.match(/\((.*?)\)/);
            if (match && match[1]) {
                sequenceArray.push({
                    station_code: match[1].trim(),
                    sequence_index: index + 1
                });
            }
        });

        // 2. Extract the ETA (Live Status)
        // Find the element highlighting the current delay or arrival time
        let etaText = "Data unavailable";
        try {
            // Priority 1: Current status text
            const statusLocator = page.locator('.rs-current-status');
            if (await statusLocator.isVisible()) {
                etaText = await statusLocator.innerText();
            }
        } catch (e) {
            console.log("ETA text not found via primary selector.");
        }

        const extractedData = {
            train_number: trainNumber,
            eta_string: etaText.replace(/\n/g, ' ').trim(), // Clean up line breaks
            station_sequence: sequenceArray,
            timestamp_fetched: Date.now()
        };

        console.log("✅ Scrape Successful. Payload:");
        console.log(JSON.stringify(extractedData, null, 2));

        return extractedData;

    } catch (error) {
        console.error("❌ Scraping failed:", error.message);
        return null;
    } finally {
        // CRITICAL: Always close the browser to prevent memory leaks
        await browser.close();
    }
}

// Execute the test run
scrapeTrainTelemetry("12605");
