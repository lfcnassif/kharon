package org.kharon;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.kharon.layout.Layout;

public class Graph implements Cloneable {

  private String type = "default";

  private GraphSettings settings = new GraphSettings();

  private Map<String, NodeHolder> nodeIndex = new HashMap<>();
  private Map<String, Edge> edgeIndex = new HashMap<>();

  private List<GraphListener> listeners = new ArrayList<>();

  private GroupPositioner groupPositioner = new RandomGroupPositioner();
  
  private Map<String, Collection<OverlappedEdges>> overlappedEdgesCache = Collections.synchronizedMap(new LinkedHashMap<String, Collection<OverlappedEdges>>(16, 0.75f, true) {

    private static final long serialVersionUID = 1L;

    @Override
      protected boolean removeEldestEntry(Map.Entry<String, Collection<OverlappedEdges>> eldest) {
          return size() > 100;
      }
  });

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getSize() {
    return nodeIndex.size();
  }

  public boolean isEmpty() {
    return nodeIndex.isEmpty();
  }

  public Set<Node> getNodes() {
    return nodeIndex.values().stream().map(s -> s.getNode()).collect(Collectors.toSet());
  }

  public Set<Node> getNodes(Collection<String> ids) {
    Set<Node> nodes = new HashSet<>(ids.size());
    for (String id : ids) {
      Node node = this.nodeIndex.get(id).getNode();
      if (node != null) {
        nodes.add(node);
      } else {
        throw new IllegalArgumentException("Node " + id + " not found.");
      }
    }
    return nodes;
  }

  public boolean containsNode(String id) {
    return this.nodeIndex.containsKey(id);
  }

  public void applyLayout(Layout layout) {
    layout.performLayout(this);
  }

  public Collection<Edge> getEdges() {
    return edgeIndex.values();
  }

  public Set<Edge> getNodesEdges(Collection<String> nodeIds) {
    Set<Edge> result = new HashSet<>();

    for (String nodeId : nodeIds) {
      NodeHolder nodeHolder = this.nodeIndex.get(nodeId);
      result.addAll(nodeHolder.getEdges());
    }

    return result;
  }
  
  public Collection<OverlappedEdges> getNodesOverlappedEdges(Collection<String> nodeIds) {
      
      String ids = nodeIds.stream().sorted().collect(Collectors.joining("-"));
      
      Collection<OverlappedEdges> result = overlappedEdgesCache.get(ids);
      if(result != null) return result;
      
      result = new ArrayList<>();
      Set<Edge> addedEdges = new HashSet<>();

      //toArray() decrease chance of ConcurrentModificationException
      for (String nodeId : nodeIds.toArray(new String[nodeIds.size()])) {
        NodeHolder nodeHolder = this.nodeIndex.get(nodeId);
        HashMap<String, OverlappedEdges> overlapsPerNode = new HashMap<>();
        for(Edge edge : nodeHolder.getEdges()) {
            if(addedEdges.contains(edge)) {
                continue;
            }
            String pairNode = edge.getSource();
            if(pairNode.equals(nodeId)) {
                pairNode = edge.getTarget();
            }
            OverlappedEdges overlapEdge = overlapsPerNode.get(pairNode);
            if(overlapEdge == null){
                overlapEdge = new OverlappedEdges(nodeId, pairNode);
                overlapsPerNode.put(pairNode, overlapEdge);
            }
            if(edge.getSource().equals(overlapEdge.getSource())) {
                overlapEdge.addOutcomingEdge(edge);
            }else {
                overlapEdge.addIncomingEdge(edge);
            }
            addedEdges.add(edge);
        }
        result.addAll(overlapsPerNode.values());
      }
      
      overlappedEdgesCache.put(ids, result);

      return result;
  }

  public void addNodes(Collection<Node> nodes) {
    addNodes(null, nodes);
  }

  public void addNodes(Object eventOriginator, Collection<Node> nodes) {
    addNodesToGraph(nodes);
    notifyElementsAdded(eventOriginator, nodes, Collections.emptySet());
  }

  private void addNodesToGraph(Collection<Node> nodes) {
    for (Node node : nodes) {
      this.nodeIndex.put(node.getId(), new NodeHolder(node));
    }
  }

  public void addNode(Node node) {
    addNodes(Arrays.asList(node));
  }

  public void removeNodes(Collection<Node> nodes) {
    removeNodes(null, nodes);
  }

  public void removeNodes(Object eventOriginator, Collection<Node> nodes) {
    Set<Edge> edges = getEdges(nodes);
    Set<Edge> removedEdges = removeEdgesFromGraph(edges);
    Set<Node> removedNodes = removeNodesFromGraph(nodes);
    notifyElementsRemoved(eventOriginator, removedNodes, removedEdges);
  }

  public Set<Edge> getEdges(Node node) {
    return getEdges(Arrays.asList(node));
  }

