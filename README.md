# Music Display 🎵

**A modern, client-side music HUD for Minecraft with karaoke lyrics, in-world lyrics, and Apple Music-inspired visuals.**

Music Display brings your music into Minecraft with a compact Mini HUD, an expanded player, album artwork, playback information, and animated lyrics. Its Liquid Glass-inspired interface uses colors extracted from the current album cover.

## Features

- **Mini HUD** — A compact player with song title, artist, album artwork, progress, and lyrics.
- **Expanded HUD** — A larger music view with lyrics, album artwork, and playback controls.
- **In-World Lyrics** — Display lyrics as floating text in the Minecraft world.
  - **Around You:** Short groups of words appear near the player.
  - **Sky:** Lyrics appear at different positions farther away.
- **Karaoke animations** — Word highlighting, waves, and smooth lyric transitions when timed lyrics are available.
- **Plain lyrics fallback** — Display unsynchronized text when a song has no timed lyrics, instead of showing only the music note.
- **Animated album-color backgrounds** — Soft, flowing, multicolored visuals inspired by Apple Music.
- **WalksyLib settings and live preview** — Adjust and preview your music HUD.
- **Multilingual lyrics** — Unicode support for multiple writing systems, including Cyrillic.

## Music Sources

- **Spotify** — Song metadata and playback information via Windows media integration.
- **Apple Music (Windows)** — Support through the same Windows media integration; functionality may vary by app version and media-session availability.
- **Lyrics** — Fetched from LRCLIB when available. Timed lyrics are needed for accurate karaoke highlighting.

**SoundCloud support is being explored and is not included in this release.**

## Requirements

- Minecraft **26.3**
- **Fabric** (client side)
- **Windows** for the included media bridge
- **WalksyLib** for the integrated settings experience

## Installation

1. Install a compatible Fabric setup for Minecraft 26.3.
2. Install the dependencies listed for the version on Modrinth.
3. Download the Music Display JAR from [Modrinth](https://modrinth.com/mods).
4. Place the JAR in your Minecraft `mods` folder.
5. Launch Minecraft and start music in a supported Windows player.

## Notes

- Lyrics availability and synchronization depend on the track and the data provided by LRCLIB and Windows.
- When timed lyrics are unavailable, Music Display can show plain lyrics if available.
- Music Display is client-side; it does not require a server installation.

## Credits

Created by **7ltq x Codex**.

Project icon designed by my mom.

Some in-world lyrics rendering ideas and components are adapted from **Lyrics in the Sky**; see the source distribution for applicable third-party attribution.

## License

See [LICENSE](LICENSE) for licensing information.
