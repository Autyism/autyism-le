<p align="center"><img src="docs/icon.png" width="128" alt="icon"></p>
<h1 align="center">Autyism's Litematica Enhancement</h1>
<p align="center">Practical quality-of-life additions for building with Litematica.</p>

**English** | [简体中文](README.zh-CN.md)

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue)

Autyism's Litematica Enhancement (ALE) is a client-side add-on for [Litematica](https://modrinth.com/mod/litematica) and [MaLiLib](https://modrinth.com/mod/malilib). It does not replace Litematica. It adds the small things you miss when you build from schematics every day: a real block picker for block lists, a way to save your schematic edits, clearer error markers, and checks for entities and container contents.

All features except the scrollable button rows can be turned off in ALE's settings. ALE never writes to your schematic files unless you click **Save edits** or **Save as...**.

## Features

### Block lists

- **Pick blocks / Pick items button.** Block lists in Litematica and other MaLiLib-based mods (for example Litematica's `ignorableExistingBlocks` or the printer's block lists) get a **Pick blocks...** button, and item lists get **Pick items...**. No more typing block IDs by hand.
- **Searchable picker.** Your current list is on the left, every block with its icon and name is on the right, and you can search by name (in your game language) or by ID. Click a block to add it, click − to remove it.

### Saving schematic edits

- **Save edits.** A new button in the placement configuration screen writes the schematic as it is now, including Schematic Preview replacements and changes made in Litematica's Edit Schematic mode, back to the placement's `.litematic` file, so your edits survive a restart. While there are unsaved changes, the button reads **Save edits\***.
- **Save as...** Saves the edited schematic as a new file next to the original (the suggested name ends in `_edited`) and leaves the original untouched.
- **Sign wood replacement that keeps your signs** (needs Schematic Preview). When you replace a sign's wood, every form of that sign (standing and wall, or hanging and wall hanging) becomes the matching form of the new wood. Rotation, facing, waterlogging and the text on both sides are kept.

### Spotting mistakes in the world

- **Blue "W": only waterlogging is wrong.** When a placed block is correct except for being waterlogged (or not), a blue W is drawn on all six faces, on top of Litematica's wrong-state overlay. You can see at once that the block only needs water, or must not have any.
- **Red "D": wrong orientation.** When it is the right block but turned the wrong way, a red D is drawn on all six faces. This covers facing, axis, rotation, rail shape, floor/wall/ceiling attachment, door hinge side, top/bottom half and crafter orientation; if both letters would apply, D is shown.
- **Render Through Glass.** Litematica's overlays and translucent ghost blocks normally disappear behind glass. With ALE they stay visible behind glass and stained glass, which helps when you build windows or greenhouses.
- **Translucent schematic entities.** Item frames, armor stands, minecarts, boats and other entities of a schematic are drawn see-through with a light ghost tint, so you can't mistake them for real ones. With Litematica's `renderBlocksAsTranslucent` option turned on, block entities such as chests, signs, beds and shulker boxes turn see-through as well.
- **Entity info overlay.** Hold Litematica's info overlay key (`I`) and look at a schematic entity to get a Schematic / World comparison: facing, item and item rotation for item frames, rotation and equipment for armor stands, facing and variant for paintings. The title is green when everything matches, yellow when something differs (the different lines are highlighted) and red when the entity is missing in the world.
- **Fluid info overlay.** The same key also works on water and lava in the schematic and shows Litematica's usual block info for them.

### Material lists and verification

- **Entities in material lists.** Item frames, armor stands, minecarts, boats, paintings and other entities that are placed with an item are counted in Litematica's material lists next to the blocks. In a placement's list, entities that already exist in the world are not counted as missing.
- **Contents list.** A **Contents** button in the material list opens a separate list of everything stored in the schematic: items in chests, barrels, shulker boxes, hoppers and other containers, in item frames, on armor stands and in container minecarts. Shulker boxes inside containers are opened up and their contents counted too.
- **Container check in the Schematic Verifier.** The verifier also compares the items in each container with the schematic (slot order does not matter). Containers that differ get a magenta outline and a message tells you how many there are; once a container is fixed, its outline disappears.

### Litematica screens

- **Scrollable button rows.** When a row of buttons in a Litematica screen does not fit, for example after ALE or another mod adds buttons, you can scroll it with the mouse wheel or the ◀ ▶ arrows instead of the buttons overlapping. Buttons that would cover text or an input box move to the bottom row, and rows that fit stay as they are.

## Screenshots

![W and D markers](docs/images/markers-w-d.png)

*Blue W: only waterlogging differs. Red D: the right block, turned the wrong way.*

![Behind stained glass, Render Through Glass off](docs/images/glass-off.png)

*Render Through Glass off: Litematica's markers are hidden behind stained glass.*

![Behind stained glass, Render Through Glass on](docs/images/glass-on.png)

*Render Through Glass on: the same view, with the wrong-orientation stairs (D) and the missing block visible.*

![Schematic entities drawn solid](docs/images/entities-off.png)

*Without Translucent Schematic Entities: the schematic's armor stand, minecart, boat and item frame look like real ones.*

