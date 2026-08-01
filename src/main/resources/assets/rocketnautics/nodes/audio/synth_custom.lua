--@name     Advanced Synth
--@category Audio
--@input    Trigger
--@input    Freq
--@input    EndFreq
--@input    Vol
--@input    Duration
--@input    Waveform [str]
--@input    FmFreq
--@input    FmDepth
--@input    LfoFreq
--@input    LfoDepth
--@input    Harmonics [str]
--@input    Formula [str]
--@input    Attack
--@input    Decay
--@input    Sustain
--@input    Release

if _G.last_trigger == nil then _G.last_trigger = 0 end

local trig = input("Trigger")
local f = input("Freq")
local end_f = input("EndFreq")
local v = input("Vol")
local dur = input("Duration")
local wf = input("Waveform", "string")
local fm_f = input("FmFreq")
local fm_d = input("FmDepth")
local lfo_f = input("LfoFreq")
local lfo_d = input("LfoDepth")
local harm = input("Harmonics", "string")
local formula = input("Formula", "string")
local a = input("Attack")
local d = input("Decay")
local s = input("Sustain")
local r = input("Release")

-- Set defaults
if f <= 0 then f = 440 end
if v <= 0 then v = 0.5 end
if dur <= 0 then dur = 0.5 end
if wf == "" then wf = "fm" end
if a <= 0 then a = 0.05 end
if d <= 0 then d = 0.05 end
if s <= 0 then s = 0.8 end
if r <= 0 then r = 0.1 end

if trig > 0 and _G.last_trigger <= 0 then
    audio.playSynth({
        frequency = f,
        end_frequency = end_f,
        volume = v,
        duration = dur,
        waveform = wf,
        attack = a,
        decay = d,
        sustain = s,
        release = r,
        fm_freq = fm_f,
        fm_depth = fm_d,
        lfo_freq = lfo_f,
        lfo_depth = lfo_d,
        harmonics = harm,
        formula = formula
    })
end

_G.last_trigger = trig
