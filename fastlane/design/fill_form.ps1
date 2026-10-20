# Fills in the sample membership form, as a reader would, by tapping each field.
# Coordinates are for a 1080x2424 phone with the form fitted to the width.
# Every value is fictional: 01632 960xxx is Ofcom's range for drama, example.com is reserved.
$d = Join-Path $PSScriptRoot "device.py"
$fields = @(
    @(538, 703, "Alice%sLiddell"),
    @(315, 800, "alice@example.com"),
    @(762, 800, "01632%s960123"),
    @(538, 898, "1%sRabbit%sHole%sLane"),
    @(315, 995, "Wonderland"),
    @(762, 995, "WL1%s1AA"),
    @(811, 1588, "20/10/2026")
)
foreach ($f in $fields) {
    python -I $d xy $f[0] $f[1]
    Start-Sleep 2
    # Clear what the field already holds, then type.
    python -I $d sh input keycombination 113 29
    python -I $d key DEL
    python -I $d sh input text $f[2]
    Start-Sleep 1
    python -I $d tap Save
    Start-Sleep 2
}
# Student, Fiction, Poetry and the rules; run once, as a second tap would clear them.
if ($args -notcontains "-textOnly") {
    foreach ($p in @(@(379, 1147), @(119, 1275), @(509, 1324), @(119, 1415))) {
        python -I $d xy $p[0] $p[1]
        Start-Sleep 2
    }
}
