---
name: taski-design
description: "Design rules for the Taski Android app (Compose, Material 3 Expressive). Use when adding, changing or reviewing any screen, component, sheet or menu in Taski: it holds the density budget with numbers, the navigation and layer rules, the Material 3 Expressive do/don't list, and the pre-delivery checklist. Calm first, expressive second."
---

# Taski design

Taski is a task app people open many times a day, in a hurry. Its look is **calm by default and expressive at the moments that matter** (finishing a task, today, a range of days). If a screen feels crowded, it is wrong — however correct each part is. Fix the screen, not the part.

This skill sits on top of `apple-design` (Simplicity, Agency), `ux-product-strategy` (Hick's and Miller's law) and `ui-ux-pro-max` (touch targets, gesture conflicts, drag alternatives). Where they disagree with Material 3, Material 3 wins for components and these rules win for density.

## 1. The rule underneath every other rule

**One screen, one job, one way to do it.** Each screen answers one question ("what is on today?", "what is in this list?", "where does this task sit in the week?"). Anything that does not help answer it is moved one tap away, not removed and not kept in view.

Progressive disclosure has three rings; put each thing in the innermost ring that still lets people do their job:

| Ring | What lives there | Cost to reach |
| --- | --- | --- |
| 1. Always visible | The content, its title, the one primary action | none |
| 2. One tap | View switcher, search, filters/sort/group (the **View** sheet), a row's properties | 1 tap |
| 3. Deliberate | Saved views, tag management, rare options, long-press menu | 2 taps or a long press |

## 2. Density budget (measured, not felt)

Measured on a 412 × 915 dp phone. These are ceilings. `DensityBudgetTest` holds the first three; the rest are checked in review with screenshots.

| Budget | Limit |
| --- | --- |
| Where the first piece of content starts | ≤ 25 % of screen height (list) · ≤ 38 % (week plan) · ≤ 45 % (day agenda) |
| Controls in front of the content | ≤ 6 tap targets |
| Navigation layers on screen | 1 (bottom bar) — a screen title is not a layer |
| Bottom layers stacked (bar, toolbar, bottom sheet, snackbar) | 1 at rest; a snackbar may sit on the bar |
| Primary action | 1 floating action button; none on screens that have no "add" |
| Header of a screen | 1 row for the title (+ its summary line), 1 optional row for the scale/date |
| Property marks on a task row | ≤ 4, and "+N" counts as one of them |
| Lines in a task row | title + at most 1 line of marks |
| Colours with meaning at once | 1 accent, plus red for overdue, plus the tag pastels (tag names only) |
| Text sizes on one card | ≤ 3 |

If a new feature needs a new always-visible control, it must displace one, or go into ring 2.

## 3. Layout and shell

- Bottom navigation: 4 destinations, icon + label, the active one in a `secondaryContainer` pill. No second tab strip below or above it.
- **One switcher per screen.** Switching how a list looks (List / Table / Board / Timeline) is the screen's title: tap the title, pick from a menu. Never a row of tabs beside a row of chips beside a toolbar.
- **One View sheet** holds filter, group, sort and "show subtasks". No inline filter rows. A badge on the tune icon says a filter is active.
- Headers use `TopAppBar` with title + subtitle, or the timeline's single row. Actions in a header: search and view options, nothing more.
- Content margins 16 dp (rows sit in 28 dp-radius sections); 8 dp grid; section gaps 16–24 dp; **space groups things, lines and boxes do not**. Prefer tonal surface steps (`surfaceContainer*`) to dividers; a divider is allowed only inside a row group.
- The floating action button must never cover the last row: the list adds bottom padding of bar + 104 dp.
- Sticky things (headers, strips) are one row tall or they scroll away.

## 4. Task rows

- Title first, alone on its line if it needs it (max 2 lines).
- One line of marks, ordered by what matters at a glance: **when** (due/range) → tag → priority → progress → timer/blocked → project → repeat. Priority and repeat are icons only in a row; their words live in the task page.
- Anything past the budget folds into a single "+N" mark that opens the task's menu. Never shrink text or wrap into a third line to fit more.
- The check circle is a 48 dp target. The marks in a row are small on purpose (≈ 20 dp): they are shortcuts, never the only way — the same edit is always reachable from the task page and the long-press menu, whose targets are ≥ 48 dp. Do not inflate marks to 48 dp; that is what made rows tall and crowded.
- The date says what it means: "Due today 16:00–17:00", "Yesterday – Oct 3", "2 days overdue". Overdue is the only thing that turns red.

