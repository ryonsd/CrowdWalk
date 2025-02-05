// -*- mode: java; indent-tabs-mode: nil -*-
package nodagumi.ananPJ;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.swing.JOptionPane;

import net.arnx.jsonic.JSON;

import nodagumi.ananPJ.Editor.MapEditor;
import nodagumi.ananPJ.Gui.Coastline;
import nodagumi.ananPJ.Gui.GsiTile;
import nodagumi.ananPJ.NetworkMap.MapPartGroup;
import nodagumi.ananPJ.NetworkMap.NetworkMap;
import nodagumi.ananPJ.misc.CrowdWalkPropertiesHandler;
import nodagumi.ananPJ.misc.GsiAccessor;
import nodagumi.ananPJ.misc.JsonicHashMapGetter;
import nodagumi.ananPJ.misc.SetupFileInfo;
import nodagumi.ananPJ.misc.SimTime;
import nodagumi.ananPJ.Simulator.Obstructer.ObstructerBase;

import nodagumi.Itk.*;

public abstract class GuiSimulationLauncher extends BasicSimulationLauncher {
    /**
     * GUI シミュレーションランチャーの一覧
     */
    public static final String LAUNCHER_CLASSES = "/gui_simulation_launcher_classes.json";

    /**
     * クラスローダー
     */
    private static ClassFinder classFinder = new ClassFinder();

    static {
        classFinder.aliasByJson(ObstructerBase.resourceToString(LAUNCHER_CLASSES));
    }

    /**
     * 使用可能なサブクラスかどうか?
     */
    public static boolean isUsableClass(String className) {
        return classFinder.isClassName(className);
    }

    /**
     * サブクラスのインスタンスを生成する
     */
    public static GuiSimulationLauncher createInstance(String className) {
        try {
            return (GuiSimulationLauncher)classFinder.newByName(className);
        } catch (Exception ex) {}
        return null;
    }

    /**
     * マップエディタ
     */
    protected MapEditor editor = null;

    /**
     * シミュレーションパネルの幅
     */
    protected int simulationPanelWidth = 800;

    /**
     * シミュレーションパネルの高さ
     */
    protected int simulationPanelHeight = 600;

    /**
     * GUI の設定情報
     */
    protected Settings settings;

    /**
     * ウィンドウクローズと共にアプリケーションを終了するか?
     */
    protected boolean exitOnClose = false;

    /**
     * シミュレーションのメインループ用 Runnable
     */
    protected transient Runnable simulationRunnable = null;

    /**
     * 地理院タイル画像
     */
    protected ArrayList<GsiTile> mapTiles = null;

    /**
     * 海岸線
     */
    protected Coastline coastline = null;

    /**
     * リセット中か?
     */
    protected boolean resetting = false;

    /**
     * 一時停止の有効/無効
     */
    protected boolean pauseEnabled = false;

    /**
     * quit() の呼び出し後か?(リセットを除く)
     */
    protected boolean quitting = false;

    /**
     * Properties
     */
    public static final String[] SHOW_STATUS_VALUES = {"none", "top", "bottom"};
    public static final String[] IMAGE_TYPES = {"bmp", "gif", "jpg", "png"};
    protected int deferFactor = 0;
    protected double verticalScale = 1.0;
    protected double agentSize = 1.0;
    protected String cameraFile = null;
    protected double zoom = 1.0;
    protected boolean showBackgroundImage = false;
    protected boolean showBackgroundMap = false;
    protected boolean showTheSea = false;
    protected boolean recordSimulationScreen = false;
    protected String screenshotDir = "screenshots";
    protected boolean clearScreenshotDir = false;
    protected String screenshotImageType = "png";
    protected boolean simulationWindowOpen = false;
    protected boolean autoSimulationStart = false;
    protected boolean hideLinks = false;
    protected boolean densityMode = false;
    protected boolean changeAgentColorDependingOnSpeed = true;
    protected boolean drawingAgentByTriageAndSpeedOrder = true;
    protected boolean showStatus = false;
    protected String showStatusPosition = "top";
    protected boolean showLogo = false;
    protected boolean show3dPolygon = true;
    protected boolean exitWithSimulationFinished = false;
    protected String agentAppearanceFile = null;
    protected String linkAppearanceFile = null;
    protected String nodeAppearanceFile = null;
    protected int gsiTileZoom = 14;

