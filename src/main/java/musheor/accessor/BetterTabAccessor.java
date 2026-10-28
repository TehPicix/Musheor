package musheor.accessor;

import meteordevelopment.meteorclient.settings.Setting;
import musheor.utils.system.MusheorSystem;

public interface BetterTabAccessor {
    Setting<MusheorSystem.TabListMode> musheor$getDisplayMode();

    Setting<Double> musheor$getScale();
}
