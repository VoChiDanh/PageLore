# PageLore

PageLore is a Paper/Folia plugin that splits long item lore into pages and lets players turn pages directly from the
inventory. It supports PlaceholderAPI, conditional requirement lines, full-lore toggle mode, configurable click
controls, and packet-side lore rendering so the original item data stays clean.

## Requirements

- Java 21
- Paper or Folia 1.21+
- PacketEvents 2.12.1+
- PlaceholderAPI is optional, but required for `{papi:...}` placeholders and dynamic checks.

## Features

- Folia-safe scheduling for inventory refreshes and delayed player updates.
- Per-player lore rendering through outgoing item packets.
- Configurable page separator, click controls, cooldowns, sounds, cache size, and parser tags.
- PlaceholderAPI support with `{papi:placeholder_name}` syntax.
- Requirement checks with `{check:{papi:placeholder}>=value}` syntax.
- Full-lore mode that shows every page while hiding separator lines.

## Basic Usage

Add `{page}` on its own lore line wherever a new page should begin.

```yaml
lore:
  - "<gray>First page line"
  - "<gray>Another first page line"
  - "{page}"
  - "<yellow>Second page line"
```

Default controls:

- `SWAP_OFFHAND`: next page
- `SHIFT_RIGHT`: previous page
- `MIDDLE`: toggle full lore mode

Run `/pagelore reload` after editing `config.yml` or `messages.yml`.

## PlaceholderAPI and Checks

Use `{papi:placeholder_name}` to render a PlaceholderAPI value in lore:

```yaml
- "<gray>Level: <white>{papi:player_level}"
```

Use `{check:...}` to show the configured met or unmet symbol:

```yaml
- "{check:{papi:mmocore_level}>=105} <gray>Combat Level 105"
```

Supported operators: `>=`, `<=`, `>`, `<`, `==`, `!=`.

## MMOItems Example

Place `{page}` as a plain lore line inside the MMOItems lore list. Keep it uncolored.

```yaml
SWORD_EXAMPLE:
  base:
    material: DIAMOND_SWORD
    name: "<gradient:#55ffff:#ffffff>Example Blade</gradient>"
    lore:
      - "<gray>Damage-focused starter page."
      - "<dark_gray>Use F to turn the page."
      - "{page}"
      - "{check:{papi:mmocore_level}>=20} <gray>Requires Combat Level 20"
      - "{check:{papi:player_level}>=15} <gray>Requires Player Level 15"
      - "{page}"
      - "<yellow>Upgrade notes and extra flavor text."
```

Do not put `{page}` inside a sentence. PageLore hides separator lines and uses them only as split points.

## Build

```bash
./gradlew clean build
```

The compiled jar is created in `build/libs/`.

## Documentation

Wiki-ready documentation is available in `docs/wiki/`.
