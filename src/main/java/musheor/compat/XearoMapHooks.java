package musheor.compat;

import java.util.ArrayList;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public class XearoMapHooks {
    private static XearoMapHooks.RightClickOptionsProvider provider = null;

    public static void setProvider(XearoMapHooks.RightClickOptionsProvider p) {
        provider = p;
    }

    public static void apply(ArrayList<RightClickOption> options, IRightClickableElement element, MapTileSelection selection) {
        if (provider != null) {
            provider.addOptions(options, element, selection);
        }
    }

    @FunctionalInterface
    public interface RightClickOptionsProvider {
        void addOptions(ArrayList<RightClickOption> var1, IRightClickableElement var2, MapTileSelection var3);
    }
}
