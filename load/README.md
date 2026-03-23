# Load testing with JMeter

This folder contains a minimal JMeter test plan and a helper script to run JMeter in Docker against the local nginx entrypoint (http://localhost).

Files:
- `jmeter_test_plan.jmx` — a minimal JMeter test plan that sends GET /api/users/health. Thread count, ramp, and loops are configurable via JMeter properties (see below).
- `run_jmeter.ps1` — PowerShell script to run the test plan in Docker. It mounts this folder into the container and writes `results.jtl` here.

Quick start (PowerShell, from repository root):
```powershell
# build app (must have executable jar present for backend images)
mvn -DskipTests package

# start services (mysql, redis, backend1, backend2, nginx)
docker compose up -d --build mysql redis backend1 backend2 nginx

# run jmeter using the provided helper (default 50 threads, 10s ramp, 100 loops)
.\load\run_jmeter.ps1
```

Notes & troubleshooting:
- If you run the JMeter container and it cannot reach http://host.docker.internal, try replacing `host.docker.internal` with your host IP or run JMeter on the host machine directly.
- After a run, open `load/results.jtl` in JMeter GUI (File → Open) to view aggregated metrics, or convert to CSV with JMeter's `-l`/reporting options.
- For large-scale tests or distributed JMeter, adapt the script to use JMeter master/worker mode or a dedicated runner.

Next steps:
- Create JMeter dashboards, add assertions, and parameterize requests for more realistic load.
- If you'd like, I can also generate a simple JMeter non-GUI summary CSV output step and a PowerShell helper to parse latency/error rates.
