package musheor.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class PlusExtensions {
    private static final PlusExtensions.Ext NONE = new PlusExtensions.Ext() {};
    private static final Map<Class<? extends Module>, List<PlusExtensions.Ext>> REGISTRY = new HashMap<>();

    private PlusExtensions() {
    }

    public static void register(Class<? extends Module> target, PlusExtensions.Ext ext) {
        REGISTRY.computeIfAbsent(target, k -> new ArrayList<>()).add(ext);
    }

    public static PlusExtensions.Ext attach(Module module, Settings settings) {
        List<PlusExtensions.Ext> exts = REGISTRY.get(module.getClass());
        if (exts != null && !exts.isEmpty()) {
            PlusExtensions.Ext ext = (PlusExtensions.Ext)(exts.size() == 1 ? exts.get(0) : new PlusExtensions.Composite(List.copyOf(exts)));
            ext.onInit(module, settings.createGroup("Musheor+"));
            return ext;
        } else {
            return NONE;
        }
    }

    private record Composite(List<PlusExtensions.Ext> delegates) implements PlusExtensions.Ext {
        @Override
        public void onInit(Module module, SettingGroup group) {
            for (PlusExtensions.Ext ext : this.delegates) {
                ext.onInit(module, group);
            }
        }

        @Override
        public void onActivate(Module module) {
            for (PlusExtensions.Ext ext : this.delegates) {
                ext.onActivate(module);
            }
        }

        @Override
        public void onDeactivate(Module module) {
            for (PlusExtensions.Ext ext : this.delegates) {
                ext.onDeactivate(module);
            }
        }

        @Override
        public void onTick(Module module) {
            for (PlusExtensions.Ext ext : this.delegates) {
                ext.onTick(module);
            }
        }

        @Override
        public boolean suppressSwap(Module module) {
            for (PlusExtensions.Ext ext : this.delegates) {
                if (ext.suppressSwap(module)) {
                    return true;
                }
            }

            return false;
        }
    }

    public interface Ext {
        default void onInit(Module module, SettingGroup group) {
        }

        default void onActivate(Module module) {
        }

        default void onDeactivate(Module module) {
        }

        default void onTick(Module module) {
        }

        default boolean suppressSwap(Module module) {
            return false;
        }
    }
}
