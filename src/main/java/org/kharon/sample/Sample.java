package org.kharon.sample;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.kharon.Edge;
import org.kharon.Graph;
import org.kharon.GraphPanel;
import org.kharon.Node;
import org.kharon.NodeListener;
import org.kharon.StageListener;
import org.kharon.StageMode;
import org.kharon.renderers.Renderers;

public class Sample {

  public static void main(String[] args) throws Exception {
    show();
  }

  public static void show() throws URISyntaxException {

    Renderers.registerNodeRenderer("bug", new BugNodeRenderer());

    Graph graph = new Graph();

    int totalNodes = 10;
    Node[] nodes = new Node[totalNodes + 1];

    for (int i = 0; i < totalNodes; i++) {
      Node node = new Node("" + i);
      node.setLabel("Node " + i);
      node.setX(50 + (int) (Math.random() * 1000));
      node.setY(50 + (int) (Math.random() * 600));
      node.setType("bug");
      node.setSize(30);
      graph.addNode(node);
      nodes[i] = node;
    }

    Node node = new Node("special");
    node.setLabel("Very long and special label for this node");
    node.setX(50 + (int) (Math.random() * 1000));
    node.setY(50 + (int) (Math.random() * 600));
    node.setType("bug");
    node.setSize(30);
    graph.addNode(node);
    nodes[totalNodes] = node;

    for (int i = 0; i < nodes.length; i++) {
      for (int j = 0; j < 2; j++) {
        Node target = nodes[(int) ((nodes.length - 1) * Math.random())];
        Edge edge = new Edge("" + i + "_" + j, nodes[i], target);
        graph.addEdge(edge);
      }
    }

    JFrame frame = new JFrame("Kharon, ferryman of Hades.");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.setPreferredSize(new Dimension(200, 200));

    final GraphPanel graphPanel = new GraphPanel(graph);
    graphPanel.setBackground(Color.WHITE);
    graphPanel.setHistoryEnabled(true);
    graphPanel.addNodeListener(new NodeListener() {
      @Override
      public void nodeClicked(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " clicked.");
        int clickCount = e.getClickCount();

        if (clickCount == 1) {
          nodeSingleClicked(node, e);
        }
      }

      private void nodeSingleClicked(Node node, MouseEvent e) {
        boolean selected = graphPanel.isNodeSelected(node);
        boolean keepSelection = e.isControlDown() || e.isShiftDown();
        if (!selected) {
          graphPanel.selectNode(node.getId(), keepSelection);
        } else if (keepSelection) {
          graphPanel.deselectNode(node.getId());
        } else {
          graphPanel.selectNode(node.getId());
        }
      }

      @Override
      public void nodeDragStarted(Collection<Node> nodes, MouseEvent e) {
        for (Node node : nodes) {
          if (graphPanel.isNodeUnderMouse(node)) {
            boolean keepSelection = graphPanel.isNodeSelected(node);
            graphPanel.selectNode(node.getId(), keepSelection);
          }
          System.out.println("Node " + node.getId() + " drag started");
        }
        graphPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      }

      @Override
      public void nodeDragStopped(Collection<Node> nodes, MouseEvent e) {
        for (Node node : nodes) {
          nodeDragStopped(node, e);
        }
        graphPanel.setCursor(Cursor.getDefaultCursor());
      }

      public void nodeDragStopped(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " drag stopped.");
      }

      @Override
      public void nodeDragged(Collection<Node> nodes, MouseEvent e) {
        for (Node node : nodes) {
          // System.out.println("Node " + node.getId() + " dragged.");
        }
      }

      @Override
      public void nodePressed(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " pressed.");
        // nodeSingleClicked(node, e);
      }

      @Override
      public void nodeReleased(Node node, MouseEvent e) {
        System.out.println("Node " + node.getId() + " released.");
      }

      @Override
      public void nodeHover(Node node, MouseEvent e) {
        graphPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void nodeOut(MouseEvent e) {
        graphPanel.setCursor(Cursor.getDefaultCursor());
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
        graphPanel.setCursor(Cursor.getDefaultCursor());
        graphPanel.setStageMode(StageMode.PAN);
      }

      @Override
      public void stageDragStarted(MouseEvent e) {
        System.out.println("Stage drag started.");
        if (e.isControlDown() || e.isShiftDown()) {
          graphPanel.setStageMode(StageMode.SELECTION);
        } else {
          graphPanel.setStageMode(StageMode.PAN);
          graphPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
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

    graphPanel.getActionMap().put("SaveImage", new AbstractAction() {

      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showSaveDialog(graphPanel);
        if (option == JFileChooser.APPROVE_OPTION) {
          BufferedImage image = graphPanel.toImage();
          File selectedFile = fileChooser.getSelectedFile();
          if (!selectedFile.getName().endsWith(".png")) {
            selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".png");
            try {
              ImageIO.write(image, "png", selectedFile);
            } catch (IOException e1) {
              throw new RuntimeException(e1);
            }
          }
        }
      }
    });
    KeyStroke controlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK);
    inputMap.put(controlS, "SaveImage");

    graphPanel.getActionMap().put("RemoveSelected", new AbstractAction() {

      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        graphPanel.removeSelectedNodes();
      }
    });
    KeyStroke delete = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
    inputMap.put(delete, "RemoveSelected");

    graphPanel.getActionMap().put("Undo", new AbstractAction() {

      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        graphPanel.getHistory().undo();
        graphPanel.repaint();
      }
    });
    KeyStroke controlZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK);
    inputMap.put(controlZ, "Undo");

    graphPanel.getActionMap().put("Redo", new AbstractAction() {

      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        graphPanel.getHistory().redo();
        graphPanel.repaint();
      }
    });
    KeyStroke controlY = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK);
    inputMap.put(controlY, "Redo");

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(graphPanel);

    frame.add(panel, BorderLayout.CENTER);
    JLabel comp = new JLabel("Kharon");
    comp.setHorizontalAlignment(JLabel.CENTER);
    frame.add(comp, BorderLayout.NORTH);

    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    // frame.setUndecorated(true);
    frame.setVisible(true);
  }

}
