--@name     Vector Scale
--@category Math
--@input    X
--@input    Y
--@input    Z
--@input    Scalar
--@output   OutX
--@output   OutY
--@output   OutZ

local x = input("X") or 0
local y = input("Y") or 0
local z = input("Z") or 0
local s = input("Scalar") or 1

output("OutX", x * s)
output("OutY", y * s)
output("OutZ", z * s)
