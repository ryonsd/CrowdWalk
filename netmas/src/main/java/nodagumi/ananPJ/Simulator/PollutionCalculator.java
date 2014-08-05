package nodagumi.ananPJ.Simulator;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ClassNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

import javax.media.j3d.Appearance;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Vector3f;

import nodagumi.ananPJ.Agents.EvacuationAgent;
import nodagumi.ananPJ.NetworkParts.Pollution.PollutedArea;
import nodagumi.ananPJ.NetworkParts.Link.MapLink;
import nodagumi.ananPJ.NetworkParts.Node.MapNode;
import nodagumi.ananPJ.misc.NetmasPropertiesHandler;


public class PollutionCalculator implements Serializable {
    private static final long serialVersionUID = 29847234890234908L;
    static double AGENT_HEIGHT = 1.5;

    double nextEvent = 0;
    double timeScale;

    public static boolean debug = false;

    HashMap<Integer, PollutedArea> polluted_area_sorted;

    private ArrayList<double[]> pollutionDataList = new ArrayList<double[]>();
    private Iterator<double[]> pollutionDataIterator = null;
    private double[] pollutionData = null;

    public ArrayList<PollutedArea> pollutedAreaFromNodes(ArrayList<MapNode>
            nodes) {
        ArrayList <PollutedArea> areas = new ArrayList<PollutedArea>();
        return areas;
    }

    public PollutionCalculator(String scheduleFileName,
            ArrayList<PollutedArea> _pollution, double _timeScale, double interpolationInterval) {
        if (scheduleFileName == null || scheduleFileName.isEmpty()) {
            nextEvent = -1.0;
        } else {
            readData(scheduleFileName);
            linearInterpolation(interpolationInterval);
            pollutionDataIterator = pollutionDataList.iterator();
            if (pollutionDataIterator.hasNext()) {
                pollutionData = pollutionDataIterator.next();
                nextEvent = pollutionData[0];
            } else {
                nextEvent = -1.0;
            }
        }
        
        setup_polluted_areas(_pollution);
        timeScale = _timeScale;
        
        //System.out.println("timeScale "+timeScale);
    }

