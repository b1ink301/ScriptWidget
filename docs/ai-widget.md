[한국어](ai-widget.ko.md)

# Widgets with AI

Describe the widget you want; an AI writes a Scriptable script and the app **runs it on the real engine to check that it draws a widget**. The result is an ordinary script, so you can change anything in the editor.

**My scripts** tab → **+ New script** → **✨ With AI**

There are two ways.

| | Copy a prompt (free) | In the app (API key) |
|---|---|---|
| You need | Any AI chat app (ChatGPT, Claude, Gemini, …) | An API key for Gemini, Claude or OpenAI |
| Cost | None (within the chat app's free use) | The service's prices; Gemini has a free tier |
| When it fails | Copy the "fix request" and paste it back into the chat | The app sends the error to the AI and **fixes it automatically, up to 3 times** |

## 1. Describe it

Write it in **What should the widget show?**, or start from an example button.

Requests that work well:
- **What to show and how**: "Today's weather in Seoul, the temperature big, the high and low below"
- **Where the data comes from**, if you know: "Dollar to won and yen rates from the Frankfurter API"
- **Conditions and colors**: "Battery level as a bar, red under 20%"
- **Size**: "for a small widget"

Requests that don't:
- Services that need a login or your account (the AI can't use them unless you give it a key)
- iOS-only features (Shortcuts, lock screen widgets, …)

## 2-A. Copy a prompt (free)

1. Pick the **Copy a prompt (free)** tab.
2. Tap **Copy prompt**. It copies your request with the rules and the list of APIs this app really supports.
3. Paste it into an AI chat app and send it.
4. Copy the **whole** answer and paste it into **The AI's answer**. Explanations around it are fine; the app picks the ```javascript part.
5. Tap **Run it**.
   - **It works**: check the preview and go to step 3.
   - **It doesn't run yet**: tap **Copy a fix request** and paste it into the same chat; it holds the error and the code. Paste the new answer and run it again.

## 2-B. In the app (API key)

1. In the **In the app (API key)** tab, pick a **Service**.
2. Paste your **API key** and tap **OK**. It's kept encrypted on the phone and sent only to that service. Scripts can't read it through `Keychain`.
3. Tap **Models** next to **Model** and pick one this key can use. The list comes from the service, so it's always current.
4. Tap **Create**. The app asks the AI, runs the script, and if it fails sends the error back for a fix, up to 3 times.

### Getting an API key

| Service | Where | Notes |
|---|---|---|
| Gemini | [Google AI Studio](https://aistudio.google.com/apikey) | Has a free tier, good for trying it (limits per minute and day). On the free tier what you send may be used to improve the service. |
| Claude | [Anthropic Console](https://console.anthropic.com/) | Pay as you go; new accounts may get trial credit. |
| OpenAI | [OpenAI Platform](https://platform.openai.com/api-keys) | Pay as you go. |

Chat subscriptions (ChatGPT Plus, Claude Pro, …) don't include the API. With only a subscription, use **Copy a prompt**.

Choosing a model: models that write code well succeed on the first try more often. Start with a service's fast, cheap model (Flash, Haiku, mini, …) and switch to a bigger one if it keeps failing.

## 3. Save it

- The preview is a real run as a **medium** widget, with real network data.
- **Uses:** lists what the script reaches for (network, location, calendar, …). Check that it matches what you asked for before saving.
- Name it in **Script name** and tap **Save and open** to open it in the editor. A taken name gets a number.
- Add a ScriptWidget widget to the home screen and pick the script.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "No script found in the answer" | Paste the whole answer. If the AI gave no code, ask "give me the whole script in one code block". |
| "The AI request failed: HTTP 400/401/403" | The key is wrong, or the model isn't available with it. Pick a model from **Models** again. |
| "HTTP 429" | You hit the request limit (common on free tiers). Try again later or pick another model. |
| Still failing after 3 tries | Be more specific (data source, layout), switch to a bigger model, or use **Copy a prompt** to fix it in a conversation. |
| Works in the app, but the widget shows `–` or an error | Widgets run in the background. Check **Settings → Permissions** for location, calendar and the like. |
| Not what you wanted | Edit it in the editor, or add "bigger text", "a black background", … to the request and create it again. |

## What is sent

- **Sent**: your request, the rules and this app's API list; after a failure, that script and its error.
- **Not sent**: your other scripts, run history, files, or personal data such as location or contacts.
- What the generated script does when it runs as a widget (e.g. requesting a weather API) is the same as any script.
