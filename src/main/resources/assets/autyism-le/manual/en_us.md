**In short**

- Pick blocks and items from a searchable grid instead of typing IDs.
- Save your schematic edits back to a file.
- Marks blocks that face the wrong way or are wrongly waterlogged; schematic overlays stay visible behind glass.
- Material lists count entities and container contents, and containers get checked too.
- 3D previews in the schematic browser, folder icons and a Replace button in material lists.
- New in 1.4.0: a hotkey takes you to the next wrong or missing block; compare a placement with another version of its schematic; containers you pick count for the material list; real names instead of "Unnamed" and hashes in placement and Syncmatica lists.
- New in 1.3.0: containers of nearby schematics are checked slot by slot all the time and the wrong ones are boxed through walls; opened containers show ghost items for what is missing; Container Searcher learns nearby containers without opening them.
- 1.2.0: an F on containers that still need filling, clear overlays on water and other see-through blocks, and Easy Place puts rails down in the schematic's shape when ALE is on the server too.

Autyism's Litematica Enhancement (ALE) is an add-on for Litematica and MaLiLib that runs on your client; the same jar can also go on a server for exact rail placement. It does not replace Litematica. It adds the small things you miss when you build from schematics every day: a real block picker for block lists, a way to save your schematic edits, clearer error markers, and checks for entities and container contents.

All features except the scrollable button rows can be turned off in ALE's settings. ALE never writes to your schematic files unless you click **Save edits** or **Save as...**.

## Features

### Block lists

- **Pick blocks / Pick items button.** Block lists in Litematica and other MaLiLib-based mods (for example Litematica's `ignorableExistingBlocks` or the printer's block lists) get a **Pick blocks...** button, and item lists get **Pick items...**. No more typing block IDs by hand.
- **Searchable picker.** Your current list is on the left, every block with its icon and name is on the right, and you can search by name (in your game language) or by ID. Click a block to add it, click − to remove it.
- The picker opens straight away when you click a block list, and **Original editor** at the top right opens the normal list editor. Middle click (your pick block key) on a block in the world or the schematic adds it, and blocks with a wall and a standing form (signs, torches, banners, heads, coral fans...) are one entry.

### Saving schematic edits

- **Save edits.** A new button in the placement configuration screen writes the schematic as it is now, including **Replace** in the material list and changes made in Litematica's Edit Schematic mode, back to the placement's `.litematic` file, so your edits survive a restart. While there are unsaved changes, the button reads **Save edits\***.
- **Save as...** Saves the edited schematic as a new file next to the original (the suggested name ends in `_edited`) and leaves the original untouched.
- **Sign wood replacement that keeps your signs.** When you replace a sign's wood with **Replace**, every form of that sign (standing and wall, or hanging and wall hanging) becomes the matching form of the new wood, and rotation, facing, waterlogging and the text on both sides are kept.

### Spotting mistakes in the world

