# Peripherals

This document covers all peripheral APIs available in **Create-Cosmonautics**. Peripherals are divided into two main environments:
1. **ComputerCraft (CC) API**: Exposed when a Sputnik block is connected directly to a ComputerCraft computer.
2. **Sputnik Local Lua API**: Internal functions used inside the Sputnik block's visual programming nodes and custom Lua scripts.

---

## 1. ComputerCraft Sputnik Peripheral API

Connecting a **Sputnik** block to a ComputerCraft computer (directly or via modems) registers it as a peripheral of type `"sputnik"`.

### API Method Reference

#### `getBiomeInfo()`
Returns information about the biome at the Sputnik's block position.
- **Returns**: `table`
  - `name`: `string` (e.g. `"minecraft:desert"`)
  - `color`: `number` (Foliage color of the biome)

#### `getBiomeName()`
Returns the biome name at the Sputnik's block position.
- **Returns**: `string`

#### `getBiomeColor()`
Returns the biome foliage color at the Sputnik's block position.
- **Returns**: `number`

#### `getGlobalPos()`
Returns the global physical coordinates of the Sputnik. If Sputnik is placed on a flying ship, it returns the global position of the ship in the dimension.
- **Returns**: `table`
  - `x`: `number`
  - `y`: `number`
  - `z`: `number`

#### `getGlobalBiomeName()`
Returns the global biome name at the Sputnik's projected global coordinates (projects coordinates out of the ship sublevel).
- **Returns**: `string`

#### `getGlobalBiomeColor()`
Returns the global biome foliage color at the Sputnik's projected global coordinates.
- **Returns**: `number`

#### `getPhysics()`
Returns complete physical telemetry of the containing ship (works only when Sputnik is placed on a flying ship).
- **Returns**: `table`
  - `mass`: `number` (Total ship mass)
  - `quaternion`: `table` containing `{w, x, y, z}` (Ship orientation quaternion)
  - `euler`: `table` containing `{pitch, yaw, roll}` (Ship orientation angles in degrees)
  - `velocity`: `table` containing `{x, y, z}` (Linear velocity vector in m/s)
  - `gravityX`, `gravityY`, `gravityZ`: `number` (Gravity acceleration components acting on the ship)
  - `space`: `table` (Space details, see `buildSpaceData()` below)

##### Sub-table `space` layout:
- `available`: `boolean` (True if orbital coordinates are ready)
- `frame`: `string` (Name of the current celestial reference frame)
- `position`: `table` containing `{x, y, z}` (Orbit position)
- `velocity`: `table` containing `{x, y, z, speed}` (Orbit velocity vector and magnitude)
- `velocityAngles`: `table` containing `{pitch, yaw}` (Angles of orbital velocity vector)

#### `getDeepSpaceData()`
Returns comprehensive Keplerian orbital telemetry and local space environment parameters.
- **Returns**: `table`
  - `inDeepSpace`: `boolean` (True if ship is situated in the Deep Space dimension)
  - `semiMajorAxis`: `number` (Orbital Semi-Major Axis in meters, or `NaN` if not orbiting)
  - `eccentricity`: `number` (Orbital eccentricity: `0.0` = circular, `1.0+` = escape)
  - `inclination`: `number` (Orbital inclination in degrees)
  - `period`: `number` (Keplerian orbital period in seconds, or `NaN` if on escape trajectory)
  - `speed`: `number` (Current orbital velocity magnitude in m/s)
  - `gravity`: `number` (Local gravitational acceleration in m/s²)
  - `parentBody`: `string` (Name of the orbiting body/frame, e.g. `"Sol"`, `"unknown"`)
  - `parentRadius`: `number` (Radius of the parent celestial body in meters)
  - `distanceToPlanet`: `number` (Distance to closest planet surface in meters)
  - `inAtmosphere`: `boolean` (True if inside transition height of the parent body's atmosphere)
  - `atmosphereFlags`: `string` (Comma-separated flags of the current atmospheric band)
  - `universeTime`: `number` (Absolute astronomical time in ticks)
  - `velocity`: `table` containing `{x, y, z}` (Absolute orbital velocity vector)
  - `velocityDir`: `table` containing `{x, y, z}` (Normalized unit vector of orbital velocity)

---

## 2. Sputnik Local Lua API (Visual Programming & Scripts)

When writing built-in Lua scripts for Sputnik nodes, Sputnik provides direct access to all connected block peripherals on the ship (such as engines/thrusters) via UUID handles.

### Peripheral Network Discovery Functions

#### `getPeripheralIds()`
Returns an indexed array (table) of all connected peripheral UUID strings on the ship.
- **Returns**: `table` of `string`

#### `getPeripheralType(id)`
Returns the type of the peripheral matching the specified UUID.
- **Arguments**: `id` (`string`)
- **Returns**: `string`
  - `"vector_engine"` (Vector Thruster)
  - `"modular_thruster"` (Modular Thruster Mount)
  - `"thruster"` (Standard Rocket Thruster)
  - `"rcs"` (Reaction Control System Thruster)
  - `"booster"` (Solid Booster Thruster)

---

### Peripheral I/O Functions

These functions allow reading or writing custom properties to connected peripherals.

#### `readPeripheral(id, key)`
Reads a double value from the peripheral.
- **Arguments**: `id` (`string`), `key` (`string`)
- **Returns**: `number`

#### `writePeripheral(id, key, value)`
Writes a double value to the peripheral.
- **Arguments**: `id` (`string`), `key` (`string`), `value` (`number`)

#### `writePeripheralValues(id, key, ...)`
Writes multiple double values (e.g. vector arrays) to the peripheral.
- **Arguments**: `id` (`string`), `key` (`string`), varargs of `number`

---

## 3. Custom Peripherals Reference

### Thrusters (`"vector_engine"`, `"modular_thruster"`, `"thruster"`, `"rcs"`, `"booster"`)

All thruster engines implement the `IThruster` interface, allowing direct control over their throttles and gimbals via Sputnik.

| Action | Key | Arguments / Return | Description |
|---|---|---|---|
| **Read** | `"thrust"` | Returns `number` | Returns the current real-time thrust output in Newtons (equivalent to `flow * 100.0`). |
| **Write** | `"throttle"` | `value` (`0.0` - `1.0`) | Directly sets the thruster's throttle percentage. Sets active state to `false` if `value <= 0.0`. |
| **Write** | `"thrust"` | `value` (in Newtons) | Calculates and sets throttle value relative to the engine's configured maximum thrust capacity. |
| **Write Mult.** | `"gimbal"` | `pitch`, `yaw` (degrees) | Adjusts the thruster nozzle tilt angles (applicable to Vector Engines and gimbal mounts). |
