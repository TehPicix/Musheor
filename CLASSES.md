# Class guide

One line per top-level class: what it does, as read from the decompiled code during deobfuscation. Descriptions are analysis notes, not the author's documentation.

## E2-hud-tech-ui

- musheor/modules/hud/DimensionalPosition: HUD element that shows the player's coordinates in the current dimension plus the converted Overworld/Nether coordinates, laid out either in columns or in one horizontal line, and shows "?" while CoordHider is active.
- musheor/modules/hud/DubCounter: HUD element that rescans loaded block entities every few ticks and counts double chests, single chests and chosen other storage block types, showing them as separate lines or one total.
- musheor/modules/hud/HudInfoPlus: "packet-limits" HUD element that keeps static timestamp queues of sent inventory, global and block-interact packets (filled by MixinClientConnection, pruned every Render2D frame from the main class) and shows the counts against the MusheorSystem limits; the count getters are also used by the obf packet-limit guard (musheor/utils/network/PacketLimiter) and KekNuker.
- musheor/modules/hud/StatsViewer: HUD element that lists HighwayBuilder paver statistics (session, blocks, performance, info/ETA, lifetime), using the musheor/utils/highway/HighwayState stats singleton and the musheor/utils/HighwayStatsUtils formatters.
- musheor/modules/tech/AntiInteract: Module that cancels InteractBlockEvent on beds, respawn anchors and a user block list, and prints a chat notice.
- musheor/modules/tech/AutoPortal: Module that locks a 4x5 nether portal frame in front of the player (yaw picks the facing, pitch picks the Y offset), places obsidian with packet-limit checks, lights it with flint and steel through a sequenced interact packet, checks for portal blocks, and renders the layout (red when blocked).
- musheor/modules/tech/ContainerTweaks: Module (static INSTANCE) that adds steal/dump buttons and move-matching/move-all keybinds to quick-move items, optionally only shulkers, between a container and the player inventory on MeteorExecutor, stopping when the packet limit would be exceeded; it also stores the draggable button offsets.
- musheor/modules/tech/MessageInteract: Module that reacts to "!tp" whispers (optionally from one allowed player) by pathing with Baritone to a block and interacting with or attacking it, or, in pearl mode, finding the sender's tracked stasis pearl trapdoor, flipping it twice, optionally dropping a pearl and pathing back; it replies by /msg with a random anti-spam suffix.
- musheor/modules/tech/PortalSkipDetection: Module that scans loaded chunks within a circular radius on a background daemon thread for portal-sized AIR regions surrounded by CAVE_AIR (portal-skip signatures), announces each one in chat, and renders the detected blocks.
- musheor/modules/tech/Whisper: "TPA" module that, when turned on, sends a TP-request whisper with a random anti-spam string to the configured stasis-bot username and then turns itself off.
- musheor/musheor: Meteor addon entry point that registers systems, tabs, modules, commands, HUD elements and the optional Xaero/Litematica/Plus integrations, and prunes the HudInfoPlus packet queues on every Render2DEvent.
- musheor/utils/hud/CustomHudButton: Data model for a user-defined screen button (id, label, color index, size, default/saved position, screen target, action list) with NBT save/load and a factory for its DraggableButtonWidget that runs the actions as one CompositeAction.
- musheor/utils/hud/DraggableButtonWidget: ButtonWidget that can be dragged while Ctrl is held, shows a move glyph and overlay in that mode, draws a colored label, and reports the new position to a PositionSaveCallback (or to two Integer settings).
- musheor/utils/hud/HudEditorKeybind: Tick listener that opens CustomButtonManagerScreen when the MusheorSystem "openEditor" keybind is pressed with no screen open.
- musheor/utils/hud/actions/ButtonAction: Interface for custom-button actions (execute on a ScreenHandler, type id, NBT serialization) with a static fromTag factory that dispatches on the "type" string.
- musheor/utils/hud/actions/ChangeSettingAction: ButtonAction that finds a module's setting by name (case-insensitive) and parses a stored string value into it.
- musheor/utils/hud/actions/CompositeAction: ButtonAction that runs a list of actions in order (not serialized).
- musheor/utils/hud/actions/RunCommandAction: ButtonAction that sends a Meteor "." command as a chat message, or sends a server command with the leading "/" removed.
- musheor/utils/hud/actions/SendMessageAction: ButtonAction that sends a fixed chat message.
- musheor/utils/hud/actions/ToggleModuleAction: ButtonAction that toggles a Meteor module by name.
- musheor/utils/hud/screen/CustomButtonManagerScreen: Screen listing custom HUD buttons, with Edit, Delete, New Button and Back controls.
- musheor/utils/hud/screen/EchestConfigScreenBase: Abstract screen that draws a 9x3 ender-chest grid from EChestMemory and maps left/right clicks on a slot to the restock whitelist/blacklist (musheor/utils/highway/EchestSlotBlacklist), saving on close.
- musheor/utils/hud/screen/EditButtonScreen: Screen for editing one custom button's label, text color, screen target, width/height and action list, with per-type input fields for adding actions and [x] links for removing them.

