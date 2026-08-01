--@name     Melody Player
--@category Audio
--@input    Play
--@input    Reset
--@input    Melody [str]
--@input    BPM
--@input    Vol
--@input    Waveform [str]
--@output   Step

if _G.play_index == nil then _G.play_index = 1 end
if _G.tick_accumulator == nil then _G.tick_accumulator = 0 end

local play = input("Play")
local reset = input("Reset")
local melody_str = input("Melody", "string")
local bpm = input("BPM")
local vol = input("Vol")
local wf = input("Waveform", "string")

if bpm <= 0 then bpm = 120 end
if vol <= 0 then vol = 0.5 end
if wf == "" then wf = "square" end

if reset > 0 then
    _G.play_index = 1
    _G.tick_accumulator = 0
end

local function note_to_freq(note_str)
    note_str = note_str:gsub("%s+", ""):upper()
    if note_str == "" or note_str == "-" or note_str == "REST" then
        return 0
    end
    
    local name, sharp_flat, octave_str = note_str:match("^([A-G])([#B]?)(%d)$")
    if not name then
        return 0
    end
    
    local semitones = { C = -9, D = -7, E = -5, F = -4, G = -2, A = 0, B = 2 }
    local val = semitones[name]
    if sharp_flat == "#" then
        val = val + 1
    elseif sharp_flat == "B" then
        val = val - 1
    end
    
    local octave = tonumber(octave_str) or 4
    val = val + (octave - 4) * 12
    
    return 440.0 * (2.0 ^ (val / 12.0))
end

-- Split melody into table of notes
local notes = {}
for note in melody_str:gmatch("[^,%s]+") do
    table.insert(notes, note)
end

if play > 0 and #notes > 0 then
    local beat_duration = 60.0 / bpm
    _G.tick_accumulator = _G.tick_accumulator + 0.05 -- 20 Hz = 0.05s per tick
    
    if _G.tick_accumulator >= beat_duration then
        _G.tick_accumulator = _G.tick_accumulator - beat_duration
        
        local note = notes[_G.play_index]
        if note then
            local freq = note_to_freq(note)
            if freq > 0 then
                audio.playSynth({
                    frequency = freq,
                    volume = vol,
                    duration = beat_duration * 0.8,
                    waveform = wf,
                    attack = 0.02,
                    decay = 0.05,
                    sustain = 0.7,
                    release = beat_duration * 0.1
                })
            end
        end
        
        _G.play_index = _G.play_index + 1
        if _G.play_index > #notes then
            _G.play_index = 1
        end
    end
end

output("Step", _G.play_index)
