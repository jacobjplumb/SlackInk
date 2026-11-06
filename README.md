# Slack Ink

A tiny, dependency-free Android Slack client for e-ink phones like the Mudita Kompakt.
Black-on-white, no animations, no push notifications. You check it when you choose to.

## What it does

- **DMs follow themselves.** Every DM and group DM is included automatically (your 40 most active).
- **Channels you choose.** Settings lists the channels you're a member of; tick the ones to follow.
- **Unread first.** The home screen shows an unread count per conversation using Slack's own
  read marker, with unread ones at the top and an "@ you" tag where you're mentioned.
- **Conversation view.** Last 30 messages with a "N NEW" divider, messages that mention you
  highlighted, reply box, and a **Mark read in Slack** button.
- **Unread digest.** Mentions first, then everything else unread, plus mentions of you in
  channels you don't follow. **Mark all read in Slack** clears it everywhere.
- **Optional switch:** hide DMs with nothing unread.
- **Optional notifications** (off by default): Settings, Notifications. Roughly every 15 minutes
  the app checks Slack in the background and posts one notification for new DMs and direct
  @mentions (including in channels you don't follow). Quiet hours (default 22:00 to 07:00)
  pause checks, and anything that arrived overnight alerts once when they end.
  Android may delay checks while the phone is idle, so alerts can be late. On Android 13+ the
  phone asks for notification permission the first time you switch it on.

## 1. Slack app and token

1. Go to https://api.slack.com/apps, open your app, then **OAuth & Permissions**.
2. Under **User Token Scopes** add all of these:
   - Read: `channels:read`, `groups:read`, `im:read`, `mpim:read`, `users:read`
   - History: `channels:history`, `groups:history`, `im:history`, `mpim:history`
   - Send: `chat:write`
   - Mark as read: `channels:write`, `groups:write`, `im:write`, `mpim:write`
   - Mentions in channels you don't follow: `search:read`
3. Click **Reinstall to Workspace**, approve, and copy the new **User OAuth Token** (`xoxp-...`).
   Any scope change issues a new token, so paste the new one into the app's Settings.

Without the `*:write` scopes, "Mark read" will fail with a missing_scope message.
Without `search:read`, everything else works but you won't see mentions in unfollowed channels.

## 2. Build the APK

Open this folder in Android Studio (File, Open), let Gradle sync, then
Build, Generate App Bundles or APKs, Generate APKs. Or push it to GitHub and run the
**Build APK** workflow in the Actions tab.

## 3. First run

Open Slack Ink, then **Settings**, paste the token, tap **Save token & load channels**,
tick the channels to follow, then **Save channels**. Go back and the home screen checks your unread.

## Notes and limits

- Nothing updates in the background. Tap **Refresh** (or reopen the app after 5 minutes).
- Each conversation costs two Slack calls per refresh. With many followed channels a refresh
  can take a little while, and Slack may briefly rate-limit; the app waits and retries.
- Unread counts cover top-level messages (up to 100 per conversation), not thread replies.
- Mention search relies on Slack's search index, which can lag a little behind live messages.
  Mentions found there are cleared with **Mark all read** in the digest (in the app only,
  since you may not be a member of those channels).
- The token is stored in app-private storage in plain text and is excluded from backups.
