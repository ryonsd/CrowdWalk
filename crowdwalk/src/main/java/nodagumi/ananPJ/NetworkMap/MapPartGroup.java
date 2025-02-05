// -*- mode: java; indent-tabs-mode: nil -*-
package nodagumi.ananPJ.NetworkMap;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Enumeration;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import nodagumi.ananPJ.Agents.AgentBase;
import nodagumi.ananPJ.NetworkMap.Link.*;
import nodagumi.ananPJ.NetworkMap.Node.*;
import nodagumi.ananPJ.NetworkMap.Area.MapArea;
import nodagumi.ananPJ.NetworkMap.Polygon.MapPolygon;

import nodagumi.Itk.*;

public class MapPartGroup extends OBNode {
    /* Position of the MapPart, relative to the parent node.
     */
    private Point2D pNorthWest = null;
    private Point2D pSouthEast = null;

    private double pTheta = 0.0;
    private double scale = 1.0;
    private double minHeight = -5.0;
    private double maxHeight = 5.0;
    private int verticalDivision = 1;   // tkokada added
    private int horizontalDivision = 1; // tkokada added
    private double rotation = 0.0;  // tkokada added
    private double defaultHeight = 0.0;
    private String imageFileName = null;
    private int zone = 0;
      
    /* Parameters related on how this frame will be drawn.
     * Will be used in EditorFrame.
     */ 
    public double tx = 0.0;
    public double ty = 0.0;
    public double sx = 1.0;
    public double sy = 1.0;
    public double r  = 0.0;

    /* Constructor */
    public MapPartGroup(String _ID,
            Point2D _pNorthWest,
            Point2D _pSouthEast,
            double _pTheta,
            String _imageFileName){
      super(_ID);
      ID = _ID;
      pNorthWest = _pNorthWest;
      pSouthEast = _pSouthEast;
      pTheta = _pTheta;
      setImageFileName(_imageFileName);
    }
      
    public MapPartGroup(String _ID,
            Point2D _pNorthWest,
            Point2D _pSouthEast,
            double _pTheta) {
        this(_ID, _pNorthWest, _pSouthEast, _pTheta, null);
    }
    
    public MapPartGroup(String _ID) {
        this(_ID,
                new Point2D.Double(5.0, 45.0), /* NW*/
                new Point2D.Double(600.0, 600.0), /* SE */
                0.0 /* _pTheta */
        );
    }

    private void addAttributesToDom(Element element) {
        element.setAttribute("id", ID);
        element.setAttribute("pNorthWestX", "" + pNorthWest.getX());
        element.setAttribute("pNorthWestY", "" + pNorthWest.getY());
        element.setAttribute("pSouthEastX", "" + pSouthEast.getX());
        element.setAttribute("pSouthEastY", "" + pSouthEast.getY());
        element.setAttribute("pTheta", "" + pTheta);

        element.setAttribute("tx", "" + tx);
        element.setAttribute("ty", "" + ty);
        element.setAttribute("sx", "" + sx);
        element.setAttribute("sy", "" + sy);
        element.setAttribute("r", "" + r);
        
        element.setAttribute("scale", "" + getScale());
        element.setAttribute("minHeight", "" + getMinHeight());
        element.setAttribute("maxHeight", "" + getMaxHeight());
        element.setAttribute("defaultHeight", "" + getDefaultHeight());
        element.setAttribute("imageFileName", getImageFileName());
        element.setAttribute("zone", "" + getZone());
    }
    
