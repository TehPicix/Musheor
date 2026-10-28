package musheor.compat;

import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public interface LitematicaHelper {
    boolean ensureSchematicAt(String var1, BlockPos var2);

    void clearAllPlacements();

    boolean verifySchematic();

    Map<BlockPos, BlockState> getBlocksInBox(BlockPos var1, BlockPos var2, boolean var3, List<Block> var4, int var5);

    Map<BlockPos, BlockState> getMismatchedStatesInBox(BlockPos var1, BlockPos var2, List<Block> var3, int var4);

    Map<Block, Integer> getMaterialCounts(List<Block> var1);

    Map<Block, Integer> getRemainingMaterialCounts(List<Block> var1, boolean var2);

    BlockPos findClosestUnplacedBlock(BlockPos var1, int var2, boolean var3, List<Block> var4);

    boolean isPositionInRenderLayer(BlockPos var1);

    List<BlockPos> getWrongSchematicBlocks(double var1, boolean var3);

    List<BlockPos> getWrongBlocksInBox(BlockPos var1, BlockPos var2, boolean var3, int var4);

    boolean hasRemainingPositions(Block var1, boolean var2, List<Block> var3);

    List<BlockPos[]> getSchematicRegionBounds();

    static boolean isLoaded() {
        return LitematicaHelper.LitematicaHelperHolder.INSTANCE != null;
    }

    static LitematicaHelper get() {
        return LitematicaHelper.LitematicaHelperHolder.INSTANCE;
    }

    static void setInstance(LitematicaHelper instance) {
        LitematicaHelper.LitematicaHelperHolder.INSTANCE = instance;
    }

    public static class LitematicaHelperHolder {
        static LitematicaHelper INSTANCE = null;
    }
}
