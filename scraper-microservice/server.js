// server.js
const express = require('express');
const app = express();

// Hugging Face Spaces automatically routes traffic to port 7860
const PORT = process.env.PORT || 7860;

// Middleware to parse incoming JSON
app.use(express.json());

// Your primary tracking endpoint skeleton
app.get('/api/v4/train/track', async (req, res) => {
    const trainNumber = req.query.train_number;

    if (!trainNumber) {
        return res.status(400).json({ error: "Please provide a train_number query parameter." });
    }

    // TODO: Connect this to Supabase and scraper.js
    res.json({
        message: `Microservice is live. Ready to track Train ${trainNumber}`,
        status: "Development Mode",
        timestamp: new Date().toISOString()
    });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚂 Scraper Microservice running on http://0.0.0.0:${PORT}`);
});
