--@name     Vector Control
--@category Actuators
--@input    id
--@input    thrust
--@input    pitch
--@input    yaw

-- Control a vector thruster's gimbal and thrust level.
local id_in = input("id")
local thrust = input("thrust")
local pitch  = input("pitch")
local yaw    = input("yaw")

local function get_id(val)
    if type(val) == "number" or (type(val) == "string" and tonumber(val)) then
        local target_str = tostring(tonumber(val))
        local ids = getPeripheralIds()
        for _, id in ipairs(ids) do
            if id == target_str then
                return target_str
            end
        end
        local idx = tonumber(val)
        if ids[idx] then
            return ids[idx]
        end
    end
    return val
end

local target_id = get_id(id_in)
if target_id and target_id ~= "" then
    writePeripheralValues(target_id, "gimbal", pitch, yaw)
    writePeripheral(target_id, "thrust", thrust)
end
