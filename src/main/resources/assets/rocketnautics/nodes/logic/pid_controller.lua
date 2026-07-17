--@name     PID Controller
--@category Logic
--@input    SetPoint
--@input    ProcessValue
--@input    Kp
--@input    Ki
--@input    Kd
--@output   Output

if _G.prev_err == nil then _G.prev_err = 0 end
if _G.integral == nil then _G.integral = 0 end

local sp = input("SetPoint") or 0
local pv = input("ProcessValue") or 0
local kp = input("Kp") or 0
local ki = input("Ki") or 0
local kd = input("Kd") or 0

local err = sp - pv
_G.integral = _G.integral + err
local diff = err - _G.prev_err
_G.prev_err = err

local out = (kp * err) + (ki * _G.integral) + (kd * diff)
output("Output", out)
