---
layout: page
title: Troubleshooting
---

## Pages Do Not Change

- Confirm the item lore contains `{page}` on its own line.
- Confirm the click type is listed in `config.yml`.
- Confirm the player is not in creative mode.
- Run `/pagelore reload` after editing config files.

## Placeholder Text Shows Literally

- Install PlaceholderAPI.
- Confirm the placeholder works with `/papi parse`.
- Use `{papi:placeholder_name}`, not `%placeholder_name%`, inside PageLore-specific checks.

## Requirement Symbol Is Always Unmet

- Confirm the placeholder returns a number when using numeric operators.
- Use `==` or `!=` for text values such as class names.
- Check capitalization for text values.

## GUI Click Conflict

If a GUI plugin already uses the same click, change PageLore controls in `config.yml`.
