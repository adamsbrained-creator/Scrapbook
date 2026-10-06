# Signing the release build

1. Create a keystore once (keep it safe, you cannot replace it later):
   `keytool -genkeypair -v -keystore release.jks -alias scrapbook -keyalg RSA -keysize 2048 -validity 10000`
2. Encode it: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`).
3. In GitHub: Settings > Secrets and variables > Actions, add
   `KEYSTORE_B64` (the output above), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (scrapbook), `KEY_PASSWORD`.
4. Run the workflow manually (Actions > Android CI > Run workflow). The release job uploads a signed APK.
   Without the secrets it still builds, but the APK is unsigned.