## A1-utils

- musheor/utils/NametagHelper: Static helpers for the MoreTags nametag module: a player's armor, main-hand and off-hand stacks, whether they hold or wear anything, distance to the local player, a red-to-green distance color, RGB lerp, and a Meteor friend check.
- musheor/utils/PearlStore: Saves and loads stasis-chamber pearl records (owner name, trapdoor BlockPos, pearl entity id) in meteor-client/pearls.json, with add/update, lookup by trapdoor or owner, remove by owner, and clear.
- musheor/utils/HighwayBuilderLogic: Per-tick HighwayBuilder routines (Auto, cardinal and diagonal pave, cardinal and diagonal dig, echest-farming start and finish), plus ring/diamond corner turning, KekBounce auto-bounce start/stop, and fall-off-highway recovery; its public static mc field is used as a shared MinecraftClient by many mixins and managers.
- musheor/utils/DiscordRpc: Starts and stops Discord IPC (app id 1346947991059300434) and updates the Rich Presence with obsidian placed/mined counts for the session and lifetime, taken from the paver stats singleton.
- musheor/utils/RestockType: Enum giving the kind of shulker or echest restock in progress (building material or echests, normal pickaxe, silk-touch pickaxe, food), used by the inventory manager.
- musheor/utils/HighwayPositions: Builds BlockPos arrays for cardinal and diagonal highway floors with rails, ceilings, clear (tunnel) volumes, behind-player blockage zones and lava-scan zones, each from the highway direction, width and stored anchor coordinates.
- musheor/utils/PlayerControlUtils: Player and module control helpers: reach checks, cancelling Baritone, disconnecting with a reason, AutoWalk and key-state control, setting a Meteor module setting by name, facing the highway yaw, FreeLook setup, GatherItem start, strafing or Baritone-pathing back onto the lane or diagonal axis, and whispering a random "!tp" code to a player.
- musheor/utils/BlockRenderUtils: Render3DEvent helpers that draw merged outlines and faces for block sets, colored by each block's map color or by fixed or Manager-setting colors, drawing only the edges and faces on the outside of the set.
- musheor/utils/HighwayStatsUtils: Computes and formats paver statistics for StatsViewer and Discord RPC (runtime, distance travelled, placements/breaks per second and hour, distance to the next section and progress through it, material count, section and refill ETAs, k/m number formatting).
- musheor/utils/HighwayUtils: Core highway world and placement helpers: the 8-way HighwayDirection enum, lane/axis targets and highway detection, returning to the highway, lava obstruction handling, offhand-swap block placement via sequenced packets, look rotations, spleefing entities that block placement, scaffolding, collision checks, item-entity search and horizontal distances.
- musheor/utils/BouncePitchPolicy: Precomputes, by relative value iteration over a height-by-vertical-velocity grid, an elytra-bounce pitch policy for given gravity and jump velocity; KekBounce's variable-pitch mode builds it on a background thread and asks it for the best pitch each tick.

## A2-manager-obf