- **Blue "W": wrong waterlogging.** When a placed block is waterlogged and the schematic's isn't (or the other way round), a blue W is drawn on all six faces, on top of Litematica's wrong-state overlay. You can see at once that the block needs water, or must not have any.
- **Red "D": wrong orientation.** When the block is turned the wrong way, a red D is drawn on all six faces. This covers facing, axis, rotation, rail shape, floor/wall/ceiling attachment, door hinge side, top/bottom half and crafter orientation. Every letter that applies is shown, also when other properties are wrong too.
- **Green "F": needs filling.** Containers whose schematic block holds items, such as hoppers, droppers, chests and furnaces, get a green F, so you don't have to open each one. The F goes away once the Schematic Verifier or opening the container shows the right contents; in single player, or on a server with Servux and Litematica's *Entity Data Sync* turned on, ALE also checks the containers near you by itself, so a filled container loses its F without opening it. The F comes back when the container is emptied, broken or replaced. Optionally, containers that hold only items from a skip list (all shulker boxes by default) are left out.
- **Wrong container contents.** Containers of the schematics around you are compared slot by slot all the time (no verifier needed); one that holds the wrong things (wrong items, too many or too few, wrong slots, different disabled crafter slots) gets a bright box you can see through walls. Same conditions as the F: single player, or a server with Servux and *Entity Data Sync*.
- **Slot hints.** Open a container of the schematic and every slot is compared: missing items are drawn as faded ghosts (hover to see what goes there), wrong items are marked red, extra ones purple, wrong counts orange.
- **Container Searcher.** With Loxoz's Container Searcher installed, the containers around you go into its list without opening them (same conditions as above).
- **One marker per block.** When a block is wrong in several ways, only the most important letter is drawn: D (facing) before W (waterlogged) before F (needs filling). Turn **One Marker per Block** off to see them side by side.
- **Highlight signs.** A **Highlight Signs** button in the Schematic Verifier marks signs with text inside the selected schematic through walls, because many schematics leave hints on signs where things need filling: bright green when the sign is placed, dark green when it is still missing.
- **Render Through Glass.** Litematica's overlays and translucent ghost blocks normally disappear behind glass. With ALE they stay visible behind glass and stained glass, which helps when you build windows or greenhouses. Water, bubble columns, ice, slime, honey, kelp and waterlogged blocks no longer wash out the overlay colours either.
- **Translucent schematic entities.** Item frames, armor stands, minecarts, boats and other entities of a schematic are drawn see-through with a light ghost tint, so you can't mistake them for real ones. With Litematica's `renderBlocksAsTranslucent` option turned on, block entities such as chests, signs, beds and shulker boxes turn see-through as well.
- **Entity info overlay.** Hold Litematica's info overlay key (`I`) and look at a schematic entity to get a Schematic / World comparison: facing, item and item rotation for item frames, rotation and equipment for armor stands, facing and variant for paintings. The title is green when everything matches, yellow when something differs (the different lines are highlighted) and red when the entity is missing in the world.
- **Fluid info overlay.** The same key also works on water and lava in the schematic and shows Litematica's usual block info for them.

### Schematic browser

- **Layouts with 3D thumbnails.** A small button at the top-left of Litematica's schematic browser switches between a list, a list with 3D thumbnails and grids with 5, 4 or 3 tiles per row. Thumbnails are built in the background so big folders stay smooth; schematics over the size limit (125,000 blocks by default) show "Too large" and unreadable files "Can't read".
- **3D preview.** The selected schematic is drawn in 3D below its information, with real block models, lighting, all regions and block entities such as chests, signs and banners. Drag to turn it and scroll to zoom; very large schematics stop at about two million faces, so the game never freezes.
- **Full screen and free camera.** Two small buttons open the preview full screen (Esc goes back) or switch on a free camera that flies with your movement keys; switching it off returns to the starting view.
- **Icons for folders and schematics.** Right-click an icon to give a folder or schematic an item as icon, shown small, large, or as the preview of a schematic inside the folder. The icons are stored in ALE's settings file, not in your schematic folders.

### Material lists and verification

