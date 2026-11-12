# CampusOne deployment guide

**Repository:** https://github.com/dipeshkumar123/campusone

This guide deploys one Spring Boot jar that serves both the API and the built React SPA. The demo database is H2 in file mode, so the deployment target must have a persistent disk and must run exactly one application instance.

## 1. What gets deployed

The build produces:

```text
backend/target/campusone.jar
```

The jar contains:

- the Spring Boot API;
- the React production build under `BOOT-INF/classes/static/`;
- the PWA manifest, service worker and icons;
- H2 as the runtime database driver.

The browser opens one origin, for example `https://campusone.example.edu`, and calls `/api` on the same origin.

## 2. Requirements

| Tool | Version |
| --- | --- |
| Java | 21 or newer |
| Node.js | 20 or newer |
| npm | 10 or newer |
| Maven | 3.9 or newer |

The build was verified with Java 23.0.2, Node 22.22.3, npm 10.9.8 and Maven 3.9.11 against a Java 21 target.

## 3. Build the deployable jar

From the repository root:

```powershell
cd web
npm ci
npm run build

cd ..\backend
mvn -q -DskipTests package
```

`npm run build` must run before the Maven package step. The Maven `prepare-package` phase copies `web/dist` into the jar's static resources.

Run it locally:

```powershell
cd backend
java -jar target\campusone.jar
```

Open http://localhost:8080. The first boot creates `backend/data/campusone.mv.db` and seeds the synthetic demo data.

To include tests in the build:

```powershell
cd backend
mvn verify
```

## 4. Configuration

The application reads these environment variables:

| Variable | Default | Required in production | Purpose |
| --- | --- | --- | --- |
| `PORT` | `8080` | No | HTTP port; most platforms set this automatically |
| `DB_URL` | `jdbc:h2:file:./data/campusone;AUTO_SERVER=TRUE;MODE=PostgreSQL` | Yes, to point at the persistent disk | H2 JDBC URL |
| `DB_USERNAME` | `sa` | No | H2 username |
| `DB_PASSWORD` | empty | Recommended | H2 password |
| `APP_JWT_SECRET` | random per boot | Yes | HMAC signing key, at least 32 characters; without it sessions end on restart |
| `APP_JWT_TTL_MINUTES` | `720` | No | Access-token lifetime |
| `APP_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Yes, if the UI is on another origin | Comma-separated allowlist |
| `APP_SEED_ENABLED` | `true` | Set `false` after the first boot | Disable demo seeding |
| `APP_DEMO_ADMIN_PASSWORD` | `Campus@2026` | Change before first boot | Seeded admin password |
| `APP_DEMO_FACULTY_PASSWORD` | `Faculty@2026` | Change before first boot | Seeded faculty password |

Generate a signing key:

```bash
openssl rand -base64 48
```

Important: the demo passwords only apply when the database is seeded for the first time. Changing the environment variable later does not update existing users.

For a same-origin deployment, CORS is not used by the browser. It is still good practice to set `APP_CORS_ORIGINS` to the public HTTPS origin in case a separate frontend host is added later.

## 5. Option A: single Linux VM

This is the most predictable option for the file database.

### 5.1 Prepare the host

On Ubuntu 24.04:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless nginx
sudo adduser --system --group --home /var/lib/campusone campusone
sudo mkdir -p /opt/campusone /var/lib/campusone
sudo chown -R campusone:campusone /opt/campusone /var/lib/campusone
```

Build the jar on your machine or in CI, then copy it:

```bash
scp backend/target/campusone.jar deploy@server:/tmp/campusone.jar
sudo mv /tmp/campusone.jar /opt/campusone/campusone.jar
sudo chown campusone:campusone /opt/campusone/campusone.jar
```

### 5.2 Environment file

Create `/etc/campusone.env`:

