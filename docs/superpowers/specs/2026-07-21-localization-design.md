# Localization System Design

## Overview

Add a localization system to Alkatraz so all user-facing strings (menus, commands, spells, tutorial, etc.) are extracted from hardcoded Java/YAML into a single `lang/english.lang` properties file. A `LangManager` loads the file, caches entries, and provides `get(key, placeholders...)` with automatic color formatting. Server-wide, not per-player.

## Architecture

### LangManager

New class `me.nagasonic.alkatraz.lang.LangManager`:
- Loads `{dataFolder}/lang/{language}.lang` on init
- `language` read from `config.yml` key `language` (default: `english`)
- Stores entries in a `Map<String, String>` (flat, dot-notation keys)
- `get(String key, Object... placeholders)` — looks up key, replaces `%var%` placeholders, runs through `ColorFormat.format()`
- `getRaw(String key, Object... placeholders)` — same but no ColorFormat (rare use for raw text)
- Falls back: requested language → `english.lang` (bundled in JAR) → raw key string
- Loaded during `onEnable()` after config is available, before other managers

### Language File Format

Properties-style: `key=value` in `core/src/main/resources/lang/english.lang`. Comments with `#`. Flat dot-notation keys organized by section.

### Config Integration

- `config.yml` gets `language: english` key (replacing the unused one at line 90)
- `Configs.java` gets new `LANGUAGE` enum value
- `Alkatraz.getLangManager()` static getter

### Migration Approach

1. **Foundation** (Task 1): LangManager class, english.lang file (~650 keys), config integration
2. **Menu extraction** (Tasks 2-8): Replace hardcoded strings with `getLang().get("key")` calls, menu by menu
3. **Commands** (Task 9): Extract command strings
4. **Spells** (Task 10): Extract spell casting/error/lore strings
5. **Tutorial & misc** (Task 11): Extract tutorial messages, progression messages, utility strings
6. **YAML overrides** (Task 12): Let lang file override YAML display_name/description fields
7. **Final sweep** (Task 13): Build verification, grep for remaining hardcoded strings

### Key Design Decisions

- **Single lang file** (not per-category) — simpler to manage, one file to translate
- **Properties format** — universally understood, easy to parse, comments supported
- **Named placeholders** (`%variable%`) — self-documenting, order-independent
- **Auto-colorization** — `get()` runs `ColorFormat.format()` by default, `getRaw()` for exceptions
- **Fallback chain** — selected language → english.lang → raw key (never crashes)
- **Server-wide** — single language for entire server (no per-player locale)
- **YAML integration** — lang file takes precedence over YAML `display_name`/`description` for spell/research items