    @Override
    protected void getAttributesFromDom(Element element) {
        super.getAttributesFromDom(element);
        ID = element.getAttribute("id");
        double x = Double.parseDouble(element.getAttribute("pNorthWestX"));
        double y = Double.parseDouble(element.getAttribute("pNorthWestY"));
        pNorthWest = new Point2D.Double(x, y);
        
        x = Double.parseDouble(element.getAttribute("pSouthEastX"));
        y = Double.parseDouble(element.getAttribute("pSouthEastY"));
        pSouthEast = new Point2D.Double(x, y);
        pTheta = Double.parseDouble(element.getAttribute("pTheta"));

        tx = Double.parseDouble(element.getAttribute("tx"));
        ty = Double.parseDouble(element.getAttribute("ty"));
        sx = Double.parseDouble(element.getAttribute("sx"));
        sy = Double.parseDouble(element.getAttribute("sy"));
        r = Double.parseDouble(element.getAttribute("r"));
        if (sx != sy) {
			Itk.logWarn("MapPartGroup", element.getAttribute("tag"),"group.",
						"Aspect Ratio mismatch.",
						"sx=", sx, "sy=", sy, ".") ;
        }

        setScale(Double.parseDouble(element.getAttribute("scale")));
        setMinHeight(Double.parseDouble(element.getAttribute("minHeight")));
        setMaxHeight(Double.parseDouble(element.getAttribute("maxHeight")));
        setDefaultHeight(Double.parseDouble(element.getAttribute("defaultHeight")));
        setImageFileName(element.getAttribute("imageFileName"));

        String _zone = element.getAttribute("zone");
        if (_zone != null && ! _zone.isEmpty()) {
            setZone(Integer.parseInt(_zone));
        }
    }

    @Override
    public NType getNodeType() {
        return NType.GROUP;
    }

