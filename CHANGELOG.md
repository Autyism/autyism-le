# Changelog

## 1.2.0 — 2026-10-14

For Minecraft 1.21.11; the other jars stay at 1.1.0.

- Mismatch colours stay clear on water, bubble columns, ice, slime and other see-through blocks, and W and D show up for every wrong property.
- Container contents are a category in the Schematic Verifier and only get highlighted when you select them.
- A green F on containers that still need filling. It disappears by itself once the container is filled right (single player, or Servux with Litematica's Entity Data Sync) and comes back when it no longer matches. A double chest gets one F in the middle.
- **One Marker per Block** (on by default): only the most important letter is drawn, D before W before F.
- **Highlight Signs** button in the Schematic Verifier: signs with text are marked through walls, bright green when placed, dark green when still missing.
- With Sodium, Render Through Glass works behind clear glass too.
- Easy Place puts rails down in the schematic's shape, ascending rails and curves included, when ALE is on the server too (or in single player).
- Pick block in the Replace dialog and the block picker; wall and standing forms are one entry, and ALE's screens open first.
- With a shader pack and LitematicaPass, the mismatch colours no longer disappear.
- Works with Litefastica (fast schematic moving): W and D show on the placements it draws, and Render Through Glass works for them too.

More: open "Details" below.

<details><summary>Details</summary>

- **Overlays on see-through blocks**: with Render Through Glass on, water, bubble columns, ice, stained glass, slime, honey, kelp and waterlogged blocks no longer wash out Litematica's colours. With a shader pack on, the overlays are drawn as before.
- **W and D**: a block that is wrong in waterlogging and in other properties (a rail with the wrong shape and water, for example) shows W, and also D when it faces the wrong way; with One Marker per Block off the letters sit side by side, F always last.
- **Container contents in the verifier**: **Wrong Contents** is listed next to Litematica's categories, with one entry per container type and the missing and extra items in the tooltip. Select the category or an entry to highlight those containers; Ignore and Reset ignored work too.
- **Needs filling (F)**: containers whose schematic block holds items get an F, without opening each one. It goes away once the verifier, opening the container, or (in single player, or with Servux and Litematica's Entity Data Sync on) an automatic check of containers near you shows the right contents, and comes back when the container is emptied, broken or replaced. A skip list (off by default; all shulker boxes in it) leaves out containers that hold only those items, for example the hoppers full of empty shulker boxes under a box loader.
- **Exact rails**: put the same ALE jar on the server too. The server needs Fabric API but not Litematica or MaLiLib. The server checks each placement like a normal one and places the rail in the schematic's shape, without bending rails that are already there. An ascending rail waits until the block on its high side is there. Without ALE on the server, Easy Place works as before. Other mods (such as a printer) can use the same placement.
- **Replace and block lists**: middle click (or your pick block key) picks the block under the mouse or crosshair, in the world or the schematic. Signs, hanging signs, torches, banners, heads and coral fans are one entry each, and every replaced block keeps its own form. Block lists and Schematic Preview's **Replace** open ALE's screen; a button at the top right opens the original one.
- New settings: **Needs Filling Marker** (with colour, skip switch and skip list), **One Marker per Block**, **Sign Highlight** colours and **Easy Place: Exact Rail Shapes**.

</details>

### 中文

适用于 Minecraft 1.21.11；其他版本的 jar 仍是 1.1.0。

- 水、气泡柱、冰、粘液块等半透明方块上的错误颜色清楚了；W 和 D 在多个属性不对时也都会显示。
- 容器内容物在投影验证器里是一个分类，只有选中时才高亮。
- 还需要往里放东西的容器显示绿色的 F；填对了自己消失（单人游戏，或者服务器有 Servux 并打开了投影的“实体数据同步”），之后又不对了会重新出现。大箱子只在中间画一个 F。
- **标记只显示一个**（默认开）：一个方块只画最重要的字母，朝向 D 优先，其次含水 W，最后填充 F。
- 投影验证器界面新增 **高亮告示牌** 按钮：有字的告示牌透墙标出来，已放置亮绿色，还没放深绿色。
- 装了 Sodium 时，“透过玻璃显示投影”对普通玻璃也有效了。
- 服务端也装了 ALE 时（单人游戏也算），轻松放置直接把铁轨放成投影里的形状，上坡、弯道都一次放好。
- “替换”界面和方块选择界面可以用选取方块键；挂墙和立着的形态合成一项，默认打开 ALE 的界面。
- 开着光影、装了 LitematicaPass 时，错误颜色不会再消失。
- 兼容 Litefastica（快速移动投影）：它接管的投影上也显示 W 和 D，“透过玻璃显示投影”也对它们生效。

更多：点开下面的“详细说明”。

<details><summary>详细说明</summary>

- **半透明方块上的标记**：开着“透过玻璃显示投影”时，水、气泡柱、冰、染色玻璃、粘液块、蜂蜜块、海带和含水方块不再把 Litematica 的颜色冲淡。开着光影包时照以前的方式画。
- **W 和 D**：含水不对、同时别的属性也不对的方块（比如形状和含水都不对的铁轨）也显示 W，朝向不对时同时显示 D；关掉“标记只显示一个”时几个字母并排，F 总在最后。
- **验证器里的容器内容物**：Litematica 的分类旁边多了 **错误内容物**，每种容器一项，鼠标停在上面能看到缺少和多出的物品。选中分类或某一项才高亮这些容器；忽略和“重置忽略”也能用。
- **需要填充（F）**：投影里装着东西的容器显示 F，不用一个个打开看。验证器、打开容器、或者自动检查（单人游戏；或者服务器有 Servux 并打开了投影的“实体数据同步”时，查玩家附近的容器）确认内容物对了以后，F 就不显示了；之后被拿空、敲掉或换掉会重新显示。跳过列表（默认关闭，列表里是各色潜影盒）可以不标只装着这些物品的容器，比如打包机下面装满空潜影盒的一排漏斗。
- **铁轨按投影形状放**：把同一个 ALE jar 也放进服务端。服务端需要 Fabric API，不需要 Litematica 和 MaLiLib。服务端像普通放置一样检查每一次放置，按投影的形状放下铁轨，也不会把已经放好的铁轨拉歪。上坡铁轨等高的那一侧的方块放好再放。服务端没装 ALE 时，轻松放置照旧。别的模组（比如打印机）也能用同样的放置方式。
- **替换和方块列表**：鼠标中键（或者你的选取方块键）选鼠标或准星指着的方块，世界里的和投影里的都可以。告示牌、悬挂式告示牌、火把、旗帜、头颅和珊瑚扇各算一项，替换后每个方块保留自己的形态。方块列表和 Schematic Preview 的“替换”默认打开 ALE 的界面，右上角的按钮打开原来的界面。
- 新设置：**需要填充标记**（含颜色、跳过开关和跳过列表）、**标记只显示一个**、**高亮告示牌** 的两种颜色和 **轻松放置：铁轨按投影形状**。

</details>

## 1.1.0 — 2026-10-08

- Schematic browser layouts: a list, a list with 3D thumbnails, or grids with 5, 4 or 3 tiles per row.
- A 3D preview of the selected schematic, with full screen and a free camera.
- Item icons for folders and schematics (right-click an icon).
- **Replace** in material lists, and the sign wood fix works with it without Schematic Preview.
- For Minecraft 1.21.11 and 26.1–26.3; the 1.21.5–1.21.10 jars stay at 1.0.0.

More: open "Details" below.

<details><summary>Details</summary>

- **Browser layouts**: a small button at the top-left of Litematica's schematic browser switches between the list, a list with 3D thumbnails and grids with 5, 4 or 3 tiles per row. Thumbnails are built in the background; schematics over the size limit (125,000 blocks by default) show "Too large".
- **3D preview** under the schematic's information: real block models with lighting, all regions and block entities. Drag to turn, scroll to zoom, and use the two small buttons for full screen and a free camera that flies with the movement keys.
- **Icons**: right-click the icon of a folder or schematic to pick an item, shown small, large or as the preview of a schematic inside the folder. The icons are kept in ALE's settings file.
- **Replace** next to **Ignore** in material lists: swaps every block of a material for another block, keeps shared states, standing/wall forms and sign text, and updates the list and the schematic right away. Replacing sign wood keeps the signs intact without Schematic Preview.
- New **Schematic browser** and **3D preview** settings tabs, and an **Open ALE Settings** hotkey (not bound).
- When Schematic Preview is installed, ALE leaves the browser previews, icons and **Replace** to it. These features follow DimasKama's Schematic Preview.
- The 1.21.5–1.21.10 builds are not re-released; the new features come to them later.

</details>

### 中文

- 投影浏览器的显示方式：列表、带 3D 缩略图的列表，或者每行 5 / 4 / 3 个的网格。
- 选中投影的 3D 预览，支持全屏和自由视角。
- 文件夹和投影可以用物品做图标（右键图标）。
- 材料列表加入“替换”，用它更换告示牌木材不再需要 Schematic Preview。
- 适用于 Minecraft 1.21.11 和 26.1–26.3；1.21.5–1.21.10 的 jar 仍是 1.0.0。

更多：点开下面的“详细说明”。

<details><summary>详细说明</summary>

- **浏览器显示方式**：Litematica 投影浏览器左上角的小按钮可以在列表、带 3D 缩略图的列表和每行 5 / 4 / 3 个的网格之间切换。缩略图在后台生成；超过体积上限（默认 125000 个方块）的投影显示“太大”。
- 投影信息下面的 **3D 预览**：真实的方块模型和光照、所有区域和方块实体。拖动转动，滚轮缩放，两个小按钮分别打开全屏和自由视角（用移动键飞行）。
- **图标**：右键文件夹或投影的图标选一个物品，可以显示成小图标、大图标，或者显示文件夹里一个投影的预览。图标保存在 ALE 的设置文件里。
- 材料列表“忽略”旁边的 **替换**：把一种材料的方块全部换成另一种方块，保留共有的方块状态、立式/挂墙形态和告示牌文字，列表和投影会马上更新。更换告示牌木材时，不装 Schematic Preview 也会保留告示牌。
- 设置里新增“投影浏览器”和“3D 预览”分页，以及“打开 ALE 设置”热键（默认不绑定）。
- 装了 Schematic Preview 时，浏览器预览、图标和“替换”由它提供。这些功能参照了 DimasKama 的 Schematic Preview。
- 1.21.5–1.21.10 不重新发布，新功能以后再加。

</details>

## 1.0.0+1.21.10 — 2026-10-07

ALE for Minecraft 1.21.9–1.21.10, with the same features and settings as 1.0.0 for 1.21.11.

- Needs Java 21, Fabric Loader 0.17.0 or newer, MaLiLib 0.26.8 or newer and Litematica 0.24.9 or newer.
- Other Minecraft versions have their own jar; see the table in the README.

### 中文

适用于 Minecraft 1.21.9–1.21.10 的 ALE，功能和设置与 1.21.11 的 1.0.0 相同。

- 需要 Java 21、Fabric Loader 0.17.0 或更高、MaLiLib 0.26.8 或更高、Litematica 0.24.9 或更高。
- 其他 Minecraft 版本有各自的 jar，见说明里的表格。

## 1.0.0+1.21.8 — 2026-10-07

ALE for Minecraft 1.21.6–1.21.8, with the same features and settings as 1.0.0 for 1.21.11.

- Needs Java 21, Fabric Loader 0.17.0 or newer, MaLiLib 0.25.7 or newer and Litematica 0.23.7 or newer.
- Other Minecraft versions have their own jar; see the table in the README.

### 中文

适用于 Minecraft 1.21.6–1.21.8 的 ALE，功能和设置与 1.21.11 的 1.0.0 相同。

- 需要 Java 21、Fabric Loader 0.17.0 或更高、MaLiLib 0.25.7 或更高、Litematica 0.23.7 或更高。
- 其他 Minecraft 版本有各自的 jar，见说明里的表格。

## 1.0.0+1.21.5 — 2026-10-07

ALE for Minecraft 1.21.5, with the same features and settings as 1.0.0 for 1.21.11.

- Needs Java 21, Fabric Loader 0.17.0 or newer, MaLiLib 0.24.3 or newer and Litematica 0.22.5 or newer.
- Schematic Preview has no 1.21.5 version, so the sign wood replacement that works with its Replace button is not available here.
- Other Minecraft versions have their own jar; see the table in the README.

### 中文

适用于 Minecraft 1.21.5 的 ALE，功能和设置与 1.21.11 的 1.0.0 相同。

- 需要 Java 21、Fabric Loader 0.17.0 或更高、MaLiLib 0.24.3 或更高、Litematica 0.22.5 或更高。
- Schematic Preview 没有 1.21.5 版，所以配合它“Replace”按钮的告示牌木材替换在这里用不了。
- 其他 Minecraft 版本有各自的 jar，见说明里的表格。

## 1.0.0+26.3 — 2026-10-07

ALE for Minecraft 26.3, with the same features and settings as 1.0.0 for 1.21.11.

- Needs Java 25, Fabric Loader 0.19.3 or newer, MaLiLib 0.30.2 or newer and Litematica 0.29.1 or newer.
- Other Minecraft versions have their own jar: `autyism-litematica-enhancement-1.0.0.jar` for 1.21.11, `autyism-litematica-enhancement-1.0.0+26.1.2.jar` for 26.1–26.1.2.

### 中文

适用于 Minecraft 26.3 的 ALE，功能和设置与 1.21.11 的 1.0.0 相同。

- 需要 Java 25、Fabric Loader 0.19.3 或更高、MaLiLib 0.30.2 或更高、Litematica 0.29.1 或更高。
- 其他 Minecraft 版本有各自的 jar：1.21.11 用 `autyism-litematica-enhancement-1.0.0.jar`，26.1–26.1.2 用 `autyism-litematica-enhancement-1.0.0+26.1.2.jar`。

## 1.0.0+26.2 — 2026-10-07

ALE for Minecraft 26.2, with the same features and settings as 1.0.0 for 1.21.11.

- Needs Java 25, Fabric Loader 0.19.3 or newer, MaLiLib 0.29.6 or newer and Litematica 0.28.8 or newer.
- Other Minecraft versions have their own jar: `autyism-litematica-enhancement-1.0.0.jar` for 1.21.11, `autyism-litematica-enhancement-1.0.0+26.1.2.jar` for 26.1–26.1.2.

### 中文

适用于 Minecraft 26.2 的 ALE，功能和设置与 1.21.11 的 1.0.0 相同。

- 需要 Java 25、Fabric Loader 0.19.3 或更高、MaLiLib 0.29.6 或更高、Litematica 0.28.8 或更高。
- 其他 Minecraft 版本有各自的 jar：1.21.11 用 `autyism-litematica-enhancement-1.0.0.jar`，26.1–26.1.2 用 `autyism-litematica-enhancement-1.0.0+26.1.2.jar`。

## 1.0.0+26.1.2 — 2026-10-07

ALE for Minecraft 26.1, 26.1.1 and 26.1.2, with the same features as 1.0.0 for 1.21.11.

- Needs Java 25, Fabric Loader 0.19.3 or newer, MaLiLib 0.28.12 or newer and Litematica 0.27.14 or newer.
- 1.21.11 players keep using `autyism-litematica-enhancement-1.0.0.jar`.

### 中文

适用于 Minecraft 26.1、26.1.1 和 26.1.2 的 ALE，功能与 1.21.11 的 1.0.0 相同。

- 需要 Java 25、Fabric Loader 0.19.3 或更高、MaLiLib 0.28.12 或更高、Litematica 0.27.14 或更高。
- 1.21.11 玩家继续用 `autyism-litematica-enhancement-1.0.0.jar`。

## 1.0.0 — 2026-10-04

First public release.

- **Pick blocks... / Pick items...**: a searchable picker for block and item lists in Litematica and other MaLiLib-based mods.
- **Save edits** and **Save as...** buttons in the placement configuration screen, so Schematic Preview replacements and Edit Schematic changes can be saved to a file.
- Blue **W** marker on blocks that only differ in waterlogging.
- Red **D** marker on the right block turned the wrong way.
- **Render Through Glass**: schematic overlays and translucent ghost blocks stay visible behind glass and stained glass.
- Translucent, ghost-tinted schematic entities.
- Info overlay for schematic entities (schematic vs. world comparison) and schematic fluids.
- Entities are counted in material lists; a new **Contents** list shows every item stored in the schematic.
- The Schematic Verifier checks container contents and outlines mismatched containers in magenta.
- Replacing sign wood with Schematic Preview keeps each sign's form, facing, waterlogging and text.
- Crowded button rows in Litematica screens become scrollable.

### 中文

首个公开版本。

- **选择方块… / 选择物品…**：为 Litematica 和其他 MaLiLib 系模组的方块、物品列表加入可搜索的选择界面。
- 投影放置配置界面加入 **保存修改** 和 **另存为…** 按钮，Schematic Preview 的替换和“编辑原理图”模式的修改可以保存到文件。
- 只差含水的方块显示蓝色 **W** 标记。
- 方块对但朝向错误时显示红色 **D** 标记。
- **透过玻璃显示投影**：投影覆盖层和半透明投影方块在玻璃、染色玻璃后面也能看到。
- 投影实体以带幽灵色调的半透明效果渲染。
- 信息显示支持投影实体（投影与世界对比）和投影中的流体。
- 材料列表统计实体；新增 **容器内容物** 列表，列出投影里存放的所有物品。
- 投影验证器检查容器内容物，不一致的容器用洋红色框标出。
- 用 Schematic Preview 替换告示牌木材时，保留每个告示牌的形态、朝向、含水和文字。
- Litematica 界面中放不下的按钮行可以左右滚动。
