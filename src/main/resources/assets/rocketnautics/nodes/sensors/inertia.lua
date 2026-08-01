--@name     Inertia Sensor
--@category Sensors
--@output   ixx
--@output   iyy
--@output   izz

-- Reads the diagonal moments of inertia (Ixx, Iyy, Izz) of the ship from the Sable engine.
local ixx, iyy, izz = getInertiaTensor()
output("ixx", ixx)
output("iyy", iyy)
output("izz", izz)
