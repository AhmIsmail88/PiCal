# Vector-native launcher proposal, separate from the installed launcher resources.
Add-Type -AssemblyName System.Drawing
$bitmap = [System.Drawing.Bitmap]::new(512, 512)
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
$background = [System.Drawing.Drawing2D.GraphicsPath]::new()
$background.AddArc(0, 0, 224, 224, 180, 90)
$background.AddArc(288, 0, 224, 224, 270, 90)
$background.AddArc(288, 288, 224, 224, 0, 90)
$background.AddArc(0, 288, 224, 224, 90, 90)
$background.CloseFigure()
$navy = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#14263D'))
$g.FillPath($navy, $background)
$pipe = [System.Drawing.Drawing2D.GraphicsPath]::new()
$pipe.AddLine(164, 360, 164, 200)
$pipe.AddBezier(164, 200, 164, 170.667, 178.667, 156, 208, 156)
$pipe.AddLine(208, 156, 338, 156)
$pipe.StartFigure()
$pipe.AddLine(164, 250, 266, 250)
$white = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 48)
$white.StartCap = $white.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$white.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$g.DrawPath($white, $pipe)
$teal = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#368997'), 16)
$g.DrawLine($teal, 264, 132, 264, 180)
$g.DrawLine($teal, 140, 302, 188, 302)
$check = [System.Drawing.Drawing2D.GraphicsPath]::new()
$check.AddLine(272, 338, 310, 376)
$check.AddLine(310, 376, 382, 278)
$amber = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#F0A202'), 32)
$amber.StartCap = $amber.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$amber.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$g.DrawPath($amber, $check)
$bitmap.Save((Join-Path $PSScriptRoot 'icon-proposal.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$small = [System.Drawing.Bitmap]::new(192,192)
$smallGraphics = [System.Drawing.Graphics]::FromImage($small)
$smallGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$smallGraphics.DrawImage($bitmap,0,0,192,192)
$small.Save((Join-Path $PSScriptRoot 'icon-proposal-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
@($smallGraphics,$small,$amber,$check,$teal,$white,$pipe,$navy,$background,$g,$bitmap) | ForEach-Object { $_.Dispose() }