- **Entities in material lists.** Item frames, armor stands, minecarts, boats, paintings and other entities that are placed with an item are counted in Litematica's material lists next to the blocks. In a placement's list, entities that already exist in the world are not counted as missing.
- **Contents list.** A **Contents** button in the material list opens a separate list of everything stored in the schematic: items in chests, barrels, shulker boxes, hoppers and other containers, in item frames, on armor stands and in container minecarts. Shulker boxes inside containers are opened up and their contents counted too.
- **Replace.** A **Replace** button next to **Ignore** swaps every block of that material in the schematic for a block you pick from a searchable grid. Shared states such as facing, half and waterlogging are kept, signs, torches, banners and heads keep their standing or wall form, signs keep their text, and the list and the schematic in the world update at once. Middle click (your pick block key) picks the block under the mouse or crosshair, the wall and standing forms are one choice, and when Schematic Preview is installed its **Replace** opens this dialog too (**Schematic Preview's dialog** at the top right opens its own).
- **Container check in the Schematic Verifier.** The verifier also compares the items in each container with the schematic (slot order does not matter). The containers that differ are listed as **Wrong Contents** next to Litematica's own categories, one entry per container type, with the missing and extra items in the tooltip; they are only highlighted when you select the category or an entry. Once a container is fixed, it drops off the list.

### Easy Place

- **Rails in the schematic's shape.** With ALE on the server too (single player counts), Litematica's Easy Place puts each rail down in the schematic's shape right away: ascending rails and curves of all four rail types, without placing other rails first, and without bending rails that are already there. The server checks every placement like a normal one. An ascending rail waits until the block on its high side is there, and a message tells you which side. Without ALE on the server, Easy Place works as before.
- **Fast deepslate for the printer.** With ALE on the server too (single player counts), the printer (Litematica Printer Autyism Edition 1.2.0) mines deepslate over an area like an instant block, with an Efficiency V netherite pickaxe and Haste II. The server only does this when the printer asks for it right before mining a block; mining by hand stays vanilla.

### Litematica screens

- **Scrollable button rows.** When a row of buttons in a Litematica screen does not fit, for example after ALE or another mod adds buttons, you can scroll it with the mouse wheel or the ◀ ▶ arrows instead of the buttons overlapping. Buttons that would cover text or an input box move to the bottom row, and rows that fit stay as they are.

## How to use

ALE has no commands and one hotkey of its own, **Open ALE Settings** (not bound by default). Everything else lives in Litematica's screens and uses Litematica's keys. These are Litematica's default keys:

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

1. Change the schematic, for example with **Replace** in the material list or with Litematica's Edit Schematic mode.
2. Open Litematica's main menu (`M`) → **Schematic Placements** → **Configure** next to your placement.
3. Click **Save edits** to overwrite the file, or **Save as...** to save a copy under a new name. To overwrite an existing file with Save as..., hold Shift when you confirm.
4. If you can't see the buttons, scroll the bottom button row with the mouse wheel or click ▶.

**Check entities and fluids**

1. Look at an entity, or at water or lava, of the schematic.
2. Hold `I`. Green means it matches, yellow means something differs, red means the entity is missing in the world.

**Check container contents**

1. Open the Schematic Verifier (`M` + `V`) and click **Start verification**.
2. **Wrong Contents** appears in the result list next to Litematica's categories. Hover an entry to see the missing and extra items, and select the category or an entry to highlight those containers in the world.
3. Fix the contents. The container drops off the list (and its outline goes away) shortly afterwards.

**See which containers still need filling**

1. Keep Litematica's schematic overlay on. Containers that hold items in the schematic show a green F.
2. Fill them. The F goes away once the verifier or opening the container shows the right contents.
3. To leave out hoppers that only hold empty shulker boxes and similar, turn on **Needs Filling: Skip Listed Items** and edit **Needs Filling: Skip Items**.

**Place rails in the schematic's shape**

1. Put the same ALE jar into the server's `mods` folder (with Fabric API). Litematica and MaLiLib are not needed there. In single player nothing extra is needed.
2. Use Easy Place as usual. Rails go down in the schematic's shape.
3. For an ascending rail, place the block on its high side first; until then Easy Place skips the rail and tells you which side.

**Gather entities and container items**

1. Open the material list (`M` + `L`). Entities are listed together with the blocks.
2. Click **Contents** in the bottom button row to see every item stored inside the schematic.

**Browse schematics with previews**

1. Open Litematica's main menu (`M`) and click **Load Schematics**.
2. Click the small button at the top-left of the list to switch the layout.
3. Click a schematic to see its 3D preview on the right. Drag to turn it, scroll to zoom, and use the two small buttons for full screen and the free camera.

**Give a folder an icon**

1. Right-click the icon of a folder (in the list with previews, the small folder symbol).
2. Type an item ID or click **Pick...**, choose where the icon goes and click **OK**. **Default** removes the icon again.

**Replace a material**

1. In the material list (`M` + `L`), click **Replace** in the row of the material.
2. Search for the new block, click it and click **OK**. A message tells you how many blocks were replaced. You can also middle click (your pick block key) a block in the world or the schematic to choose it.
3. Use **Save edits** if you want to keep the change.

**Change the wood of signs**

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
| Waterlogged Marker | ON | Blue W on blocks with the wrong waterlogging. |
| Waterlogged Marker Color | `#FF1E64FF` (blue) | Colour of the W. |
| Wrong Orientation Marker | ON | Red D on blocks turned the wrong way. |
| Orientation Marker Color | `#FFFF2020` (red) | Colour of the D. |
| Material List: Entities & Contents | ON | Counts entities in material lists and adds the **Contents** button. |
| Verifier: Container Contents | ON | The Schematic Verifier also checks what is inside containers. |
| Sign Wood Replace Fix | ON | Replacing sign wood with Schematic Preview's **Replace** keeps each sign's form, facing, waterlogging and text (ALE's own **Replace** always does). |
| Render Through Glass | ON | Keeps schematic overlays and translucent ghost blocks visible behind glass and stained glass. |
| Needs Filling Marker | ON | Green F on containers whose schematic block holds items. |
| Needs Filling Marker Color | `#FF2BD96B` (green) | Colour of the F. |
| Needs Filling: Skip Listed Items | OFF | No F on containers that hold only items from the list below. |
| Needs Filling: Skip Items | all shulker boxes | The items for the option above. |
| One Marker per Block | ON | Only the most important letter on a block: D, then W, then F. |
| Check Container Contents | OFF | Boxes containers of nearby schematics that hold the wrong things, through walls. |
| Wrong Container Box Color | `#FFB4FF14` (lime) | Colour of those boxes. |
| Container Slot Hints | ON | Ghost items and coloured slots in opened containers of the schematic. |
| Container Searcher Without Opening | ON | With Container Searcher installed, nearby containers go into its list without opening them. |
| Sign Highlight: Placed Color | `#FF55FF55` (bright green) | Placed signs while **Highlight Signs** is on. |
| Sign Highlight: Missing Color | `#FF0E7A2E` (dark green) | Signs that are still missing. |
| Easy Place: Exact Rail Shapes | ON | With ALE on the server too, Easy Place puts rails down in the schematic's shape. |
| Schematic Browser Previews | ON | Browser layouts, thumbnails, the 3D preview and the icons. |
| Material List: Replace | ON | The **Replace** button in material lists. |
| Open ALE Settings | not bound | Hotkey that opens ALE's settings. |

Colours use the `#AARRGGBB` format.

Two more tabs hold the details: **Schematic browser** (Browser Layout: List, Horizontal Gap and Vertical Gap: 2, List Row Height: 15, Preview List Row Height: 35, Tile Height Ratio: 1.0, Thumbnail Size Limit: 125000 blocks) and **3D preview** (Block Entities In Previews: ON, Preview Field Of View: 50, Preview Start Yaw: -45, Preview Start Pitch: 30).

A few Litematica options affect ALE's features: the W and D markers are drawn on Litematica's wrong-state overlay (`enableSchematicOverlay` and `schematicOverlayTypeWrongState` must be on), the entity info needs `blockInfoOverlayEnabled`, and the transparency of schematic entities follows `ghostBlockAlpha`.

## Requirements

There is a separate jar for each Minecraft version:

| Minecraft | Jar | Java | Fabric Loader | MaLiLib | Litematica |
|---|---|---|---|---|---|
| 1.21.5 | `autyism-litematica-enhancement-1.4.0+1.21.5.jar` | 21 or newer | 0.17.0 or newer | 0.24.3 or newer | 0.22.5 or newer |
| 1.21.6, 1.21.7, 1.21.8 | `autyism-litematica-enhancement-1.4.0+1.21.8.jar` | 21 or newer | 0.17.0 or newer | 0.25.7 or newer | 0.23.7 or newer |
| 1.21.9, 1.21.10 | `autyism-litematica-enhancement-1.4.0+1.21.10.jar` | 21 or newer | 0.17.0 or newer | 0.26.8 or newer | 0.24.9 or newer |
| 1.21.11 | `autyism-litematica-enhancement-1.4.0+1.21.11.jar` | 21 or newer | 0.17.0 or newer | 0.27.0 or newer | 0.26.0 or newer |
| 26.1, 26.1.1, 26.1.2 | `autyism-litematica-enhancement-1.4.0+26.1.2.jar` | 25 or newer | 0.19.3 or newer | 0.28.12 or newer | 0.27.14 or newer |
| 26.2 | `autyism-litematica-enhancement-1.4.0+26.2.jar` | 25 or newer | 0.19.3 or newer | 0.29.6 or newer | 0.28.8 or newer |
| 26.3 | `autyism-litematica-enhancement-1.4.0+26.3.jar` | 25 or newer | 0.19.3 or newer | 0.30.2 or newer | 0.29.1 or newer |

Fabric API, MaLiLib and Litematica are required, each in the build for your Minecraft version.

| Optional | |
|---|---|
| Mod Menu | adds a settings button to the mod list |
| Schematic Preview | not needed (when it is installed, ALE leaves the previews, icons and **Replace** to it); it has no 1.21.5 version |
| Litematica Printer Autyism Edition | its block lists get the picker too |

ALE runs on your client and needs nothing on the server. Only for rails in the schematic's shape with Easy Place and the printer's fast deepslate, put the same jar on the server too; the server needs Fabric API, not Litematica or MaLiLib.

## Compatibility

- **Sodium and Iris:** ALE's rendering features, including the 3D previews, were tested with Sodium and Iris installed on 1.21.11, and with Sodium on 1.21.5, 1.21.8, 1.21.10 and 26.1.2. With a shader pack on, the overlays on water and other see-through blocks are drawn the way they were before 1.2.0.
- **Other rendering mods:** Render Through Glass changes when Litematica draws its see-through parts. If another mod changes world rendering so that this is not possible, ALE falls back to Litematica's normal drawing and only the see-through-glass effect is lost.
- **Schematic Preview:** optional. When it is installed, ALE turns its own browser previews, icons and **Replace** off and Schematic Preview provides them; ALE still keeps signs intact when sign wood is replaced with its **Replace**.
- **Litematica Printer Autyism Edition:** optional and independent; neither mod needs the other. With both installed, the printer's block lists get the picker.
- **Other MaLiLib-based mods:** their block and item lists get the picker when ALE recognizes them (by the list's name or its current entries).
- **Mods that add buttons to Litematica screens:** crowded rows become scrollable, so all buttons stay reachable.
- **Litematica versions:** ALE changes how parts of Litematica work, so it depends on Litematica's internals. It was built and tested with Litematica 0.22.5 and MaLiLib 0.24.3 on 1.21.5, Litematica 0.23.7 and MaLiLib 0.25.7 on 1.21.8, Litematica 0.24.9 and MaLiLib 0.26.8 on 1.21.10, Litematica 0.26.16 and MaLiLib 0.27.20 on 1.21.11, Litematica 0.27.14 and MaLiLib 0.28.12 on 26.1.2, Litematica 0.28.8 and MaLiLib 0.29.6 on 26.2, and Litematica 0.29.1 and MaLiLib 0.30.2 on 26.3; a future Litematica update may need a matching ALE update.
- No incompatibilities are known at the time of release.

## FAQ

**Do I need ALE on the server?**
No. Everything works with ALE only on your client (see Known limitations for the container check on servers). Only Easy Place's rails in the schematic's shape and the printer's fast deepslate need ALE on the server too; without it, Easy Place places rails like before and deepslate is mined one block at a time.

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
- With Schematic Preview's own **Replace** dialog, the sign fix only applies when a sign is replaced with another sign.
- The 3D previews show blocks and block entities, not entities such as item frames or armor stands.
- Very large schematics are shown only partly in the 3D preview (about two million faces), and schematics over the size limit get no thumbnail.
- The previews were tested with Sodium and Iris without a shader pack; with a shader pack they may look different.
- Material lists only count entities that are placed with an item; mobs are not counted.
- Entities are matched by type and position, not by identity.
- The container check only looks at containers whose block already matches the schematic exactly. On multiplayer servers ALE can only read container contents when Litematica receives block entity data (Litematica's entity data sync, for example with Servux on the server); otherwise those containers are not checked. The container check was verified in single player.
- The F marker hides a container once it is known to hold the right items (from the verifier or from opening it); until then it stays, even if the container is already full.
- Rails in the schematic's shape need ALE on the server. An ascending rail is only placed once the block on its high side is there.
