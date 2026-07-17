--@name     Redstone Link Transmitter
--@category Communication
--@input    Signal

if _G.f1 == nil then
    _G.f1 = ui.itempicker()
end
if _G.f2 == nil then
    _G.f2 = ui.itempicker()
end

addElement(_G.f1)
addElement(_G.f2)

local freq1 = _G.f1.getItem()
local freq2 = _G.f2.getItem()
local strength = input("Signal") or 0

if freq1 and freq2 then
    transmitWirelessRedstone(freq1, freq2, strength)
end
