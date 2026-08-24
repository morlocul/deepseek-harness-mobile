Add-Type -AssemblyName System.Drawing

$W = 540; $H = 960
$BG = [System.Drawing.Color]::FromArgb(255, 15, 17, 21)
$SURF = [System.Drawing.Color]::FromArgb(255, 21, 24, 31)
$SURF2 = [System.Drawing.Color]::FromArgb(255, 27, 33, 41)
$ACCENT = [System.Drawing.Color]::FromArgb(255, 77, 107, 254)
$TEXT = [System.Drawing.Color]::FromArgb(255, 231, 233, 238)
$MUTED = [System.Drawing.Color]::FromArgb(255, 135, 140, 154)
$OUT = Join-Path (Split-Path $PSScriptRoot -Parent) "docs"

New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function New-Bitmap { return New-Object System.Drawing.Bitmap($W, $H) }
function MakeFont($size, $style, $family='Consolas') {
    New-Object System.Drawing.Font($family, $size, [System.Drawing.FontStyle]$style)
}

function Draw-RoundRect($g, $x, $y, $w, $h, $r, $color) {
    $brush = New-Object System.Drawing.SolidBrush($color)
    $d = [int]$r * 2
    if ($d -lt 1) { $g.FillRectangle($brush, [float]$x, [float]$y, [float]$w, [float]$h); $brush.Dispose(); return }
    if ($d -gt $w) { $d = $w }; if ($d -gt $h) { $d = $h }
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    $g.FillPath($brush, $p)
    $brush.Dispose(); $p.Dispose()
}

function Draw-Text($g, $text, $x, $y, $font, $color) {
    $b = New-Object System.Drawing.SolidBrush($color)
    $g.DrawString($text, $font, $b, [float]$x, [float]$y)
    $b.Dispose()
}

function Set-Bg($g) { $g.Clear($BG) }