![Schematic entities drawn see-through](docs/images/entities-on.png)

*With Translucent Schematic Entities: see-through with a ghost tint, while the real chest stays solid.*

![Pick blocks button](docs/images/pick-blocks-button.png)

*The new Pick blocks... button in one of Litematica's block lists.*

![Block picker](docs/images/block-picker.png)

*The picker: current list on the left, all blocks on the right, searchable by name or ID.*

![Save as](docs/images/save-as.png)

*Save as: the edited placement is written to a new schematic file.*

![Container contents](docs/images/container-contents.png)

*Contents: a material list of everything the schematic's containers should hold.*

![Verifier with containers](docs/images/verifier-container.png)

*The Schematic Verifier marks a container whose contents differ from the schematic.*

![Entity info overlay](docs/images/info-entity.png)

*The info overlay compares a schematic entity with the one in the world.*

## How to use

ALE adds no hotkeys and no commands of its own. Its features live in Litematica's screens and use Litematica's keys. These are Litematica's default keys:

| Action | Default key | Where to change it |
|---|---|---|
| Show the info overlay (entity comparison, fluid info) | Hold `I` | Litematica settings → Hotkeys → `renderInfoOverlay` |
| Open Litematica's main menu (Schematic Placements → Configure has the save buttons) | `M` | Litematica settings → Hotkeys → `openGuiMainMenu` |
| Open the material list of the selected placement | `M` + `L` | Litematica settings → Hotkeys → `openGuiMaterialList` |
| Open the Schematic Verifier of the selected placement | `M` + `V` | Litematica settings → Hotkeys → `openGuiSchematicVerifier` |
| Open Litematica's settings | `M` + `C` | Litematica settings → Hotkeys → `openGuiSettings` |

`M` + `C` means: hold M, then press C.

**Opening ALE's settings**

- With Mod Menu: Mods → Autyism's Litematica Enhancement → settings button.
- Without Mod Menu: open Litematica's settings (`M` + `C`) and pick **Autyism's Litematica Enhancement** in the drop-down list in the top-right corner. Most other MaLiLib-based mods have the same list in their settings screens.
- The settings are stored in `config/autyism-le.json`.

**Pick blocks for a list**

1. Open the settings of Litematica (or another MaLiLib-based mod) and click a block list to edit it, for example `ignorableExistingBlocks` on the Visuals tab.
2. Click **Pick blocks...** in the top-right corner of the list editor.
3. Type part of a name or ID into the search box. Click a block on the right to add it (added blocks turn green with a check mark), or click − on the left to remove one. Shift-click **Clear list** to empty the whole list.
4. Click **Done**. The list editor now shows the new entries.

**Keep your schematic edits**

1. Change the schematic, for example with Schematic Preview's **Replace** in the material list or with Litematica's Edit Schematic mode.
2. Open Litematica's main menu (`M`) → **Schematic Placements** → **Configure** next to your placement.
3. Click **Save edits** to overwrite the file, or **Save as...** to save a copy under a new name. To overwrite an existing file with Save as..., hold Shift when you confirm.
4. If you can't see the buttons, scroll the bottom button row with the mouse wheel or click ▶.

**Check entities and fluids**

1. Look at an entity, or at water or lava, of the schematic.
2. Hold `I`. Green means it matches, yellow means something differs, red means the entity is missing in the world.

**Check container contents**

1. Open the Schematic Verifier (`M` + `V`) and click **Start verification**.
2. When it finishes, containers whose contents differ get a magenta outline and a message tells you how many there are.
3. Fix the contents. The outline goes away on its own shortly afterwards.

**Gather entities and container items**

1. Open the material list (`M` + `L`). Entities are listed together with the blocks.
2. Click **Contents** in the bottom button row to see every item stored inside the schematic.

**Change the wood of signs (needs Schematic Preview)**

1. In the material list, click **Replace** next to a sign.
2. Choose a sign of another wood and confirm. A message shows how many signs were changed.
3. Use **Save edits** if you want to keep the change.

## Settings

| Option (as shown in game) | Default | What it does |
|---|---|---|
| Block Picker For Block Lists | ON | Adds **Pick blocks...** / **Pick items...** to block and item list editors. |
| Save Edit Buttons | ON | Adds **Save edits** and **Save as...** to the placement configuration screen. |
| Info Overlay: Fluids & Entities | ON | The info overlay also compares schematic entities and shows info for schematic fluids. |
| Translucent Schematic Entities | ON | Draws schematic entities see-through with a ghost tint. |
| Waterlogged-Only Marker | ON | Blue W on blocks that only differ in waterlogging. |
| Waterlogged Marker Color | `#FF1E64FF` (blue) | Colour of the W. |
| Wrong Orientation Marker | ON | Red D on the right block turned the wrong way. |
| Orientation Marker Color | `#FFFF2020` (red) | Colour of the D. |
| Material List: Entities & Contents | ON | Counts entities in material lists and adds the **Contents** button. |
| Verifier: Container Contents | ON | The Schematic Verifier also checks what is inside containers. |
| Sign Wood Replace Fix | ON | Replacing sign wood with Schematic Preview keeps each sign's form, facing, waterlogging and text. |
| Render Through Glass | ON | Keeps schematic overlays and translucent ghost blocks visible behind glass and stained glass. |

