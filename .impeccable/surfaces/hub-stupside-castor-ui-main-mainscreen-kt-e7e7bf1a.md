---
version: 1
slug: "hub-stupside-castor-ui-main-mainscreen-kt-e7e7bf1a"
primary_target: "android/app/src/main/java/io/github/stupside/castor/ui/main/MainScreen.kt"
related_targets: ["android/app/src/main/java/io/github/stupside/castor/theme/Theme.kt","android/app/src/main/java/io/github/stupside/castor/MainActivity.kt"]
---

# Android casting

Mode: Operate. Scope: the Android client, including ready, receiver selection,
playback preferences, extraction, active cast, errors, and permissions.

## Direction contract

THESIS: Move a real video link from phone to television with two obvious choices
and one reachable action, replacing the long technical setup form.

OWN-WORLD: Liquid Glass, explicitly requested by the user on 2026-10-18.
A continuous aqua, blue and warm-sand backplate carries translucent optical
surfaces, bright specular rims, pill controls, rounded floating panels and
clear ink. Android Compose records only the scenic backplate for blur; text
and controls remain sharp. Android 12+ uses the native blur render effect;
Android 9–11 retains the same material without blur. Dark mode uses deep
blue-green glass, not a light-mode inversion. No new UI dependency.
The user preference supersedes the old flat Laguna material and any skill
default discouraging glass or large material radii.
STORY: Paste or share a link, select a television, optionally adjust playback,
then cast. See honest connection/extraction stages and stop without hunting.

FIRST VIEWPORT: Compact Castor app bar, a clear task heading, one prominent URL
field with paste and clear, a receiver row, compact playback summary and a fixed
bottom action. Advanced inputs stay in focused native sheets. The signature is
the persistent source-to-TV pairing, promoted into the active transmission view.

FORM: Native Android Operate, code-led. The user pinned Liquid Glass across
the app after rejecting the native AlertDialog language picker. Language
selection is now a native modal bottom sheet with immediate choice and a
large checkmarked selected surface. All menu, input, receiver, playback,
error, active-cast and permission surfaces share the same material.
No new aesthetic selection is needed; existing casting affordances and
Android Back, ripple, IME, selection semantics and font scaling remain.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Constraints: keep existing casting RPCs and native engine behavior. Do not add
unsupported seek, pause, volume or invented playback-position controls. Preserve
manual receiver entry, all subtitle/height values, share input, restart, stop,
interactive extraction, complete copyable errors, dark mode and font scaling.

## Source composer revision — 2026-10-18

The user rejected the nested source card (attached screenshot) and requested
modern source entry plus Native quality. Keep the Liquid Glass world. Replace
the nested card with an exterior label/type toolbar, one uninterrupted glass
input, then paste and a short source-kind label. No rhetorical question,
floating outline or repeated format list around the same link. Preserve native
editing, visible invalid-input guidance, paste/clear and source-type overrides.
Native quality is selectable beside existing height limits, preserves selected
source resolution through the existing high-ceiling request, and explicitly
allows device-required re-encoding; it is not a promise of bit-identical video.
Existing saved quality and the 1080p default remain unchanged.

## Appearance selection — 2026-10-18

Add System, Light and Dark under the app menu's Appearance entry. Dark is the
default for both new and upgraded installs with no saved theme. Selection is
immediate and saved; System tracks device night-mode changes. Keep the established
light/dark Liquid Glass palettes. Reuse the language chooser's native radio rows,
checkmark, scrolling glass sheet and close affordance. Apply the selection without
recreating the Activity, losing the draft or disturbing the current cast. Status
and navigation bar foregrounds must follow the effective app theme. Localize EN/IT.
