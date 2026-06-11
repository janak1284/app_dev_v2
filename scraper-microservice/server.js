// server.js
require('dotenv').config({ path: __dirname + '/.env' });
const express = require('express');
const { createClient } = require('@supabase/supabase-js');
const ws = require('ws');
const { scrapeTrainTelemetry } = require('./scraper'); 

const app = express();
const PORT = process.env.PORT || 7860;

const supabase = createClient(
    process.env.SUPABASE_URL, 
    process.env.SUPABASE_KEY,
    { realtime: { transport: ws } }
);

app.use(express.json());

/**
 * GET: Retrieve a cached segment from the cloud (Shared by all users)
 */
app.get('/api/v4/train/route/cache', async (req, res) => {
    const { segment_key } = req.query;
    if (!segment_key) return res.status(400).json({ error: "Missing segment_key" });

    try {
        const { data: cached, error } = await supabase
            .from('railway_track_cache')
            .select('*')
            .eq('segment_key', segment_key)
            .maybeSingle();

        if (error) throw error;

        if (cached) {
            console.log(`🎯 CACHE HIT: ${segment_key}`);
            return res.json({
                points: cached.polyline,
                distance: cached.distance,
                time: cached.duration,
                cache_hit: true
            });
        }
        console.log(`⚪ CACHE MISS: ${segment_key}`);
        res.status(404).json({ error: "Cache miss" });
    } catch (err) {
        console.error("Cache Fetch Error:", err.message);
        res.status(500).json({ error: "Database error" });
    }
});

/**
 * POST: Save a newly fetched segment to the global cloud cache
 */
app.post('/api/v4/train/route/cache', async (req, res) => {
    const { segment_key, polyline, distance, duration } = req.body;
    if (!segment_key || !polyline) return res.status(400).json({ error: "Incomplete data" });

    try {
        const { error } = await supabase
            .from('railway_track_cache')
            .upsert({
                segment_key: segment_key,
                polyline: polyline,
                distance: distance,
                duration: duration,
                last_updated: new Date().toISOString()
            });

        if (error) throw error;
        console.log(`💾 CLOUD CACHED: ${segment_key}`);
        res.json({ success: true });
    } catch (err) {
        console.error("Cache Save Error:", err.message);
        res.status(500).json({ error: "Failed to save to cloud cache" });
    }
});

/**
 * POST: Update station coordinates globally (Self-Healing)
 */
app.post('/api/v4/stations/correct', async (req, res) => {
    const { station_code, latitude, longitude } = req.body;
    if (!station_code) return res.status(400).json({ error: "Missing station_code" });

    try {
        console.log(`🛠️ GLOBAL HEALING: Station ${station_code} updated to ${latitude}, ${longitude}`);
        const { error } = await supabase
            .from('stations')
            .update({ 
                latitude: latitude, 
                longitude: longitude, 
                is_verified: true, 
                updated_at: new Date().toISOString() 
            })
            .eq('station_code', station_code);

        if (error) throw error;

        // Clear any old/broken cache entries involving this station
        await supabase
            .from('railway_track_cache')
            .delete()
            .or(`segment_key.ilike.${station_code}-%,segment_key.ilike.%-${station_code}`);

        res.json({ success: true });
    } catch (err) {
        console.error("Coordinate Update Error:", err.message);
        res.status(500).json({ error: "Failed to update global station data" });
    }
});

app.get('/api/v4/train/track', async (req, res) => {
    const trainNumber = req.query.train_number;
    const forceRefresh = req.query.force_refresh === 'true';
    const customTtl = req.query.ttl_mins;
    if (!trainNumber) return res.status(400).json({ error: "Missing train_number" });

    try {
        const { data: cacheData } = await supabase.from('train_cache').select('*').eq('train_number', trainNumber).maybeSingle();
        const ttlMinutes = customTtl && !isNaN(customTtl) ? parseInt(customTtl) : (forceRefresh ? 3 : 10);
        if (cacheData) {
            const age = (Date.now() - new Date(cacheData.last_updated).getTime()) / (1000 * 60);
            if (age < ttlMinutes) return res.json({ ...cacheData.payload, cache_hit: true, timestamp_fetched: new Date(cacheData.last_updated).getTime(), server_time: Date.now() });
        }
        const scrapedData = await scrapeTrainTelemetry(trainNumber);
        if (!scrapedData) return res.status(500).json({ error: "Scrape failed" });
        await supabase.from('train_cache').upsert({ train_number: trainNumber, payload: scrapedData, last_updated: new Date().toISOString() });
        res.json({ ...scrapedData, cache_hit: false, timestamp_fetched: Date.now(), server_time: Date.now() });
    } catch (err) {
        console.error("Telemetry Error:", err.message);
        res.status(500).json({ error: "Internal Error" });
    }
});

app.listen(PORT, '0.0.0.0', () => console.log(`🚂 V4 Cloud Memory running on port ${PORT}`));
