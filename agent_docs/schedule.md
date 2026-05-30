# Day 1: Transplant & Decontamination

[ ] Initialize the clean Android project inside /android-client.
[ ] Transplant the old Roadway OSRM networking, Tracking Service, and History UI from v3-develop.
[ ] Strip out all legacy GTFS code until the project compiles cleanly.
[ ] Run the app on a physical device and verify that the old Roadway tracking still works perfectly in the new repository.

# Day 2: The Railway Expansion (The New Engine)

[ ] Add the Retrofit interfaces to talk to Developer 1's new Hugging Face microservice (/api/v4/train/track).
[ ] Build the new RailwayTrackingService alongside your existing RoadwayTrackingService.
[ ] Implement the Turf-Java "Snap & Slice" logic specifically for the Railway tracking.
[ ] Integrate the new Railway engine into the UI (so the user can toggle between your old, working Roadway mode and the new Railway microservice mode).