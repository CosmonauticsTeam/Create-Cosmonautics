--@name     Button Trigger
--@category Logic
--@output   Out

if _G.btn == nil then
    _G.btn = ui.button("Press", 60)
end
addElement(_G.btn)

if _G.btn.wasClicked() then
    output("Out", 1)
else
    output("Out", 0)
end
