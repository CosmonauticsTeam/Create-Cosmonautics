--@name     Mass Sensor
--@category Sensors
--@output   kg

-- Reads the total physical mass of the ship from the Sable engine.
local mass = getShipMass()
output("kg", mass)