Colours use the `#AARRGGBB` format.

A few Litematica options affect ALE's features: the W and D markers are drawn on Litematica's wrong-state overlay (`enableSchematicOverlay` and `schematicOverlayTypeWrongState` must be on), the entity info needs `blockInfoOverlayEnabled`, and the transparency of schematic entities follows `ghostBlockAlpha`.

## Requirements

| | Required? | Version |
|---|---|---|
| Minecraft | Required | 1.21.11 |
| Fabric Loader | Required | 0.17.0 or newer |
| Fabric API | Required | any version for 1.21.11 |
| MaLiLib | Required | 0.27.0 or newer |
| Litematica | Required | 0.26.0 or newer |
| Java | Required | 21 or newer |
| Mod Menu | Optional | adds a settings button to the mod list |
| Schematic Preview | Optional | needed for the sign wood replacement |
| Litematica Printer Autyism Edition | Optional | its block lists get the picker too |

ALE is client-side only. Nothing needs to be installed on the server.

## Compatibility

- **Sodium and Iris:** ALE's rendering features were tested with Sodium and Iris installed.
- **Other rendering mods:** Render Through Glass changes when Litematica draws its see-through parts. If another mod changes world rendering so that this is not possible, ALE falls back to Litematica's normal drawing and only the see-through-glass effect is lost.
- **Schematic Preview:** optional. Its **Replace** button works as usual; ALE only steps in when a sign is replaced with another sign.
- **Litematica Printer Autyism Edition:** optional and independent; neither mod needs the other. With both installed, the printer's block lists get the picker.
- **Other MaLiLib-based mods:** their block and item lists get the picker when ALE recognizes them (by the list's name or its current entries).
- **Mods that add buttons to Litematica screens:** crowded rows become scrollable, so all buttons stay reachable.
- **Litematica versions:** ALE changes how parts of Litematica work, so it depends on Litematica's internals. It was built and tested with Litematica 0.26.16 and MaLiLib 0.27.20; a future Litematica update may need a matching ALE update.
- No incompatibilities are known at the time of release.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API, MaLiLib and Litematica for 1.21.11 into your `mods` folder.
3. Download `autyism-litematica-enhancement-1.0.0.jar` and put it into the `mods` folder too.
4. Optional: add Mod Menu and Schematic Preview.
5. Start the game.

## FAQ

**Do I need ALE on the server?**
No. ALE runs only on your client and works without anything on the server (see Known limitations for the container check on servers).

**Does ALE need the printer, or the printer ALE?**
No. ALE and Litematica Printer Autyism Edition are separate mods and each works on its own.

**Where is the Save edits button?**
At the end of the bottom button row in the placement configuration screen (Schematic Placements → Configure). If the row is too long, scroll it with the mouse wheel or click ▶. Also check that Save Edit Buttons is on.

**The W or D markers don't show up.**
They are drawn on Litematica's wrong-state overlay, so they only appear on blocks Litematica marks as "wrong state", and only when Litematica's `enableSchematicOverlay` and `schematicOverlayTypeWrongState` options are on.

**Does ALE change my schematic files on its own?**
No. ALE only writes to a schematic file when you click **Save edits** or **Save as...**. Save edits writes a temporary file first and then replaces the original, and both buttons remove leftover data of replaced blocks, for example the contents of a chest that was replaced by stone.

**Does ALE slow the game down?**
The screen features only run while a screen is open, the info overlay only while you hold its key, and the markers are built together with Litematica's own overlay. In testing, frame rates were the same with ALE on and off.

## Known limitations

- The picker adds plain block or item IDs. Block tags such as `#minecraft:wooden_slabs` still have to be typed in the normal list editor; entries the picker doesn't recognize are shown as they are and can still be removed.
- The picker search matches names in your current game language and IDs; there is no fuzzy or pinyin search.
- Lists are recognized by their name and current entries, so an unusual list may not get the picker button.
- **Save edits** can only overwrite `.litematic` files. A placement without a file, or one loaded from another format, needs **Save as...**.
- The sign fix needs Schematic Preview and only applies when a sign is replaced with another sign.
- Material lists only count entities that are placed with an item; mobs are not counted.
- Entities are matched by type and position, not by identity.
- The container check only looks at containers whose block already matches the schematic exactly. On multiplayer servers ALE can only read container contents when Litematica receives block entity data (Litematica's entity data sync, for example with Servux on the server); otherwise those containers are not checked. The container check was verified in single player.

## Credits

- **masa** and **Sakura-Ryoko** for Litematica and MaLiLib, which ALE builds on.
- **DimasKama** for Schematic Preview, used by the optional sign wood replacement.
- The layout of the block picker is inspired by the block list setting in Meteor Client.

Made by **Autyism**. Source code and bug reports: [GitHub](https://github.com/Autyism/autyism-le) · [Issues](https://github.com/Autyism/autyism-le/issues)

## License

**AGPL-3.0-only**: ALE is free software under the GNU Affero General Public License v3.0; see [LICENSE.md](LICENSE.md).
