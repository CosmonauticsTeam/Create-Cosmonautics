--@name     Sin & Cos
--@category Math
--@input    AngleRad
--@output   Sin
--@output   Cos

local a = input("AngleRad") or 0
output("Sin", math.sin(a))
output("Cos", math.cos(a))
