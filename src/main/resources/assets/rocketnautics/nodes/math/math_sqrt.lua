--@name     Square Root
--@category Math
--@input    In
--@output   Out

local val = input("In") or 0
output("Out", math.sqrt(math.max(0, val)))