    public static String getNodeTypeString() {
        return "Group";
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Element toDom(Document dom, String tagname) {
        Element element = super.toDom(dom, getNodeTypeString());

        addAttributesToDom(element);
        Enumeration e = children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child != null) {
                element.appendChild(child.toDom(dom, null));
            }
        }
        return element;
    }

    public static OBNode fromDom(Element element) {
        MapPartGroup group = new MapPartGroup(null);
        group.getAttributesFromDom(element);
        NodeList elm_children = element.getChildNodes();
        for (int i = 0; i < elm_children.getLength(); ++i) {
            if (elm_children.item(i) instanceof Element) {
                Element node = (Element)elm_children.item(i);
                OBNode child = OBNode.fromDom(node);
                if (child != null) {
                    group.add(child);
                }
            }
        }
        return group;
    }

    /* getters and setters */
    public void setDefaultHeight(double defaultHeight) {
        this.defaultHeight = defaultHeight;
    }

    public double getDefaultHeight() {
        return defaultHeight;
    }

    public void setMinHeight(double minHeight) {
        this.minHeight = minHeight;
    }

    public double getMinHeight() {
        return minHeight;
    }

    public void setMaxHeight(double maxHeight) {
        this.maxHeight = maxHeight;
    }

    public double getMaxHeight() {
        return maxHeight;
    }
    // tkokada added
    public void setVerticalDivision(int verticalDivision) {
        this.verticalDivision = verticalDivision;
    }
    // tkokada added
    public int getVerticalDivision() {
        return verticalDivision;
    }
    // tkokada added
    public void setHorizontalDivision(int horizontalDivision) {
        this.horizontalDivision = horizontalDivision;
    }
    // tkokada added
    public int getHorizontalDivision() {
        return horizontalDivision;
    }
    // tkokada added
    public void setRotation(double rotation) {
        this.rotation = rotation;
    }
    // tkokada added
    public double getRotation() {
        return rotation;
    }
    
    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public String getImageFileName() {
        return imageFileName;
    }
    
    public void setWest(double west) {
        pNorthWest = new Point2D.Double(west, pNorthWest.getY());
    }
    public double getWest() {
        return pNorthWest.getX();
    }

    public void setNorth(double north) {
        pNorthWest = new Point2D.Double(pNorthWest.getX(), north);
    }
    public double getNorth() {
        return pNorthWest.getY();
    }

    public void setEast(double east) {
        pSouthEast = new Point2D.Double(east, pSouthEast.getY());
    }
    public double getEast() {
        return pSouthEast.getX();
    }

    public void setSouth(double south) {
        pSouthEast = new Point2D.Double(pSouthEast.getX(), south);
    }
    public double getSouth() {
        return pSouthEast.getY();
    }

    public void setZone(int zone) {
        this.zone = zone;
    }
    public int getZone() {
        return zone;
    }

    @Override
    public String toString() {
        return getTagString();
    }
    
    @Override
    /* verbose output used for hints */
    public String getHintString() {
        return "(" + pNorthWest.toString() + ")-"
        + "(" + pSouthEast.toString() + "), pTheta=" + pTheta + "\n"
        + imageFileName + "\n"
        + "* (sx=" + sx + ", sy=" + sy + ") +(tx="
        + tx + ", ty=" + ty + ") r=" + r
        ;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getScale() {
        return scale;
    }
    
    @SuppressWarnings("unchecked")
    public MapNodeTable getChildNodes() {
        MapNodeTable children = new MapNodeTable();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.NODE) {
                children.add((MapNode)child);
            }
        }
        return children;
    }
    @SuppressWarnings("unchecked")
    public MapNodeTable getChildNodesAndSymlinks() {
        MapNodeTable children = new MapNodeTable();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.NODE) {
                children.add((MapNode)child);
            } else if (child.getNodeType() == OBNode.NType.SYMLINK) {
                OBNode orig = ((OBNodeSymbolicLink)child).getOriginal();
                if (orig.getNodeType() == OBNode.NType.NODE) {
                    children.add((MapNode)orig);
                }
            }
        }
        return children;
    }

    // 基準座標から象限を絞って抽出する
    public MapNodeTable getChildNodesAndSymlinks(MapNode from, int quadrant) {
        MapNodeTable children = new MapNodeTable();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.NODE) {
                if (from.include((MapNode)child, quadrant)) {
                    children.add((MapNode)child);
                }
            } else if (child.getNodeType() == OBNode.NType.SYMLINK) {
                OBNode orig = ((OBNodeSymbolicLink)child).getOriginal();
                if (orig.getNodeType() == OBNode.NType.NODE) {
                    if (from.include((MapNode)orig, quadrant)) {
                        children.add((MapNode)orig);
                    }
                }
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    public MapLinkTable getChildLinks() {
        MapLinkTable children = new MapLinkTable();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.LINK) {
                children.add((MapLink)child);
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<AgentBase> getChildAgents() {
        ArrayList<AgentBase> children = new ArrayList<AgentBase>();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.AGENT) {
                children.add((AgentBase)child);
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<MapPartGroup> getChildGroups() {
        ArrayList<MapPartGroup> children = new ArrayList<MapPartGroup>();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.GROUP) {
                children.add((MapPartGroup)child);
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<MapArea> getChildMapAreas() {
        ArrayList<MapArea> children = new ArrayList<MapArea>();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.AREA) {
                children.add((MapArea)child);
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<MapPolygon> getChildMapPolygons() {
        ArrayList<MapPolygon> children = new ArrayList<MapPolygon>();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.POLYGON) {
                children.add((MapPolygon)child);
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<OBNodeSymbolicLink> getSymbolicLinks() {
        ArrayList<OBNodeSymbolicLink> children = new ArrayList<OBNodeSymbolicLink>();
        Enumeration e = this.children();
        while (e.hasMoreElements()) {
            OBNode child = (OBNode)e.nextElement();
            if (child.getNodeType() == OBNode.NType.SYMLINK) {
                children.add((OBNodeSymbolicLink)child);
            }
        }
        return children;
    }

    /**
     * 属性パラメータの値をコピーする
     */
    public void copyAttributes(MapPartGroup group) {
        group.pNorthWest = (Point2D)pNorthWest.clone();
        group.pSouthEast = (Point2D)pSouthEast.clone();
        group.pTheta = pTheta;
        group.tx = tx;
        group.ty = ty;
        group.sx = sx;
        group.sy = sy;
        group.r = r;
        group.setScale(scale);
        group.setMinHeight(minHeight);
        group.setMaxHeight(maxHeight);
        group.setDefaultHeight(defaultHeight);
        group.setImageFileName(imageFileName);
        group.setZone(zone);

        group.getTags().clear();
        for (String tag : getTags()) {
            group.getTags().add(tag);
        }
    }
}
//;;; Local Variables:
//;;; mode:java
//;;; tab-width:4
//;;; End:
