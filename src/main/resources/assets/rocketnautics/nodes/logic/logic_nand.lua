--@name     Logic NAND
--@category Logic
--@input    A
--@input    B
--@output   Out

local a = (input("A") or 0) ~= 0
local b = (input("B") or 0) ~= 0
local out = not (a and b)
output("Out", out and 1 or 0)
