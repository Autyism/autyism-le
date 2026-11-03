package com.autyism.ale.compat;

import ch.endte.syncmatica.data.ServerPlacement;
import com.autyism.ale.schematic.SchematicNames;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Names for Syncmatica's shared entries (only loaded when Syncmatica is installed, from its compat mixins).
 * <p>
 * An entry the server loaded again from its syncmatics folder is called after the stored file, which is the file's
 * hash; an entry shared from a schematic saved from an unnamed area selection is called "Unnamed". Shown instead:
 * the shared file's name if it is a real name, else what a local file with the same content says (its metadata name,
 * file name, region names, or author and size), else the stored name with the player who shared it.
 */
public final class SyncmaticaNames {
    private SyncmaticaNames() {
    }

    /** What to show for this entry; raw is its stored display name */
    public static String display(ServerPlacement placement, String raw) {
        if (!SchematicNames.enabled() || !SchematicNames.isPlaceholder(raw)) return raw;
        return SchematicNames.cached(placement, raw, () -> resolve(placement, raw));
    }

    private static String resolve(ServerPlacement placement, String raw) {
        String fileName = placement.getFileName();
        if (fileName != null) {
            // the stored file name can be a whole path
            fileName = SchematicNames.baseName(fileName.replace('\\', '/').substring(fileName.replace('\\', '/').lastIndexOf('/') + 1));
            if (!SchematicNames.isPlaceholder(fileName)) return fileName;
        }
        if (placement.getHash() != null) {
            String local = SchematicNames.fromLocalCopies(placement.getHash());
            if (local != null) return local;
        }
        String owner = placement.getOwner() != null ? placement.getOwner().getName() : null;
        String shown = SchematicNames.isPlaceholder(raw) && SchematicNames.hashName(raw) != null
                ? raw.substring(0, Math.min(8, raw.length())) : raw;
        if (owner != null && !owner.isBlank()) return StringUtils.translate("autyism-le.name.shared_by", shown, owner);
        return raw;
    }
}