  public Set<Edge> getIncomingEdges(Node node) {
    return getIncomingEdges(Arrays.asList(node));
  }

  private Set<Edge> getEdges(Collection<Node> nodes) {
    Set<Edge> edges = new HashSet<>();
    for (Node node : nodes) {
      String id = node.getId();
      if (this.nodeIndex.containsKey(id)) {
        NodeHolder nodeHolder = getNodeHolder(id);
        edges.addAll(nodeHolder.getEdges());
      }
    }
    return edges;
  }

  private Set<Edge> getIncomingEdges(Collection<Node> nodes) {
    Set<Edge> edges = new HashSet<>();
    for (Node node : nodes) {
      String id = node.getId();
      if (this.nodeIndex.containsKey(id)) {
        NodeHolder nodeHolder = getNodeHolder(id);
        edges.addAll(nodeHolder.getIncomingEdges());
      }
    }
    return edges;
  }

  private Set<Node> removeNodesFromGraph(Collection<Node> nodes) {
    Set<Node> removed = new HashSet<>(nodes.size());
    for (Node node : nodes) {
      String id = node.getId();
      if (this.nodeIndex.containsKey(id)) {
        this.nodeIndex.remove(id);
        removed.add(node);
      }
    }
    return removed;
  }

  public void removeNode(Node node) {
    removeNodes(Arrays.asList(node));
  }

  public void addEdges(Collection<Edge> edges) {
    addEdges(null, edges);
  }

  public void addEdges(Object eventOriginator, Collection<Edge> edges) {
    addEdgesToGraph(edges);
    notifyElementsAdded(eventOriginator, Collections.emptySet(), edges);
  }

  private void addEdgesToGraph(Collection<Edge> edges) {
    for (Edge edge : edges) {
      this.edgeIndex.put(edge.getId(), edge);

      String source = edge.getSource();
      NodeHolder sourceHolder = getNodeHolder(source);
      sourceHolder.addEdge(edge);
      sourceHolder.getNode().increaseOutcomingDegree();

      String target = edge.getTarget();
      NodeHolder targetHolder = getNodeHolder(target);
      targetHolder.getNode().increaseIncomingDegree();
      targetHolder.addEdge(edge);
    }
    overlappedEdgesCache.clear();
  }

  public void addEdge(Edge edge) {
    addEdges(Arrays.asList(edge));
  }

  public void removeEdges(Collection<Edge> edges) {
    removeEdges(null, edges);
  }

  public void removeEdges(Object eventOriginator, Collection<Edge> edges) {
    Set<Edge> removedEdges = removeEdgesFromGraph(edges);
    notifyElementsRemoved(eventOriginator, Collections.emptySet(), removedEdges);
  }

  private Set<Edge> removeEdgesFromGraph(Collection<Edge> edges) {
    Set<Edge> removedEdges = new HashSet<>();
    for (Edge edge : edges) {
      Edge removed = this.edgeIndex.remove(edge.getId());
      if (removed != null) {
        String source = edge.getSource();
        NodeHolder sourceHolder = getNodeHolder(source);
        sourceHolder.removeEdge(edge);
        sourceHolder.getNode().decreaseOutcomingDegree();

        String target = edge.getTarget();
        NodeHolder targetHolder = getNodeHolder(target);
        targetHolder.removeEdge(edge);
        targetHolder.getNode().decreaseIncomingDegree();

        removedEdges.add(removed);
      }
    }
    overlappedEdgesCache.clear();
    return removedEdges;
  }

  public void removeEdge(Edge edge) {
    removeEdges(Arrays.asList(edge));
  }

  public void addElements(Collection<Node> nodes, Collection<Edge> edges) {
    addElements(null, nodes, edges);
  }

  public void addElements(Object eventOriginator, Collection<Node> nodes, Collection<Edge> edges) {
    addNodesToGraph(nodes);
    addEdgesToGraph(edges);
    notifyElementsAdded(eventOriginator, nodes, edges);
  }

  public void removeElements(Collection<Node> nodes, Collection<Edge> edges) {
    removeElements(null, nodes, edges);
  }

  public void removeElements(Object eventOriginator, Collection<Node> nodes, Collection<Edge> edges) {
    edges = new HashSet<>(edges);
    edges.addAll(getEdges(nodes));
    Set<Edge> removedEdges = removeEdgesFromGraph(edges);
    Set<Node> removedNodes = removeNodesFromGraph(nodes);
    notifyElementsRemoved(eventOriginator, removedNodes, removedEdges);
  }

  public boolean containsEdge(String id) {
    return this.edgeIndex.containsKey(id);
  }

  public GraphSettings getSettings() {
    return settings;
  }

  public Node getNode(String id) {
    return nodeIndex.get(id).getNode();
  }

  private NodeHolder getNodeHolder(String id) {
    return nodeIndex.get(id);
  }

