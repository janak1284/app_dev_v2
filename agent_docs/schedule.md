# Day 1: Transplant & Decontamination

[x] Initialize the clean Android project inside /android-client.
[x] Transplant the old Roadway OSRM networking, Tracking Service, and History UI from v3-develop.
[x] Strip out all legacy GTFS code until the project compiles cleanly.
[x] Run the app on a physical device and verify that the old Roadway tracking still works perfectly in the new repository.

# Day 2: The Railway Expansion (The New Engine)

[x] Add the Retrofit interfaces to talk to Developer 1's new Hugging Face microservice (/api/v4/train/track).
[x] Build the new RailwayTrackingService alongside your existing RoadwayTrackingService.
[x] Implement the Turf-Java "Snap & Slice" logic specifically for the Railway tracking.
[x] Integrate the new Railway engine into the UI (so the user can toggle between your old, working Roadway mode and the new Railway microservice mode).