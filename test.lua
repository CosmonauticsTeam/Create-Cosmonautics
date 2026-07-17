-- Example Sputnik ComputerCraft integration test:
local sputnik = peripheral.find("sputnik")
if not sputnik then
    print("Error: Sputnik peripheral not found!")
    return
end

local data = sputnik.getDeepSpaceData()

print("In Deep Space?: ", data.inDeepSpace)
if data.inDeepSpace then
    print("Semi-Major Axis (A): ", data.semiMajorAxis)
    print("Eccentricity (E):    ", data.eccentricity)
    print("Inclination (I):     ", data.inclination)
    print("Orbital Period:      ", data.period)
    print("Orbital Speed:       ", data.speed)
    print("Local Gravity:       ", data.gravity)
    print("Parent Body:         ", data.parentBody)
    print("Parent Radius:       ", data.parentRadius)
    print("Dist. to Surface:    ", data.distanceToPlanet)
    print("In Atmosphere?:      ", data.inAtmosphere)
    print("Atmosphere Flags:    ", data.atmosphereFlags)
    print("Universe Time:       ", data.universeTime)
    
    -- Absolute orbital velocity vector components {x, y, z}
    if data.velocity then
        print(string.format("Velocity (m/s): X:%.2f Y:%.2f Z:%.2f", data.velocity.x, data.velocity.y, data.velocity.z))
    end
    
    -- Normalized unit vector of orbital velocity {x, y, z}
    if data.velocityDir then
        print(string.format("Vel. Dir:       X:%.3f Y:%.3f Z:%.3f", data.velocityDir.x, data.velocityDir.y, data.velocityDir.z))
    end
end
