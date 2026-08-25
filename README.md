# Harness — DeepSeek Harness on your phone (Android)

A native Android app (Kotlin + Jetpack Compose) that talks to **your own DeepSeek Harness** instance over **Tailscale** — from your phone, anywhere. Live streaming chat, model switching, image attachments, workspace + **shared files** download/preview, light/dark themes.

> Each user connects to their **own** DSH instance, through their **own** tailnet. The app never accesses someone else's PC.

> **Beta.** Core features work; some edges are still rough.

**Demo (illustrative):**

![Harness demo](demo.gif)

---

## What you need
- A **PC with DeepSeek Harness** (`dsh`) installed.
- **Tailscale** installed on the PC **and** your phone, signed into the **same tailnet**.
- An Android phone (Android 8+).

---

## 1. Set up remote access on the PC (once)

On the PC, run the setup script (open PowerShell and):

```powershell
powershell -ExecutionPolicy Bypass -File .\setup-dsh-remote.ps1
```

The script automatically:
- binds the DSH web GUI to `0.0.0.0` (via the `web` profile patch),
- adds a firewall rule allowing port **3080** **only from the tailnet** (`100.64.0.0/10`),
- prints the **address** to enter in the app (e.g. `http://100.x.y.z:3080` or `http://host.ts.net:3080`),
- **generates a per-user connect QR** (saved as `dsh-connect-qr.png` in your home folder).

(It will ask for administrator rights once — normal.)

After the script: **restart DSH** — `Ctrl+C` in its terminal, then `ollama launch dsh` (or `dsh web`).

### Manual (without the script)
1. Edit `$DSH_HOME\profiles\web\cordis.patch.yml` and add:
   ```yaml
   - id: webserver
     config:
       host: '0.0.0.0'
       port: 3080
   ```
2. Firewall rule (from an admin terminal):
   ```
   netsh advfirewall firewall add rule name="DSH Web tailnet" dir=in action=allow protocol=TCP localport=3080 remoteip=100.64.0.0/10
   ```
3. Restart DSH.

---

## 2. Install the app on your phone

- Download the latest **`app-release.apk`** from **GitHub Releases** (the *Assets* section of a release).
- Open the file on your phone → allow *install from unknown sources* → **Install**.

---

## 3. Connect

1. Open **Harness**.
2. Enter your address, **or scan the connect QR** the script generated (`dsh-connect-qr.png`).
3. Tap **Connect** — the address is remembered for next time.

**Address:** use `http://<tailnet-ip>:3080` (reliable, no DNS) or `http://<hostname>.ts.net:3080` (needs **MagicDNS** enabled on the device). If a hostname gives `ERR_NAME_NOT_RESOLVED`, enable MagicDNS in Tailscale, or just use the IP.

**QR:** the script's QR encodes `harness://open?url=…`. Scan it with the phone (Harness installed) → the app opens with your address pre-filled. Each person uses **their own** QR (their own address).

---

## 4. Using the app

- **Chats** — conversations grouped by workspace, streaming, live **reasoning** ("thinking…" indicator + deep-think content), markdown (tables, code blocks).
- **Model** — switch via the top button or `/model` (or `/model` typed in the composer). Vision models (e.g. `qwen3.8:27b-128k`) can read attached images.
- **Images** — attach an image in chat (base64), and chat images render inline.
- **Workspace** — browse the project folder on the PC.
- **Settings** — theme (light/dark/system), **Server** (address + setup QR + change/reconnect), **Updates** (check GitHub for the latest release).
- Bottom bar: **Chats · Workspace · Settings** (icons). Shared files are inside Workspace.

---

## 5. Shared files — sending files from the PC (download / preview over Tailscale)

Files you place in a **`shared`** folder in your workspace (e.g. `<your-workspace>\shared`) are served over Tailscale:

- **In the app:** the **Shared** view (from Workspace) lists them; images preview inline, other files (PDF/DOCX/video/…) download + open.
- **In a browser:** open `http://<host>:3080/shared/index.html` — a gallery that previews images/PDFs and gives download buttons.
- Any file is also reachable at `http://<host>:3080/shared/<filename>` from any device on the tailnet.

**How it's served:** the `shared` folder is exposed through the DSH web server via a **junction** from the served `dist/shared` folder to your `<workspace>\shared` folder. Files placed there are immediately downloadable/previewable — no rebuild.

**Workflow:** when you ask the assistant (or an agent) for a file, it copies it into `shared` and gives you a **ready-to-click link** here, e.g. `http://<host>:3080/shared/report.pdf`. You download it from any device on the tailnet.

---

## 6. Updates

- **In-app (server):** when connected, the app checks the DSH server's `/harness-version.json` and offers an **Update** banner if newer.
- **In Settings → Updates:** a **"Check for updates (GitHub)"** button compares against the latest **GitHub Release**.
- To publish a new version: push a **tag `v1.x`** → CI builds the APK and creates a GitHub Release (see below).

> ⚠️ **Signing:** an in-app update installs over your app **only if signed with the same key**. Your locally-built APK uses your own key; the GitHub Release APK uses the **debug** key unless you configure signing secrets (see *Publishing*). For a fresh install (new users), the GitHub APK works fine.

---

## Release / CI (GitHub Actions)

- Every push / PR builds the APK automatically (`.github/workflows/build.yml`).
- Push a **tag** `v1.x` → CI creates a **GitHub Release** with `app-release.apk` in *Assets*.

---

## Publishing on GitHub (steps + commands)

Create an empty repo on GitHub (e.g. `harness-android`), then from the project folder:

```bash
git remote add origin https://github.com/<YOU>/harness-android.git
git push -u origin main
```

**Release** — create a tag and CI builds + attaches the APK:
```bash
git tag v1.25
git push origin v1.25
```

**Signing secrets** (optional, so the GitHub APK installs over yours): in the repo → *Settings → Secrets and variables → Actions → New repository secret*:
| Secret | Value |
|---|---|
| `SIGNING_STORE` | your keystore content in **base64** (`certutil -encode release.keystore tmp.b64`, then paste the content) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

> Without secrets, releases are built with the debug key — installable, but updates between releases must use the same signature.

---

## Security

DSH has no authentication. Port 3080 is open **only** to the tailnet (firewall rule `remoteip=100.64.0.0/10`) — anyone can reach it **only** if they are on your tailnet. Do **not** expose port 3080 on the public network / LAN.

---

## Development

Kotlin + Compose · `compileSdk 36` · Gradle 8.14.3.

Build the APK:
```bash
./gradlew assembleRelease
```
The APK is written to `app/build/outputs/apk/release/app-release.apk`. Signing reads `keystore.properties` (created locally, **not committed**); CI gets it from GitHub Secrets.