  public Set<String> getNodeIds() {
    return this.nodeIndex.keySet();
  }

  public void addListener(GraphListener listener) {
    this.listeners.add(listener);
  }

  public void removeListener(GraphListener listener) {
    this.listeners.remove(listener);
  }

  private void notifyElementsAdded(Object originator, Collection<Node> nodes, Collection<Edge> edges) {
    if (!nodes.isEmpty() || !edges.isEmpty()) {
      for (GraphListener listener : this.listeners) {
        listener.elementsAdded(new GraphEvent(originator, nodes, edges));
      }
    }
  }

  private void notifyElementsRemoved(Object originator, Collection<Node> nodes, Collection<Edge> edges) {
    if (!nodes.isEmpty() || !edges.isEmpty()) {
      for (GraphListener listener : this.listeners) {
        listener.elementsRemoved(new GraphEvent(originator, nodes, edges));
      }
    }
  }

  private static class NodeHolder implements Cloneable {

    private Node node;

    private Map<String, Edge> incoming = new ConcurrentHashMap<>();
    private Map<String, Edge> outcoming = new ConcurrentHashMap<>();

    private NodeHolder() {
      super();
    }

    public NodeHolder(Node node) {
      super();
      this.node = node;
    }

    public void removeEdge(Edge edge) {
      this.incoming.remove(edge.getId());
      this.outcoming.remove(edge.getId());
    }

    public void addEdge(Edge edge) {
      String source = edge.getSource();
      String target = edge.getTarget();
      String id = node.getId();
      if (id.equals(source)) {
        outcoming.put(edge.getId(), edge);
      } else if (id.equals(target)) {
        incoming.put(edge.getId(), edge);
      }
    }

    public Set<Edge> getEdges() {
      Set<Edge> edges = new HashSet<>(incoming.values());
      edges.addAll(outcoming.values());
      return edges;
    }

    public Set<Edge> getIncomingEdges() {
      Set<Edge> edges = new HashSet<>(incoming.values());
      return edges;
    }

    public Set<Edge> getOutcomingEdges() {
      Set<Edge> edges = new HashSet<>(outcoming.values());
      return edges;
    }

    public Node getNode() {
      return node;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object clone() throws CloneNotSupportedException {
      NodeHolder clone = new NodeHolder();

      clone.node = (Node) node.clone();

      clone.incoming = (Map<String, Edge>) ((HashMap<String, Edge>) incoming).clone();
      clone.outcoming = (Map<String, Edge>) ((HashMap<String, Edge>) incoming).clone();

      return clone;
    }

  }

  @SuppressWarnings("unchecked")
  @Override
  public Object clone() throws CloneNotSupportedException {
    Graph clone = new Graph();
    clone.type = type;
    clone.settings = (GraphSettings) settings.clone();
    clone.edgeIndex = (Map<String, Edge>) ((HashMap<String, Edge>) edgeIndex).clone();
    clone.nodeIndex = (Map<String, NodeHolder>) ((HashMap<String, NodeHolder>) nodeIndex).clone();
    return clone;
  }

  public Graph cloneGraph() {
    try {
      return (Graph) clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public Set<Node> getNeighbours(Node node) {
    NodeHolder nodeHolder = this.nodeIndex.get(node.getId());
    Set<Edge> edges = nodeHolder.getEdges();
    return getNeighbours(node, edges);
  }

  public Set<Node> getOutcomingNeighbours(Node node) {
    NodeHolder nodeHolder = this.nodeIndex.get(node.getId());
    Set<Edge> edges = nodeHolder.getOutcomingEdges();
    return getNeighbours(node, edges);
  }

  private Set<Node> getNeighbours(Node node, Set<Edge> edges) {

    Set<Node> neighbours = new HashSet<>();
    for (Edge edge : edges) {
      String source = edge.getSource();
      String target = edge.getTarget();

      neighbours.add(this.nodeIndex.get(source).node);
      neighbours.add(this.nodeIndex.get(target).node);
    }
    neighbours.remove(node);
    return neighbours;
  }

  public Rectangle getBoundingBox() {
    Collection<NodeHolder> holders = nodeIndex.values();
    if (!holders.isEmpty()) {
      Iterator<NodeHolder> iterator = holders.iterator();

      Node first = iterator.next().node;
      Rectangle box = first.getBoundingBox();

      while (iterator.hasNext()) {
        box.add(iterator.next().node.getBoundingBox());
      }
      return box;
    } else {
      return null;
    }
  }

  public void ungroup(NodeGroup group) {
    removeNode(group);

    for (Node node : group.getNodes()) {
      groupPositioner.place(group, node);
    }

    addNodes(group.getNodes());

    Set<Node> neighbours = getNeighbours(group);
    for (Node neighbour : neighbours) {
      if (neighbour instanceof NodeGroup) {

      }
    }
//    addEdges(group.getEdges());
  }

}
