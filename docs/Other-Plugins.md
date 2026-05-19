---
layout: page
title: Other Plugins
---

PageLore is compatible with item plugins that store lore as normal Bukkit item lore.

## ItemsAdder, Oraxen, ExecutableItems, EcoItems

Use the same rule everywhere:

```yaml
- "{page}"
```

The separator must be a normal lore line. Avoid plugin-specific formatting that transforms or removes the separator
before the item reaches Bukkit lore.

## PlaceholderAPI-Based Plugins

Any plugin that exposes PlaceholderAPI placeholders can be used in PageLore:

```yaml
- "<gray>Money: <white>{papi:vault_eco_balance_fixed}"
- "{check:{papi:player_level}>=10} <gray>Level 10"
```

If a placeholder does not update immediately, lower `settings.auto-update-interval` or keep it at `20` ticks.

## GUI Plugins

For menu or GUI items, avoid using the same click type for both PageLore and the menu action. If a menu already uses
middle click, move PageLore full-lore mode to another `ClickType`.
