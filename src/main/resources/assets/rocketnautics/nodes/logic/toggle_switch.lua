--@name     Toggle Switch
--@category Logic
--@output   Out

if _G.chk == nil then
    _G.chk = ui.checkbox("Active")
end
if _G.val == nil then
    _G.val = ui.textfield(50)
    _G.val.setValue("1")
end

addElement(_G.chk)
addElement(_G.val)

if _G.chk.isChecked() then
    local num = tonumber(_G.val.getValue()) or 0
    output("Out", num)
else
    output("Out", 0)
end
