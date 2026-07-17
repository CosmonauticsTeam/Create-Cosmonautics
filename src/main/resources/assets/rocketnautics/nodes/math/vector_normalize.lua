--@name     Vector Normalize
--@category Math
--@input    X
--@input    Y
--@input    Z
--@output   OutX
--@output   OutY
--@output   OutZ

local x = input("X") or 0
local y = input("Y") or 0
local z = input("Z") or 0

local len = math.sqrt(x*x + y*y + z*z)
if len > 1e-6 then
    output("OutX", x / len)
    output("OutY", y / len)
    output("OutZ", z / len)
else
    output("OutX", 0)
    output("OutY", 0)
    output("OutZ", 0)
end
