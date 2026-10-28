package musheor.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.tech.ContainerTweaks;
import musheor.utils.hud.CustomHudButton;
import musheor.utils.hud.DraggableButtonWidget;
import musheor.utils.network.PacketLimiter;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({HandledScreen.class})
public abstract class ScreenHandlerMixin<T extends ScreenHandler> extends Screen implements ScreenHandlerProvider<T> {
    @Shadow
    protected T handler;
    @Shadow
    protected int x;
    @Shadow
    protected int y;

    @Shadow
    @Override
    public abstract T getScreenHandler();

    public ScreenHandlerMixin(Text title) {
        super(title);
    }

    @Inject(
        method = {"init"},
        at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo info) {
        ContainerTweaks ct = Modules.get().get(ContainerTweaks.class);
        if (ct.isActive() && ct.showButtons.get()) {
            int stealX = ct.stealOffsetX.get() != 0 ? ct.stealOffsetX.get() : this.x;
            int stealY = ct.stealOffsetY.get() != 0 ? ct.stealOffsetY.get() : this.y - 22;
            int dumpX = ct.dumpOffsetX.get() != 0 ? ct.dumpOffsetX.get() : this.x + 42;
            int dumpY = ct.dumpOffsetY.get() != 0 ? ct.dumpOffsetY.get() : this.y - 22;
            this.addDrawableChild(
                new DraggableButtonWidget(
                    stealX, stealY, 40, 20, Text.literal("Steal"), button -> ct.stealItems(this.getScreenHandler()), ct.stealOffsetX, ct.stealOffsetY
                )
            );
            this.addDrawableChild(
                new DraggableButtonWidget(
                    dumpX, dumpY, 40, 20, Text.literal("Dump"), button -> ct.dumpItems(this.getScreenHandler()), ct.dumpOffsetX, ct.dumpOffsetY
                )
            );
        }

        for (CustomHudButton btn : MusheorSystem.get().customButtons) {
            if (btn.screenTarget.appliesTo(CustomHudButton.ScreenTarget.Inventory)) {
                this.addDrawableChild(btn.createWidget(this.getScreenHandler()));
            }
        }
    }

    @Inject(
        method = {"onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (ContainerTweaks.INSTANCE.isActive() && MusheorSystem.Manager.noPacketKick.get() && !PacketLimiter.canSendInventoryPacket()) {
            ci.cancel();
        } else if (ContainerTweaks.INSTANCE != null && ContainerTweaks.INSTANCE.isActive()) {
            if (slot != null && slot.hasStack()) {
                if (ContainerTweaks.INSTANCE.moveAll.get().isPressed()) {
                    ContainerTweaks.INSTANCE.moveAllItems(this.handler, slot);
                    ci.cancel();
                }

                if (ContainerTweaks.INSTANCE.moveMatching.get().isPressed()) {
                    ContainerTweaks.INSTANCE.moveMatchingItems(this.handler, slot);
                    ci.cancel();
                }
            }
        }
    }
}
