import sys
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
import datetime
import math

# Chennai Suburban Line: TAMBARAM -> PERUNGULATTUR -> VANDALUR
route_points = [
    [80.119157, 12.926035], # TAMBARAM (Start)
    [80.111267, 12.919174], [80.103377, 12.912313], # Accelerating
    [80.095486, 12.905451], # PERUNGULATTUR Station (STOP)
    [80.095486, 12.905451], # PERUNGULATTUR (Dwell Time)
    [80.089940, 12.898365], # High speed cruise
    [80.084393, 12.891280]  # VANDALUR Station (Arrival)
]

def haversine_distance(lat1, lon1, lat2, lon2):
    R = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi/2.0)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2.0)**2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1-a))

current_time = datetime.datetime.now(datetime.timezone.utc)
gpx_elements = []
total_nodes = len(route_points)

start_lon, start_lat = route_points[0]
gpx_elements.append(f'        <trkpt lat="{start_lat}" lon="{start_lon}">\n            <time>{current_time.strftime("%Y-%m-%dT%H:%M:%SZ")}</time>\n        </trkpt>')

for idx in range(1, total_nodes):
    prev_lon, prev_lat = route_points[idx - 1]
    curr_lon, curr_lat = route_points[idx]
    
    distance_meters = haversine_distance(prev_lat, prev_lon, curr_lat, curr_lon)
    
    # Railway Kinematics Profile
    if distance_meters == 0:
        # If coordinates are identical, simulate a 45-second Platform Dwell Time
        calculated_seconds = 45.0
    else:
        journey_progress = idx / total_nodes
        
        # Determine speed based on position to nearest stop
        if 0.1 < journey_progress < 0.3 or 0.6 < journey_progress < 0.8:
            # Express Cruise Sector (~75 km/h)
            target_velocity = 20.8 
        else:
            # Acceleration/Deceleration Zones near platforms (~25 km/h)
            target_velocity = 6.9 

        calculated_seconds = distance_meters / target_velocity

    current_time += datetime.timedelta(seconds=max(1.0, calculated_seconds))
    time_stamp = current_time.strftime("%Y-%m-%dT%H:%M:%SZ")
    
    gpx_elements.append(f'        <trkpt lat="{curr_lat}" lon="{curr_lon}">\n            <time>{time_stamp}</time>\n        </trkpt>')

gpx_document = f"""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="LocationAlarmV4_Railway" xmlns="http://www.topografix.com/GPX/1/1">
    <trk>
        <name>Suburban_Tambaram_To_Vandalur</name>
        <trkseg>
{"\n".join(gpx_elements)}
        </trkseg>
    </trk>
</gpx>"""

with open("railway_demo_route.gpx", "w") as out_file:
    out_file.write(gpx_document)

print("✅ railway_demo_route.gpx generated successfully!")