## 5. Time views (Timeline)

- **Week and Month are a plan, not a grid**: only days that matter get a card; quiet days fold into "3 free days". Never render 30 empty rows.
- **Day is an agenda on a rail** by default (what, when, and the free stretch between as a length: "3h free"); the hour grid is an option in the scale menu for people who want to drag.
- Every drag has a tap alternative (tap a range's end to open its picker; long-press for the menu). Drags start after a long press or on a 14 dp handle so scrolling never moves a task.
- A several-day task is one ribbon with a start cap and an end cap — from where to where — not a repeated chip on each day.
- Header: scale chip (menu) · what is being looked at · today · ‹ ›. One row.

## 6. Material 3 Expressive — where to spend it

Spend expressiveness in **one place per screen**, quiet everywhere else.

Do:
- Shape as meaning: project avatars are `MaterialShapes` cookies/flowers keyed by the project id; **today** is a cookie badge; a card that is pressed morphs its corners.
- Springs, not tweens: `MotionScheme.expressive()` for spatial changes (sheets, cards, the FAB menu), `effects` specs for fades and colour.
- Big type only for the one thing to read from across the room (the title, today's date); emphasised weights (`…Emphasized`) rather than more sizes.
- Tonal colour from the warm Paper palette; one blue accent; container roles (`primaryContainer`, `secondaryContainer`) for selection, never for decoration.
- Wavy progress for something alive (focus timer, progress of a task); flat for the rest.
- `ConnectedToggleGroup` / `ButtonGroup` for 2–4 mutually exclusive choices inside sheets.

Don't:
- More than one FAB, or a FAB plus a bottom toolbar.
- Cards inside cards; a shadow on anything that is not lifted (sheets, FAB, menus).
- Colour for its own sake: a tag colour is for a tag; a container colour is for a state.
- Custom animation on frequent taps (checking a task, opening a row). It must feel instant; delight lives in the completion, not the navigation.
- Text under 12 sp, grey-on-grey below 4.5 : 1 (3 : 1 for 18 sp and up), colour as the only carrier of meaning (status has an icon; priority has a shape).

## 7. Right-to-left and Persian

- Use `start`/`end`, never left/right; icons that mean direction (`AutoMirrored`) mirror, icons that mean a thing (clock, calendar) do not.
- Digits, dates (Jalali) and weekday order follow the language; a range's start is at the reading start.
- Measure Persian screenshots too: longer strings must not push a row past its line budget.

## 8. Pre-delivery checklist

Run this before a screen is called done.

1. **Squint test** on a screenshot: can you find the title, the content and the one action in under a second? If not, remove something.
2. **Count**: controls above the first content, layers, marks per row, text sizes. Compare with the table in §2.
3. **Touch**: every stand-alone target ≥ 48 dp (in-row shortcut marks excepted, see §4); ≥ 8 dp between neighbours; no gesture fighting scroll; every drag has an alternative.
4. **State**: empty, loading, error and "nothing left" are designed, with one sentence and one action, no exclamation marks.
5. **Contrast and scale**: light and dark, Persian, 130 % font scale (nothing clipped, nothing overlapping the FAB).
6. **Consistency**: same thing, same name and icon everywhere; sentence case; one verb per action.
7. **Tests**: `:app:testDebugUnitTest` (density budget, timeline gestures) passes; screenshots re-recorded with `:app:recordRoborazziDebug` and looked at, not just generated.

## 9. How to work

1. Screenshot the screen (`docs/screenshots` via Roborazzi) before changing it.
2. Write the numbers from §2 next to it. Anything over budget is the work list.
3. Move things down a ring before you restyle them. Restyling clutter makes prettier clutter.
4. Change, re-record, compare side by side, and only then polish.
5. When a rule here stops being true, change the rule in the same commit as the design.
