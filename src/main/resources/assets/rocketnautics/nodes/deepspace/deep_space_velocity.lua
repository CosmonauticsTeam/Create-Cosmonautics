--@name     Deep Space Velocity
--@category DeepSpace
--@output   vx
--@output   vy
--@output   vz
--@output   dx
--@output   dy
--@output   dz
--@output   speed

local vx, vy, vz = getDeepSpaceVelocity()
local dx, dy, dz = getDeepSpaceVelocityDir()

local speed = 0
if vx == vx and vy == vy and vz == vz then
    speed = math.sqrt(vx*vx + vy*vy + vz*vz)
else
    speed = 0
end

output("vx", vx)
output("vy", vy)
output("vz", vz)
output("dx", dx)
output("dy", dy)
output("dz", dz)
output("speed", speed)
