### Hour 0: The Knowledge Base

Before starting the clock, open a shared Obsidian vault. Create a single markdown file called `API_Contract.md`. Write down the exact JSON structure the backend will send (as discussed previously). This is your single source of truth. If Developer 2 knows exactly what the JSON looks like, they can build the entire app using fake data while Developer 1 learns how to scrape it.

---

### Day 1: The Scaffolding & Local Logic

**Developer 1 Focus (Cloud/Backend):** Get the web scraper working locally on your laptop. Do not worry about Docker or Cloud deployment yet.
**Developer 2 Focus (Android Client):** Initialize the new project layout, execute the surgical code transplant, and decontaminate the legacy codebase.

**Developer 1 Checklist:**

* [ ] Install Node.js on your machine.
* [ ] Run `npm init -y` to create a new project.
* [ ] Run `npm install express playwright`.
* [ ] Write a basic JavaScript file that uses Playwright to open a headless chromium browser and navigate to a train tracking website.
* [ ] Read the Playwright documentation on "Locators". Write the code to extract the text for ETA and the Station Sequence.
* [ ] Print the extracted data to your terminal as a clean JSON object.
* [ ] Create a free Supabase account and copy the Postgres database connection string.

**Developer 2 Checklist:**

* [ ] Open Android Studio and create a brand new clean project inside the `/android-client` directory.
* [ ] Add the required Gradle dependencies for Room, Retrofit, Osmdroid/Mapbox, and Turf-Java.
* [ ] Run selective `git checkout` commands to extract the working OSRM network classes, `RoadwayTrackingService.kt`, and the entire History UI layer from the old `v3-develop` branch.
* [ ] Strip out all legacy GTFS database imports, dead references, and old offline timetable schedules from the transplanted files.
* [ ] Resolve all compilation errors until the project builds with the clean, standalone Roadway engine and Room History UI.
* [ ] Deploy the app to a physical device to verify that the transplanted local Roadway tracking and saved journey history operate flawlessly in the new workspace.

---

### Day 2: The Math & The Network

**Developer 1 Focus (Cloud/Backend):** Build the anti-ban shield, wrap the scraper in an API, and push it to the internet.
**Developer 2 Focus (Android Client):** Integrate the remote network interfaces, implement the railway geometry math, and construct the dual-service tracking loop.

**Developer 1 Checklist:**

* [ ] Run `npm install @supabase/supabase-js puppeteer-extra-plugin-stealth`.
* [ ] Inject the stealth plugin into your Playwright script so the target website thinks you are a real human.
* [ ] Write the Supabase caching logic: Check the database first. If the data is less than 10 minutes old, return it. If it is older, run the Playwright scraper.
* [ ] Wrap all of this inside an Express.js server that listens on port `7860`.
* [ ] Write your `Dockerfile` using the official Playwright base image.
* [ ] Create a free Hugging Face Space (Docker template) and push your code. Wait for it to build and give you a public URL.

**Developer 2 Checklist:**

* [ ] Write the Retrofit network client to consume the real JSON payloads from Developer 1's live Hugging Face URL.
* [ ] Build a new `RailwayTrackingService` that will run in parallel alongside your verified, transplanted `RoadwayTrackingService`.
* [ ] Wire the local Room database to accept and hold the Overpass GeoJSON linestrings for the physical railway tracks.
* [ ] Implement the Turf-Java "Snap, Slice, and Measure" algorithm inside the railway loop to anchor live hardware GPS coordinates directly to the track lines.
* [ ] Update the primary Map UI and Search layout to include a clear toggle switch allowing the user to select between Roadway Mode and Railway Mode.
* [ ] Wire the input search bar to use Kotlin `StateFlow` with a 350ms debounce window before hitting the OSRM `/table` matrix endpoint.

---

### The Final Hours: Merge & Fallbacks

This is where you integrate everything and put in the safety nets for when things break in the real world.

**Developer 1 Checklist:**

* [ ] Test the live Hugging Face URL using Postman or your browser. Ensure it responds within 8 seconds.
* [ ] Write a `try/catch` block in your Node.js code to act as a fallback: if the primary train website fails to load, immediately attempt to scrape your backup train website.

**Developer 2 Checklist:**

* [ ] Add the Android 14 exact alarm permissions (`SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`) to the AndroidManifest.xml.
* [ ] Configure the `AudioAttributes.USAGE_ALARM` flags on your ringtone player to guarantee the notification bypasses system-level Do Not Disturb profiles.
* [ ] Code the Offline Math Fallback for Railway tracking: if Retrofit caught a `SocketTimeoutException` in a remote area, divide the remaining Turf-Java track distance by the average speed to calculate a local ETA.
* [ ] Verify the Dynamic Reroute Trigger in your transplanted Roadway engine: confirm that a 500-meter deviation from the OSRM polyline silently requests a fresh background route configuration.

---