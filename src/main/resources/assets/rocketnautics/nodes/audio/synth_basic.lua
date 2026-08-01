--@name     Basic Synth
--@category Audio
--@input    Trigger
--@input    Freq
--@input    Vol
--@input    Duration
--@input    Waveform [str]
--@input    Attack
--@input    Decay
--@input    Sustain
--@input    Release

if _G.last_trigger == nil then _G.last_trigger = 0 end

local trig = input("Trigger")
local f = input("Freq")
local v = input("Vol")
local dur = input("Duration")
local wf = input("Waveform", "string")
local a = input("Attack")
local d = input("Decay")
local s = input("Sustain")
local r = input("Release")

-- Set defaults if values are 0 or empty (standard values)
if f <= 0 then f = 440 end
if v <= 0 then v = 0.5 end
if dur <= 0 then dur = 0.5 end
if wf == "" then wf = "sine" end
if a <= 0 then a = 0.05 end
if d <= 0 then d = 0.05 end
if s <= 0 then s = 0.8 end
if r <= 0 then r = 0.1 end

if trig > 0 and _G.last_trigger <= 0 then
    audio.playSynth({
        frequency = f,
        volume = v,
        duration = dur,
        waveform = wf,
        attack = a,
        decay = d,
        sustain = s,
        release = r
    })
end

_G.last_trigger = trig
