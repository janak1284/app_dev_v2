// server.js
require('dotenv').config({ path: __dirname + '/.env' });
const express = require('express');
const { createClient } = require('@supabase/supabase-js');
const ws = require('ws');
const { scrapeTrainTelemetry } = require('./scraper'); 

const app = express();
// Hugging Face strictly requires port 7860
const PORT = process.env.PORT || 7860;

// Initialize Supabase with WebSocket support for Node 20
const supabase = createClient(
    process.env.SUPABASE_URL, 
    process.env.SUPABASE_KEY,
    {
        realtime: {
            transport: ws,
        },
    }
);

app.use(express.json());

app.get('/api/v4/train/track', async (req, res) => {
    const trainNumber = req.query.train_number;
    const forceRefresh = req.query.force_refresh === 'true';

    if (!trainNumber) {
        return res.status(400).json({ error: "Missing train_number parameter" });
    }

    try {
        // 1. CHECK THE CACHE
        const { data: cacheData, error } = await supabase
            .from('train_cache')
            .select('*')
            .eq('train_number', trainNumber)
            .single();

        // 2. EVALUATE CACHE AGE (10 Min Default, 3 Min for Manual Refresh)
        const ttlMinutes = forceRefresh ? 3 : 10;
        
        if (cacheData) {
            const lastUpdated = new Date(cacheData.last_updated).getTime();
            const ageInMinutes = (Date.now() - lastUpdated) / (1000 * 60);

            if (ageInMinutes < ttlMinutes) {
                console.log(`⚡ CACHE HIT (${forceRefresh ? 'Manual' : 'Auto'}): Returning data for ${trainNumber}`);
                return res.json({
                    ...cacheData.payload,
                    cache_hit: true,
                    timestamp_fetched: lastUpdated,
                    server_time: Date.now()
                });
            }
            console.log(`⏳ CACHE EXPIRED: Data is ${ageInMinutes.toFixed(1)} mins old (TTL: ${ttlMinutes}m). Re-scraping...`);
        }

        // 3. CACHE MISS: FIRE UP PLAYWRIGHT
        console.log(`🚀 CACHE MISS: Launching scraper for ${trainNumber}...`);
        const scrapedData = await scrapeTrainTelemetry(trainNumber);

        if (!scrapedData) {
            return res.status(500).json({ error: "Failed to extract train data." });
        }

        // 4. UPSERT NEW DATA TO SUPABASE
        await supabase
            .from('train_cache')
            .upsert({ 
                train_number: trainNumber, 
                payload: scrapedData,
                last_updated: new Date().toISOString()
            });

        // 5. RETURN FRESH DATA
        const now = Date.now();
        return res.json({
            ...scrapedData,
            cache_hit: false,
            timestamp_fetched: now,
            server_time: now
        });

    } catch (err) {
        console.error("Server Error:", err);
        return res.status(500).json({ error: "Internal Server Error" });
    }
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚂 V4 Microservice running on port ${PORT}`);
});