- musheor/utils/manager/RotationManager (inner enum MoveMode): Priority-based server yaw/pitch override; MoveMode picks whether movement input is re-mapped to the overridden yaw (default) or forced to straight forward.
- musheor/utils/manager/PongHolder: Static queue that, while holding is enabled, captures CommonPing parameters and later flushes them as CommonPongC2SPacket (or discards them); used by ElytraManager/KekBounce while spoofing the gliding flag.
- musheor/utils/manager/ElytraManager: Static elytra-flight helper for CreativeFly/KekFly/ElytraTweakz/KekBounce: fake-fly (swaps the elytra onto the chest, sends START_FALL_FLYING, rewrites the self gliding flag in EntityTrackerUpdate packets while holding pongs), stowing the elytra, and auto-rocket timing/firing.
- musheor/utils/manager/InventoryManager: Meteor Module "inventory-manager" (AUTOMATION) with item/shulker/delay settings plus static inventory utilities (slot search, counting, shulker content checks, tool selection) and the HighwayBuilder restock state machine (place a shulker or ender chest at the restock position, steal pickaxes/food/materials or shulkers, then break the container and pick it up).
- musheor/utils/baritone/BaritoneHelper: Thin static wrapper over the primary Baritone instance (pathing checks, cancel, setGoalAndPath, elytra pathTo, command execution, "goto x y z").
- musheor/utils/mapart/MapArtGrid: Singleton tracking an item-frame map-art grid anchored on a clicked frame and corner (top-left/bottom-left), scanning frames into Cells, matching filled maps in the inventory to cells by numbers parsed from their names ("x,y" grid or linear index), and giving placement geometry for MapPlacer/MapPlacerCommand.
- musheor/utils/highway/HighwayState: Singleton holding HighwayBuilder runtime state (direction, start/anchor/align coordinates, detected highway, restock/echest-farm/lava flags, pending placement ticks) and session plus lifetime paver stats (obsidian placed/mined, netherrack, ender chests, runtime ticks) read by StatsViewer, ServerStatsTracker and Discord RPC.
- musheor/utils/printer/BlockStateCycler: Computes how many right-click interactions cycle a block's interactable property (open, delay, mode, note, inverted, powered) from the current to the target state, ignoring non-interactable properties; used by Printer.
- musheor/utils/highway/EchestSlotBlacklist: Static set of ender-chest slots (0-26) excluded from echest shulker restocking, serialised to/from a bitmask in HighwayBuilder's tag and edited by EchestConfigScreenBase.
- musheor/utils/placement/BlockPlacer: Static block placement helpers (airplace, airplace with offhand swap, rotate-place), batch placement of position arrays with pending-placement tracking, and an AutoCloseable PlaceBatch for repeated placements of one block.
- musheor/utils/network/PacketLimiter: Static checks against HudInfoPlus packet windows: whether another place packet (limit 8/9) or inventory packet (inventory-packet-limit) can be sent, and how many inventory packets remain.
- musheor/utils/highway/HighwayDetector: Detects the nearest 2b2t highway for a position and travel direction (axis, ring, diamond, diagonal, 5k grid highways using HighwayNetwork distances), returning a DetectedHighway with id/label/category/width/axis/align/start values, plus rail and inside-highway position tests and distance formatting.

## B-printer-maps

- musheor/commands/MapPlacerCommand: The .mp command that sets up the map-placer canvas (origin corner taken from the targeted or right-clicked item frame, size, coordinate base), scans and lists frame fill status, resolves map names to cells, and starts/pauses/resumes/stops/clears the MapPlacer module.
- musheor/modules/automation/KekNuker: Nuker module ("kek-nuker") that breaks blocks around the player from a cube/sphere shape, Litematica wrong blocks, a Baritone selection or a highway position list filled by other code, with layer, whitelist/blacklist and sort filters, optional KekMine packet-mining, and Baritone ring-scan auto-pathfinding when nothing is in range.
- musheor/modules/automation/MapDuper: Duplicates filled maps at an open cartography table in Extract/Copy/Store stages, optionally pulling the maps out of a source bundle, returning one original to it, packing copies into other bundles and dropping overflow at your feet.
- musheor/modules/automation/MapPlacer: Places filled maps into a wall of item frames by the canvas grid (musheor/utils/mapart/MapArtGrid) parsed from map names, swapping the map into the hotbar, rotating by packet or client, auto-scanning frames, and rendering each cell's status box and label.
- musheor/modules/automation/Printer: Schematic printer ("kek-printer") that places Litematica schematic blocks or fills Baritone selections with one block, simulating placement to find a hit side and yaw/pitch for each block state, fixing block states by interacting, auto-restocking from RestockConfig containers or the Refill module, and pathfinding with Baritone to the next placeable chunk.

## C-movement-highway

- musheor/modules/automation/ElytraTweakz: Elytra helpers that swap in a fresh elytra when durability is low, equip the elytra and start gliding on an airborne double-tap of jump, swap back to the chestplate on landing, and fire a rocket from a keybind while gliding.
- musheor/modules/automation/HighwayBuilder: 2b2t highway paver/digger that sets up the shared paver state, auto-detects or uses a chosen highway type, width and direction, toggles helper modules (KekNuker, AutoEat/AutoGap, KillAura, SourceRemover, InventoryCleaner, HotbarReplenish, FreeLook), restocks from shulkers and the ender chest, farms ender chests for obsidian, renders the planned pavement, and exposes its settings through static getters.
- musheor/modules/automation/IceRailBuilder: Unfinished ice-highway helper that places netherrack supports, then blue ice every other block along a fixed rail at Y 115 / Z -200, depending on the selected axis.
- musheor/modules/automation/KekBounce: Elytra bounce module that locks pitch (fixed or solver-driven variable pitch) and yaw, re-starts the glide each hop, supports a Grim "fake-fly" mode (elytra kept in the hotbar and swapped on only for the glide start, with spoofed gliding flags and held-back pongs), optionally passes obstacles with Baritone when stuck or rubberbanded, repairs the elytra with XP bottles, and in Navigate mode routes along the 2b2t highway network to Xaero temporary waypoints via HighwayRouter/HighwayNavigator.
- musheor/modules/automation/WaypointFollower: Follows Xaero temporary waypoints in order using Baritone elytra pathing, KekFly rocket flight, or yaw-only steering, deleting each waypoint on arrival and optionally drawing the path on the Xaero map.
- musheor/modules/automation/highway/HighwayNavigator: Per-tick state machine that flies a HighwayRouter route leg by leg (align yaw, bounce, approach, Baritone-walk the intersection, settle), pausing and resuming bouncing through a BounceController callback and reporting state changes and route completion to a Listener.
- musheor/modules/automation/highway/HighwayNetwork: Static model of the 2b2t highway network (axis, diagonal, ring-road, diamond and 5000-block grid highways from HighwayNetworkManager distances) with precomputed intersections, edges between neighbouring intersections, and geometry helpers.
- musheor/modules/automation/highway/HighwayRouter: Plans a highway route between two XZ points, either on one highway, via one shared intersection, or by Dijkstra over the intersection graph, and merges collinear legs into a Route of legs and waypoints.
- musheor/modules/features/CreativeFly: Grim-oriented creative-style elytra flight that sets server-side yaw/pitch from WASD/jump/sneak input, and when idle either freezes the player in place (with slow periodic descent) or hovers by flipping yaw or pitch each tick.
- musheor/modules/features/GrimRocketBoost: While a rocket boost is active during gliding, sets velocity along the flight direction at boost-speed rescaled into a per-axis box so diagonal flight exceeds the single-axis cap, and jitters yaw by 0.01 degrees per tick.
- musheor/modules/features/HorseFly: Flies a horse the player controls by overriding its travel with keyboard-driven velocity (speed, vertical speed, glide sink), and flags when to send NaN steering input through ViaFabricPlus to bypass Grim.
- musheor/modules/features/KekFly: Rocket/elytra flight module that auto-swaps to the elytra (or uses Grim fake-fly), drives the shared flight manager each tick, and can steer pitch to hold a target Y level outside the Nether.

## D-automation-commands

- musheor/commands/CountItems: .countItems <item> reports how many of an item the player holds, including inside shulker boxes (via the InventoryManager helper).
- musheor/commands/EchestConfigCommand: .erc sets a two-tick countdown on its own TickEvent listener and then opens EchestConfigScreen.
- musheor/commands/Execute: .execute Class.method args debug command that scans every class of the musheor mod container by reflection, suggests class and method names, converts the string arguments to the parameter types and invokes the method.
- musheor/commands/FindItem: .find <item> passes the item to the HighwayUtils helper so Baritone can find and pick up dropped copies of it.
- musheor/commands/FolderCommand: .folder [name] opens .minecraft, or one of its subfolders (tab-completed), in Windows Explorer.
- musheor/commands/ItemIdCommand: .itemid [range] lists the entity IDs, types/names and distances of all entities within range (default 16), nearest first, capped at 100 lines.
- musheor/commands/KitCommand: .kit create|load|delete|list [name] saves, activates, deletes and lists ReKit loadout kits.
- musheor/commands/PearlStoreCommand: .pearlstore clear / .pearlstore remove <player> clears all Pearl Store entries or removes one player's entry through PearlStore.
- musheor/commands/RestockConfig: .rc command that keeps a static item -> container-positions map for restocking, filled either by right-clicking containers for one item (add mode) or by reading the contents of each opened container (auto mode), and saved as NBT through MusheorSystem.
- musheor/commands/WhisperCommand: .tp [player] remembers the target player and sends them !tp <random text>, with the text length taken from the Whisper module's anti-spam-length setting.
- musheor/commands/XaeroUtilsCommand: .xaero deleteAllTempWaypoints deletes all temporary Xaero waypoints.
- musheor/modules/automation/AutoBreeder: finds the nearest selected adult animal that accepts the held breeding item, paths to it with Baritone and feeds it, then skips that animal for 5 minutes.
- musheor/modules/automation/Dispatcher: once the player is past a minimum distance from spawn, it alerts in chat and/or through a queued Discord webhook (plus optional role ping, Xaero waypoint and sound) when players enter or leave visual range, newly loaded chunks hold enough tracked containers (or any single instant-hit container), illegal bedrock appears, or ender pearls are seen.
- musheor/modules/automation/EchestFarmer: "obi-farmer" places and breaks ender chests at one spot to farm obsidian, stopping at a target count or when the inventory is full; it can use KekMine instant rebreak and falls back to a normal break when the rebreak stalls.
- musheor/modules/automation/GatherItem: "pickup-item" picks the nearest dropped item of the chosen type within a radius and sends Baritone to safe stand positions next to it, ignoring an item for a cooldown once no position works.
- musheor/modules/automation/HotbarReplenish: refills each hotbar slot and the offhand from the main inventory with that slot's configured items once the stack drops to a threshold.
- musheor/modules/automation/InventoryCleaner: "inv-cleaner" drops items by whitelist/blacklist, and drops shulker boxes that have stayed empty for a grace period, optionally while facing backwards; its button fills the list with the current inventory.
- musheor/modules/automation/ReKit: saves inventory loadouts as JSON kits in the Meteor folder (musheor/kits) and rearranges the inventory to match the active kit, pulling items from an open container, either when a container opens or on a keybind.
- musheor/modules/automation/Refill: selects a shulker box, places it below the player or where the crosshair points, opens it and shift-clicks the chosen item out until the inventory, or a set number of slots, is filled.
- musheor/modules/automation/SourceRemover: "source-filler" fills lava and/or water source blocks within placement range using solid, whitelisted or non-blacklisted blocks, and renders nearby source blocks.
- musheor/modules/automation/Stripper: places the chosen log or copper block in front of the player, uses an axe on it until it reaches the target stripped, unwaxed or de-oxidized state, then breaks it, and repeats.

## E1-features

