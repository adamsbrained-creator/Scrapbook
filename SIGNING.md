# Signing the app, so every new version updates the old one

## Why this is needed

Android only lets a new APK replace an installed app when **both** of these are true:

1. It is signed with **the same key** as the installed one.
2. Its **versionCode is higher**.

Without your own key, GitHub builds a new random "debug" key on every run, so every APK looks like a
different author's app and Android says *"App not installed as package conflicts with an existing package"*.
That is why you had to delete the app first. This project now handles both points:

- **Same key:** you create one key file once and store it in GitHub as a secret. Every build uses it.
- **Higher versionCode:** the build uses GitHub's run number (1, 2, 3, ...), so it goes up by itself.

## One-time setup

### 1. Create the key file (on your computer)

You need `keytool`, which comes with Java. Easiest ways to get it:

- **Android Studio installed:** use its bundled copy.
  Windows: `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`
  macOS: `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool`
- **Nothing installed:** install Java 17 from https://adoptium.net, then `keytool` works in any terminal.

Open a terminal (Windows: PowerShell, macOS: Terminal) in a folder you will remember and run:

```
keytool -genkeypair -v -keystore release.jks -alias scrapbook -keyalg RSA -keysize 2048 -validity 10000
```

(If you use the Android Studio path above, type that path instead of the word `keytool`.)

It asks several questions:

- **Keystore password:** choose a strong password and write it down. Nothing appears while you type, that is normal.
- **First and last name, organization, ...:** anything is fine (your name, "Scrapbook"). You can skip the rest with Enter.
- **Enter key password for scrapbook:** press **Enter** to use the same password as the keystore.
  (Keep them the same: it avoids the most common signing error.)

You now have `release.jks`.

### 2. Back it up. This is the most important step.

Copy `release.jks` and the password into a password manager, cloud drive or USB stick.
**If you lose this file, you can never update the app again** (users, including you, would have to delete it
and install fresh, losing data). Nobody, including Google and me, can recreate it.
Never commit it to GitHub, never send it to anyone. (`.gitignore` already blocks `*.jks`.)

### 3. Turn the file into text

GitHub secrets only hold text, so the file is converted to base64 and copied to the clipboard:

- **Windows (PowerShell):**
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard`
- **macOS:** `base64 -i release.jks | pbcopy`
- **Linux:** `base64 -w0 release.jks` (select and copy the output)

### 4. Add four secrets in GitHub

In your repository: **Settings > Secrets and variables > Actions > New repository secret**. Add exactly these names:

| Name | Value |
| --- | --- |
| `KEYSTORE_B64` | paste the clipboard text from step 3 |
| `KEYSTORE_PASSWORD` | the keystore password you chose |
| `KEY_ALIAS` | `scrapbook` |
| `KEY_PASSWORD` | the same password again |

### 5. Build

Push to `main` (or **Actions > Android CI > Run workflow**). When the run finishes, open it and download
**`scrapbook-release-apk`** from the *Artifacts* section at the bottom. It is a zip containing `app-release.apk`.
Unzip it on your phone (or copy the APK over) and install it.

The run page also shows a **Signing key** box with a SHA-256 line. It must be **identical on every run**.
If it ever changes, the key secrets changed and phones will refuse the update.

## The one-time uninstall

The app you have now was signed with a throwaway key, so Android will not let your new properly signed app
replace it. Do this **once**:

1. In the old app: menu > **App style and backup** > **Back up archive**, and save the zip somewhere safe
   (Drive, email to yourself, Files).
2. Uninstall the old Scrapbook.
3. Install the new `app-release.apk`.
4. Open **App style and backup** > **Restore** and pick the backup zip.

From then on you never uninstall again. Install each new APK over the old one and your notes stay.

## Everyday use

1. Change the code and push to `main`.
2. Download `scrapbook-release-apk` from the finished run.
3. Install it over the old version. Done.

## If something goes wrong

- **Run page says "No signing key" or the release job is skipped:** one of the four secrets is missing or the
  name is misspelled (names are case-sensitive).
- **`Keystore was tampered with, or password was incorrect`:** `KEYSTORE_PASSWORD` is wrong. Re-add the secret.
- **`Cannot recover key`:** `KEY_PASSWORD` differs from the keystore password. Use the same one for both.
- **`No key with alias ...`:** `KEY_ALIAS` must be exactly `scrapbook`, or whatever you used after `-alias`.
- **Android says "App not installed" or "conflicts with an existing package":** the installed copy was signed
  with a different key. Do the one-time uninstall above.
- **Android says "newer version already installed" / downgrade:** the installed copy has a higher versionCode,
  for example if you recreated the GitHub repository (the run number restarts at 1). Add an offset in
  `app/build.gradle.kts`, e.g. `val runNumber = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 100`
  (anything higher than the installed versionCode), and do not reset it again.
- **Building on your own computer with Android Studio** uses a different (debug) key and versionCode 1, so
  such builds cannot replace a GitHub build. Uninstall first when switching between the two.
