--@name     Vector Length
--@category Math
--@input    X
--@input    Y
--@input    Z
--@output   Length

local x = input("X") or 0
local y = input("Y") or 0
local z = input("Z") or 0

output("Length", math.sqrt(x*x + y*y + z*z))
