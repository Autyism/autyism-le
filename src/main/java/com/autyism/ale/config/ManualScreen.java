package com.autyism.ale.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mod's manual: what every feature does, where it works and where it is set, in the game's language. The text is
 * the manual bundled in the mod (assets/autyism-le/manual/<language>.md), shown with headings, lists and bold text;
 * a search box shows only the parts that mention a word.
 */
public class ManualScreen extends Screen {
	private static final int TOP = 44;
	private static final int BOTTOM = 32;
	private final Screen parent;
	private final List<String> source;
	private final List<Line> lines = new ArrayList<>();
	private double scroll;
	private int maxWidth;
	private String query = "";
	private EditBox search;

	private record Line(FormattedCharSequence text, int indent, int color, int gapBefore) {
	}

	public ManualScreen(Screen parent) {
		super(Component.translatable("autyism-le.manual.title"));
		this.parent = parent;
		this.source = read();
	}

	private static List<String> read() {
		Minecraft mc = Minecraft.getInstance();
		String lang = mc.options.languageCode == null ? "en_us" : mc.options.languageCode.toLowerCase(Locale.ROOT);
		String file = lang.startsWith("zh") ? "zh_cn" : "en_us";
		try (InputStream in = ManualScreen.class.getResourceAsStream("/assets/autyism-le/manual/" + file + ".md")) {
			if (in == null) return List.of("(manual missing)");
			return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "").split("\n"));
		} catch (Exception e) {
			return List.of("(manual unreadable: " + e + ")");
		}
	}

	@Override
	protected void init() {
		this.maxWidth = Math.min(this.width - 40, 520);
		int left = (this.width - this.maxWidth) / 2;
		this.search = new EditBox(this.font, left, 20, Math.min(200, this.maxWidth), 18, Component.literal("search"));
		this.search.setHint(Component.translatable("autyism-le.manual.search").withStyle(ChatFormatting.DARK_GRAY));
		this.search.setValue(this.query);
		this.search.setResponder(s -> {
			this.query = s;
			this.layout();
			this.scroll = 0;
		});
		this.addRenderableWidget(this.search);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
		this.layout();
	}

	/** Markdown-ish text to wrapped lines; with a search, only the sections (between headings) that mention it */
	private void layout() {
		this.lines.clear();
		String q = this.query.trim().toLowerCase(Locale.ROOT);
		List<List<String>> sections = new ArrayList<>();
		List<String> current = new ArrayList<>();
		for (String raw : this.source) {
			if (raw.startsWith("#") && !current.isEmpty()) {
				sections.add(current);
				current = new ArrayList<>();
			}
			current.add(raw);
		}
		if (!current.isEmpty()) sections.add(current);
		for (List<String> section : sections) {
			if (!q.isEmpty() && !String.join("\n", section).toLowerCase(Locale.ROOT).contains(q)) continue;
			for (String raw : section) addLine(raw);
		}
		if (this.lines.isEmpty()) {
			addLine(q.isEmpty() ? "" : "-");
		}
	}

	private void addLine(String raw) {
		String s = raw.stripTrailing();
		if (s.isBlank()) {
			this.lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0, 0));
			return;
		}
		int color = 0xFFE0E0E0;
		int indent = 0;
		int gap = 0;
		boolean bold = false;
		if (s.startsWith("#")) {
			int level = 0;
			while (level < s.length() && s.charAt(level) == '#') level++;
			s = s.substring(level).trim();
			color = level <= 2 ? 0xFFFFD75E : 0xFFFFF0A0;
			bold = true;
			gap = 6;
		} else if (s.matches("^\\s*[-*] .*")) {
			int spaces = s.indexOf(s.trim().charAt(0));
			indent = 6 + spaces * 4;
			s = "• " + s.trim().substring(2);
		} else if (s.matches("^\\s*\\d+\\. .*")) {
			indent = 6;
			s = s.trim();
		} else if (s.startsWith("|")) {
			if (s.replace("|", "").replace("-", "").replace(":", "").isBlank()) return; // table rule
			String[] cells = s.substring(1, s.endsWith("|") ? s.length() - 1 : s.length()).split("\\|");
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < cells.length; i++) {
				if (i > 0) b.append(i == 1 ? "  —  " : "  ·  ");
				b.append(cells[i].trim());
			}
			s = b.toString();
			indent = 6;
		} else if (s.startsWith(">")) {
			s = s.substring(1).trim();
			color = 0xFFB0B0B0;
			indent = 8;
		}
		MutableComponent text = styled(s, bold);
		int width = this.maxWidth - indent;
		boolean first = true;
		for (FormattedCharSequence part : this.font.split(text, Math.max(40, width))) {
			this.lines.add(new Line(part, indent + (first || indent == 0 ? 0 : 10), color, first ? gap : 0));
			first = false;
		}
	}

	/** **bold** and `code` (shown grey) */
	private static MutableComponent styled(String s, boolean allBold) {
		MutableComponent out = Component.empty();
		boolean bold = false, code = false, italic = false;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (s.startsWith("**", i)) {
				flush(out, run, bold || allBold, code, italic);
				bold = !bold;
				i++;
				continue;
			}
			if (s.charAt(i) == '`') {
				flush(out, run, bold || allBold, code, italic);
				code = !code;
				continue;
			}
			if (s.charAt(i) == '*' && !code && (italic || (i + 1 < s.length() && !Character.isWhitespace(s.charAt(i + 1))))) {
				flush(out, run, bold || allBold, code, italic);
				italic = !italic;
				continue;
			}
			run.append(s.charAt(i));
		}
		flush(out, run, bold || allBold, code, italic);
		return out;
	}

	private static void flush(MutableComponent out, StringBuilder run, boolean bold, boolean code, boolean italic) {
		if (run.isEmpty()) return;
		MutableComponent part = Component.literal(run.toString());
		if (bold) part = part.withStyle(ChatFormatting.BOLD);
		if (code) part = part.withStyle(ChatFormatting.AQUA);
		if (italic) part = part.withStyle(ChatFormatting.ITALIC);
		out.append(part);
		run.setLength(0);
	}

	private int contentHeight() {
		int h = 0;
		for (Line l : this.lines) h += 10 + l.gapBefore();
		return h;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		this.scroll = Math.max(0, Math.min(Math.max(0, contentHeight() - (this.height - TOP - BOTTOM)), this.scroll - verticalAmount * 20));
		return true;
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
	//?}
		text(g, this.title.getVisualOrderText(), (this.width - this.font.width(this.title)) / 2, 6, 0xFFFFFFFF);
		int left = (this.width - this.maxWidth) / 2;
		g.enableScissor(0, TOP, this.width, this.height - BOTTOM);
		int y = TOP - (int) this.scroll;
		for (Line l : this.lines) {
			y += l.gapBefore();
			if (y > TOP - 10 && y < this.height - BOTTOM) text(g, l.text(), left + l.indent(), y, l.color());
			y += 10;
		}
		g.disableScissor();
		int content = contentHeight(), view = this.height - TOP - BOTTOM;
		if (content > view) {
			int barH = Math.max(12, view * view / content);
			int barY = TOP + (int) ((view - barH) * (this.scroll / Math.max(1, content - view)));
			int x = left + this.maxWidth + 6;
			g.fill(x, TOP, x + 3, TOP + view, 0x40000000);
			g.fill(x, barY, x + 3, barY + barH, 0xFFC0C0C0);
		}
	}

	private void text(GuiGraphics g, FormattedCharSequence s, int x, int y, int color) {
		//? if >=26.1 {
		/*g.text(this.font, s, x, y, color);
		*///?} else {
		g.drawString(this.font, s, x, y, color);
		//?}
	}

	@Override
	public void onClose() {
		Minecraft mc = Minecraft.getInstance();
		//? if >=26.2 {
		/*mc.gui.setScreen(this.parent);
		*///?} else
		mc.setScreen(this.parent);
	}
}
