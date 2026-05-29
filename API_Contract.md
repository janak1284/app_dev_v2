# API Contract - Location Alarm V4

## Endpoint 1: Railway Live Telemetry
**URL:** `/api/v4/train/track`
**Method:** `GET`
**Query Parameters:** `train_number` (String)

### Success Payload (200 OK):
```json
{
  "train_number": "12605",
  "eta_epoch_ms": 1714567890000,
  "station_sequence": [
    {"station_code": "MS", "sequence_index": 1},
    {"station_code": "TBM", "sequence_index": 2},
    {"station_code": "CGL", "sequence_index": 3},
    {"station_code": "VM", "sequence_index": 4}
  ],
  "cache_hit": false,
  "timestamp_fetched": 1714567290000
}
```

## Endpoint 2: Roadway Matrix Search
**URL:** `/api/v4/road/matrix`
**Method:** `POST`

### Request Payload:
```json
{
  "origin": {"lat": 13.0827, "lng": 80.2707},
  "destinations": [
    {"lat": 12.9249, "lng": 80.1418},
    {"lat": 12.6934, "lng": 79.9756}
  ]
}
```

### Success Payload (200 OK):
```json
{
  "distances_km": [12.4, 38.1],
  "durations_seconds": [1440, 3120]
}
```
