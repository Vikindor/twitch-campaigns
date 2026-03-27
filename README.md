<h1 align="center">
Twitch Campaigns
</h1>

GitHub Actions app that tracks **Twitch Drops** and **reward campaigns** and posts newly detected entries to **Telegram**.  
It periodically checks external campaign feeds, keeps lightweight cache state, and sends compact Telegram updates when new drops or rewards appear.

It is a small personal automation project for monitoring Twitch promo activity without manually checking campaign pages.

This project relies on the excellent [twitch-drops-api](https://github.com/SunkwiBOT/twitch-drops-api) by [SunkwiBOT](https://github.com/SunkwiBOT) and [GregMMA](https://github.com/GregMMA). Kudos to them for making these endpoints available.

## 🌐 Telegram

### **[@twitch_campaigns](https://t.me/twitch_campaigns)**

## 🛠️ Tech stack

- Java 17
- Gradle
- Jackson JSON
- GitHub Actions
- GitHub Gist
- Telegram Bot API
