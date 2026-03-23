<#
Run JMeter in Docker against the local nginx endpoint.

Usage examples:
  # default: 50 threads, 10s ramp, 100 loops
  .\run_jmeter.ps1

  # custom
  .\run_jmeter.ps1 -Threads 200 -Ramp 20 -Loops 1000 -Target http://host.docker.internal -TargetPort 80

Notes:
 - This script uses the image justb4/jmeter:5.5. If you prefer another image, edit the $image variable.
 - When running JMeter inside Docker and targeting services on the host (nginx running in Docker Compose mapped to host 80), use host.docker.internal as the target host.
#>

param(
    [int]$Threads = 50,
    [int]$Ramp = 10,
    [int]$Loops = 100,
    [string]$Target = 'http://host.docker.internal',
    [int]$TargetPort = 80,
    [string]$Image = 'justb4/jmeter:5.5'
)

$cwd = Get-Location
$testsDir = Join-Path $cwd 'load'
if (-not (Test-Path $testsDir)) { Write-Error "Directory $testsDir not found. Run this script from repository root."; exit 1 }

# Use safe formatting to avoid PowerShell parsing like 'variable:part' which raises errors
Write-Output ("Running JMeter in Docker: threads={0} ramp={1} loops={2} target={3}:{4}" -f $Threads, $Ramp, $Loops, $Target, $TargetPort)

# Pull image if needed
docker image inspect $Image > $null 2>&1
if ($LASTEXITCODE -ne 0) { docker pull $Image }

# Determine host and scheme from $Target
try {
  $uri = [uri]$Target
  $scheme = $uri.Scheme
  $hostOnly = $uri.Host
} catch {
  # If parsing fails, assume Target is host only
  $scheme = 'http'
  $hostOnly = $Target
}

Write-Output ("Using scheme={0} host={1} port={2}" -f $scheme, $hostOnly, $TargetPort)

# Run container, mount tests directory to /tests. Pass target_host and scheme explicitly
#docker run --rm -v "${testsDir}:/tests" $Image -n -t /tests/jmeter_test_plan.jmx -l /tests/results.jtl -Jthreads="$Threads" -Jramp="$Ramp" -Jloops="$Loops" -Jtarget_host="$hostOnly" -Jscheme="$scheme" -Jtarget_port="$TargetPort"
docker run --rm `
  -e http_proxy="" -e https_proxy="" -e HTTP_PROXY="" -e HTTPS_PROXY="" `
  -v "${testsDir}:/tests" $Image `
  -n -t /tests/jmeter_test_plan.jmx -l /tests/results.jtl `
  -Jthreads="$Threads" -Jramp="$Ramp" -Jloops="$Loops" -Jtarget="$hostOnly" -Jscheme="$scheme" -Jtarget_port="$TargetPort"

if ($LASTEXITCODE -ne 0) { Write-Error "JMeter run failed (exit $LASTEXITCODE)."; exit $LASTEXITCODE }

Write-Output "JMeter finished. Results stored in $testsDir\results.jtl (JTL format). You can open with JMeter GUI or convert to CSV." 
