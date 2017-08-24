// -*- mode: java; indent-tabs-mode: nil -*-
package nodagumi.ananPJ;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

// import nodagumi.ananPJ.Editor.MapEditor;
import nodagumi.ananPJ.Editor.MapEditorInterface;
import nodagumi.ananPJ.misc.SimTime;

import nodagumi.Itk.Itk;

/**
 * CrowdWalk の起動を司る
 */
public class CrowdWalkLauncher {
    public static String optionsFormat = "[-c] [-e] [-g|g2] [-h] [-l <LEVEL>] [-t <FILE>] [-f <FALLBACK>]* [-v]"; // これはメソッドによる取得も可能
    public static String commandLineSyntax = String.format("crowdwalk %s [properties-file]", optionsFormat);
    public static String SETTINGS_FILE_NAME = "GuiSimulationLauncher.ini";

    /**
     * GUI 設定情報のパス設定項目(マップファイル以外)
     */
    private static String[] SETTING_PATH_ITEMS = {"generation", "scenario", "fallback", "obstructer"};

    /**
     * GUI の設定情報
     */
    private static Settings settings = null;

    /**
     * 2D GUI シミュレータを使用する
     */
    public static boolean use2dSimulator = false;

    /**
     * コマンドラインオプションの定義
     */
    public static void defineOptions(Options options) {
        options.addOption("c", "cui", false, "CUI モードでシミュレーションを開始する\nproperties-file の指定が必須");
        options.addOption("e", "old-editor", false, "旧バージョンのエディタを起動する");
        options.addOption("g", "gui", false, "マップエディタウィンドウを開かずに GUI モードでシミュレーションを開始する\nproperties-file の指定が必須");
        options.addOption("2", "use-2d-simulator", false, "2D GUI シミュレータを使用する");
        options.addOption("h", "help", false, "この使い方を表示して終了する");
        options.addOption(OptionBuilder.withLongOpt("log-level")
            .withDescription("ログレベルを指定する\nLEVEL = Trace | Debug | Info | Warn | Error | Fatal")
            .hasArg().withArgName("LEVEL").create("l"));
        options.addOption(OptionBuilder.withLongOpt("tick")
            .withDescription("tick 情報を FILE に出力する\nCUI モード時のみ有効")
            .hasArg().withArgName("FILE").create("t"));
        options.addOption(OptionBuilder.withLongOpt("fallback")
                          .withDescription("fallback の先頭に追加する")
                          .hasArg().withArgName("JSON").create("f"));
        options.addOption("v", "version", false, "バージョン情報を表示して終了する");
    }