# ---------- FRAME 1: Connect ----------
$bmp = New-Bitmap; $g = [System.Drawing.Graphics]::FromImage($bmp); $g.SmoothingMode = 'AntiAlias'; Set-Bg $g
Draw-Text $g "HARNESS" 120 120 (MakeFont 42 'Bold') $ACCENT
Draw-Text $g "DeepSeek Harness on your phone" 118 180 (MakeFont 16 'Regular') $MUTED
# address field
Draw-RoundRect $g 60 260 420 70 14 $SURF
Draw-Text $g "http://host.ts.net:3080" 80 286 (MakeFont 20 'Regular') $TEXT
Draw-Text $g "DSH address" 80 254 (MakeFont 12 'Regular') $MUTED
# connect button
Draw-RoundRect $g 60 360 420 70 16 $ACCENT
Draw-Text $g "Connect" 210 384 (MakeFont 20 'Bold') ([System.Drawing.Color]::White)
Draw-Text $g "How do I connect?" 180 460 (MakeFont 16 'Regular') $ACCENT
$g.Dispose(); $bmp.Save("$OUT\frame1.png", [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# ---------- FRAME 2: Conversations ----------
$bmp = New-Bitmap; $g = [System.Drawing.Graphics]::FromImage($bmp); $g.SmoothingMode = 'AntiAlias'; Set-Bg $g
Draw-Text $g "HARNESS" 30 40 (MakeFont 24 'Bold') $ACCENT
Draw-Text $g "CONVERSATIONS" 30 90 (MakeFont 13 'Bold') $MUTED
# session cards
$titles = @("App Android chat AI Tailscale","Verificare disponibilitate asistent","Problema limitei token FaceAI")
$y = 130
foreach($t in $titles){
  Draw-RoundRect $g 30 $y 480 92 14 $SURF
  Draw-RoundRect $g 30 $y 6 92 3 $ACCENT
  Draw-Text $g $t 52 ($y+20) (MakeFont 18 'Regular') $TEXT
  Draw-Text $g "just now" 52 ($y+56) (MakeFont 13 'Regular') $MUTED
  $y += 108
}
# bottom bar
Draw-RoundRect $g 0 870 540 90 0 $SURF
Draw-Text $g "Chats" 60 906 (MakeFont 15 'Bold') $ACCENT
Draw-Text $g "Workspace" 250 906 (MakeFont 15 'Regular') $TEXT
Draw-Text $g "Settings" 410 906 (MakeFont 15 'Regular') $TEXT
$g.Dispose(); $bmp.Save("$OUT\frame2.png", [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# ---------- FRAME 3: Chat ----------
$bmp = New-Bitmap; $g = [System.Drawing.Graphics]::FromImage($bmp); $g.SmoothingMode = 'AntiAlias'; Set-Bg $g
Draw-Text $g "App Android chat AI" 30 40 (MakeFont 20 'Bold') $TEXT
# user bubble (blue, right)
Draw-RoundRect $g 220 130 290 70 16 $ACCENT
Draw-Text $g "Hi! What can we build?" 242 156 (MakeFont 17 'Regular') ([System.Drawing.Color]::White)
# assistant bubble (left)
Draw-RoundRect $g 30 230 400 150 14 $SURF2
Draw-Text $g "We can build a native chat for" 50 252 (MakeFont 17 'Regular') $TEXT
Draw-Text $g "DeepSeek Harness over Tailscale," 50 280 (MakeFont 17 'Regular') $TEXT
Draw-Text $g "with markdown and code blocks." 50 308 (MakeFont 17 'Regular') $TEXT
# code block
Draw-RoundRect $g 30 400 400 120 12 $SURF
Draw-RoundRect $g 30 400 400 34 12 ([System.Drawing.Color]::FromArgb(255,23,28,36))
Draw-Text $g "CODE" 380 408 (MakeFont 11 'Bold') $MUTED
Draw-Text $g "val url = buildString {" 46 450 (MakeFont 15 'Regular') ([System.Drawing.Color]::FromArgb(255,230,237,243))
Draw-Text $g "  append(host).append(port)" 46 478 (MakeFont 15 'Regular') ([System.Drawing.Color]::FromArgb(255,230,237,243))
# composer
Draw-RoundRect $g 30 820 400 70 18 $SURF
Draw-Text $g "Type a message..." 50 848 (MakeFont 16 'Regular') $MUTED
Draw-RoundRect $g 452 830 58 50 25 $ACCENT
Draw-Text $g "->" 468 846 (MakeFont 22 'Bold') ([System.Drawing.Color]::White)
$g.Dispose(); $bmp.Save("$OUT\frame3.png", [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# ---------- FRAME 4: Settings / QR ----------
$bmp = New-Bitmap; $g = [System.Drawing.Graphics]::FromImage($bmp); $g.SmoothingMode = 'AntiAlias'; Set-Bg $g
Draw-Text $g "SETTINGS" 30 40 (MakeFont 13 'Bold') $ACCENT
Draw-Text $g "Quick setup" 30 80 (MakeFont 20 'Bold') $TEXT
# QR placeholder (grid pattern)
$qx = 160; $qy = 140; $cell = 12; $white = [System.Drawing.Brushes]::White
$g.FillRectangle($white, $qx, $qy, 220, 220)
# simple pseudo-QR pattern
$rng = New-Object System.Random(7)
for($i=0;$i -lt 200;$i++){
  $px = $qx + $rng.Next(0,220); $py = $qy + $rng.Next(0,220)
  $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,15,17,21))
  $g.FillRectangle($b, $px, $py, $cell, $cell); $b.Dispose()
}
# finder squares
foreach($fx in @($qx, $qx+160)){ foreach($fy in @($qy, $qy+160)){
  $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,15,17,21))
  $g.FillRectangle($b, $fx, $fy, 44, 44); $b.Dispose()
  $g.FillRectangle($white, $fx+10, $fy+10, 24, 24)
  $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,15,17,21))
  $g.FillRectangle($b, $fx+17, $fy+17, 10, 10); $b.Dispose()
}}
Draw-Text $g "Scan with a QR reader ->" 60 390 (MakeFont 14 'Regular') $MUTED
Draw-Text $g "address pre-filled" 60 416 (MakeFont 14 'Regular') $MUTED
# theme options
Draw-Text $g "Theme" 30 480 (MakeFont 18 'Bold') $TEXT
Draw-RoundRect $g 30 520 480 66 14 $SURF
Draw-Text $g "● System (follow device)" 50 544 (MakeFont 16 'Regular') $TEXT
Draw-RoundRect $g 30 596 480 66 14 $SURF
Draw-Text $g "○ Dark" 50 620 (MakeFont 16 'Regular') $MUTED
$g.Dispose(); $bmp.Save("$OUT\frame4.png", [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

Write-Host "frames written to $OUT"
