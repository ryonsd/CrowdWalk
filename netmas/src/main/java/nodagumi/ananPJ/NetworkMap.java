// -*- mode: java; indent-tabs-mode: nil -*-
package nodagumi.ananPJ;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.ClassNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.vecmath.Vector3d;

import net.arnx.jsonic.JSON ;

import nodagumi.ananPJ.NetworkMapBase;
import nodagumi.ananPJ.Agents.AgentBase;
import nodagumi.ananPJ.Agents.WalkAgent;
import nodagumi.ananPJ.Editor.EditorFrame;
import nodagumi.ananPJ.NetworkParts.OBNode;
import nodagumi.ananPJ.NetworkParts.Link.*;
import nodagumi.ananPJ.NetworkParts.Node.*;
import nodagumi.ananPJ.NetworkParts.MapPartGroup;

import nodagumi.Itk.*;

//======================================================================
/**
 * シミュレーションで用いるデータをまとめて保持するクラス。
 */
public class NetworkMap extends NetworkMapBase {
    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
	 * ???
	 */
    private ArrayList<EditorFrame> frames = new ArrayList<EditorFrame>();

    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
	 * ???
	 */
    private String filename = null;

    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
	 * 汚染地域データのファイル
	 */
    private String pollutionFile = null;

    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
	 * エージェント生成ルールのファイル
	 */
    private String generationFile = null;

    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
	 * シナリオファイル
	 */
    private String scenarioFile = null;

	//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	/**
	 * fallback file
	 */
	private String fallbackFile = null ;

	//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	/**
	 * fallback parameter slot name
	 */
	static public final String FallbackSlot = "_fallback" ;

	//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	/**
	 * fallback parameter resource name
	 */
	static public final String FallbackResource = "/fallbackParameters.json" ;

	//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	/**
	 * fallback parameter
	 */
	public Term fallbackParameters = null ;

    //------------------------------------------------------------
    /**
     * constructor
     */
    public NetworkMap() {
		super() ;
    }

	//------------------------------------------------------------
	/**
	 * Editor Frame
	 */
    public boolean existNodeEditorFrame(MapPartGroup _obiNode){
        for (EditorFrame frame : getFrames()) {
            if (_obiNode.equals(frame)) return true;
        }
        return false;
    }

	/**
	 * Editor Frame
	 */
    public EditorFrame openEditorFrame(NetworkMapEditor editor, MapPartGroup obinode) {
        EditorFrame frame = new EditorFrame(editor, obinode);

        obinode.setUserObject(frame);

        getFrames().add(frame);
        frame.setVisible(true);

        return frame;
    }

	/**
	 * Editor Frame
	 */
    public void removeEditorFrame(MapPartGroup _obinode){
        getFrames().remove(_obinode.getUserObject());
        _obinode.setUserObject(null);
    }
    
	//------------------------------------------------------------
	/**
	 * ファイル名（？？？）
	 */
    public String getFileName() {
        return filename;
    }

	/**
	 * ファイル名（？？？）
	 */
    public void setFileName(String file_name) {
        filename = file_name;
    }

	//------------------------------------------------------------
	/**
	 * pollutionファイル。
	 */
    public String getPollutionFile() {
        if (pollutionFile == null)
            return null;
        File pollution_file = new File(pollutionFile);
        return pollution_file.getPath();
    }

	/**
	 * pollutionファイル。
	 */
    public void setPollutionFile(String s) {
        pollutionFile = s;
    }

	//------------------------------------------------------------
	/**
	 * エージェント生成ファイル。
	 */
    public String getGenerationFile() {
        if (generationFile == null)
            return null;
        File generation_file = new File(generationFile);
        return generation_file.getPath();
    }

	/**
	 * エージェント生成ファイル。
	 */
    public void setGenerationFile(String s) {
        generationFile = s;
    }

	//------------------------------------------------------------
	/**
	 * シナリオファイル。
	 */
	public String getScenarioFile() {
        if (scenarioFile == null)
            return null;
		File _scenarioFile = new File(scenarioFile) ;
        return _scenarioFile.getPath();
    }

	/**
	 * シナリオファイル。
	 */
    public void setScenarioFile(String s) {
        scenarioFile = s;
    }

	//------------------------------------------------------------
	/**
	 * fallback （デフォルトセッティング）のファイル取得
	 */
	public String getFallbackFile() {
		return fallbackFile ;
	}

	/**
	 * fallback （デフォルトセッティング）のファイルセット
	 */
	public void setFallbackFile(String s) {
		fallbackFile = s ;
	}

	//------------------------------------------------------------
	/**
	 * fallback （デフォルトセッティング）の読み込み
	 * @param scanResourceP : resource の fallback も読み込むかどうか
	 */
	public void scanFallbackFile(boolean scanResourceP) {
		if(fallbackFile != null) {
			try {
				BufferedReader buffer =
					new BufferedReader(new FileReader(fallbackFile)) ;
				fallbackParameters = Term.newByScannedJson(JSON.decode(buffer),
														   true) ;
				Itk.logInfo("Load Fallback File", fallbackFile) ;
			} catch (Exception ex) {
				ex.printStackTrace() ;
				Itk.logError("Can not scan a fallback parameter file:",
							 fallbackFile) ;
				Itk.logError_("Exception",ex) ;
			}
		} else {
			fallbackParameters = new Term() ;
		}

		if(scanResourceP) {
			try {
				InputStream istrm =
					getClass().getResourceAsStream(FallbackResource) ;
				Term finalFallback =
					Term.newByScannedJson(JSON.decode(istrm),true) ;
				fallbackParameters.setArg(FallbackSlot, finalFallback) ;
			} catch (Exception ex) {
				ex.printStackTrace() ;
				Itk.logError("Can not scan a fallback resource file.") ;
				Itk.logError_("Exception",ex) ;
			}
		}
	}

    //------------------------------------------------------------
    /**
     * フレームセット
     */
    public void setFrames(ArrayList<EditorFrame> frames) {
        this.frames = frames;
    }
    /**
     * フレーム取得
     */
    public ArrayList<EditorFrame> getFrames() {
        return frames;
    }
    
}
//;;; Local Variables:
//;;; mode:java
//;;; tab-width:4
//;;; End:
