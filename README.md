# SkyHanni Development

![Build](https://github.com/DavidArthurCole/SkyHanniDevelopment/workflows/Build/badge.svg)

<!-- Plugin description -->
An IntelliJ IDE plugin that provides development assistance for the [SkyHanni](https://github.com/hannibal002/SkyHanni) Minecraft Skyblock mod. It adds inspections, intentions, inlay hints, line markers, and code completion tailored to SkyHanni's event system and configuration framework.
<!-- Plugin description end -->

## Features

### Event Handling

The plugin understands SkyHanni's event system and validates event handler declarations across the project.

**Inspections**

- **Missing `@HandleEvent`** — Flags public functions that accept a `SkyHanniEvent` parameter (or match a `@PrimaryFunction` name) without the `@HandleEvent` annotation. A quick fix adds it.
- **Missing `@PrimaryFunction`** — Flags concrete `SkyHanniEvent` subclasses with no `@PrimaryFunction` annotation. Without it, handlers must use the verbose `eventType = ...` argument on every `@HandleEvent`. A quick fix suggests a derived name when one is available and not already taken.
- **Duplicate `@PrimaryFunction` name** — Flags event classes that share a `@PrimaryFunction` name with another event. The name-to-event mapping is 1:1, so duplicates break handler dispatch.
- **Mismatched `@PrimaryFunction` name** — Flags `@HandleEvent` functions with an explicit `eventType` whose function name does not match the `@PrimaryFunction` declared on that event class.

**Navigation and hints**

- **Line markers** — Gutter icons on `@HandleEvent` functions link to the corresponding event class declaration.
- **Inlay hints** — For handlers that use the `@PrimaryFunction` convention, the resolved event type is shown inline beside the function name.
- **Code completion** — Suggests valid handler function names based on `@PrimaryFunction` declarations found across the project.

---

### Configuration

The plugin resolves SkyHanni's `@ConfigOption` properties to their full dotted config paths (e.g. `inventory.items.slot`) by walking the config class hierarchy.

**Inspections**

- **Copy config path** — Highlights every `@ConfigOption` property and offers a quick fix that copies its full config path to the clipboard.

**Intentions**

- **Convert to `Property<T>`** — Converts a `@ConfigOption var T` field to `val Property<T>`, wrapping the initializer in `Property.of(...)` and adding the required import.
- **Create config migration** — Inserts an `event.move(...)` call into the class's `onConfigFix` handler. Creates the `@SkyHanniModule companion object` and handler function if they do not already exist, and increments `CONFIG_VERSION` automatically.
- **Navigate to config property** — From a dotted path string inside `event.move("...")`, jumps directly to the property declaration in the config class hierarchy.

**Inlay hints and navigation**

- **Config path inlay hints** — Displays the resolved config path inline beside each `@ConfigOption` property.
- **Config path references** — Dotted path strings in migration calls are treated as references, enabling Ctrl+Click navigation to the target property.

---

### Module Validation

- **Missing `@SkyHanniModule`** — Flags `object` declarations that contain `@HandleEvent` functions or repo patterns without a `@SkyHanniModule` annotation. Also flags `class` declarations that incorrectly carry the annotation, which is only valid on `object`s. A quick fix adds or removes the annotation as appropriate.

---

### Miscellaneous

- **Regex101 line markers** — Gutter icons on regex pattern declarations open the pattern directly in [regex101.com](https://regex101.com) for testing.

---

## Installation

Using the IDE built-in plugin system:

<kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "SkyHanni Development"</kbd> > <kbd>Install</kbd>

Manually:

Download the [latest release](https://github.com/DavidArthurCole/SkyHanniDevelopment/releases/latest) and install it via
<kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙</kbd> > <kbd>Install plugin from disk...</kbd>

---

Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
