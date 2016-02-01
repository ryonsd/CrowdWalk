// -*- mode: java; indent-tabs-mode: nil -*-
package nodagumi.ananPJ.navigation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.ArrayList;

import nodagumi.ananPJ.NetworkMap.NetworkMap;
import nodagumi.ananPJ.NetworkMap.Link.MapLink;
import nodagumi.ananPJ.NetworkMap.Node.MapNode;
import nodagumi.ananPJ.NetworkMap.Node.MapNodeTable;
import nodagumi.ananPJ.navigation.NavigationHint;
import nodagumi.ananPJ.navigation.PathChooser;
import nodagumi.ananPJ.navigation.Formula.*;

import nodagumi.Itk.Itk;
import nodagumi.Itk.Term;

//======================================================================
/**
 * Dijkstra法で最短経路探索。
 */
public class Dijkstra {
    //============================================================
    /**
     * 探索結果の格納用クラス。
     */
    static public class Result
        extends LinkedHashMap<MapNode, NavigationHint> {}

    //============================================================
    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * 標準の PathChooser。
     * これ以外の Chooser を使う機会があるのか、不明。
     */
    public static PathChooser DefaultPathChooser =
        new PathChooser();

    //============================================================
    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Chooser テーブル。
     */
    static public HashMap<String, PathChooser> pathChooserTable =
        new HashMap<String, PathChooser>() ;

    //============================================================
    //------------------------------------------------------------
    /**
     * 主観的距離計算機を取得。
     */
    static public PathChooser getPathChooser(Term subjectiveMode) {
        return getPathChooser((subjectiveMode == null ?
                               (String)null :
                               subjectiveMode.getString())) ;
    }
    /** */
    static public PathChooser getPathChooser(String subjectiveMode) {
        if(subjectiveMode == null) {
            return DefaultPathChooser ;
        } else {
            return pathChooserTable.get(subjectiveMode) ;
        }
    }
        
    /**
     * 主観的距離計算機を新規登録。
     */
    static public PathChooser newPathChooser(Term subjectiveMode,
                                             NetworkMap networkMap) {
        PathChooser chooser =
            new PathChooser(subjectiveMode,
                            networkMap.getSubjectiveMapRule(subjectiveMode)) ;
        pathChooserTable.put(subjectiveMode.getString(), chooser) ;

        return chooser ;
    }
    /** */
    static public PathChooser newPathChooser(String subjectiveMode,
                                             NetworkMap networkMap) {
        return newPathChooser((subjectiveMode == null ?
                               (Term)null :
                               new Term(subjectiveMode)),
                              networkMap) ;
    }

    //============================================================
    //------------------------------------------------------------
    /**
     * 探索メインルーチン。
     * 上記の calc は、無駄が多いと思われる。何度も同じノードを展開している。
     * 下記はそれを避ける。
     * @param subjectiveMode 主観的モード
     * @param goalTag 探索するゴールのタグ。
     * @param subgoals 目標とするゴール集合。
     * @param networkMap 地図。
     * @return 探索結果。Resule class のインスタンス。
     */
    static public Result calc(Term subjectiveMode,
                              String goalTag,
                              MapNodeTable subgoals,
                              NetworkMap networkMap) {
        //Itk.timerStart("calc") ;

        PathChooser chooser = Dijkstra.DefaultPathChooser ;
        if(subjectiveMode != null) {
            chooser = getPathChooser(subjectiveMode) ;
            if(chooser == null) {
                chooser = newPathChooser(subjectiveMode, networkMap) ;
            }
        }
            
        Result frontier = new Result();
        ArrayList<MapNode> closedList = new ArrayList<MapNode>() ;
        Result result = new Result() ;

        // ゴールノードは、initial cost で。
        for (MapNode subgoal : subgoals) {
            double cost = chooser.calcGoalNodeCost(subgoal);
            NavigationHint hint =
                new NavigationHint(subjectiveMode,
                                   goalTag, subgoal, null, null, cost) ;
            frontier.put(subgoal, hint) ;
            result.put(subgoal, hint) ;
        }

        // 探索ループ。
        while (true) {
            NavigationHint bestHint =
                new NavigationHint(null, null,
                                   null, null, null, Double.POSITIVE_INFINITY) ;
            closedList.clear() ;
            for (MapNode frontierNode : frontier.keySet()) {
                int countPerNode = 0 ;
                for (MapLink preLink :
                         frontierNode.getValidReverseLinkTable()) {
                    MapNode preNode = preLink.getOther(frontierNode);
                    if (result.containsKey(preNode)) continue;
                    countPerNode ++ ;
                    double dist =
                        result.get(frontierNode).distance
                        + chooser.calcLinkCost(preLink, preNode) ;
                    if (dist < bestHint.distance) {
                        bestHint.set(subjectiveMode, goalTag,
                                     preNode, preLink, frontierNode, dist) ;
                    }
                }
                // すべてのリンク先がすでに探査済みであれば、そのノードは close
                if(countPerNode == 0) {
                    closedList.add(frontierNode) ;
                }
            }
            // close されたノードを frontiner から除去。
            for(MapNode node : closedList) {
                frontier.remove(node) ;
            }
            // 新たに付け加えるものがなければ、探査終わり。
            if (bestHint.fromNode == null) {
                break;
            }
            // 新しノードを frontier に追加。
            frontier.put(bestHint.fromNode, bestHint) ;
            result.put(bestHint.fromNode, bestHint) ;
        }

        //Itk.logWarn("Dijkstra.calc() for ", subjectiveMode, ":", goalTag) ;
        //Itk.timerShowLap("calc") ;

        return result ;
    }

}
//;;; Local Variables:
//;;; mode:java
//;;; tab-width:4
//;;; End:
