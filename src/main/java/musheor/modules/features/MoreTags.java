package musheor.modules.features;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.utils.NametagHelper;
import musheor.utils.PearlStore;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class MoreTags extends Module {
    private final SettingGroup sgPlayer = this.settings.createGroup("Player");
    private final SettingGroup sgPearl = this.settings.createGroup("Pearl");
    public final Setting<Boolean> enabled = this.sgPlayer
        .add(new Builder().name("enabled").description("Show custom nametags above players.").defaultValue(true).build());
    private final Setting<Double> scale = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("scale")
                .description("Scale of the player nametag.")
                .defaultValue(1.0)
                .sliderRange(0.5, 3.0)
                .decimalPlaces(1)
                .visible(this.enabled::get)
                .build()
        );
    private final Setting<Boolean> distanceScaling = this.sgPlayer
        .add(
            new Builder()
                .name("distance-scaling")
                .description("Scales the nametag based on distance from the player.")
                .defaultValue(false)
                .visible(this.enabled::get)
                .build()
        );
    private final Setting<Boolean> ignoreSelf = this.sgPlayer
        .add(
            new Builder()
                .name("ignore-self")
                .description("Don't render a nametag above your own player.")
                .defaultValue(true)
                .visible(this.enabled::get)
                .build()
        );
    private final Setting<MoreTags.NameColorType> nameColor = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MoreTags.NameColorType>()
                                .name("name-color")
                            .description("How to color the player's name.")
                        .defaultValue(MoreTags.NameColorType.Default)
                    .visible(this.enabled::get)
                .build()
        );
    private final Setting<SettingColor> nameColorValue = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("name-color-value")
                .defaultValue(new SettingColor(255, 255, 255))
                .visible(() -> this.enabled.get() && this.nameColor.get() == MoreTags.NameColorType.Color)
                .build()
        );
    private final Setting<SettingColor> nameGradientStart = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("name-gradient-start")
                .defaultValue(new SettingColor(255, 100, 100))
                .visible(() -> this.enabled.get() && this.nameColor.get() == MoreTags.NameColorType.Gradient)
                .build()
        );
    private final Setting<SettingColor> nameGradientEnd = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("name-gradient-end")
                .defaultValue(new SettingColor(100, 100, 255))
                .visible(() -> this.enabled.get() && this.nameColor.get() == MoreTags.NameColorType.Gradient)
                .build()
        );
    private final Setting<SettingColor> friendColor = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("friend-color")
                .description("Name color for friends.")
                .defaultValue(new SettingColor(Color.GREEN))
                .visible(this.enabled::get)
                .build()
        );
    private final Setting<Boolean> background = this.sgPlayer
        .add(new Builder().name("background").description("Show a background behind the nametag.").defaultValue(true).visible(this.enabled::get).build());
    private final Setting<SettingColor> backgroundColor = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("background-color")
                .defaultValue(new SettingColor(0, 0, 0, 160))
                .visible(() -> this.enabled.get() && this.background.get())
                .build()
        );
    private final Setting<Double> backgroundPadding = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("background-padding")
                .defaultValue(6.0)
                .sliderRange(0.0, 20.0)
                .decimalPlaces(1)
                .visible(() -> this.enabled.get() && this.background.get())
                .build()
        );
    private final Setting<MoreTags.OutlineType> outline = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MoreTags.OutlineType>()
                            .name("outline")
                        .defaultValue(MoreTags.OutlineType.Disabled)
                    .visible(this.enabled::get)
                .build()
        );
    private final Setting<SettingColor> outlineColor = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("outline-color")
                .defaultValue(new SettingColor(255, 255, 255, 200))
                .visible(() -> this.enabled.get() && this.outline.get() == MoreTags.OutlineType.Color)
                .build()
        );
    private final Setting<Double> outlineRounding = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("outline-rounding")
                .defaultValue(3.0)
                .sliderRange(0.0, 10.0)
                .decimalPlaces(1)
                .visible(() -> this.enabled.get() && this.outline.get() != MoreTags.OutlineType.Disabled)
                .build()
        );
    private final Setting<SettingColor> outlineGradientStart = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("outline-gradient-start")
                .defaultValue(new SettingColor(255, 100, 100))
                .visible(() -> this.enabled.get() && this.outline.get() == MoreTags.OutlineType.Gradient)
                .build()
        );
    private final Setting<SettingColor> outlineGradientEnd = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("outline-gradient-end")
                .defaultValue(new SettingColor(100, 100, 255))
                .visible(() -> this.enabled.get() && this.outline.get() == MoreTags.OutlineType.Gradient)
                .build()
        );
    private final Setting<MoreTags.DistanceType> distance = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MoreTags.DistanceType>()
                            .name("distance")
                        .defaultValue(MoreTags.DistanceType.Auto)
                    .visible(this.enabled::get)
                .build()
        );
    private final Setting<SettingColor> distanceColor = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("distance-color")
                .defaultValue(new SettingColor(Color.WHITE))
                .visible(() -> this.enabled.get() && this.distance.get() == MoreTags.DistanceType.Color)
                .build()
        );
    private final Setting<Boolean> showSkin = this.sgPlayer
        .add(
            new Builder()
                .name("show-skin")
                .description("Show the player's face icon next to their name.")
                .defaultValue(true)
                .visible(this.enabled::get)
                .build()
        );
    private final Setting<Double> skinScale = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("skin-scale")
                .defaultValue(1.0)
                .sliderRange(0.5, 3.0)
                .decimalPlaces(1)
                .visible(() -> this.enabled.get() && this.showSkin.get())
                .build()
        );
    private final Setting<Boolean> showItems = this.sgPlayer
        .add(new Builder().name("show-items").description("Show armor and held items above the nametag.").defaultValue(true).visible(this.enabled::get).build());
    private final Setting<Double> itemScale = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("item-scale")
                .defaultValue(1.0)
                .sliderRange(0.5, 2.0)
                .decimalPlaces(1)
                .visible(() -> this.enabled.get() && this.showItems.get())
                .build()
        );
    private final Setting<Double> rowSpacing = this.sgPlayer
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("row-spacing")
                .description("Vertical spacing between nametag rows.")
                .defaultValue(2.0)
                .sliderRange(0.0, 10.0)
                .decimalPlaces(1)
                .visible(this.enabled::get)
                .build()
        );
    private final Setting<MoreTags.PearlRenderingType> pearlRendering = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MoreTags.PearlRenderingType>()
                            .name("pearl-rendering")
                        .description("Choose whether to render only pearls that have been assigned an owner or render all pearls.")
                    .defaultValue(MoreTags.PearlRenderingType.Assigned)
                .build()
        );
    private final Setting<SettingColor> assignedRenderingColor = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("assigned-rendering-color")
                .description("Outline color of a pearl whose owner has been resolved.")
                .defaultValue(new SettingColor(Color.MAGENTA))
                .visible(
                    () -> this.pearlRendering.get() == MoreTags.PearlRenderingType.Assigned || this.pearlRendering.get() == MoreTags.PearlRenderingType.Both
                )
                .build()
        );
    private final Setting<SettingColor> unknownRenderingColor = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("unknown-rendering-color")
                .description("Outline color of a pearl whose owner is still unresolved.")
                .defaultValue(new SettingColor(Color.WHITE))
                .visible(() -> this.pearlRendering.get() == MoreTags.PearlRenderingType.Both)
                .build()
        );
    private final Setting<MoreTags.TagDisplayType> tagDisplay = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MoreTags.TagDisplayType>()
                            .name("tag-display")
                        .description("What to display above the pearl.")
                    .defaultValue(MoreTags.TagDisplayType.Both)
                .build()
        );
    private final Setting<Boolean> tagDistanceScaling = this.sgPearl
        .add(
            new Builder()
                .name("tag-distance-scaling")
                .description("Scales the tag automatically based on the distance from the player.")
                .defaultValue(true)
                .visible(() -> this.tagDisplay.get() != MoreTags.TagDisplayType.Disabled)
                .build()
        );
    private final Setting<Boolean> lockTag = this.sgPearl
        .add(
            new Builder()
                .name("lock-tag")
                .description("Locks the tag's position 1 block above the pearl (stops it from bobbing in stasis chambers).")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> tagColor = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tag-color")
                .description("Color of the owner's tag rendered above a pearl.")
                .defaultValue(new SettingColor(Color.CYAN))
                .visible(() -> this.tagDisplay.get() != MoreTags.TagDisplayType.Disabled)
                .build()
        );
    private final Setting<Double> tagScale = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("tag-scale")
                .description("Scale of the nametag rendered above pearls.")
                .defaultValue(1.0)
                .decimalPlaces(1)
                .visible(() -> this.tagDisplay.get() != MoreTags.TagDisplayType.Disabled)
                .build()
        );
    private final Setting<Double> pearlSkinScale = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("skin-scale")
                .description("Scale of the player face icon rendered above a pearl.")
                .defaultValue(1.0)
                .decimalPlaces(1)
                .visible(() -> this.tagDisplay.get() == MoreTags.TagDisplayType.Skin || this.tagDisplay.get() == MoreTags.TagDisplayType.Both)
                .build()
        );
    public static final Map<Integer, String> pearlOwners = new HashMap<>();
    private final Set<Integer> unresolvedPearls = new HashSet<>();
    private final Map<Integer, Integer> pendingOwnerIds = new HashMap<>();
    private final Map<String, Identifier> skinTextures = new HashMap<>();
    private final Set<String> loadingSkins = new HashSet<>();

    public MoreTags() {
        super(musheor.MAIN, "kek-tags", "Enhanced nametags for players, pearls and items (WIP)");
    }

    @Override
    public void onActivate() {
        PearlStore.load();
        if (this.mc.world != null) {
            for (Entity entity : this.mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl) {
                    Entity owner = pearl.getOwner();
                    if (owner != null) {
                        String name = owner.getName().getString();
                        pearlOwners.put(pearl.getId(), name);
                        BlockPos pos = this.findStasisTrapdoor(pearl);
                        if (pos != null) {
                            PearlStore.addPearl(name, pos, pearl.getId());
                        }
                    } else {
                        BlockPos pos = this.findStasisTrapdoor(pearl);
                        if (pos != null) {
                            PearlStore.PearlEntry record = PearlStore.findByTrapdoor(pos);
                            if (record != null) {
                                pearlOwners.put(pearl.getId(), record.owner);
                                PearlStore.updateEntityId(pos, pearl.getId());
                                continue;
                            }
                        }

                        Integer ownerEntityId = this.pendingOwnerIds.get(pearl.getId());
                        if (ownerEntityId != null && this.mc.world.getEntityById(ownerEntityId) instanceof PlayerEntity player) {
                            String name = player.getName().getString();
                            pearlOwners.put(pearl.getId(), name);
                            if (pos != null) {
                                PearlStore.addPearl(name, pos, pearl.getId());
                            }
                        } else {
                            Iterator var13 = PearlStore.getPearls().iterator();

                            while (true) {
                                if (var13.hasNext()) {
                                    PearlStore.PearlEntry record = (PearlStore.PearlEntry)var13.next();
                                    if (record.entityId != pearl.getId()) {
                                        continue;
                                    }

                                    pearlOwners.put(pearl.getId(), record.owner);
                                    PearlStore.updateEntityId(record.trapdoorPos, pearl.getId());
                                }

                                this.unresolvedPearls.add(pearl.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        pearlOwners.clear();
        this.unresolvedPearls.clear();
        this.skinTextures.forEach((name, id) -> this.mc.getTextureManager().destroyTexture(id));
        this.skinTextures.clear();
        this.loadingSkins.clear();
    }

    @EventHandler(
        priority = -200
    )
    private void onEntityAdded(EntityAddedEvent event) {
        if (this.mc.world != null && this.mc.player != null) {
            if (event.entity instanceof EnderPearlEntity pearl) {
                this.trackPearl(pearl);
            } else if (event.entity instanceof PlayerEntity player) {
                String name = player.getName().getString();
                if (this.skinTextures.containsKey(name)) {
                    this.mc.getTextureManager().destroyTexture(this.skinTextures.get(name));
                    this.skinTextures.remove(name);
                }

                this.pendingOwnerIds.entrySet().removeIf(entry -> {
                    if (entry.getValue() == player.getId()) {
                        int pearlId = entry.getKey();
                        pearlOwners.put(pearlId, player.getName().getString());
                        if (this.mc.world.getEntityById(pearlId) instanceof EnderPearlEntity pearl2) {
                            BlockPos pos = this.findStasisTrapdoor(pearl2);
                            if (pos != null) {
                                PearlStore.addPearl(player.getName().getString(), pos, pearlId);
                            }
                        }

                        this.unresolvedPearls.remove(pearlId);
                        return true;
                    } else {
                        return false;
                    }
                });
                this.resolvePendingPearls();
            }
        }
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.mc.world != null && !this.unresolvedPearls.isEmpty()) {
            this.resolvePendingPearls();
        }
    }

    private void trackPearl(EnderPearlEntity pearl) {
        Entity owner = pearl.getOwner();
        if (owner != null) {
            String name = owner.getName().getString();
            pearlOwners.put(pearl.getId(), name);
            BlockPos pos = this.findStasisTrapdoor(pearl);
            if (pos != null) {
                PearlStore.addPearl(name, pos, pearl.getId());
            }
        } else {
            BlockPos pos = this.findStasisTrapdoor(pearl);
            if (pos != null) {
                PearlStore.PearlEntry record = PearlStore.findByTrapdoor(pos);
                if (record != null) {
                    pearlOwners.put(pearl.getId(), record.owner);
                    PearlStore.updateEntityId(pos, pearl.getId());
                    return;
                }
            }

            this.unresolvedPearls.add(pearl.getId());
        }
    }

    private void resolvePendingPearls() {
        this.unresolvedPearls.removeIf(id -> {
            Entity entity = this.mc.world.getEntityById(id);
            if (entity == null) {
                return true;
            } else if (entity instanceof EnderPearlEntity pearl) {
                UUID ownerUuid = this.getOwnerUuid(pearl);
                if (ownerUuid == null) {
                    return false;
                } else {
                    PlayerEntity owner = this.mc.world.getPlayers().stream().filter(p -> p.getUuid().equals(ownerUuid)).findFirst().orElse(null);
                    if (owner == null) {
                        return false;
                    } else {
                        String name = owner.getName().getString();
                        pearlOwners.put(id, name);
                        BlockPos pos = this.findStasisTrapdoor(pearl);
                        if (pos != null) {
                            PearlStore.addPearl(name, pos, id);
                        }

                        return true;
                    }
                }
            } else {
                return true;
            }
        });
    }

    private BlockPos findStasisTrapdoor(EnderPearlEntity pearl) {
        if (this.mc.world == null) {
            return null;
        } else {
            BlockPos base = pearl.getBlockPos();

            for (BlockPos candidate : new BlockPos[]{base.down(), base}) {
                if (this.mc.world.getBlockState(candidate).getBlock() instanceof TrapdoorBlock) {
                    return candidate;
                }
            }

            return null;
        }
    }

    public void cachePearlOwnerId(int pearlId, int ownerEntityId) {
        this.pendingOwnerIds.put(pearlId, ownerEntityId);
    }

    private UUID getOwnerUuid(EnderPearlEntity pearl) {
        try {
            Field f = ProjectileEntity.class.getDeclaredField("ownerUuid");
            f.setAccessible(true);
            return (UUID)f.get(pearl);
        } catch (Exception var3) {
            return null;
        }
    }

    @EventHandler
    private void renderPearlTags(Render2DEvent event) {
        if (this.tagDisplay.get() != MoreTags.TagDisplayType.Disabled && this.mc.world != null && !pearlOwners.isEmpty()) {
            boolean showSkin = this.tagDisplay.get() == MoreTags.TagDisplayType.Skin || this.tagDisplay.get() == MoreTags.TagDisplayType.Both;
            boolean showName = this.tagDisplay.get() == MoreTags.TagDisplayType.NameTag || this.tagDisplay.get() == MoreTags.TagDisplayType.Both;

            for (Entry<Integer, String> entry : pearlOwners.entrySet()) {
                if (this.mc.world.getEntityById(entry.getKey()) instanceof EnderPearlEntity pearl) {
                    String playerName = entry.getValue();
                    if (showSkin) {
                        this.requestSkin(playerName);
                    }

                    Vec3d lerpedPos = pearl.getLerpedPos(event.tickDelta);
                    double lerpedY = lerpedPos.y + pearl.getHeight() + 0.5;
                    Vector3d pos = new Vector3d(lerpedPos.x, !this.lockTag.get() ? lerpedY : Math.ceil(lerpedY), lerpedPos.z);
                    if (NametagUtils.to2D(pos, this.tagScale.get(), this.tagDistanceScaling.get())) {
                        NametagUtils.begin(pos);
                        TextRenderer textRenderer = TextRenderer.get();
                        float iconSize = (float)(8.0 * this.pearlSkinScale.get() * 2.0);
                        boolean hasSkin = showSkin && this.skinTextures.containsKey(playerName);
                        if (hasSkin) {
                            Identifier skinId = this.skinTextures.get(playerName);
                            float x0 = showName ? -iconSize - 2.0F : -iconSize / 2.0F;
                            float y0 = -iconSize / 2.0F;
                            VersionHelper.get().drawSkinTexture(skinId, x0, y0, iconSize, iconSize);
                        }

                        if (showName) {
                            textRenderer.beginBig();
                            double w = textRenderer.getWidth(playerName);
                            float nameX = hasSkin ? 2.0F : (float)(-w / 2.0);
                            textRenderer.render(playerName, nameX, -textRenderer.getHeight() / 2.0, this.tagColor.get());
                            textRenderer.end();
                        }

                        NametagUtils.end();
                    }
                }
            }
        }
    }

    @EventHandler
    private void renderPlayerTags(Render2DEvent event) {
        if (this.enabled.get() && this.mc.world != null) {
            for (PlayerEntity player : this.mc.world.getPlayers()) {
                boolean noSpecialView = !Modules.get().get(Freecam.class).isActive() && this.mc.options.getPerspective().isFirstPerson();
                if (player != this.mc.player || !this.ignoreSelf.get() && !noSpecialView) {
                    String name = player.getName().getString();
                    boolean friend = NametagHelper.isFriend(player);
                    double distance = NametagHelper.getDistance(player);
                    if (this.showSkin.get()) {
                        this.requestSkin(name);
                    }

                    Vec3d headPos = player.getLerpedPos(event.tickDelta).add(0.0, player.getEyeHeight(player.getPose()) + 0.6, 0.0);
                    Vector3d pos = new Vector3d(headPos.x, headPos.y, headPos.z);
                    if (NametagUtils.to2D(pos, this.scale.get(), this.distanceScaling.get())) {
                        NametagUtils.begin(pos);
                        TextRenderer tr = TextRenderer.get();
                        float pad = this.backgroundPadding.get().floatValue();
                        float spacing = this.rowSpacing.get().floatValue();
                        float iconSize = (float)(8.0 * this.skinScale.get() * 2.0);
                        boolean hasSkin = this.showSkin.get() && this.skinTextures.containsKey(name);
                        tr.beginBig();
                        float nameW = (float)tr.getWidth(name);
                        float nameH = (float)tr.getHeight();
                        tr.end();
                        float skinW = hasSkin ? iconSize + 2.0F : 0.0F;
                        float totalNameLineW = nameW + skinW;
                        float totalNameLineH = Math.max(nameH, hasSkin ? iconSize : 0.0F);
                        float bgX = -(totalNameLineW / 2.0F) - pad;
                        float bgY = -(totalNameLineH / 2.0F) - pad;
                        float bgW = totalNameLineW + pad * 2.0F;
                        float bgH = totalNameLineH + pad * 2.0F;
                        if (this.background.get()) {
                            Renderer2D.COLOR.begin();
                            Renderer2D.COLOR.quad(bgX, bgY, bgW, bgH, this.backgroundColor.get());
                            VersionHelper.get().renderColorRenderer();
                        }

                        if (this.outline.get() != MoreTags.OutlineType.Disabled) {
                            int colA;
                            int colB;
                            if (this.outline.get() == MoreTags.OutlineType.Gradient) {
                                colA = this.toRgb(this.outlineGradientStart.get());
                                colB = this.toRgb(this.outlineGradientEnd.get());
                            } else {
                                colA = this.toRgb(this.outlineColor.get());
                                colB = colA;
                            }

                            this.drawRoundedOutline(bgX, bgY, bgW, bgH, this.outlineRounding.get().floatValue(), colA, colB);
                        }

                        if (hasSkin) {
                            Identifier skinId = this.skinTextures.get(name);
                            float skinX = -(totalNameLineW / 2.0F);
                            float skinY = -iconSize / 2.0F;
                            VersionHelper.get().drawSkinTexture(skinId, skinX, skinY, iconSize, iconSize);
                        }

                        tr.beginBig();
                        float textX = hasSkin ? -(totalNameLineW / 2.0F) + skinW : -(nameW / 2.0F);
                        float textY = -(nameH / 2.0F);
                        if (friend) {
                            tr.render(name, textX, textY, this.friendColor.get());
                        } else if (this.nameColor.get() == MoreTags.NameColorType.Gradient) {
                            this.renderGradientText(tr, name, textX, textY, this.nameGradientStart.get(), this.nameGradientEnd.get());
                        } else if (this.nameColor.get() == MoreTags.NameColorType.Color) {
                            tr.render(name, textX, textY, this.nameColorValue.get());
                        } else {
                            tr.render(name, textX, textY, new SettingColor(255, 255, 255));
                        }

                        tr.end();
                        if (this.distance.get() != MoreTags.DistanceType.Disabled) {
                            String distStr = String.format("[%dm]", (int)distance);
                            tr.begin();
                            float distW = (float)tr.getWidth(distStr);
                            float distY = totalNameLineH / 2.0F + spacing;
                            int distCol;
                            if (this.distance.get() == MoreTags.DistanceType.Auto) {
                                distCol = NametagHelper.getDistanceColor(distance);
                            } else {
                                SettingColor dc = this.distanceColor.get();
                                distCol = dc.r << 16 | dc.g << 8 | dc.b;
                            }

                            int dr = distCol >> 16 & 0xFF;
                            int dg = distCol >> 8 & 0xFF;
                            int db = distCol & 0xFF;
                            tr.render(distStr, -(distW / 2.0F), distY, new SettingColor(dr, dg, db, 255));
                            tr.end();
                        }

                        if (this.showItems.get() && NametagHelper.hasAnyEquipment(player)) {
                            List<ItemStack> items = new ArrayList<>();
                            items.add(NametagHelper.getMainHand(player));
                            items.addAll(NametagHelper.getArmor(player));
                            items.add(NametagHelper.getOffHand(player));
                            items.removeIf(ItemStack::isEmpty);
                            float itemSz = (float)(16.0 * this.itemScale.get());
                            float itemGap = 2.0F;
                            float totalItemW = items.size() * (itemSz + itemGap) - itemGap;
                            float itemStartX = -totalItemW / 2.0F;
                            float itemY = -(totalNameLineH / 2.0F) - pad - itemSz - spacing;

                            for (ItemStack stack : items) {
                                RenderUtils.drawItem(event.drawContext, stack, (int)itemStartX, (int)itemY, this.itemScale.get().floatValue(), false);
                                if (stack.getCount() > 1) {
                                    String countStr = String.valueOf(stack.getCount());
                                    tr.begin(0.5, false, true);
                                    float countW = (float)tr.getWidth(countStr);
                                    tr.render(
                                        countStr, itemStartX + itemSz - countW - 1.0F, itemY + itemSz - (float)tr.getHeight(), new SettingColor(255, 255, 255)
                                    );
                                    tr.end();
                                }

                                itemStartX += itemSz + itemGap;
                            }
                        }

                        NametagUtils.end();
                    }
                }
            }
        }
    }

    public boolean shouldOutlinePearl(EnderPearlEntity pearl) {
        if (this.isActive() && this.pearlRendering.get() != MoreTags.PearlRenderingType.Disabled) {
            int id = pearl.getId();

            return switch ((MoreTags.PearlRenderingType)this.pearlRendering.get()) {
                case Assigned -> pearlOwners.containsKey(id);
                case Both -> true;
                default -> false;
            };
        } else {
            return false;
        }
    }

    public int getPearlOutlineColor(EnderPearlEntity pearl) {
        SettingColor color = pearlOwners.containsKey(pearl.getId()) ? this.assignedRenderingColor.get() : this.unknownRenderingColor.get();
        return color.r << 16 | color.g << 8 | color.b;
    }

    private void requestSkin(String playerName) {
        if (!this.skinTextures.containsKey(playerName) && !this.loadingSkins.contains(playerName)) {
            this.loadingSkins.add(playerName);
            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL("https://mc-heads.net/avatar/" + playerName + "/64");
                    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "musheor-addon");
                    BufferedImage img = ImageIO.read(conn.getInputStream());
                    if (img == null) {
                        this.loadingSkins.remove(playerName);
                        return;
                    }

                    boolean online = this.isOnSameServer(playerName);
                    if (!online) {
                        img = this.toGrayscale(img);
                    }

                    NativeImage nativeImage = this.toNativeImage(img);
                    Identifier id = Identifier.of("musheor", "pearl_skin_" + playerName.toLowerCase());
                    this.mc.execute(() -> {
                        VersionHelper.get().registerSkinTexture(nativeImage, playerName, id);
                        this.skinTextures.put(playerName, id);
                        this.loadingSkins.remove(playerName);
                    });
                } catch (Exception var8) {
                    this.loadingSkins.remove(playerName);
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    private boolean isOnSameServer(String playerName) {
        return VersionHelper.get().onSameServer(playerName);
    }

    private BufferedImage toGrayscale(BufferedImage img) {
        BufferedImage gray = new BufferedImage(img.getWidth(), img.getHeight(), 2);

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int argb = img.getRGB(x, y);
                int a = argb >> 24 & 0xFF;
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                int lum = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                gray.setRGB(x, y, a << 24 | lum << 16 | lum << 8 | lum);
            }
        }

        return gray;
    }

    private NativeImage toNativeImage(BufferedImage img) {
        NativeImage nativeImage = new NativeImage(img.getWidth(), img.getHeight(), false);

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                nativeImage.setColorArgb(x, y, img.getRGB(x, y));
            }
        }

        return nativeImage;
    }

    private void drawRoundedOutline(float x, float y, float w, float h, float r, int colA, int colB) {
        if (r < 0.0F) {
            r = 0.0F;
        }

        int n = 10;
        float[][] ctr = new float[][]{{x + r, y + r}, {x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}};
        double[] arcStart = new double[]{Math.PI, Math.PI * 3.0 / 2.0, 0.0, Math.PI / 2};
        Renderer2D.COLOR.begin();
        float prevX = 0.0F;
        float prevY = 0.0F;
        boolean first = true;

        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= n; i++) {
                double a = arcStart[c] + i * (Math.PI / 2) / n;
                float vx = ctr[c][0] + (float)(Math.cos(a) * r);
                float vy = ctr[c][1] + (float)(Math.sin(a) * r);
                if (!first) {
                    this.drawGradientLine(prevX, prevY, vx, vy, x, w, colA, colB);
                }

                first = false;
                prevX = vx;
                prevY = vy;
            }

            int nextC = (c + 1) % 4;
            float ex = ctr[nextC][0] + (float)(Math.cos(arcStart[nextC]) * r);
            float ey = ctr[nextC][1] + (float)(Math.sin(arcStart[nextC]) * r);
            float dx = (ex - prevX) / n;
            float dy = (ey - prevY) / n;

            for (int i = 0; i < n; i++) {
                float nx = prevX + dx;
                float ny = prevY + dy;
                this.drawGradientLine(prevX, prevY, nx, ny, x, w, colA, colB);
                prevX = nx;
                prevY = ny;
            }
        }

        VersionHelper.get().renderColorRenderer();
    }

    private void drawGradientLine(float x1, float y1, float x2, float y2, float bx, float bw, int colA, int colB) {
        float t = Math.max(0.0F, Math.min(1.0F, ((x1 + x2) / 2.0F - bx) / bw));
        int col = NametagHelper.lerpColor(colA, colB, t);
        Renderer2D.COLOR.line(x1, y1, x2, y2, new SettingColor(col >> 16 & 0xFF, col >> 8 & 0xFF, col & 0xFF, 255));
    }

    private void renderGradientText(TextRenderer tr, String text, float x, float y, SettingColor start, SettingColor end) {
        float cursor = x;
        float totalW = (float)tr.getWidth(text);

        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float t = totalW > 0.0F ? (cursor - x) / totalW : 0.0F;
            int col = NametagHelper.lerpColor(this.toRgb(start), this.toRgb(end), t);
            SettingColor c = new SettingColor(col >> 16 & 0xFF, col >> 8 & 0xFF, col & 0xFF, 255);
            tr.render(ch, cursor, y, c);
            cursor += (float)tr.getWidth(ch);
        }
    }

    private int toRgb(SettingColor c) {
        return c.r << 16 | c.g << 8 | c.b;
    }

    private static enum DistanceType {
        Disabled,
        Color,
        Auto;
    }

    private static enum NameColorType {
        Default,
        Color,
        Gradient;
    }

    private static enum OutlineType {
        Disabled,
        Color,
        Gradient;
    }

    private static enum PearlRenderingType {
        Disabled,
        Assigned,
        Both;
    }

    private static enum TagDisplayType {
        Disabled,
        NameTag,
        Skin,
        Both;
    }
}
