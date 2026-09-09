# DeepSpace Telemetry & Ephemeris HTTP API

**Create-Cosmonautics** features a built-in, high-performance, and secure HTTP JSON server designed to stream real-time orbital, celestial mechanics, planetary ephemerides, and spacecraft telemetry data to external applications, scientific calculators, 3D web maps, Discord bots, and mission control dashboards.

---

## 1. Key Architectural Features

- **Pure JSON Output**: All endpoints return standard UTF-8 JSON (`application/json; charset=utf-8`).
- **Zero-Lag & Thread Safety**: Telemetry is captured in an immutable snapshot at the end of each server tick and cached in memory. External requests never block the Minecraft main server loop.
- **Strictly Read-Only**: The server does not accept mutative requests (POST/PUT/DELETE), ensuring complete world integrity.
- **Self-Documenting**: The root endpoint `GET /api/v1` returns a complete machine-readable directory of all available endpoints, parameter descriptions, and SI measurement units.
- **CORS Enabled**: Out-of-the-box `Access-Control-Allow-Origin: *` support allows direct `fetch()` calls from browser-based WebGL/Three.js applications without proxying.

---

## 2. Configuration

Settings can be adjusted in **`rocketnautics-server.toml`** (located in your world's `serverconfig/` folder or server `defaultconfigs/`) or via the in-game Settings UI (**Pause Menu ➡️ Cosmonautics Settings ➡️ Telemetry** or via the *Configured* mod):

```toml
[TelemetryServer]
    # Enables or disables the HTTP API server (default: false)
    enabled = true

    # Network interface IP to bind (127.0.0.1 for local/proxy, 0.0.0.0 for public access)
    bindAddress = "127.0.0.1"

    # TCP port (default: 8085, range: 1024 - 65535)
    port = 8085

    # Optional Bearer authorization token. Leave empty ("") for open access.
    authToken = ""

    # Frequency in server ticks to refresh world snapshots (1 = 20 Hz, every tick)
    snapshotIntervalTicks = 1
```

> [!TIP]
> Changes made to `enabled` in the in-game UI take effect dynamically on the fly without requiring a server reboot.

---

## 3. Standard Units & Conventions

All numerical values strictly adhere to the International System of Units (**SI**):
- **Distance / Coordinates**: Meters ($m$)
- **Velocity / Speed**: Meters per second ($m/s$)
- **Mass**: Kilograms ($kg$)
- **Time**: Astronomical seconds ($s$) and server ticks ($1\text{ tick} = 0.05\text{ s}$)
- **Angles**: Decimal degrees ($^{\circ}$) and radians ($rad$)
- **Gravitational Parameter**: $\mu = G \cdot M$ in $m^3/s^2$
- **Pressure**: Pascals ($Pa$)
- **Reference Frame**: International Celestial Reference Frame (ICRF / Barycentric) and local parent-body centered inertial frames.

---

## 4. Endpoints Reference

### 4.1. Directory & Self-Documentation
- **`GET /api/v1`**  
  Returns the complete catalog of endpoints, descriptions, query options, and standard units.

---

### 4.2. Complete Universe State Dump (All-In-One)
- **`GET /api/v1/dump`**  
  Returns the entire universe state (all bodies, active vessels, time, frames, and constants) in a nested JSON structure.
- **`GET /api/v1/dump?flat=true`**  
  Returns a **100% flat dot-notation key-value dictionary** (ideal for time-series databases like InfluxDB/Prometheus, table parsers, or simple scripts).

#### Example Flat Response:
```json
{
  "time.universe_tick": 1482900,
  "time.epoch_iso": "2026-08-20T05:59:51.120Z",
  "constants.gravitational_constant_G_m3_kg_s2": 6.6743e-11,
  "bodies.earth.physics.mass_kg": 5.972e24,
  "bodies.earth.physics.gravitational_parameter_mu_m3_s2": 3.986004418e14,
  "bodies.earth.position.x_m": 147095000000.0,
  "bodies.earth.position.z_m": 26340000000.0,
  "bodies.earth.orbit.semi_major_axis_m": 149598023000.0,
  "bodies.earth.orbit.eccentricity": 0.0167086,
  "bodies.earth.orbit.inclination_deg": 0.00005,
  "bodies.earth.rotation.quaternion.w": 0.7071
}
```

---

### 4.3. Time & Physics Constants
- **`GET /api/v1/time`**  
  Astronomical clock and tick timing:
  ```json
  {
    "universe_tick": 1482900,
    "epoch_iso": "2026-08-20T05:59:51.120Z",
    "julian_date": 2461272.74989,
    "modified_julian_date": 61272.24989,
    "timescale": 1.0,
    "tick_rate": 20.0
  }
  ```
- **`GET /api/v1/constants`**  
  Universal constants ($G, AU, c, g_0$):
  ```json
  {
    "gravitational_constant_G_m3_kg_s2": 6.6743e-11,
    "astronomical_unit_AU_m": 149597870700.0,
    "speed_of_light_c_m_s": 299792458.0,
    "standard_gravity_g0_m_s2": 9.80665
  }
  ```
- **`GET /api/v1/frames`**  
  Hierarchy of celestial reference frames (`FrameTree`):
  ```json
  {
    "sol": { "id": 0, "name": "Sol", "parent_id": -1, "parent_name": null },
    "earth": { "id": 1, "name": "Earth", "parent_id": 0, "parent_name": "Sol" },
    "moon": { "id": 2, "name": "Moon", "parent_id": 1, "parent_name": "Earth" }
  }
  ```

---

### 4.4. Celestial Bodies (Planets, Stars, Moons)
- **`GET /api/v1/bodies`**  
  Returns the complete dataset of all celestial bodies.
- **`GET /api/v1/bodies/{bodyId}`**  
  Returns the complete dataset for a specific body (e.g. `earth`, `sol`, `moon`).
- **`GET /api/v1/bodies/{bodyId}/position`**  
  Instantaneous Cartesian coordinates and velocity vector:
  ```json
  {
    "body": "Earth",
    "position": { "x_m": 147095000000.0, "y_m": 0.0, "z_m": 26340000000.0, "distance_from_parent_m": 149437937485.0 },
    "velocity": { "x_m_s": -5300.0, "y_m_s": 0.0, "z_m_s": 29800.0, "speed_m_s": 30268.3 }
  }
  ```
- **`GET /api/v1/bodies/{bodyId}/orbit`**  
  Keplerian orbital elements:
  ```json
  {
    "body": "Earth",
    "orbit": {
      "semi_major_axis_m": 149598023000.0,
      "eccentricity": 0.0167086,
      "inclination_deg": 0.00005,
      "raan_deg": -11.26064,
      "arg_periapsis_deg": 114.20783,
      "true_anomaly_deg": 358.598,
      "mean_anomaly_deg": 358.617,
      "eccentric_anomaly_deg": 358.608,
      "period_s": 31558149.8,
      "periapsis_radius_m": 147098290918.0,
      "apoapsis_radius_m": 152097755082.0
    }
  }
  ```
- **`GET /api/v1/bodies/{bodyId}/physics`**  
  Gravitational and physical parameters:
  ```json
  {
    "body": "Earth",
    "physics": {
      "mass_kg": 5.972e24,
      "radius_m": 6371000.0,
      "gravitational_parameter_mu_m3_s2": 3.986004418e14,
      "surface_gravity_m_s2": 9.819,
      "escape_velocity_m_s": 11186.0,
      "sphere_of_influence_roi_m": 924000000.0
    }
  }
  ```
- **`GET /api/v1/bodies/{bodyId}/rotation`**  
  Spatial orientation quaternion and axial rotation:
  ```json
  {
    "body": "Earth",
    "rotation": {
      "quaternion": { "w": 0.7071, "x": 0.0, "y": 0.7071, "z": 0.0 },
      "rotation_rate_rad_s": 7.292115e-5,
      "rotation_period_s": 86164.09
    }
  }
  ```
- **`GET /api/v1/bodies/{bodyId}/atmosphere`**  
  Atmospheric transition boundary, hazard flags, and aerodynamic drag curve:
  ```json
  {
    "body": "Earth",
    "atmosphere": {
      "transition_height_m": 100000,
      "day_time_controller_id": 0,
      "apply_gravity_correction": true,
      "allowed_transfer": "BOTH",
      "flags": ["OXYGEN", "BREATHABLE"],
      "drag_multiplier_curve": [
        { "altitude_m": 0.0, "value": 1.0, "slope": -0.0001 },
        { "altitude_m": 100000.0, "value": 0.0, "slope": 0.0 }
      ]
    }
  }
  ```

---

### 4.5. Active Spacecraft & Stations (Vessels)
- **`GET /api/v1/vessels`**  
  Returns all active player spacecraft in Deep Space.
- **`GET /api/v1/vessels/{instanceId}`**  
  Full telemetry for a specific vessel instance:
  ```json
  {
    "instance_id": 105,
    "frame_name": "earth",
    "position": { "x_m": 147095006700.0, "y_m": 420000.0, "z_m": 26340000000.0 },
    "velocity": { "x_m_s": -5300.0, "y_m_s": 0.0, "z_m_s": 37550.0, "speed_m_s": 7750.0 },
    "orbit": {
      "semi_major_axis_m": 6778000.0,
      "eccentricity": 0.001,
      "inclination_deg": 51.6,
      "raan_deg": 120.4,
      "arg_periapsis_deg": 45.0,
      "true_anomaly_deg": 180.2,
      "mean_anomaly_deg": 180.1,
      "period_s": 5560.0,
      "periapsis_altitude_m": 400000.0,
      "apoapsis_altitude_m": 414000.0
    },
    "bounds": {
      "chunk_side_length": 2,
      "side_length_m": 32,
      "neg_x_corner": 64,
      "neg_z_corner": 128
    }
  }
  ```
- **`GET /api/v1/vessels/{instanceId}/position`**  
  Only 3D Cartesian coordinates and velocity vector.
- **`GET /api/v1/vessels/{instanceId}/orbit`**  
  Only Keplerian orbital parameters, periapsis, and apoapsis.
- **`GET /api/v1/vessels/{instanceId}/bounds`**  
  Sublevel bounding dimensions and physical chunk coordinates.

---

## 5. Client Integration Examples

### 5.1. Python (Real-Time Planetary Ephemerides)
```python
import requests

BASE_URL = "http://127.0.0.1:8085/api/v1"

# Fetch full universe dump
data = requests.get(f"{BASE_URL}/dump").json()

print(f"Universe Tick: {data['time']['universe_tick']} (ISO: {data['time']['epoch_iso']})")

for name, body in data['bodies'].items():
    pos = body['position']
    vel = body['velocity']
    print(f"\n[{body['name']}] ({body['type']})")
    print(f"  Coordinates (XYZ): ({pos['x_m']:.1f}, {pos['y_m']:.1f}, {pos['z_m']:.1f}) m")
    print(f"  Orbital Speed:     {vel['speed_m_s']:.2f} m/s")
    print(f"  Semi-Major Axis:   {body['orbit']['semi_major_axis_m']:.1f} m")
    print(f"  Eccentricity:      {body['orbit']['eccentricity']:.6f}")
```

---

### 5.2. JavaScript / Browser (Three.js 3D Orbit Visualizer)
```javascript
async function updateOrbits() {
    const response = await fetch("http://127.0.0.1:8085/api/v1/bodies");
    const bodies = await response.json();

    for (const [id, body] of Object.entries(bodies)) {
        console.log(`Body: ${body.name}, Pos: X=${body.position.x_m} Z=${body.position.z_m}`);
        // Update 3D mesh position in Three.js scene:
        // planetMesh[id].position.set(body.position.x_m / SCALE, body.position.y_m / SCALE, body.position.z_m / SCALE);
    }
}

// Refresh telemetry every second
setInterval(updateOrbits, 1000);
```

---

### 5.3. Rust
```rust
use serde_json::Value;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let resp: Value = reqwest::get("http://127.0.0.1:8085/api/v1/dump?flat=true")
        .await?
        .json()
        .await?;

    let tick = resp["time.universe_tick"].as_i64().unwrap_or(0);
    let earth_sma = resp["bodies.earth.orbit.semi_major_axis_m"].as_f64().unwrap_or(0.0);

    println!("Current Tick: {}, Earth SMA: {} m", tick, earth_sma);
    Ok(())
}
```

---

### 5.4. cURL & PowerShell
```powershell
# Get flat dictionary
curl "http://127.0.0.1:8085/api/v1/dump?flat=true"

# Get only Earth position with formatted JSON output
(Invoke-RestMethod "http://127.0.0.1:8085/api/v1/bodies/earth/position") | ConvertTo-Json
```