    private void readData(String fileName) {
        pollutionDataList.clear();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            String line = reader.readLine();
            while (line != null) {
                if (! line.trim().startsWith("#")) {
                    String[] strItems = line.split(",");
                    double[] items = new double[strItems.length];
                    for (int index = 0; index < items.length; index++) {
                        items[index] = Double.parseDouble(strItems[index]);
                    }
                    pollutionDataList.add(items);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // pollution データを interval 秒区分で線形補間する
    private void linearInterpolation(double interval) {
        if (interval <= 0.0 || pollutionDataList.isEmpty()) {
            return;
        }
        ArrayList<double[]> interpolatedPollutionDataList = new ArrayList<double[]>();
        double[] lastItems = null;
        for (double[] items : pollutionDataList) {
            if (lastItems != null) {
                double lastEventTime = lastItems[0];
                double eventTime = items[0];
                if ((eventTime - lastEventTime) > interval) {
                    // 線形補間
                    for (double time = lastEventTime + interval; time < eventTime; time += interval) {
                        double[] interpolatedItems = new double[items.length];
                        interpolatedItems[0] = time;
                        for (int index = 1; index < items.length; index++) {
                            if (items[index] == lastItems[index]) {
                                interpolatedItems[index] = items[index];
                            } else {
                                double a = (time - lastEventTime) / (eventTime - lastEventTime);    // 補間係数
                                interpolatedItems[index] = lastItems[index] + a * (items[index] - lastItems[index]);
                            }
                        }
                        interpolatedPollutionDataList.add(interpolatedItems);
                    }
                }
            }
            interpolatedPollutionDataList.add(items);
            lastItems = items;
        }
        pollutionDataList.clear();
        pollutionDataList.addAll(interpolatedPollutionDataList);
    }

    public void updateNodesLinksAgents(double time,
            ArrayList<MapNode> nodes,
            ArrayList<MapLink> links,
            List<EvacuationAgent> agents) {
        //if (debug) System.err.println("PC update: " + time + ", next: " + nextEvent);

        if (nextEvent != -1.0 && nextEvent <= time) {
            // System.out.println("  PC update next event: " + time);
            update_pollution();

            // pollution対象リンクの汚染フラグを更新する(汚染度が0に戻ることも考慮する)
            for (MapLink link : links) {
                if (link.getIntersectedPollutionAreas().isEmpty()) {
                    continue;
                }
                link.setPolluted(false);
                for (PollutedArea area : link.getIntersectedPollutionAreas()) {
                    if ((Double)area.getUserObject() != 0.0) {
                        link.setPolluted(true);
                        break;
                    }
                }
            }
        }

        for (EvacuationAgent agent : agents) {
            if (agent.isEvacuated())
                continue;
            if (! agent.getCurrentLink().isPolluted()) {
                agent.exposed(0.0);
                continue;
            }

            double min_distance = Double.MAX_VALUE;
            PollutedArea best_area = null;
            Vector3f point = new Vector3f((float)agent.getPos().getX(),
                    (float)agent.getPos().getY(),
                    (float)(agent.getHeight() + AGENT_HEIGHT));
            for (PollutedArea area : agent.getCurrentLink().getIntersectedPollutionAreas()) {
                if (area.contains(point)) {
                    best_area = area;
                    break;
                }

                // double d = area.distance(point);     無意味な呼び出しなのでコメント化(斉藤)
                //System.err.println("  area : " + area + ", point: " + point +
                //        ", d: " + d);

                // if (d < min_distance) {
                    // min_distance = d;
                    // best_area = area;
                // }
            }

            if (best_area != null) {
                Double pollutionLevel = (Double)best_area.getUserObject();
                if (debug) System.err.println(agent.agentNumber + " " + pollutionLevel);

                // System.err.printf("in pollution calculator agent: %04d, " + 
                        // "d: %.4f, speed: %.4f, %s\n", agent.ID, d,
                        // agent.getSpeed(), best_area.getTags());
                if (pollutionLevel != null) {
                    agent.exposed(pollutionLevel * timeScale);
                    best_area.setContactOfAgents(true);
                }
            } else {
                agent.exposed(0.);
            }
        }
    }

    class AreaGroups extends PollutedArea implements Serializable {
        private static final long serialVersionUID = -2438038596474793650L;
        private ArrayList<PollutedArea> subgroups;
        private boolean view;
        public AreaGroups(
                double from_x, double from_y, double from_z,
                double to_x, double to_y, double to_z,
                ArrayList<PollutedArea> subgroup_candidates) {
            super(0);
            view = false;
            subgroups.addAll(subgroup_candidates);
        }

        @Override
        public boolean contains(Vector3f point) {
            return false;
        }

        @Override
        public boolean contains(Point2D point) {
            return false;
        }

        @Override
        public boolean intersectsLine(Line2D line) {
            return false;
        }

        @Override
        public void draw(Graphics2D g, boolean experiment) { /* do nothing */}
        @Override
        public TransformGroup get3DShape(Appearance app) {
            /* do nothing */
            return null;
        }

        @Override
        public double getDensity() {
            /* do nothing */
            return 0;
        }

        public double getDensity(Point2D point, double height) {
            
            return 0;
        }

        @Override
        public Shape getShape() {
            /* do nothing */
            return null;
        }
        
        @Override   // tkokada
        public ArrayList<Point2D> getAllVertices() {
            return null;
        }
        
        @Override // tkokada
        public double getAngle() {
            double d = Double.NaN;
            return d;
        }

        @Override
        public NType getNodeType() {
            /* do nothing */
            return null;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public double distance(Vector3f point) {
            return 0;
        }

        @Override
        public boolean getContactOfAgents() {
            return view;
        }

        @Override
        public void setContactOfAgents(boolean _view) {
            view = _view;
        }

        private void writeObject(ObjectOutputStream stream) {
            try {
                stream.defaultWriteObject();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private void readObject(ObjectInputStream stream) {
            try {
                stream.defaultReadObject();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (ClassNotFoundException cnfe) {
                cnfe.printStackTrace();
            }
        }
    }

    private void setup_polluted_areas(ArrayList<PollutedArea> areas) {
        polluted_area_sorted = new HashMap<Integer, PollutedArea>();

        //System.out.println("in setup_polluted_areas");

        for (PollutedArea area : areas) {
            //System.out.println("in setup_polluted_areas"+areas);

            Matcher m = area.matchTag("^(\\d+)$");
            if (m != null) {
                int index = Integer.parseInt(m.group(0));

                //System.out.println("index "+index);

                polluted_area_sorted.put(index, area);
            }
        }
    }

    private Double correct_density(double d) {
        /* 2010/02/18 for HCN gas
         *  2.28 \times 10^{-13} * d^{4.56}
         */
        /* reduce density */
        //d *= 10E-2;

        //d *= 10E6;/* the values are given in 10^{-6} */
        //return Math.pow(d, 4.56) * 2.28 * 10E-13;
        return d * 10E-4;
    }

    private void update_pollution() {
        if (debug) System.err.println("PC: updating pollution " + nextEvent);
        
        //System.out.println("in update_pollution() items: "+items[0]+" "+items[1]+" "+items[2]+" "+items[3]+" "+items[4]);
        
        for (Integer index : polluted_area_sorted.keySet()) {
            PollutedArea area = polluted_area_sorted.get(index);
            
            //System.out.println("index "+index+"index.intValue() "+index.intValue());
                
            double _d = pollutionData[index.intValue()];
            Double pollutionLevel = correct_density(_d);
            
            if (debug) System.err.println("(" + index + "=" + pollutionLevel + ") ");
            
            area.setUserObject(pollutionLevel);
        }

        if (pollutionDataIterator.hasNext()) {
            pollutionData = pollutionDataIterator.next();
            nextEvent = pollutionData[0];
        } else {
            nextEvent = -1.0;
        }
    }
    
    public ArrayList<PollutedArea> getPollutions() {
        return new ArrayList<PollutedArea>(polluted_area_sorted.values());
    }
}
