## Pages

Use `{page}` on its own line:

```yaml
lore:
  - "<gray>Page one"
  - "{page}"
  - "<yellow>Page two"
```

The first section before the first separator is page 1. Each separator starts the next page.

## PlaceholderAPI

Use PageLore placeholder tags:

```yaml
- "<gray>Your level: <white>{papi:player_level}"
```

PageLore converts `{papi:player_level}` to `%player_level%` before calling PlaceholderAPI.

## Requirement Checks

```yaml
- "{check:{papi:mmocore_level}>=20} <gray>Combat Level 20"
```

Supported operators:

- `>=`
- `<=`
- `>`
- `<`
- `==`
- `!=`

Numeric values are compared as numbers. Non-numeric values support `==` and `!=`.

## Full-Lore Mode

Full-lore mode displays all pages at once and hides separator lines. It is useful when players want to inspect the whole
item without clicking through every page.
