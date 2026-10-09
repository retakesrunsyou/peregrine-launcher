"""Peregrine Launcher: a Minecraft: Java Edition launcher for Linux."""

NAME = "Peregrine"
VERSION = "0.5.3"
TAGLINE = "Fast to launch. Faster to play."
USER_AGENT = f"peregrine-launcher/{VERSION} (Linux Minecraft launcher)"

# Your Azure app's client ID, built into the launcher so players only have to
# sign in. Get it once (see README), paste it here, and ship. It isn't a secret.
CLIENT_ID = "d3308ff4-f23e-46e0-a49a-848849087bfd"

# Discord application ID for Rich Presence (discord.com/developers/applications).
# Leave empty to turn the Discord status off. Not a secret.
DISCORD_APP_ID = "1557771317699149895"

# GitHub repository that hosts releases, as "owner/repo".
# Set this once you've made the repo; leave empty to turn update checks off.
UPDATE_REPO = "retakesrunsyou/peregrine-launcher"
