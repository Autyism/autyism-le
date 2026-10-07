plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        // Before 1.21.11 the render types are static methods on renderer.RenderType (no rendertype package, no RenderTypes).
        // One way only: the shared source always stays in the 1.21.11 state.
        regex(current.parsed < "1.21.11") {
            replace("""net\.minecraft\.client\.renderer\.rendertype\.RenderTypes?\b""" to """net.minecraft.client.renderer.RenderType""", """(?!)""" to """unused""")
            replace("""net/minecraft/client/renderer/rendertype/RenderType\b""" to """net/minecraft/client/renderer/RenderType""", """(?!)""" to """unused""")
            replace("""\bRenderTypes\b""" to """RenderType""", """(?!)""" to """unused""")
        }
        // Before 1.21.11: MaLiLib draws GUIs with the vanilla GuiGraphics (no GuiContext), Painting is in entity.decoration,
        // and items share the block atlas (no separate translucentBlockItemSheet). One way only, like above.
        regex(current.parsed < "1.21.11") {
            replace("""fi\.dy\.masa\.malilib\.render\.GuiContext\b""" to """net.minecraft.client.gui.GuiGraphics""", """(?!)""" to """unused""")
            replace("""(?<!\.)\bGuiContext\b""" to """GuiGraphics""", """(?!)""" to """unused""")
            replace("""net\.minecraft\.world\.entity\.decoration\.painting\.Painting\b""" to """net.minecraft.world.entity.decoration.Painting""", """(?!)""" to """unused""")
            replace("""\bSheets\.translucentBlockItemSheet\(\)""" to """Sheets.translucentItemSheet()""", """(?!)""" to """unused""")
        }
        // 26.2: screens moved from Minecraft to Gui; entity type and concrete constants moved (gametests use `c` / `client` for the Minecraft instance)
        regex(current.parsed >= "26.2") {
            replace("""\b(c|client)\.setScreen\(""" to """$1.gui.setScreen(""", """\b(c|client)\.gui\.setScreen\(""" to """$1.setScreen(""")
            replace("""\b(c|client)\.screen\b(?!\()""" to """$1.gui.screen()""", """\b(c|client)\.gui\.screen\(\)""" to """$1.screen""")
            replace("""\bEntityType\.(ARMOR_STAND|MINECART|OAK_BOAT)\b""" to """net.minecraft.world.entity.EntityTypes.$1""",
                """net\.minecraft\.world\.entity\.EntityTypes\.(ARMOR_STAND|MINECART|OAK_BOAT)\b""" to """EntityType.$1""")
            replace("""\bBlocks\.RED_CONCRETE\b""" to """Blocks.CONCRETE.red()""", """\bBlocks\.CONCRETE\.red\(\)""" to """Blocks.RED_CONCRETE""")
            replace("""\bBlocks\.RED_STAINED_GLASS\b""" to """Blocks.STAINED_GLASS.red()""", """\bBlocks\.STAINED_GLASS\.red\(\)""" to """Blocks.RED_STAINED_GLASS""")
            replace("""\bItems\.WHITE_SHULKER_BOX\b""" to """Items.DYED_SHULKER_BOX.white()""", """\bItems\.DYED_SHULKER_BOX\.white\(\)""" to """Items.WHITE_SHULKER_BOX""")
        }
        // 26.3: blaze3d API classes moved to renderpearl
        string(current.parsed >= "26.3") {
            replace("com.mojang.blaze3d.textures.GpuSampler", "com.mojang.renderpearl.api.textures.GpuSampler")
            replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        }
        // 26.1: GUI drawing moved to an extract pass
        string(current.parsed >= "26.1") {
            replace("GuiGraphics", "GuiGraphicsExtractor")
            replace("fi.dy.masa.malilib.util.ItemType", "fi.dy.masa.malilib.util.data.ItemType")
            replace("net.minecraft.client.renderer.block.model.BakedQuad", "net.minecraft.client.resources.model.geometry.BakedQuad")
            replace("net.minecraft.client.renderer.block.model.BlockStateModel", "net.minecraft.client.renderer.block.dispatch.BlockStateModel")
        }
    }
}
