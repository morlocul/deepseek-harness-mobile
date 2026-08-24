# Harness — DeepSeek Harness pe telefon (Android)

Aplicație Android nativă (Kotlin + Jetpack Compose) care vorbește cu **instanța ta de DeepSeek Harness** de pe PC, prin **Tailscale** — de pe telefon, de oriunde. Chat cu streaming live, schimbare de model, fișiere workspace, atașare imagini, light/dark.

> Fiecare utilizator se conectează la **propria** instanță DSH, prin **propriul** tailnet. Aplicația nu accesează PC-ul altcuiva.

> **Beta.** Funcții esențiale merg, dar mai sunt muchii de slefuit. Actualizările se instalează din app (sau descarci ultimul APK din GitHub Releases).

---

## De ce ai nevoie
- **PC cu DeepSeek Harness** (`dsh`) instalat.
- **Tailscale** instalat pe PC **și** pe telefon, conectate la **același tailnet**.
- Telefon Android (minim 8.0).

---

## 1. Setare remote access pe PC (o singură dată)

Pe PC, rulează script-ul de setup (deschide PowerShell și):

```powershell
# descarcă scriptul, apoi:
powershell -ExecutionPolicy Bypass -File .\setup-dsh-remote.ps1
```

Scriptul, automat:
- leagă GUI-ul DSH la `0.0.0.0` (prin patch-ul profilului `web`),
- adaugă o regulă de firewall care permite portul **3080** **doar din tailnet** (`100.64.0.0/10`),
- îți afișează **adresa** de introdus în aplicație (ex. `http://100.x.y.z:3080` sau `http://nume.ts.net:3080`).

(va cere drepturi de administrator o dată, normal).

După script: **repornește DSH** — `Ctrl+C` în terminalul lui, apoi `ollama launch dsh` (sau `dsh web`).

### Manual (fără script)
1. Editează `$DSH_HOME\profiles\web\cordis.patch.yml` și adaugă:
   ```yaml
   - id: webserver
     config:
       host: '0.0.0.0'
       port: 3080
   ```
2. Regula de firewall (din terminal administrator):
   ```
   netsh advfirewall firewall add rule name="DSH Web tailnet" dir=in action=allow protocol=TCP localport=3080 remoteip=100.64.0.0/10
   ```
3. Repornește DSH.

---

## 2. Instalează aplicația pe telefon

- Descarcă cel mai recent **`app-release.apk`** din **GitHub Releases** (secțiunea *Assets* a unui release).
- Deschide fișierul pe telefon → permiți *instalarea din surse necunoscute* → **Instalează**.

---

## 3. Conectare
1. Deschide **Harness**.
2. Introdu adresa afișată de script (ex. `http://desktop-nume.ts.net:3080`).
3. Apasă **Conectează-te** — vezi conversațiile, schimbi modelul (buton sus / `/model`), atașezi imagini, răsfoiești workspace-ul.

**Configurare rapidă (QR):** după conectare, în **Settings (⚙)** ai un **QR de configurare**. Alt telefon îl scanează cu orice cititor de QR → se deschide Harness cu adresa **precompletată** (deep link `harness://open?url=…`), fără să tastezi nimic.

---

## Dezvoltare
Proiect: `G:\...\Harness` · Kotlin + Compose · `compileSdk 36` · Gradle 8.14.3.
Build APK:
```bash
./gradlew assembleRelease
```
APK-ul iese în `app/build/outputs/apk/release/app-release.apk`. (Semnarea folosește un keystore local, nu se commit-uiește.)

## Release / CI (GitHub Actions)
- Fiecare push / PR construiește automat APK-ul (`.github/workflows/build.yml`).
- Pentru un **release semnat**: în GitHub → repo → *Settings → Secrets and variables → Actions*, setează:
  - `SIGNING_STORE` (keystore în base64), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
  - Fără secrets, build-ul folosește cheia **debug** (instalabilă, ok pentru beta).
- Creezi un **tag** `v1.x` → CI generează un **GitHub Release** cu `app-release.apk` în *Assets*.

---

## Securitate
DSH nu are autentificare. Portul 3080 e deschis **doar** către tailnet (regula de firewall `remoteip=100.64.0.0/10`) — oricine poate ajunge la el **doar** dacă e pe tailnet-ul tău. Nu expune portul 3080 pe rețeaua publică/LAN.
