package musheor;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import musheor.commands.CountItems;
import musheor.commands.EchestConfigCommand;
import musheor.commands.Execute;
import musheor.commands.FindItem;
import musheor.commands.FolderCommand;
import musheor.commands.ItemIdCommand;
import musheor.commands.KitCommand;
import musheor.commands.MapPlacerCommand;
import musheor.commands.PearlStoreCommand;
import musheor.commands.RestockConfig;
import musheor.commands.WhisperCommand;
import musheor.commands.XaeroUtilsCommand;
import musheor.compat.LitematicaHelper;
import musheor.compat.VersionHelper;
import musheor.compat.XearoHelper;
import musheor.modules.automation.AutoBreeder;
import musheor.modules.automation.Dispatcher;
import musheor.modules.automation.EchestFarmer;
import musheor.modules.automation.ElytraTweakz;
import musheor.modules.automation.GatherItem;
import musheor.modules.automation.HighwayBuilder;
import musheor.modules.automation.HotbarReplenish;
import musheor.modules.automation.IceRailBuilder;
import musheor.modules.automation.InventoryCleaner;
import musheor.modules.automation.KekBounce;
import musheor.modules.automation.KekNuker;
import musheor.modules.automation.MapDuper;
import musheor.modules.automation.MapPlacer;
import musheor.modules.automation.Printer;
import musheor.modules.automation.ReKit;
import musheor.modules.automation.Refill;
import musheor.modules.automation.SourceRemover;
import musheor.modules.automation.Stripper;
import musheor.modules.automation.WaypointFollower;
import musheor.modules.features.AirPlace;
import musheor.modules.features.AntiCheat;
import musheor.modules.features.AxisViewer;
import musheor.modules.features.CoordHider;
import musheor.modules.features.CreativeFly;
import musheor.modules.features.GrimRocketBoost;
import musheor.modules.features.GrimScaffold;
import musheor.modules.features.HorseFly;
import musheor.modules.features.KekFly;
import musheor.modules.features.KekMine;
import musheor.modules.features.LogoutGoal;
import musheor.modules.features.MoreTags;
import musheor.modules.features.NoMoreJumpDelay;
import musheor.modules.features.PortalSpawnESP;
import musheor.modules.hud.DimensionalPosition;
import musheor.modules.hud.DubCounter;
import musheor.modules.hud.HudInfoPlus;
import musheor.modules.hud.StatsViewer;
import musheor.modules.tech.AntiInteract;
import musheor.modules.tech.AutoPortal;
import musheor.modules.tech.ContainerTweaks;
import musheor.modules.tech.MessageInteract;
import musheor.modules.tech.PortalSkipDetection;
import musheor.modules.tech.Whisper;
import musheor.utils.ServerStatsTracker;
import musheor.utils.hud.HudEditorKeybind;
import musheor.utils.manager.InventoryManager;
import musheor.utils.system.MusheorSystem;
import musheor.utils.system.MusheorTab;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public class musheor extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category MAIN = new Category("Musheor");
    public static final Category AUTOMATION = new Category("Automation");
    public static final Category PLUS = new Category("Musheor+");
    public static final HudGroup MUSHEOR_HUD = new HudGroup("Musheor");

    @Override
    public void onInitialize() {
        LOG.info("Musheor initializing");

        try {
            Class.forName("musheor.compat.VersionHelperImpl").getMethod("init").invoke(null);
            LOG.info("VersionHelper initialized");
        } catch (Exception var6) {
            LOG.error("Failed to initialize VersionHelper", var6);
        }

        Systems.add(new MusheorSystem());
        Tabs.add(new MusheorTab());

        try {
            Class.forName("musheor.plus.PlusInitializer").getMethod("register").invoke(null);
            LOG.info("Plus modules registered");
        } catch (ClassNotFoundException var4) {
        } catch (Exception var5) {
            LOG.error("Failed to register Plus modules", var5);
        }

        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
            try {
                XearoHelper.setInstance((XearoHelper)Class.forName("musheor.compat.XearoHelperImpl").getDeclaredConstructor().newInstance());
                LOG.info("XearoHelper initialized");
            } catch (Exception var3) {
                LOG.error("Failed to initialize XearoHelper — is XaeroPlus also installed?", var3);
            }
        } else {
            LOG.warn("Xaero's Minimap not found — waypoint and navigation features will be unavailable.");
        }

        if (FabricLoader.getInstance().isModLoaded("litematica")) {
            try {
                LitematicaHelper.setInstance((LitematicaHelper)Class.forName("musheor.compat.LitematicaHelperImpl").getDeclaredConstructor().newInstance());
                LOG.info("LitematicaHelper initialized");
            } catch (Exception var2) {
                LOG.error("Failed to initialize LitematicaHelper — is Malilib also installed?", var2);
            }
        } else {
            LOG.warn("Litematica not found — Printer (Litematica mode) and KekNuker (Litematica mode) will warn if selected.");
        }

        Modules.get().add(new Printer());
        Modules.get().add(new AirPlace());
        Modules.get().add(new GrimScaffold());
        Modules.get().add(new AntiCheat());
        Modules.get().add(new LogoutGoal());
        Modules.get().add(new KekMine());
        Modules.get().add(new AxisViewer());
        Modules.get().add(new PortalSpawnESP());
        Modules.get().add(new KekFly());
        Modules.get().add(new CreativeFly());
        Modules.get().add(new HorseFly());
        Modules.get().add(new GrimRocketBoost());
        Modules.get().add(new NoMoreJumpDelay());
        Modules.get().add(new CoordHider());
        Modules.get().add(new MoreTags());
        Modules.get().add(new Dispatcher());
        Modules.get().add(new HotbarReplenish());
        Modules.get().add(new HighwayBuilder());
        Modules.get().add(new EchestFarmer());
        Modules.get().add(new GatherItem());
        Modules.get().add(new SourceRemover());
        Modules.get().add(new InventoryCleaner());
        Modules.get().add(new InventoryManager());
        Modules.get().add(new KekNuker());
        Modules.get().add(new Refill());
        Modules.get().add(new IceRailBuilder());
        Modules.get().add(new AutoBreeder());
        Modules.get().add(new ElytraTweakz());
        Modules.get().add(new KekBounce());
        Modules.get().add(new ReKit());
        Modules.get().add(new Stripper());
        Modules.get().add(new MapPlacer());
        Modules.get().add(new MapDuper());
        if (XearoHelper.isLoaded()) {
            Modules.get().add(new WaypointFollower());
        } else {
            LOG.warn("Xaero not found — WaypointFollower module will not be available.");
        }

        Modules.get().add(new PortalSkipDetection());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new ContainerTweaks());
        Modules.get().add(new Whisper());
        Modules.get().add(new MessageInteract());
        Modules.get().add(new AntiInteract());
        Commands.add(new WhisperCommand());
        Commands.add(new FindItem());
        Commands.add(new CountItems());
        Commands.add(new RestockConfig());
        Commands.add(new Execute());
        Commands.add(new XaeroUtilsCommand());
        Commands.add(new PearlStoreCommand());
        Commands.add(new FolderCommand());
        Commands.add(new KitCommand());
        Commands.add(new EchestConfigCommand());
        Commands.add(new ItemIdCommand());
        Commands.add(new MapPlacerCommand());
        Hud.get().register(StatsViewer.INFO);
        Hud.get().register(HudInfoPlus.INFO);
        Hud.get().register(DimensionalPosition.INFO);
        Hud.get().register(DubCounter.INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
        MeteorClient.EVENT_BUS.subscribe(new ServerStatsTracker());
        if (VersionHelper.get().supportsHudButtons()) {
            MeteorClient.EVENT_BUS.subscribe(new HudEditorKeybind());
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        HudInfoPlus.pruneExpiredPackets();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(MAIN);
        Modules.registerCategory(AUTOMATION);
        Modules.registerCategory(PLUS);
    }

    @Override
    public String getPackage() {
        return "musheor";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("musheck", "musheor");
    }

    @Override
    public String getWebsite() {
        return "https://musheck.dev";
    }
}
