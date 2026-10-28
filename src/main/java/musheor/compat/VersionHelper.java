package musheor.compat;

import com.google.gson.Gson;
import com.mojang.brigadier.context.CommandContext;
import java.lang.reflect.Method;
import java.util.UUID;
import meteordevelopment.meteorclient.mixin.ContainerComponentAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface VersionHelper {
    Gson getGson();

    static VersionHelper get() {
        return VersionHelper.VersionHelperHolder.INSTANCE;
    }

    Vec3d getPlayerPos();

    Vec3d getVelocity(EntityVelocityUpdateS2CPacket var1);

    static void setInstance(VersionHelper instance) {
        VersionHelper.VersionHelperHolder.INSTANCE = instance;
    }

    NbtCompound getCompound(NbtCompound var1, String var2);

    NbtList getList(NbtCompound var1, String var2, int var3);

    String getString(NbtCompound var1, String var2);

    int getInt(NbtCompound var1, String var2, int var3);

    boolean hasControlDown();

    default boolean supportsHudButtons() {
        return true;
    }

    void sendCommand(String var1);

    boolean isPickaxe(ItemStack var1);

    boolean isTool(ItemStack var1);

    void playSoundPlayer(SoundEvent var1);

    void playSoundAtBlockCenter(BlockPos var1, SoundEvent var2, SoundCategory var3, float var4, float var5);

    DefaultedList<ItemStack> getStacks(ContainerComponentAccessor var1);

    String getName(CommandContext<?> var1);

    UUID getUUID(PlayerListEntry var1);

    boolean onSameServer(String var1);

    void syncInventory();

    void interactEntityAt(Entity var1, Hand var2);

    void registerSkinTexture(NativeImage var1, String var2, Identifier var3);

    void drawSkinTexture(Identifier var1, float var2, float var3, float var4, float var5);

    void renderColorRenderer();

    void pushHudScale(DrawContext var1, float var2);

    void popHudScale(DrawContext var1);

    default Vec3d getCameraPos() {
        try {
            Object cam = MinecraftClient.getInstance().getEntityRenderDispatcher().camera;
            if (cam == null) {
                return Vec3d.ZERO;
            }

            for (String name : new String[]{"getPos", "getPosition"}) {
                try {
                    Method m = cam.getClass().getMethod(name);
                    if (m.invoke(cam) instanceof Vec3d v) {
                        return v;
                    }
                } catch (NoSuchMethodException var9) {
                }
            }
        } catch (Exception var10) {
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : Vec3d.ZERO;
    }

    public static class VersionHelperHolder {
        static VersionHelper INSTANCE;
    }
}