```bash
sudo tee /etc/campusone.env >/dev/null <<'EOF'
PORT=8080
DB_URL=jdbc:h2:file:/var/lib/campusone/campusone;AUTO_SERVER=TRUE;MODE=PostgreSQL
DB_USERNAME=sa
DB_PASSWORD=replace-with-a-long-database-password
APP_JWT_SECRET=replace-with-at-least-32-random-characters
APP_JWT_TTL_MINUTES=720
APP_CORS_ORIGINS=https://campusone.example.edu
APP_SEED_ENABLED=true
APP_DEMO_ADMIN_PASSWORD=replace-before-first-boot
APP_DEMO_FACULTY_PASSWORD=replace-before-first-boot
EOF

sudo chown root:root /etc/campusone.env
sudo chmod 600 /etc/campusone.env
```

For the first boot, keep `APP_SEED_ENABLED=true`. After verifying the demo, change it to `false`; the existing database remains.

### 5.3 systemd service

Create `/etc/systemd/system/campusone.service`:

```ini
[Unit]
Description=CampusOne API and web
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=campusone
Group=campusone
WorkingDirectory=/var/lib/campusone
EnvironmentFile=/etc/campusone.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=75 -jar /opt/campusone/campusone.jar
Restart=always
RestartSec=5
SuccessExitStatus=143
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/campusone

[Install]
WantedBy=multi-user.target
```

Start it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now campusone
sudo systemctl status campusone
curl -fsS http://127.0.0.1:8080/actuator/health
```

Expected:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

Follow logs:

```bash
sudo journalctl -u campusone -f
```

### 5.4 Nginx reverse proxy and TLS

Create `/etc/nginx/sites-available/campusone`:

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name campusone.example.edu;

    client_max_body_size 2m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
```

Enable it and obtain a certificate:

```bash
sudo ln -s /etc/nginx/sites-available/campusone /etc/nginx/sites-enabled/campusone
sudo nginx -t
sudo systemctl reload nginx
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d campusone.example.edu
```

Certbot rewrites the server block for HTTPS and sets up renewal. Verify:

```bash
curl -fsS https://campusone.example.edu/actuator/health
```

Then open the site and sign in with the admin account.

### 5.5 Firewall

Allow only SSH, HTTP and HTTPS:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
```

The application port 8080 should stay private behind Nginx.

## 6. Option B: managed platform with a persistent disk

Render, Railway and similar platforms can run the jar, but the H2 file must live on a persistent volume. An ephemeral filesystem will erase the database on every deploy.

Use these settings:

- **Build command:**

  ```bash
  cd web && npm ci && npm run build && cd ../backend && mvn -q -DskipTests package
  ```

- **Start command:**

  ```bash
  java -jar backend/target/campusone.jar
  ```

- **Health check path:** `/actuator/health`
- **Instances:** exactly one. Do not enable autoscaling; H2 file mode is not a shared database.
- **Persistent disk mount:** `/var/lib/campusone`

Environment:

```text
PORT=<platform-provided port>
DB_URL=jdbc:h2:file:/var/lib/campusone/campusone;AUTO_SERVER=TRUE;MODE=PostgreSQL
DB_USERNAME=sa
DB_PASSWORD=<long random value>
APP_JWT_SECRET=<at least 32 random characters>
APP_CORS_ORIGINS=https://<public-host>
APP_SEED_ENABLED=true
APP_DEMO_ADMIN_PASSWORD=<change before first boot>
APP_DEMO_FACULTY_PASSWORD=<change before first boot>
```

After the first successful boot, set `APP_SEED_ENABLED=false` and redeploy.

If the platform requires the port to be bound on `0.0.0.0`, no extra configuration is needed: Spring Boot binds all interfaces by default and reads `PORT`.

## 7. First-boot verification

1. `GET /actuator/health` returns `UP`.
2. The login page loads from the public origin.
3. The browser console has no 4xx/5xx responses during sign-in.
4. Admin login shows 23 active students and a populated fee desk.
5. Faculty login shows two classes for today.
6. Taking attendance once succeeds; taking it again with a new submit updates the same class rather than creating a second session.
7. The database file exists at the configured `DB_URL` path.

Useful checks:

```bash
curl -fsS https://campusone.example.edu/actuator/health
curl -fsS https://campusone.example.edu/ | head
```

The second command should return the SPA HTML, not a whitelabel error.

## 8. Demo data, accounts and reset

On first boot with `APP_SEED_ENABLED=true`, the app creates:

- 1 campus;
- 23 students;
- 3 programmes and 5 subjects;
- attendance history, internal marks and fee accounts;
- risk flags and mentor tasks.

Demo accounts:

| Role | Email | Password source |
| --- | --- | --- |
| Office admin | `admin@campusone.demo` | `APP_DEMO_ADMIN_PASSWORD` |
| Faculty | `faculty@campusone.demo` | `APP_DEMO_FACULTY_PASSWORD` |

To reset a demo deployment:

```bash
sudo systemctl stop campusone
sudo mv /var/lib/campusone/campusone.mv.db /var/lib/campusone/campusone.mv.db.bak
sudo systemctl start campusone
```

The next boot seeds a fresh database. Never do this on a deployment containing data you need.

## 9. Backup and restore

The H2 file is the only stateful component. Use a stop-copy-start backup for a consistent snapshot:

```bash
sudo systemctl stop campusone
sudo cp /var/lib/campusone/campusone.mv.db \
  /var/backups/campusone-$(date +%Y%m%d-%H%M%S).mv.db