- musheor/modules/features/AirPlace: While the use key is held with a block or spawn egg in the main hand, it places at the replaceable block under the crosshair (custom or vanilla reach), once on press and then every 4 ticks, and renders a preview of the block.
- musheor/modules/features/AntiCheat: Holds the hidden "air-place-bypass" toggle that MixinBlockUtilsBypass reads to swap Meteor's BlockUtils.place for the addon's air-place, and cancels server CloseScreenS2CPacket when "no-screen-close" is on.
- musheor/modules/features/AxisViewer: Draws lines at a set Y per dimension for the cardinal and diagonal highway axes, plus optional ring roads and diamond highways (spacing from HighwayNetwork) and a grid every 5000 blocks, all clipped to a render range around the player.
- musheor/modules/features/DepthInteract: Each tick it steps along the look vector in 0.1 steps to find the first chest, trapped chest, barrel, shulker box or ender chest within reach (through blocks), outlines it, and exposes its position so ClientPlayerInteractionManagerMixin can redirect interactBlock to it.
- musheor/modules/features/GrimScaffold: ("simple-scaffold") Places an allowed block (default obsidian or netherrack) below the player, optionally expanding in a radius or forward-only, through the addon's inventory and placement managers, and renders where it will place.
- musheor/modules/features/KekMine: Packet miner hooked into vanilla attackBlock/updateBlockBreakingProgress (MixinClientPlayerInteractionManager) that keeps a primary and a parked secondary MineContext plus a break queue, instamines when possible, silently swaps to the best tool and restores it after hold-ticks, and uses Grim tricks (y=1337 offset START, 22-packet burst on long breaks, double-break with alternation, legacy STOP+START bypass), with optional server-confirmed breaks, auto-rebreak and progress-box rendering.
- musheor/modules/features/LogoutGoal: Disconnects the player (via a fake DisconnectS2CPacket with a custom message, optionally with coordinates) once they are within range of a target X/Z in a chosen dimension; it can also turn off AutoReconnect and disable itself.
- musheor/modules/features/MoreTags: ("kek-tags") Draws custom 2D player nametags (name color/gradient, friend color, background, rounded/gradient outline, distance text, mc-heads.net face icon greyed out when the player is not on the server, armor/held items) and finds ender pearl owners (entity owner, spawn-packet owner id, reflected ownerUuid, or PearlStore trapdoor stasis records) so it can make those pearls glow in a set color and draw owner name/skin tags above them.
- musheor/modules/features/PortalSpawnESP: On a background thread it scans a square around a target position for columns where a 4-long run of solid-top blocks with 4 air blocks above each could hold a spawned Nether portal, and renders 5-block boxes at the matches.

## Mixins

Generated from the `@Mixin` annotations and `musheor.mixins.json`.

| Mixin | Target | Status |
|---|---|---|
| `AbstractClientPlayerEntityMixin` | `AbstractClientPlayerEntity` | active (client) |
| `AxeItemAccessor` | `AxeItem` | active (common) |
| `ChatScreenMixin` | `ChatScreen` | active (client) |
| `DebugHudMixin` | `DebugHud` | active (client) |
| `MixinClientCommonNetworkHandler` | `ClientCommonNetworkHandler` | active (client) |
| `MixinClientConnection` | `ClientConnection` | active (client) |
| `MixinClientPlayerEntity` | `ClientPlayerEntity` | active (client) |
| `MixinClientPlayerInteractionManager` | `ClientPlayerInteractionManager` | active (client) |
| `MixinClientPlayNetworkHandler` | `ClientPlayNetworkHandler` | active (client) |
| `MixinEntity` | `Entity` | active (common) |
| `MixinKeyboardInput` | `KeyboardInput` | active (client) |
| `MixinLivingEntity` | `LivingEntity` | active (common) |
| `MixinPearlOutline` | `Entity` | active (common) |
| `MixinPlayerEntityRenderer` | `PlayerEntityRenderer` | active (client) |
| `MixinPlayerOutline` | `Entity` | active (common) |
| `MixinProjectileEntity` | `ProjectileEntity` | **not registered** (dead code in the jar) |
| `MixinXaeroGuiMapCoords` | `GuiMap` | active (common) |
| `MixinXaeroInfoDisplayCompiler` | `InfoDisplayCompiler` | active (common) |
| `MixinXearoGuiMap` | `GuiMap` | **not registered** (dead code in the jar) |
| `ScreenHandlerMixin` | `HandledScreen` | active (client) |
| `ViaClientVehicleStorageMixin` | `ClientVehicleStorage (string target, Via)` | active (client) |
| `meteor.AutoEatMixin` | `AutoEat` | **not registered** (dead code in the jar) |
| `meteor.BaritonePathManagerMixin` | `BaritonePathManager` | active (common) |
| `meteor.BetterTabMixin` | `BetterTab` | active (common) |
| `meteor.ClientPlayerInteractionManagerMixin` | `ClientPlayerInteractionManager` | active (client) |
| `meteor.FriendsCommandMixin` | `FriendsCommand` | **not registered** (dead code in the jar) |
| `meteor.KillAuraMixin` | `KillAura` | **not registered** (dead code in the jar) |
| `meteor.MixinAutoGap` | `AutoGap` | active (common) |
| `meteor.MixinBlockUtilsBypass` | `BlockUtils` | **not registered** (dead code in the jar) |
| `meteor.PlayerListHudMixin` | `PlayerListHud` | active (client) |
| `meteor.TimerMixin` | `Timer` | active (common) |