    /**
     * 画像ファイルかどうかをファイル名の拡張子で判別するフィルタ
     */
    protected FilenameFilter imageFileFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            for (String suffix : IMAGE_TYPES) {
                if (name.endsWith("." + suffix)) {
                    return true;
                }
            }
            return false;
        }
    };

    /**
     * コンストラクタ.
     */
    public GuiSimulationLauncher() {
        super(null) ;
    }

    /**
     * アプリ起動時にシミュレーションを開始する場合の初期設定.
     */
    public void init(String _propertiesPath, Settings _settings,
            ArrayList<String> commandLineFallbacks) {
        // load properties
        setPropertiesFromFile(_propertiesPath, commandLineFallbacks) ;
        setPropertiesForDisplay();

        // check property options
        if (getNetworkMapFile() == null) {
            Itk.logFatal("GuiSimulationLauncher", "map file is required.");
            Itk.quitByError() ;
        } else if (!((File) new File(getNetworkMapFile())).exists()) {
            Itk.logFatal("GuiSimulationLauncher",
                         "specified map file does not exist.");
            Itk.quitByError() ;
        }
        try {
            map = readMapWithName(getNetworkMapFile()) ;
        } catch (IOException ioe) {
            Itk.quitWithStackTrace(ioe) ;
        }

        settings = _settings;
        exitOnClose = true;
        loadGsiTiles();
        loadCoastlines();
    }

    /**
     * マップエディタからシミュレーションを開始する場合の初期設定.
     */
    public void init(MapEditor _editor, Settings _settings) {
        editor = _editor;
        properties = editor.getProperties().clone();
        random = new Random(properties.getRandseed());
        setPropertiesForDisplay();
        setupFileInfo = editor.getSetupFileInfo().clone();
        map = editor.getMap().clone();
        map.sortNodesById();
        map.sortLinksById();
        settings = _settings;
        // ending condition
        setExitCount(properties.getExitCount()) ;
        setIsAllAgentSpeedZeroBreak(properties.getIsAllAgentSpeedZeroBreak());
        loadGsiTiles();
        loadCoastlines();
    }

    public void simulate() {
        // 既に終わっていたら、警告メッセージ
        if (!finished) {
            JOptionPane.showMessageDialog(null,
                    "Previous simulation not finished?",
                    "Could not start simulation",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        System.gc();

        // スクリーンショットディレクトリのクリア
        // recordSimulationScreen があるとおかしい。
        //        if (recordSimulationScreen && clearScreenshotDir) {
        if (clearScreenshotDir) {
            File dir = new File(screenshotDir);
            if (dir.isDirectory()) {
                for (String filename : dir.list(imageFileFilter)) {
                    File file = new File(dir, filename);
                    if (! file.delete()) {
                        Itk.logError("File can not delete", file.toString());
                    }
                }
            }
        }

        // シミュレータの実体の初期化
        initializeSimulatorEntity() ;

        // ウィンドウとGUIの構築
        setupFrame();

        System.gc();

        // メインループの Runnable 作成。
        simulationRunnable = new Runnable() {
            public void run() {
                simulateMainLoop() ;
                paused = true ;
            }
        };
    }

    /**
     * シミュレータを終了または再起動する
     */
    public void quit() {
        if (quitting) {
            return;
        }

        Itk.logInfo("Simulation window closed.") ;
        // スクリーンショットの保存中ならば完了するのを待つ
        while (getSaveThreadCount() > 0) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {}
            }
        }
        if (! finished) {
            // ログファイルのクローズ処理
            simulator.finish();
            Itk.logInfo("Status", getStatusLine());
            finished = true;
        }
        if (resetting) {
            resetting = false;
            initPropertyValues();
            if (editor == null) {
                init(getPropertiesFile(), settings, commandLineFallbacks);
            } else {
                init(editor, settings);
            }
            simulate();
        } else {
            quitting = true;
            if (editor != null) {
                editor.getFrame().setDisableSimulationButtons(false);
            }
            if (exitOnClose) {
                if (editor != null) {
                    editor.getFrame().exit();
                } else {
                    exit(0);
                }
            }
        }
    }

    public void start() {
        synchronized (simulationRunnable) {
            if (paused) {
                paused = false ;
                Thread thread = new Thread(simulationRunnable);
                thread.start();
            }
        }
    }

    public void pause() {
        synchronized (simulationRunnable) {
            paused = true ;
        }
    }

    public void step() {
        synchronized (simulationRunnable) {
            simulateOneStepBare() ;
            if (exitCount > 0 && counter > exitCount) {
                finished = true;
            }
            if (finished) {
                finish() ;
            }
        }
    }

    public boolean isRunning() {
        return !paused;
    }

    /**
     * シミュレーションの終了処理。
     */
    @Override
    protected void finish() {
        super.finish() ;
        if (isExitWithSimulationFinished()) {
            exitOnClose = true;
            quit();
        }
    }

    /**
     * ディスプレーを持つかどうか。
     */
    public boolean hasDisplay() {
        return true;
    }

    /**
     * リセット中かどうかを設定する
     */
    public void setResetting(boolean resetting) {
        this.resetting = resetting;
    }

    /**
     * シミュレーションの完了と共にアプリケーションを終了させるかどうか。
     */
    public abstract boolean isExitWithSimulationFinished();

    /**
     * ウィンドウとGUIを構築する
     */
    protected abstract void setupFrame();

    /**
     * ボタン類のアップデート
     */
    public abstract void update_buttons();

    /**
     * スクリーンショット保存用のスレッド数カウンタ値を取得する
     */
    public abstract int getSaveThreadCount();

    /**
     * アプリケーションを終了する
     */
    public abstract void exit(int exitCode);

    /**
     * 一時停止の有効/無効を設定する
     */
    public void setPauseEnabled(boolean pauseEnabled) {
        this.pauseEnabled = pauseEnabled;
    }

    /**
     * 設定値を初期化する
     */
    protected void initPropertyValues() {
        pauseEnabled = false;
        quitting = false;

        deferFactor = 0;
        verticalScale = 1.0;
        agentSize = 1.0;
        cameraFile = null;
        zoom = 1.0;
        showBackgroundImage = false;
        showBackgroundMap = false;
        showTheSea = false;
        recordSimulationScreen = false;
        screenshotDir = "screenshots";
        clearScreenshotDir = false;
        screenshotImageType = "png";
        simulationWindowOpen = false;
        autoSimulationStart = false;
        hideLinks = false;
        densityMode = false;
        changeAgentColorDependingOnSpeed = true;
        drawingAgentByTriageAndSpeedOrder = true;
        showStatus = false;
        showStatusPosition = "top";
        showLogo = false;
        show3dPolygon = true;
        exitWithSimulationFinished = false;
        agentAppearanceFile = null;
        linkAppearanceFile = null;
        nodeAppearanceFile = null;
        gsiTileZoom = 14;
    }

    /**
     * 画面出力用properties設定
     */
    protected void setPropertiesForDisplay() {
        try {
            deferFactor = properties.getInteger("defer_factor", deferFactor);
            if (deferFactor < 0 || deferFactor > 299) {
                throw new Exception("Property error - 設定値が範囲(0～299)外です: defer_factor:" + deferFactor);
            }
            verticalScale = properties.getDouble("vertical_scale", verticalScale);
            if (verticalScale < 0.1 || verticalScale > 10.0) {
                throw new Exception("Property error - 設定値が範囲(0.1～10.0)外です: vertical_scale:" + verticalScale);
            }
            agentSize = properties.getDouble("agent_size", agentSize);
            zoom = properties.getDouble("zoom", zoom);
            if (zoom < 0.0 || zoom > 9.9) {
                throw new Exception("Property error - 設定値が範囲(0.0～9.9)外です: zoom:" + zoom);
            }
            cameraFile = properties.getFilePath("camera_file", null);
            agentAppearanceFile = properties.getFilePath("agent_appearance_file", null);
            linkAppearanceFile = properties.getFilePath("link_appearance_file", null);
            nodeAppearanceFile = properties.getFilePath("node_appearance_file", null);
            showBackgroundImage = properties.getBoolean("show_background_image", false);
            showBackgroundMap = properties.getBoolean("show_background_map", false);
            showTheSea = properties.getBoolean("show_the_sea", false);
            recordSimulationScreen = properties.getBoolean("record_simulation_screen", recordSimulationScreen);
            screenshotDir = properties.getDirectoryPath("screenshot_dir", screenshotDir).replaceFirst("[/\\\\]+$", "");
            clearScreenshotDir = properties.getBoolean("clear_screenshot_dir", clearScreenshotDir);
            if (clearScreenshotDir && ! properties.isDefined("screenshot_dir")) {
                throw new Exception("Property error - clear_screenshot_dir を有効にするためには screenshot_dir の設定が必要です。");
            }
            if (recordSimulationScreen && ! clearScreenshotDir && new File(screenshotDir).list(imageFileFilter).length > 0) {
                throw new Exception("Property error - スクリーンショットディレクトリに画像ファイルが残っています: screenshot_dir: " + screenshotDir);
            }
            screenshotImageType =
                properties.getStringInPattern("screenshot_image_type",
                                              screenshotImageType,
                                              IMAGE_TYPES);
            hideLinks = properties.getBoolean("hide_links", hideLinks);
            densityMode = properties.getBoolean("density_mode", densityMode);
            changeAgentColorDependingOnSpeed =
                properties.getBoolean("change_agent_color_depending_on_speed", changeAgentColorDependingOnSpeed);
            drawingAgentByTriageAndSpeedOrder = properties.getBoolean("drawing_agent_by_triage_and_speed_order", drawingAgentByTriageAndSpeedOrder);
            String show_status =
                properties.getStringInPattern("show_status", "none",
                                              SHOW_STATUS_VALUES);
            if (show_status.equals("none")) {
                showStatus = false;
            } else {
                showStatus = true;
                showStatusPosition = show_status;
            }
            showLogo = properties.getBoolean("show_logo", showLogo);
            show3dPolygon = properties.getBoolean("show_3D_polygon", show3dPolygon);
            simulationWindowOpen = properties.getBoolean("simulation_window_open", simulationWindowOpen);
            autoSimulationStart = properties.getBoolean("auto_simulation_start", autoSimulationStart);
            exitWithSimulationFinished = properties.getBoolean("exit_with_simulation_finished",
                    exitWithSimulationFinished);
            simulationPanelWidth = properties.getInteger("simulation_panel_width", simulationPanelWidth);
            simulationPanelHeight = properties.getInteger("simulation_panel_height", simulationPanelHeight);
        } catch(Exception e) {
            Itk.logFatal("Property file error", e.getMessage()) ;
            Itk.quitByError() ;
        }
    }

    /**
     * agent appearance file を読み込む
     */
    public ArrayList<HashMap> loadAgentAppearance() {
        try {
            JSON json = new JSON(JSON.Mode.TRADITIONAL);
            ArrayList<HashMap> appearances = new ArrayList();
            if (agentAppearanceFile != null) {
                InputStream is = new FileInputStream(agentAppearanceFile);
                appearances.addAll((ArrayList<HashMap>)json.parse(is));
            }
            InputStream is = getClass().getResourceAsStream("/agent_appearance.json");
            appearances.addAll((ArrayList<HashMap>)json.parse(is));
            return appearances;
        } catch (Exception e) {
            Itk.quitWithStackTrace(e) ;
        }
        return null;
    }

    /**
     * エージェント表示の準備
     */
    protected abstract void setupAgentView();

    /**
     * link appearance file を読み込む
     */
    public ArrayList<HashMap> loadLinkAppearance() {
        try {
            JSON json = new JSON(JSON.Mode.TRADITIONAL);
            ArrayList<HashMap> appearances = new ArrayList();
            if (linkAppearanceFile != null) {
                InputStream is = new FileInputStream(linkAppearanceFile);
                Object object = json.parse(is);
                if (object instanceof HashMap) {
                    // 旧書式の場合
                    appearances.addAll(convertLinkAppearanceForm((Map<String, Object>)object));
                } else {
                    appearances.addAll((ArrayList<HashMap>)object);
                }
            }
            InputStream is = getClass().getResourceAsStream("/link_appearance.json");
            appearances.addAll((ArrayList<HashMap>)json.parse(is));
            return appearances;
        } catch (Exception e) {
            Itk.quitWithStackTrace(e) ;
        }
        return null;
    }

    /**
     * 旧書式の link appearance オブジェクトを新書式に変換する.
     *
     * ※ width_ratio でリンク幅の倍率を変える機能は廃止した。
     */
    public ArrayList<HashMap> convertLinkAppearanceForm(Map<String, Object> object) throws Exception {
        ArrayList<HashMap> appearances = new ArrayList();
        JsonicHashMapGetter jsonMap = new JsonicHashMapGetter();
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String tag = entry.getKey();
            BigDecimal widthRatio = new BigDecimal(1.0);
            boolean widthFixed = false;
            String colorName = null;
            BigDecimal transparency = new BigDecimal(0.75);

            jsonMap.setParameters((HashMap)entry.getValue());
            widthRatio = jsonMap.getBigDecimalParameter("width", widthRatio);
            widthRatio = jsonMap.getBigDecimalParameter("width_ratio", widthRatio);
            widthFixed = jsonMap.getBooleanParameter("width_fixed", widthFixed);
            colorName = jsonMap.getStringParameter("color", colorName);
            transparency = jsonMap.getBigDecimalParameter("transparency", transparency);

            HashMap<String, Object> appearance = new HashMap();
            appearance.put("tag", tag);
            for (int dimension = 2; dimension <= 3; dimension++) {
                HashMap<String, Object> view = new HashMap();
                HashMap<String, Object> parameters = new HashMap();
                if (widthFixed) {
                    view.put("className", "FixedWidth" + dimension + "D");
                    parameters.put("width", widthRatio);
                } else {
                    view.put("className", "ActualWidth" + dimension + "D");
                }
                if (colorName != null) {
                    parameters.put("color", colorName);
                }
                parameters.put("transparency", transparency);
                parameters.put("method", "filling");
                view.put("parameters", parameters);
                appearance.put("" + dimension + "D_View", view);
            }
            appearances.add(appearance);
        }
        return appearances;
    }

    /**
     * node appearance file を読み込む
     */
    public ArrayList<HashMap> loadNodeAppearance() {
        try {
            JSON json = new JSON(JSON.Mode.TRADITIONAL);
            ArrayList<HashMap> appearances = new ArrayList();
            if (nodeAppearanceFile != null) {
                InputStream is = new FileInputStream(nodeAppearanceFile);
                Object object = json.parse(is);
                if (object instanceof HashMap) {
                    // 旧書式の場合
                    appearances.addAll(convertNodeAppearanceForm((Map<String, Object>)object));
                } else {
                    appearances.addAll((ArrayList<HashMap>)object);
                }
            }
            InputStream is = getClass().getResourceAsStream("/node_appearance.json");
            appearances.addAll((ArrayList<HashMap>)json.parse(is));
            return appearances;
        } catch (Exception e) {
            Itk.quitWithStackTrace(e) ;
        }
        return null;
    }

    /**
     * 旧書式の node appearance オブジェクトを新書式に変換する.
     */
    public ArrayList<HashMap> convertNodeAppearanceForm(Map<String, Object> object) throws Exception {
        ArrayList<HashMap> appearances = new ArrayList();
        JsonicHashMapGetter jsonMap = new JsonicHashMapGetter();
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String tag = entry.getKey();
            BigDecimal diameter = new BigDecimal(1.5);
            String colorName = null;
            BigDecimal transparency = new BigDecimal(0.75);

            jsonMap.setParameters((HashMap)entry.getValue());
            diameter = jsonMap.getBigDecimalParameter("diameter", diameter);
            colorName = jsonMap.getStringParameter("color", colorName);
            transparency = jsonMap.getBigDecimalParameter("transparency", transparency);

            HashMap<String, Object> appearance = new HashMap();
            appearance.put("tag", tag);
            for (int dimension = 2; dimension <= 3; dimension++) {
                HashMap<String, Object> view = new HashMap();
                HashMap<String, Object> parameters = new HashMap();
                view.put("className", "RoundNode" + dimension + "D");
                parameters.put("diameter", diameter);
                if (colorName != null) {
                    parameters.put("color", colorName);
                }
                parameters.put("transparency", transparency);
                parameters.put("method", "filling");
                view.put("parameters", parameters);
                appearance.put("" + dimension + "D_View", view);
            }
            appearances.add(appearance);
        }
        return appearances;
    }

    /**
     * 地理院タイル画像の準備
     */
    protected void loadGsiTiles() {
        try {
            String tileName = properties.getString("gsi_tile_name", GsiTile.DATA_ID_PALE);
            gsiTileZoom = properties.getInteger("gsi_tile_zoom", gsiTileZoom);
            MapPartGroup root = (MapPartGroup)map.getRoot();
            int zone = properties.getInteger("zone", root.getZone());
            if (zone != 0) {
                mapTiles = GsiTile.loadGsiTiles(map, tileName, gsiTileZoom, zone);
            }
        } catch(Exception e) {
            Itk.logError("loadGsiTiles error", e.getMessage());
        }
    }

    /**
     * 海岸線の準備
     */
    protected void loadCoastlines() {
        if (mapTiles == null) {
            return;
        }
        try {
            ArrayList<String> fileNames = new ArrayList();
            for (String fileName : properties.getString("coastline_file", "").split(",")) {
                if (! fileName.isEmpty()) {
                    fileNames.add(properties.furnishPropertiesDirPath(fileName, true, false));
                }
            }
            if (fileNames.isEmpty()) {
                return;
            }

            Rectangle2D boundary = new Rectangle2D.Double();
            for (GsiTile mapTile : mapTiles) {
                if (boundary.isEmpty()) {
                    boundary.setFrameFromDiagonal(mapTile.getPoint(), mapTile.getLowerRightPoint());
                } else {
                    boundary.add(mapTile.getPoint());
                    boundary.add(mapTile.getLowerRightPoint());
                }
            }
            Itk.logInfo("Region of coastline", String.format("(%f, %f)-(%f, %f)", boundary.getX(), boundary.getY(), boundary.getMaxX(), boundary.getMaxY()));

            String cachePath = GsiTile.makeCachePath();
            GsiTile mapTile = mapTiles.get(0);
            double elevation = GsiAccessor.getElevation(cachePath, mapTile.getTileNumberX(), mapTile.getTileNumberY(), gsiTileZoom);

            coastline = new Coastline();
            for (String fileName : fileNames) {
                coastline.read(fileName, boundary);
            }
            coastline.polygonization(boundary, elevation == 0.0);
        } catch(Exception e) {
            Itk.logError("loadCoastlines error", e.getMessage());
        }
    }

    /**
     * 海岸線を取得する
     */
    public Coastline getCoastline() {
        return coastline;
    }

    //// properties 変数用のアクセサ ////

    public void setRecordSimulationScreen(boolean b) {
        recordSimulationScreen = b;
    }

    public boolean isRecordSimulationScreen() {
        return recordSimulationScreen;
    }
}
