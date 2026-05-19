---
layout: page
title: Configuration
---

Main file: `plugins/PageLore/config.yml`

## Page Separator

```yaml
settings:
  page-separator: "{page}"
```

The separator should be a full plain lore line. PageLore removes this line from the displayed lore.

## Controls

```yaml
controls:
  next-page:
    - "SWAP_OFFHAND"
  previous-page:
    - "SHIFT_RIGHT"
  full-lore:
    - "MIDDLE"
```

Control names must match Bukkit `ClickType` names. Common values are `LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`,
`MIDDLE`, `DROP`, `CONTROL_DROP`, and `SWAP_OFFHAND`.

## Cooldown

```yaml
settings:
  cooldown:
    enabled: true
    time: 0.5
    message-type: "ACTION_BAR"
```

Cooldowns prevent fast repeated clicks from spamming packet updates.

## Cache

```yaml
settings:
  cache:
    expire-time-seconds: 1
    maximum-size: 5000
```

The cache stores rendered lore briefly per player, page, item, and raw lore. Keep expiry short when using PlaceholderAPI
values that change often.
