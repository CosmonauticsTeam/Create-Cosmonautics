--@name     Min & Max
--@category Math
--@input    A
--@input    B
--@output   Min
--@output   Max

local a = input("A") or 0
local b = input("B") or 0
output("Min", math.min(a, b))
output("Max", math.max(a, b))
