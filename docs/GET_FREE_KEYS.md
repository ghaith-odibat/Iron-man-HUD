# Getting free API keys (≈2 minutes each)

Iron HUD identifies objects with cloud vision models on their **free tiers**. Keys are pasted into
the app's **VAULT** screen, encrypted with the Android Keystore, and never stored in this repo or in
the APK. Add as many as you like — when one hits its limit the HUD switches to the next one
automatically and puts the limited key back into rotation when its limit resets.

> **Tip:** free limits are counted **per project / per account**, not per key. Five keys from the
> same Google Cloud project still share one quota — create each Gemini key in a **new project**.

## 1. Google Gemini — primary (recommended)

1. Open <https://aistudio.google.com/apikey> and sign in with a Google account.
2. Click **Create API key** → **Create API key in new project**.
3. Copy the key (starts with `AIza…`).
4. In Iron HUD tap **VAULT** → under *GOOGLE GEMINI* paste it → **ADD** → **TEST**.
   The test shows which free model the app picked (the newest stable *Flash-Lite*).
5. For more daily capacity repeat step 2 with *another new project* (or another Google account).

The app selects the model automatically, so it keeps working when Google retires old models.
You can force a specific model in the VAULT (e.g. `gemini-3.1-flash-lite`).

## 2. Groq — very fast fallback

1. Open <https://console.groq.com/keys>, sign in (Google/GitHub/email).
2. **Create API Key**, copy it (starts with `gsk_`).
3. VAULT → *GROQ* → paste → **ADD** → **TEST**.

Default model: `meta-llama/llama-4-scout-17b-16e-instruct` (vision). If Groq renames it, put the new
vision model id in the GROQ model box.

## 3. OpenRouter — last-resort fallback

1. Open <https://openrouter.ai/settings/keys>, sign in, **Create Key** (starts with `sk-or-`).
2. VAULT → *OPENROUTER* → paste → **ADD** → **TEST**.

Default model: `openrouter/free`, a router that picks a free model that can see images. The free
daily cap is small, which is why it is last in line.

## Order and behaviour

* Providers are tried in the order shown in the VAULT (change it with **▲ PRIORITY**).
* Within a provider, requests rotate across healthy keys to spread per-minute limits.
* A rate-limited key is benched until the provider says it recovers (per-minute limits: seconds;
  Gemini daily limits: midnight Pacific time). Rejected keys are benched until you press **RESET**.
* With no usable key the HUD still tracks objects and shows the on-device (offline) label.
