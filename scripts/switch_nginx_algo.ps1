<#
Usage: .\switch_nginx_algo.ps1 roundrobin|least_conn|ip_hash
This script copies the chosen upstream template into the running nginx container and reloads nginx.
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('roundrobin','least_conn','ip_hash')]
    [string]$algo
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Resolve-Path "$scriptDir\.."

switch ($algo) {
    'roundrobin' { $src = "$repoRoot\nginx\upstreams_rr.conf" }
    'least_conn' { $src = "$repoRoot\nginx\upstreams_least.conf" }
    'ip_hash'    { $src = "$repoRoot\nginx\upstreams_iphash.conf" }
}

if (-not (Test-Path $src)) {
    Write-Error "Upstream template not found: $src"
    exit 1
}

# get nginx container id via compose
$cid = (docker compose ps -q nginx) -join ''
if (-not $cid) {
    Write-Error "nginx container not found. Is docker compose running?"
    exit 1
}

Write-Output "Copying $src -> $($cid):/etc/nginx/upstreams.conf"
docker cp "$src" "$($cid):/etc/nginx/upstreams.conf"
if ($LASTEXITCODE -ne 0) { Write-Error "docker cp failed"; exit 1 }

Write-Output "Reloading nginx..."
Write-Output "Copy complete. Checking if nginx container is running..."
$status = (docker inspect --format '{{.State.Running}}' $cid) 2>$null
if ($status -eq 'true') {
    Write-Output "nginx is running in container $cid. Reloading nginx..."
    docker compose exec nginx nginx -s reload
    if ($LASTEXITCODE -ne 0) { Write-Error "nginx reload failed"; docker compose logs --no-log-prefix --tail 50 nginx; exit 1 }
} else {
    Write-Output "nginx container is not running. Starting nginx container..."
    docker compose up -d nginx
    Start-Sleep -Seconds 2
    # check again
    $status2 = (docker inspect --format '{{.State.Running}}' $cid) 2>$null
    if ($status2 -ne 'true') { Write-Error "Failed to start nginx container. Check docker compose logs."; docker compose logs --no-log-prefix --tail 50 nginx; exit 1 }
}

Write-Output "Switched algorithm to $algo and reloaded nginx successfully." 
