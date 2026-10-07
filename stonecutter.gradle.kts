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
        // 1.21.5: MaLiLib draws GUI rectangles immediately, without the GuiGraphics (see gui/GuiRects). One way only.
        regex(current.parsed < "1.21.6") {
            replace("""\bRenderUtils\.(drawRect|drawOutlinedBox)\(ctx,""" to """com.autyism.ale.gui.GuiRects.$1(ctx,""", """(?!)""" to """unused""")
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
            // schematic previews
            replace("com.mojang.blaze3d.buffers.GpuBuffer;", "com.mojang.renderpearl.api.buffers.GpuBuffer;")
            replace("com.mojang.blaze3d.buffers.GpuBufferSlice;", "com.mojang.renderpearl.api.buffers.GpuBufferSlice;")
            replace("com.mojang.blaze3d.pipeline.RenderPipeline;", "com.mojang.renderpearl.api.pipeline.RenderPipeline;")
            replace("com.mojang.blaze3d.systems.CommandEncoder;", "com.mojang.renderpearl.api.commands.CommandEncoder;")
            replace("com.mojang.blaze3d.systems.GpuDevice;", "com.mojang.renderpearl.api.device.GpuDevice;")
            replace("com.mojang.blaze3d.systems.RenderPass;", "com.mojang.renderpearl.api.commands.RenderPass;")
            replace("com.mojang.blaze3d.textures.FilterMode;", "com.mojang.renderpearl.api.textures.FilterMode;")
            replace("com.mojang.blaze3d.textures.GpuTexture;", "com.mojang.renderpearl.api.textures.GpuTexture;")
            replace("com.mojang.blaze3d.textures.GpuTextureView;", "com.mojang.renderpearl.api.textures.GpuTextureView;")
            replace("com.mojang.blaze3d.vertex.VertexFormat;", "com.mojang.renderpearl.api.vertex.VertexFormat;")
            replace("com.mojang.blaze3d.vertex.VertexFormatElement;", "com.mojang.renderpearl.api.vertex.VertexFormatElement;")
            replace("com.mojang.blaze3d.GpuFormat", "com.mojang.renderpearl.api.GpuFormat")
            replace("com.mojang.blaze3d.IndexType", "com.mojang.renderpearl.api.pipeline.IndexType")
            replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
        }
        // 26.1: GUI drawing moved to an extract pass
        string(current.parsed >= "26.1") {
            replace("GuiGraphics", "GuiGraphicsExtractor")
            replace("fi.dy.masa.malilib.util.ItemType", "fi.dy.masa.malilib.util.data.ItemType")
            replace("net.minecraft.client.renderer.block.model.BakedQuad", "net.minecraft.client.resources.model.geometry.BakedQuad")
            replace("net.minecraft.client.renderer.block.model.BlockStateModel", "net.minecraft.client.renderer.block.dispatch.BlockStateModel")
            // schematic previews
            replace("net.minecraft.client.gui.render.state.BlitRenderState", "net.minecraft.client.renderer.state.gui.BlitRenderState")
            replace("net.minecraft.client.renderer.state.CameraRenderState", "net.minecraft.client.renderer.state.level.CameraRenderState")
            replace("net.minecraft.world.level.BlockAndTintGetter", "net.minecraft.client.renderer.block.BlockAndTintGetter")
        }
    }
}
