package org.kharon;

import java.awt.event.MouseEvent;

public interface NodeListener {

  void nodeClicked(Node node, MouseEvent e);

  void nodePressed(Node node, MouseEvent e);

  void nodeReleased(Node node, MouseEvent e);

  void nodeDragStarted(Node node, MouseEvent e);

  void nodeDragStopped(Node node, MouseEvent e);

  void nodeDragged(Node node, MouseEvent e);

  void nodeHover(Node node, MouseEvent e);

  void nodeOut(MouseEvent e);

}
