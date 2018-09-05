package org.kharon;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Rectangle2D.Double;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.kharon.renderers.EdgeRenderer;
import org.kharon.renderers.GraphRenderer;
import org.kharon.renderers.LabelRenderer;
import org.kharon.renderers.NodeHoverRenderer;
import org.kharon.renderers.NodeRenderer;
import org.kharon.renderers.RenderContext;
import org.kharon.renderers.Renderers;
import org.kharon.renderers.SelectionRenderer;

public class GraphPanel extends JComponent
    implements MouseListener, MouseWheelListener, MouseMotionListener, GraphListener, ComponentListener {

  private static final long serialVersionUID = 3827345534868023684L;

  private Graph graph;
  private StageMode stageMode = StageMode.PAN;
  private NodeDragMode nodeDragMode = NodeDragMode.SELECTION;
  private RenderContext renderContext;

  private List<NodeListener> nodeListeners = new ArrayList<>();
  private List<StageListener> stageListeners = new ArrayList<>();

  private boolean showBoundingBoxes = false;

  private boolean mouseWheelZoomEnabled = true;
  private double scale = 1d / 5d;
  private static final double MIN_ZOOM = 1d / 4d;
  private static final double MAX_ZOOM = 5d;

  private boolean isDragging = false;
  private Node nodeUnderMouse = null;

  private AffineTransform transform = new AffineTransform();
  private AffineTransform inverseTransform = new AffineTransform();

  private int startDragX;
  private int startDragY;

  private int nodeDragOffsetX;
  private int nodeDragOffsetY;

  private Set<String> selectedNodes = new HashSet<>();
  private Map<String, NodeBoundingBox> boxesIndex = new HashMap<>();

  private Set<String> idleNodes = new HashSet<>();
  private Set<String> liveNodes = new HashSet<>();

  private Double selectionBox;

  private BufferedImage idleBuffer;
  private Graphics2D idleGraphics;

  private int lastBufferX;
  private int lastBufferY;

  private boolean isPrinting;

  public GraphPanel(Graph graph) {
    super();
    this.graph = graph;
    this.renderContext = new RenderContext(this, graph);
    this.addMouseWheelListener(this);
    this.addMouseMotionListener(this);
    this.addMouseListener(this);
    this.addComponentListener(this);

    this.graph.addListener(this);

    this.idleNodes.addAll(this.graph.getIds());
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;
    paintGraph(g, this.transform, g2d.getClipBounds());
  }

  private void paintGraph(Graphics g, AffineTransform tx, Rectangle2D clipBounds) {
    Graphics2D g2d = (Graphics2D) g;

    AffineTransform currentTransform = g2d.getTransform();
    AffineTransform graphTransformation = (AffineTransform) currentTransform.clone();
    graphTransformation.concatenate(tx);

    int originX = (int) (-1 * currentTransform.getTranslateX());
    int originY = (int) (-1 * currentTransform.getTranslateY());

    try {
      clipBounds.setFrame(clipBounds.getX() - Math.abs(originX), clipBounds.getY() - Math.abs(originY),
          clipBounds.getWidth() + 2 * Math.abs(originX), clipBounds.getHeight() + 2 * Math.abs(originY));
      clipBounds = graphTransformation.createInverse().createTransformedShape(clipBounds).getBounds2D();
    } catch (NoninvertibleTransformException e) {
      throw new RuntimeException(e);
    }

    RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int imageWidth = this.getWidth() + Math.abs(originX);
    int imageHeight = this.getHeight() + Math.abs(originY);

    BufferedImage liveBuffer = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D liveGraphics = liveBuffer.createGraphics();
    liveGraphics.setRenderingHints(rh);

    boolean paintIdleNodes = idleBuffer == null || idleBuffer.getHeight() != imageHeight
        || idleBuffer.getWidth() != imageWidth || this.lastBufferX != originX || this.lastBufferY != originY;
    if (paintIdleNodes) {
      idleBuffer = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
      idleGraphics = idleBuffer.createGraphics();
      idleGraphics.setRenderingHints(rh);

      idleGraphics.setBackground(getBackground());
      idleGraphics.clearRect(0, 0, imageWidth, imageHeight);

      this.lastBufferX = originX;
      this.lastBufferY = originY;
    }

    GraphRenderer graphRenderer = Renderers.getGraphRenderer(this.graph.getType());
    graphRenderer.render(liveGraphics, this.graph);

    Set<Edge> liveEdges = graph.getNodesEdges(this.liveNodes);
    paintEdges(liveGraphics, graphTransformation, clipBounds, liveEdges);
    paintNodes(liveGraphics, graphTransformation, currentTransform, clipBounds, graph.getNodes(this.liveNodes));

    if (paintIdleNodes) {
      Set<Edge> idleEdges = graph.getNodesEdges(this.idleNodes);
      idleEdges.removeAll(liveEdges);
      paintEdges(idleGraphics, graphTransformation, clipBounds, idleEdges);
      paintNodes(idleGraphics, graphTransformation, currentTransform, clipBounds, graph.getNodes(this.idleNodes));
    }

    g2d.drawImage(idleBuffer, originX, originY, null);

    Node hoveredNode = getHoveredNode();
    if (hoveredNode != null && !isPrinting) {
      NodeBoundingBox box = this.boxesIndex.get(hoveredNode.getId());
      if (box != null) {
        NodeHoverRenderer renderer = Renderers.getNodeHoverRenderer("default");
        GraphShape graphShape = renderer.render(g2d, box, renderContext);
        graphShape.draw(g2d, tx);
        paintNodes(liveGraphics, graphTransformation, currentTransform, clipBounds, Arrays.asList(hoveredNode));
      }
    }

    if (!isPrinting) {
      paintSelections(liveGraphics, graphTransformation, clipBounds);
    }

    if (this.selectionBox != null && !isPrinting) {
      paintSelectionBox(liveGraphics, graphTransformation);
    }

    Color color = liveGraphics.getColor();
    liveGraphics.setColor(Color.BLUE);
    liveGraphics.fill(graphTransformation.createTransformedShape(new Rectangle2D.Double(-2, -2, 4, 4)));
    liveGraphics.setColor(Color.GREEN);
    liveGraphics.fill(new Rectangle2D.Double(-2, -2, 4, 4));

    liveGraphics.setColor(color);
    g2d.drawImage(liveBuffer, originX, originY, null);

    liveGraphics.dispose();
  }

  private void paintSelections(Graphics2D g2d, AffineTransform tx, Rectangle2D clipBounds) {
    for (String selectedId : this.selectedNodes) {
      Node node = this.graph.getNode(selectedId);
      NodeBoundingBox boundingBox = this.boxesIndex.get(node.getId());
      if (boundingBox.intersects(clipBounds)) {
        SelectionRenderer renderer = Renderers.getSelectionRenderer(node.getSelectionType());
        GraphShape selectionGraphShape = renderer.render(g2d, boundingBox, renderContext);
        if (selectionGraphShape != null) {
          selectionGraphShape.draw(g2d, tx);
        }
      }
    }
  }

  private void paintSelectionBox(Graphics2D g2d, AffineTransform tx) {
    Color oldColor = g2d.getColor();
    g2d.setColor(this.graph.getSettings().getSelectionColor());
    g2d.draw(tx.createTransformedShape(selectionBox));
    g2d.setColor(oldColor);
  }

  private void paintNodes(Graphics2D g2d, AffineTransform stageTx, AffineTransform componentTx, Rectangle2D clipBounds,
      Collection<Node> nodes) {
    for (Node node : nodes) {
      NodeBoundingBox nodeBoundingBox = this.boxesIndex.get(node.getId());
      if (nodeBoundingBox == null) {
        nodeBoundingBox = new NodeBoundingBox(1.4d);
        this.boxesIndex.put(node.getId(), nodeBoundingBox);
      } else {
        nodeBoundingBox.clear();
      }

      NodeRenderer renderer = Renderers.getNodeRenderer(node.getType());
      GraphShape nodeGraphShape = renderer.render(g2d, node, renderContext);
      if (nodeGraphShape != null && nodeGraphShape.getShape().intersects(clipBounds)) {
        nodeGraphShape.draw(g2d, stageTx);

        Rectangle2D bounds = nodeBoundingBox.addBox(nodeGraphShape.getShape());
        if (this.showBoundingBoxes) {
          g2d.draw(stageTx.createTransformedShape(bounds));
        }
      }

      LabelRenderer labelRenderer = Renderers.getLabelRenderer(node.getLabelType());
      GraphShape labelGraphShape = labelRenderer.render(g2d, node, renderContext);
      if (labelGraphShape != null && labelGraphShape.getShape().intersects(clipBounds)) {
        labelGraphShape.draw(g2d, stageTx);

        Rectangle2D bounds = nodeBoundingBox.addBox(labelGraphShape.getShape());
        if (this.showBoundingBoxes) {
          g2d.draw(stageTx.createTransformedShape(bounds));
        }
      }
    }
  }

  private void paintEdges(Graphics2D g2d, AffineTransform tx, Rectangle2D clipBounds, Collection<Edge> edges) {
    for (Edge edge : edges) {
      EdgeRenderer renderer = Renderers.getEdgeRenderer(edge.getType());
      GraphShape graphShape = renderer.render(g2d, edge, renderContext);
      if (graphShape != null && graphShape.getShape().intersects(clipBounds)) {
        graphShape.draw(g2d, tx);
      }
    }
  }

  public BufferedImage toImage() {
    BufferedImage image = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_INT_ARGB);
    print(image.getGraphics());
    return image;
  }

  public Graph getGraph() {
    return graph;
  }

  public void addNodeListener(NodeListener nodeListener) {
    this.nodeListeners.add(nodeListener);
  }

  public void removeNodeListener(NodeListener nodeListener) {
    this.nodeListeners.remove(nodeListener);
  }

  public void addStageListener(StageListener stageListener) {
    this.stageListeners.add(stageListener);
  }

  public void removeStageListener(StageListener stageListener) {
    this.stageListeners.remove(stageListener);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (this.isEnabled()) {
      if (this.nodeUnderMouse != null) {
        notifyNodeClicked(this.nodeUnderMouse, e);
      } else {
        notifyStageClicked(e);
      }
    }
  }

  private void notifyStageClicked(MouseEvent e) {
    for (StageListener stageListener : this.stageListeners) {
      stageListener.stageClicked(e);
    }
  }

  private void notifyZoomChanged(MouseWheelEvent e) {
    for (StageListener stageListener : this.stageListeners) {
      stageListener.stageZoomChanged(e);
    }
  }

  private void notifyStageDragStarted(MouseEvent e) {
    for (StageListener stageListener : this.stageListeners) {
      stageListener.stageDragStarted(e);
    }
  }

  private void notifyStageDragStopped(MouseEvent e) {
    for (StageListener stageListener : this.stageListeners) {
      stageListener.stageDragStopped(e);
    }
  }

  private void notifyStageDragged(MouseEvent e) {
    for (StageListener stageListener : this.stageListeners) {
      stageListener.stageDragged(e);
    }
  }

  private void notifyNodeClicked(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeClicked(node, e);
    }
  }

  private void notifyNodePressed(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodePressed(node, e);
    }
  }

  private void notifyNodeHover(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeHover(node, e);
    }
  }

  private void notifyNodeOut(MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeOut(e);
    }
  }

  private void notifyNodeReleased(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeReleased(node, e);
    }
  }

  private void notifyNodeDragStarted(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeDragStarted(node, e);
    }
  }

  private void notifyNodeDragStopped(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeDragStopped(node, e);
    }
  }

  private void notifyNodeDragged(Node node, MouseEvent e) {
    for (NodeListener listener : this.nodeListeners) {
      listener.nodeDragged(node, e);
    }
  }

  private Node getNodeUnderMouse(MouseEvent evt) {
    Point2D evtPoint = invert(evt.getPoint());
    for (Entry<String, NodeBoundingBox> entry : boxesIndex.entrySet()) {
      NodeBoundingBox box = entry.getValue();
      if (box.contains(evtPoint)) {
        String id = entry.getKey();
        return this.graph.getNode(id);
      }
    }
    return null;
  }

  private void applyCurrentSelection(MouseEvent e) {
    if (!e.isControlDown() && !e.isShiftDown()) {
      this.selectedNodes.clear();
    }
    for (Entry<String, NodeBoundingBox> entry : boxesIndex.entrySet()) {
      NodeBoundingBox box = entry.getValue();
      if (box.intersects(selectionBox)) {
        String id = entry.getKey();
        this.selectedNodes.add(id);
      }
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (this.isEnabled()) {
      this.nodeUnderMouse = getNodeUnderMouse(e);
      if (this.nodeUnderMouse != null) {
        notifyNodePressed(this.nodeUnderMouse, e);
      }
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (this.isEnabled()) {
      stopDrag(e);
      this.nodeUnderMouse = getNodeUnderMouse(e);
      if (this.nodeUnderMouse != null) {
        notifyNodeReleased(this.nodeUnderMouse, e);
      }
      repaint();
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {

  }

  @Override
  public void mouseExited(MouseEvent e) {
    if (this.isEnabled()) {
      stopDrag(e);
      repaint();
    }
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    if (this.isEnabled()) {
      int notches = -1 * e.getWheelRotation();
      double zoom = getZoom();
      double newZoom = zoom + (scale * notches);
      newZoom = Math.min(newZoom, MAX_ZOOM);
      newZoom = Math.max(newZoom, MIN_ZOOM);

      if (zoom != newZoom) {
        setZoom(newZoom);
        notifyZoomChanged(e);
        repaint();
      }
    }
  }

  public boolean isMouseWheelZoomEnabled() {
    return mouseWheelZoomEnabled;
  }

  public void setMouseWheelZoomEnabled(boolean mouseWheelZoomEnabled) {
    this.mouseWheelZoomEnabled = mouseWheelZoomEnabled;
    if (!mouseWheelZoomEnabled) {
      this.removeMouseWheelListener(this);
    }
  }

  public double getZoom() {
    return transform.getScaleX();
  }

  public void setZoom(double zoom) {
    double translateX = this.transform.getTranslateX();
    double translateY = this.transform.getTranslateY();
    this.transform.setToScale(zoom, zoom);
    this.transform.translate(translateX, translateY);
    try {
      this.inverseTransform = this.transform.createInverse();
    } catch (NoninvertibleTransformException e) {
      throw new RuntimeException(e);
    }
    resetBuffer();
  }

  @Override
  public void mouseDragged(MouseEvent evt) {
    if (this.isEnabled() && SwingUtilities.isLeftMouseButton(evt)) {
      if (!isDragging) {
        startDrag(evt);
      }
      if (nodeUnderMouse != null) {
        if (this.nodeDragMode == NodeDragMode.CURRENT) {
          dragNode(evt);
        } else {
          dragSelectedNodes(evt);
        }
        repaint();
      } else if (this.stageMode == StageMode.PAN) {
        dragStage(evt);
        repaint();
      } else if (this.stageMode == StageMode.SELECTION) {
        updateSelection(evt);
        repaint();
      }
    }
  }

  private void updateSelection(MouseEvent evt) {
    Point2D evtLocation = invert(evt.getPoint());
    double x = Math.min(this.startDragX, evtLocation.getX());
    double y = Math.min(this.startDragY, evtLocation.getY());
    double width = Math.abs(this.startDragX - evtLocation.getX());
    double height = Math.abs(this.startDragY - evtLocation.getY());
    if (selectionBox == null) {
      this.selectionBox = new Rectangle2D.Double(x, y, width, height);
    } else {
      this.selectionBox.x = x;
      this.selectionBox.y = y;
      this.selectionBox.width = width;
      this.selectionBox.height = height;
    }
  }

  private void dragStage(MouseEvent evt) {
    Point2D evtLocation = invert(evt.getPoint());
    double x = evtLocation.getX();
    double y = evtLocation.getY();

    double stageOffsetX = x - this.startDragX;
    double stageOffsetY = y - this.startDragY;

    translateStage(stageOffsetX, stageOffsetY);
    notifyStageDragged(evt);
  }

  private void translateStage(double x, double y) {
    this.transform.translate(x, y);

    try {
      this.inverseTransform = this.transform.createInverse();
    } catch (NoninvertibleTransformException e) {
      throw new RuntimeException(e);
    }
    resetBuffer();
  }

  private void dragSelectedNodes(MouseEvent evt) {
    int oldX = this.nodeUnderMouse.getX();
    int oldY = this.nodeUnderMouse.getY();
    dragNode(evt);

    Set<String> selected = new HashSet<>(this.selectedNodes);
    selected.remove(this.nodeUnderMouse.getId());
    Set<Node> nodes = this.graph.getNodes(selected);

    int offsetX = this.nodeUnderMouse.getX() - oldX;
    int offsetY = this.nodeUnderMouse.getY() - oldY;
    for (Node node : nodes) {
      int x = node.getX() + offsetX;
      int y = node.getY() + offsetY;

      node.setX(x);
      node.setY(y);

      notifyNodeDragged(node, evt);
    }

  }

  private void dragNode(MouseEvent evt) {
    Point2D evtLocation = invert(evt.getPoint());
    int x = (int) (evtLocation.getX() - this.nodeDragOffsetX);
    int y = (int) (evtLocation.getY() - this.nodeDragOffsetY);

    nodeUnderMouse.setX(x);
    nodeUnderMouse.setY(y);

    notifyNodeDragged(nodeUnderMouse, evt);
  }

  private void startDrag(MouseEvent e) {
    this.isDragging = true;
    this.nodeUnderMouse = getNodeUnderMouse(e);

    Point2D startDrag = invert(e.getPoint());
    this.startDragX = (int) startDrag.getX();
    this.startDragY = (int) startDrag.getY();

    if (nodeUnderMouse != null) {
      this.nodeDragOffsetX = this.startDragX - nodeUnderMouse.getX();
      this.nodeDragOffsetY = this.startDragY - nodeUnderMouse.getY();

      this.idleNodes.remove(nodeUnderMouse.getId());
      this.liveNodes.add(nodeUnderMouse.getId());
      if (this.nodeDragMode == NodeDragMode.SELECTION) {
        this.liveNodes.addAll(selectedNodes);
        this.idleNodes.removeAll(selectedNodes);
      }
      resetBuffer();

      notifyNodeDragStarted(nodeUnderMouse, e);
    } else {
      notifyStageDragStarted(e);
    }

  }

  private void stopDrag(MouseEvent e) {
    if (this.isDragging) {
      if (this.selectionBox != null && this.stageMode == StageMode.SELECTION) {
        applyCurrentSelection(e);
      }

      if (nodeUnderMouse != null) {
        notifyNodeDragStopped(nodeUnderMouse, e);

        this.idleNodes.addAll(this.liveNodes);
        this.liveNodes.clear();
        resetBuffer();
      } else {
        notifyStageDragStopped(e);
      }
      this.isDragging = false;
      this.nodeUnderMouse = null;
      this.selectionBox = null;
    }
  }

  private Point2D invert(Point original) {
    return this.inverseTransform.transform(original, new Point2D.Double());
  }

  public Shape invert(Shape shape) {
    return this.inverseTransform.createTransformedShape(shape);
  }

  public Shape transform(Shape shape) {
    return this.transform.createTransformedShape(shape);
  }

  public double transform(double distance) {
    return this.transform.getScaleX() * distance;
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    boolean wasHovering = this.nodeUnderMouse != null;
    String oldId = wasHovering ? this.nodeUnderMouse.getId() : null;

    this.nodeUnderMouse = getNodeUnderMouse(e);

    boolean isHovering = this.nodeUnderMouse != null;
    String id = isHovering ? this.nodeUnderMouse.getId() : null;

    if (wasHovering && !isHovering) {
      notifyNodeOut(e);
    } else if (!wasHovering && isHovering) {
      notifyNodeHover(getHoveredNode(), e);
    } else if (wasHovering && isHovering && !id.equals(oldId)) {
      notifyNodeOut(e);
      notifyNodeHover(getHoveredNode(), e);
    }
    repaint();
  }

  public boolean isShowBoundingBoxes() {
    return showBoundingBoxes;
  }

  public void setShowBoundingBoxes(boolean showBoundingBoxes) {
    this.showBoundingBoxes = showBoundingBoxes;
  }

  public Set<String> getSelected() {
    return selectedNodes;
  }

  public boolean isNodeSelected(Node node) {
    return this.selectedNodes.contains(node.getId());
  }

  public Set<Node> getSelectedNodes() {
    return this.graph.getNodes(this.selectedNodes);
  }

  public void removeNode(String id) {
    Node node = this.graph.getNode(id);
    this.graph.removeNode(node);
    repaint();
  }

  public void removeNodes(Set<String> ids) {
    Set<Node> nodes = this.graph.getNodes(ids);
    for (Node node : nodes) {
      this.graph.removeNode(node);
    }
    repaint();
  }

  public void removeSelectedNodes() {
    removeNodes(selectedNodes);
  }

  public void selectNodes(Collection<String> ids, boolean keepSelection) {
    if (!keepSelection) {
      this.selectedNodes.clear();
    }
    boolean modified = !keepSelection;
    for (String id : ids) {
      if (!this.graph.containsNode(id)) {
        throw new IllegalArgumentException("Node " + id + " does not exist.");
      }
      modified = modified | this.selectedNodes.add(id);
    }
    if (modified) {
      repaint();
    }
  }

  public void selectNodes(Collection<String> ids) {
    this.selectNodes(ids, false);
  }

  public void selectNode(String id) {
    this.selectNode(id, false);
  }

  public void selectNode(String id, boolean keepSelection) {
    if (!this.graph.containsNode(id)) {
      throw new IllegalArgumentException("Node " + id + " does not exist.");
    }
    if (!keepSelection) {
      this.selectedNodes.clear();
    }
    if (this.selectedNodes.add(id) | !keepSelection) {
      repaint();
    }
  }

  public void deselectNode(String id) {
    this.selectedNodes.remove(id);
    repaint();
  }

  public void deselectAll() {
    this.selectedNodes.clear();
    repaint();
  }

  public void selectAll() {
    this.selectedNodes.addAll(this.graph.getIds());
    repaint();
  }

  @Override
  public void nodeAdded(Node node) {
    this.idleNodes.add(node.getId());
    resetBuffer();
  }

  @Override
  public void nodeRemoved(Node node) {
    String nodeId = node.getId();
    this.selectedNodes.remove(nodeId);
    this.boxesIndex.remove(nodeId);
    this.liveNodes.remove(nodeId);
    this.idleNodes.remove(nodeId);

    resetBuffer();
  }

  @Override
  public void edgeAdded(Edge edge) {
    resetBuffer();
  }

  @Override
  public void edgeRemoved(Edge edge) {
    resetBuffer();
  }

  public StageMode getStageMode() {
    return stageMode;
  }

  public void setStageMode(StageMode stageMode) {
    this.stageMode = stageMode;
  }

  public NodeDragMode getNodeDragMode() {
    return nodeDragMode;
  }

  public void setNodeDragMode(NodeDragMode nodeDragMode) {
    this.nodeDragMode = nodeDragMode;
  }

  private void resetBuffer() {
    if (this.idleGraphics != null) {
      this.idleGraphics.dispose();
    }
    this.idleBuffer = null;
    this.idleGraphics = null;
  }

  @Override
  public void componentResized(ComponentEvent e) {
    resetBuffer();
    repaint();
  }

  @Override
  public void componentMoved(ComponentEvent e) {

  }

  @Override
  public void componentShown(ComponentEvent e) {
    resetBuffer();
  }

  @Override
  public void componentHidden(ComponentEvent e) {

  }

  public Node getHoveredNode() {
    return this.nodeUnderMouse;
  }

  public boolean isHovered(Node node) {
    return this.nodeUnderMouse != null && this.nodeUnderMouse.getId().equals(node.getId());
  }

  @Override
  protected void printComponent(Graphics g) {
    this.isPrinting = true;
    resetBuffer();
    super.printComponent(g);
    this.isPrinting = false;
  }

}
