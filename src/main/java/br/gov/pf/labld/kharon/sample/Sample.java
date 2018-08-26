package br.gov.pf.labld.kharon.sample;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.net.URISyntaxException;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import br.gov.pf.labld.kharon.Edge;
import br.gov.pf.labld.kharon.Graph;
import br.gov.pf.labld.kharon.GraphPanel;
import br.gov.pf.labld.kharon.Node;
import br.gov.pf.labld.kharon.NodeListener;
import br.gov.pf.labld.kharon.StageListener;
import br.gov.pf.labld.kharon.renderers.Renderers;

public class Sample {

  public static void main(String[] args) throws Exception {
    show();
  }

  public static void show() throws URISyntaxException {

    Renderers.registerNodeRenderer("bug", new BugNodeRenderer());

    Graph graph = new Graph();

    int totalNodes = 20;
    Node[] nodes = new Node[totalNodes];

    for (int i = 0; i < totalNodes; i++) {
      Node node = new Node("" + i);
      node.setLabel("Node " + i);
      node.setX((int) (Math.random() * 1200));
      node.setY((int) (Math.random() * 700));
      node.setType("bug");
      graph.addNode(node);
      nodes[i] = node;
    }

    for (int i = 0; i < totalNodes; i++) {
      for (int j = 0; j < 2; j++) {
        Node target = nodes[(int) ((totalNodes - 1) * Math.random())];
        Edge edge = new Edge("" + i + "_" + j, nodes[i], target);
        graph.addEdge(edge);
      }
    }

    JFrame frame = new JFrame("Kharon, ferryman of Hades.");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.setPreferredSize(new Dimension(200, 200));

    final GraphPanel graphPanel = new GraphPanel(graph);
    graphPanel.addNodeListener(new NodeListener() {
      @Override
      public void nodeClicked(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " clicked.");
        graphPanel.selectNode(node.getId());
      }

      @Override
      public void nodeDragStarted(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " drag started.");
        graphPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        graphPanel.selectNode(node.getId());
      }

      @Override
      public void nodeDragStopped(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " drag stopped.");
        graphPanel.setCursor(Cursor.getDefaultCursor());
      }

      @Override
      public void nodeDragged(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " dragged.");
      }
    });

    graphPanel.addStageListener(new StageListener() {

      @Override
      public void stageZoomChanged(MouseWheelEvent e) {
        System.out.println("Zoom changed " + graphPanel.getZoom());
      }

      @Override
      public void stageDragged(MouseEvent e) {
        System.out.println("Stage dragged.");
      }

      @Override
      public void stageDragStopped(MouseEvent e) {
        System.out.println("Stage drag stopped.");
      }

      @Override
      public void stageDragStarted(MouseEvent e) {
        System.out.println("Stage drag started.");
      }

      @Override
      public void stageClicked(MouseEvent e) {
        System.out.println("Stage clicked.");
        graphPanel.deselectAll();
      }
    });

    graphPanel.getActionMap().put("SelectAll", new AbstractAction() {

      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        graphPanel.selectAll();
      }
    });
    KeyStroke controlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_MASK);
    InputMap inputMap = graphPanel.getInputMap();
    inputMap.put(controlA, "SelectAll");

    graphPanel.getActionMap().put("RemoveSelected", new AbstractAction() {

      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        graphPanel.removeSelectedNodes();
      }
    });
    KeyStroke delete = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
    inputMap.put(delete, "RemoveSelected");

    // graphPanel.setShowBoundingBoxes(true);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(graphPanel);

    frame.add(panel);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    // frame.setUndecorated(true);
    frame.setVisible(true);
  }

}