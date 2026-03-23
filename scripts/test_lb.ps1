<#
Usage: .\test_lb.ps1 -Count 100
Sends repeated requests to /api/users/health and counts responses by reported port.
#>
param(
    [int]$Count = 100
)

$counts = @{ }
for ($i=0; $i -lt $Count; $i++) {
    try {
        $r = Invoke-RestMethod -Uri 'http://localhost/api/users/health' -Method Get -TimeoutSec 5
        if ($r -match 'port:(\d+)') {
            $p = $matches[1]
        } else {
            $p = 'unknown'
        }
    } catch {
        $p = 'error'
    }
    if (-not $counts.ContainsKey($p)) { $counts[$p] = 0 }
    $counts[$p] += 1
}

$counts.GetEnumerator() | Sort-Object Name | Format-Table -AutoSize
