--@name     Vector Add
--@category Math
--@input    Ax
--@input    Ay
--@input    Az
--@input    Bx
--@input    By
--@input    Bz
--@output   X
--@output   Y
--@output   Z

local ax = input("Ax") or 0
local ay = input("Ay") or 0
local az = input("Az") or 0
local bx = input("Bx") or 0
local by = input("By") or 0
local bz = input("Bz") or 0

output("X", ax + bx)
output("Y", ay + by)
output("Z", az + bz)
