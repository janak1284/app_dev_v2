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
        // TODO: Replace with the actual target URL (e.g., RailYatri, ConfirmTkt)
        const targetUrl = `https://www.google.com/search?q=train+${trainNumber}+status`; 
        console.log(`🌐 Navigating to ${targetUrl}...`);
        
        // Wait until the network is mostly idle, ensuring dynamic JS has loaded
        await page.goto(targetUrl, { waitUntil: 'networkidle', timeout: 60000 });

        // -----------------------------------------------------------
        // THE EXTRACTION ZONE
        // -----------------------------------------------------------
        // Here is where you will write your fuzzy DOM locators.
        // Example logic (will fail until we target a real site):
        
        // const etaText = await page.locator('.eta-class-name').innerText();
        // const stationNodes = await page.locator('.station-list-item').allInnerTexts();

        // -----------------------------------------------------------

        // Mocking the extraction for now based on your API Contract
        const extractedData = {
            train_number: trainNumber,
            eta_epoch_ms: Date.now() + 3600000, // Dummy data: 1 hour from now
            station_sequence: [
                { station_code: "MS", sequence_index: 1 },
                { station_code: "TBM", sequence_index: 2 }
            ],
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
