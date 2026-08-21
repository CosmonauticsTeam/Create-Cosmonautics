--@name     Gyrodyne Control
--@category Actuators
--@input    id
--@input    mode
--@input    active
--@output   current_mode
--@output   is_active
--@output   tilt_x
--@output   tilt_z
--@output   rotor_speed

-- Control a Gyrodyne (CMG) attitude stabilizer, set flight modes, and read telemetry.
local id_in     = input("id")
local mode_in   = input("mode")
local active_in = input("active")

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
    if mode_in ~= nil and mode_in ~= "" then
        local mode_map = {
            off = 0, sas = 1, hold = 2, prograde = 3,
            retrograde = 4, normal = 5, antinormal = 6,
            radial_in = 7, radial_out = 8, horizon = 9, sun = 10
        }
        local m_val = tonumber(mode_in)
        if not m_val and type(mode_in) == "string" then
            m_val = mode_map[string.lower(mode_in)]
        end
        if m_val ~= nil then
            writePeripheral(target_id, "mode", m_val)
        end
    end

    if active_in ~= nil and active_in ~= "" then
        local act = tonumber(active_in)
        if act and act <= 0 then
            writePeripheral(target_id, "mode", 0) -- Set to OFF
        end
    end

    output("current_mode", readPeripheral(target_id, "mode"))
    output("is_active",    readPeripheral(target_id, "active"))
    output("tilt_x",       readPeripheral(target_id, "tilt_x"))
    output("tilt_z",       readPeripheral(target_id, "tilt_z"))
    output("rotor_speed",  readPeripheral(target_id, "rotor_speed"))
end
