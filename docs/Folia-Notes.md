# Folia Notes

PageLore declares Folia support in `paper-plugin.yml`.

## What Is Folia-Safe

- Periodic refreshes use the global region scheduler only to iterate players.
- Player inventory reads and updates are moved to the owning entity scheduler.
- Delayed inventory refreshes after clicks run through the player's entity scheduler.
- The repeating task is cancelled on plugin disable.
- Packet listener cache is cleared on disable.

## Configuration Advice

Use a reasonable refresh interval:

```yaml
settings:
  auto-update-interval: 20
```

Set it to `0` only if your lore does not need live PlaceholderAPI refreshes.