    /**
     * コマンドラインオプションを解析して指定された処理を実行する
     * @param args : main メソッドの args 引数
     */
    public static void parseCommandLine(String[] args, Options options) {
        String propertiesFilePath = null;
        ArrayList<String> fallbackStringList = new ArrayList<String>() ;

        try {
            CommandLine commandLine = new PosixParser().parse(options, args);
            // ヘルプ表示オプションもしくはコマンドライン引数エラー
            if (commandLine.hasOption("help") || commandLine.getArgs().length > 1) {
                printHelp(options);
                System.exit(0);
            }
            // バージョン表示
            if (commandLine.hasOption("version")) {
                System.err.println("CrowdWalk Version " + GuiSimulationEditorLauncher.getVersion());
                System.exit(0);
            }

            // fallback への追加
            if (commandLine.hasOption("fallback")) {
                for(String fallback : commandLine.getOptionValues("fallback")) {
                    fallbackStringList.add(fallback) ;
                }
            }

            // プロパティファイルの指定あり
            if (commandLine.getArgs().length == 1) {
                propertiesFilePath = commandLine.getArgs()[0];
            }

            // ログレベルの指定
            if (commandLine.hasOption("log-level")) {
                setLogLevel(commandLine.getOptionValue("log-level"));
            }

            // 2D GUI シミュレータを使用する
            use2dSimulator = commandLine.hasOption("use-2d-simulator");

            // CUI モードで実行
            if (commandLine.hasOption("cui")) {
                if (propertiesFilePath == null) {
                    printHelp(options);
                    System.exit(1);
                }
                CuiSimulationLauncher launcher
                    = launchCuiSimulator(propertiesFilePath, fallbackStringList);
                // tick 情報の出力
                if (commandLine.hasOption("tick")) {
                    String tickFilePath = commandLine.getOptionValue("tick");
                    saveTick(tickFilePath, launcher.simulator.currentTime);
                }
            }
            // GUI モードで実行
            else if (commandLine.hasOption("gui")) {
                if (propertiesFilePath == null) {
                    printHelp(options);
                    System.exit(1);
                }
                launchGuiSimulator(propertiesFilePath, fallbackStringList);
            }
            // マップエディタの実行
            else if (commandLine.hasOption("old-editor") || ! isUsable3dSimulator()) {
                launchGuiSimulationEditorLauncher(propertiesFilePath, fallbackStringList);
            } else {
                launchMapEditor(propertiesFilePath, fallbackStringList);
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            printHelp(options);
            System.exit(1);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * ログレベルを設定する
     */
    public static void setLogLevel(String logLevelName) throws Exception {
        for (Map.Entry<Itk.LogLevel, String> entry : Itk.LogTag.entrySet()) {
            if (entry.getValue().equals(Itk.LogTagPrefix + logLevelName)) {
                Itk.logLevel = entry.getKey();
                return;
            }
        }
        throw new Exception("Option error - ログレベル名が間違っています: "
                            + logLevelName);
    }

    /**
     * CUI シミュレータを実行する
     */
    public static CuiSimulationLauncher
        launchCuiSimulator(String propertiesFilePath,
                           ArrayList<String> commandLineFallbacks)
    {
        CuiSimulationLauncher launcher =
            new CuiSimulationLauncher(propertiesFilePath,
                                      commandLineFallbacks);
        launcher.initialize();
        launcher.start();
        return launcher;
    }

    /**
     * GUI シミュレータを実行する
     */
    public static GuiSimulationLauncher
        launchGuiSimulator(String propertiesFilePath,
                           ArrayList<String> commandLineFallbacks) throws Exception
    {
        settings = Settings.load(SETTINGS_FILE_NAME);
        GuiSimulationLauncher launcher;
        if (use2dSimulator) {
            launcher = GuiSimulationLauncher.createInstance("GuiSimulationLauncher2D");
        } else {
            if (! GuiSimulationLauncher.isUsableClass("GuiSimulationLauncher3D")) {
                throw new Exception("このビルドには 3D シミュレータ機能が組み込まれていません。");
            }
            String vmName = System.getProperty("java.vm.name");
            if (vmName.toLowerCase().indexOf("openjdk") != -1) {
                throw new Exception("Java Virtual Machine: " + vmName
                        + "\n\t3D シミュレータは Oracle 版の Java でないと実行できません。");
            }
            launcher = GuiSimulationLauncher.createInstance("GuiSimulationLauncher3D");
        }
        launcher.init(propertiesFilePath, settings, commandLineFallbacks);
        launcher.simulate();
        return launcher;
    }

    /**
     * マップエディタを実行する
     */
    public static void launchMapEditor(String propertiesFilePath,
            ArrayList<String> commandLineFallbacks) throws Exception {
        settings = Settings.load(SETTINGS_FILE_NAME);
        // MapEditor editor = new MapEditor(settings);

        // OpenJDK に JavaFX が同梱されるまでの一時的な対応
        MapEditorInterface editor = null;
        try {
            Class<?> klass = Class.forName("nodagumi.ananPJ.Editor.MapEditor");
            editor = (MapEditorInterface)klass.newInstance();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        editor.setSettings(settings);

        if (propertiesFilePath != null) {
            editor.setPropertiesFromFile(propertiesFilePath, commandLineFallbacks);
            if (! editor.loadNetworkMap()) {
                return;
            }
        } else {
            editor.initProperties(commandLineFallbacks);
            editor.initNetworkMap();
        }
        editor.show();
    }

    /**
     * マップエディタを実行する
     */
    public static GuiSimulationEditorLauncher
        launchGuiSimulationEditorLauncher(String propertiesFilePath,
                                          ArrayList<String> commandLineFallbacks)
        throws Exception
    {
        Random random = new Random();
        settings = Settings.load(SETTINGS_FILE_NAME);
        GuiSimulationEditorLauncher mapEditor
            = new GuiSimulationEditorLauncher(random, settings);
        if (propertiesFilePath != null) {
            mapEditor.setPropertiesFromFile(propertiesFilePath,
                                            commandLineFallbacks);
            mapEditor.setPropertiesForDisplay();
        } else {
            mapEditor.initProperties(commandLineFallbacks);
        }
        mapEditor.updateAll();
        mapEditor.setVisible(true);
        return mapEditor;
    }

    /**
     * tick 情報をファイルに出力する
     */
    public static void saveTick(String filePath, SimTime currentTime) throws IOException {
        PrintWriter writer = new PrintWriter(filePath);
        if (filePath.toLowerCase().endsWith(".json")) {
            writer.write("{\"tick\" : " + currentTime.getRelativeTime() + "}");
        } else {
            writer.println(currentTime.getRelativeTime());
        }
        writer.close();
    }

    /**
     * コマンドラインヘルプを標準出力に表示する
     */
    public static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        int usageWidth = 7 + commandLineSyntax.length();    // "usege: " + commandLineSyntax
        formatter.setWidth(Math.max(usageWidth, 80));       // 行の折りたたみ最小幅は80桁
        formatter.printHelp(commandLineSyntax, options);
    }

    /**
     * 3D シミュレータが使用可能か?
     */
    public static boolean isUsable3dSimulator() {
        String vmName = System.getProperty("java.vm.name");
        return GuiSimulationLauncher.isUsableClass("GuiSimulationLauncher3D")
            && vmName.toLowerCase().indexOf("openjdk") == -1;
    }

    public static void main(String[] args) {
        Options options = new Options();
        defineOptions(options);
        parseCommandLine(args, options);

        // Settings の保存
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                if (settings != null) {
                    // ディレクトリがマップファイルと異なる場合は危険なので保存しない
                    String mapDir = settings.get("mapDir", "");
                    for (String item : SETTING_PATH_ITEMS) {
                        if (! settings.get(item + "Dir", "").equals(mapDir)) {
                            settings.put(item + "Dir", "");
                            settings.put(item + "File", "");
                        }
                    }

                    Settings.save();
                }
            }
        });
    }
}
