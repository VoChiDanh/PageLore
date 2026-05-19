# MMOItems Guide

PageLore works with MMOItems as long as the page separator is stored as a normal lore line.

## Correct Page Splitting

Put `{page}` exactly where the next page should begin:

```yaml
MYTHIC_SWORD:
  base:
    material: DIAMOND_SWORD
    name: "<red>Mythic Sword"
    lore:
      - "<gray>Main stats and item description."
      - "<gray>This is page one."
      - "{page}"
      - "{check:{papi:mmocore_level}>=30} <gray>Combat Level 30"
      - "{check:{papi:player_level}>=25} <gray>Player Level 25"
      - "{page}"
      - "<yellow>Upgrade path and extra notes."
```

## Rules

- Keep `{page}` uncolored and alone on the line.
- Do not write text before or after `{page}`.
- Use `{papi:...}` inside checks if the value comes from PlaceholderAPI.
- Use MiniMessage colors normally on all other lore lines.

## Requirement Examples

MMOCore combat level:

```yaml
- "{check:{papi:mmocore_level}>=50} <gray>Requires Combat Level 50"
```

Player level:

```yaml
- "{check:{papi:player_level}>=20} <gray>Requires Player Level 20"
```

Class check:

```yaml
- "{check:{papi:mmocore_class}==Warrior} <gray>Requires Warrior"
```

## Common Mistakes

Wrong:

```yaml
- "<dark_gray>{page}"
- "Page: {page}"
```

Correct:

```yaml
- "{page}"
```