sudo systemctl start campusone
```

Store backups off the host. Test a restore before relying on it:

```bash
sudo systemctl stop campusone
sudo cp /var/backups/campusone-YYYYMMDD-HHMMSS.mv.db /var/lib/campusone/campusone.mv.db
sudo chown campusone:campusone /var/lib/campusone/campusone.mv.db
sudo systemctl start campusone
```

## 10. Rollback

Keep the previous jar:

```bash
sudo cp /opt/campusone/campusone.jar /opt/campusone/campusone.jar.previous
```

To roll back:

```bash
sudo systemctl stop campusone
sudo cp /opt/campusone/campusone.jar.previous /opt/campusone/campusone.jar
sudo systemctl start campusone
curl -fsS http://127.0.0.1:8080/actuator/health
```

If a release changed the schema, restore the matching database backup as well. The demo uses Hibernate `ddl-auto=update`, which is convenient for a take-home but is not a substitute for versioned migrations.

## 11. Production hardening checklist

Before using this with real students:

- Replace H2 with managed PostgreSQL and add Flyway or Liquibase migrations.
- Set a strong `APP_JWT_SECRET` from a secret manager, not a file in the repository.
- Change all demo passwords before the first boot, then disable seeding.
- Remove or replace the synthetic demo records.
- Terminate TLS at the proxy and redirect HTTP to HTTPS.
- Add an edge rate limit for `/api/auth/login`.
- Keep `/actuator/health` internal if the platform allows it.
- Configure structured logs, request ids and alerting.
- Schedule and test encrypted backups.
- Run a security review of JWT storage, CSP and dependency versions.
- Add a staging environment and a rollback rehearsal.
- Use a shared rate limiter and a shared database before adding a second app instance.

## 12. Known constraints

- H2 file mode supports one application instance. Horizontal scaling is not supported.
- An ephemeral disk loses all data on redeploy.
- The login throttle is in memory and resets on restart.
- There is no refresh token or server-side token revocation.
- The fee flow is mock/test mode and does not contact a gateway.
- The assistant is deterministic and intentionally limited; it is not an LLM.
- The repository does not yet include a PostgreSQL driver or migration pipeline.

These are deliberate take-home tradeoffs. The technical design document records the production path for each one.

## 13. Local smoke commands

From the repository:

```powershell
cd backend
mvn test

cd ..\web
npm test
npm run build

cd ..\backend
mvn -q -DskipTests package
java -jar target\campusone.jar
```

Then open http://localhost:8080/login and use:

- `admin@campusone.demo / Campus@2026`
- `faculty@campusone.demo / Faculty@2026`

The packaged jar was verified to serve the SPA and complete an admin login/dashboard flow in Chromium.
