/*
 * Copyright 2013 Katarzyna Szawan <kat.szwn@gmail.com>
 *     and Michał Rus <https://michalrus.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.agh.mindmap.fragment

import com.michalrus.android.helper.Helper
import com.actionbarsherlock.app.SherlockFragment
import android.view._
import android.os.Bundle
import edu.agh.mindmap.R
import java.util.UUID
import edu.agh.mindmap.model.{MindNode, MindMap}
import edu.agh.mindmap.component.{NodeView, HorizontalScrollViewWithPropagation}
import android.widget._
import edu.agh.mindmap.util.{Refresher, MapPainter}
import scala.util.Try
import android.content.{DialogInterface, Context}
import android.view.inputmethod.{EditorInfo, InputMethodManager}
import android.widget.TextView.OnEditorActionListener
import android.view.View.OnFocusChangeListener
import android.app.AlertDialog

class MapFragment extends SherlockFragment with Helper {
  implicit def activity = getActivity

  private var painter: Option[MapPainter] = None

  private lazy val dummyFocus = getView.find[View](R.id.dummy_focus)
  private lazy val inputManager = safen(getActivity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager])

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, bundle: Bundle) = {
    val view = inflater.inflate(R.layout.map, container, false)

    painter = Some(new MapPainter(
      dp2px,
      nodeLayoutId = R.layout.mind_node,
      paperPadding = 20, // [dp]
      subtreeMargin = 5, // [dp]
      childHorizontalDistance = 50, // [dp]
      arcShortRadius,
      nodeViewSize,
      initializeNodeView,
      updateNodeView
    ))

    for {
      uuid <- Try(UUID fromString (getArguments getString "uuid"))
      map <- MindMap findByUuid uuid
      painter <- painter
      paper = view.find[RelativeLayout](R.id.paper)
      hScroll = view.find[HorizontalScrollViewWithPropagation](R.id.hscroll)
      vScroll = view.find[ScrollView](R.id.vscroll)
    } {
      hScroll.inner = Some(vScroll)
      paper onClick defocus()
      laterOnUiThread { () =>
        painter paint (map, paper, inflater)
      }
    }

    view
  }

  def refreshMap() {
    for (painter <- painter)
      laterOnUiThread { () => painter.repaint() }
  }

  /**
   * Initialize inflated `R.layout.mind_node` view (event listeners and stuff).
   * @param node A node from the model.
   * @param v An inflated `R.layout.mind_node`.
   */
  def initializeNodeView(node: MindNode, v: NodeView) {
    v.addButton onClick addChildTo(node)
    v.content onLongClick removeNode(node)
    v.content setBackgroundColor randomColor

    v.content setOnEditorActionListener new OnEditorActionListener {
      def onEditorAction(vv: TextView, actionId: Int, event: KeyEvent) =
        if (actionId == EditorInfo.IME_ACTION_DONE) { defocus(); true }
        else false
    }

    v.content setOnFocusChangeListener new OnFocusChangeListener {
      def onFocusChange(vv: View, hasFocus: Boolean) =
        if (!hasFocus && !node.isRemoved) {
          val newContent = Some(v.content.getText.toString)
          if (newContent != node.content) {
            node.content = newContent
            Refresher.refresh(node.map.uuid, refreshDrawing = true)
          }
        }
    }
  }

  /**
   * Update `R.layout.mind_node` view with accordance to the model.
   * @param node A node from the model.
   * @param v An inflated `R.layout.mind_node`.
   */
  def updateNodeView(node: MindNode, v: NodeView) = {
    val cnt = node.content getOrElse ""
    if (v.content.getText.toString != cnt && !v.content.hasFocus) v.content setText cnt
  }

  def arcShortRadius(numChildren: Int): Int = // [dp]
    if (numChildren <= 3) 50
    else if (numChildren <= 8) 100
    else 150

  /**
   * Provide the size of a to-be-inflated `R.layout.mind_node`.
   * @param node A node from the model.
   * @return `(width, height)` in DP
   */
  def nodeViewSize(node: MindNode): (Int, Int) =
    node.content match {
      case Some(s) =>
        if (s.length > 0) {
          val w = s.length * 12
          if (w > 200) (205, 45 * (w / 200 + 1))
          else (w, 45)
        }
        else
          (150, 45)
      case None =>
        (0, 0)


  }

  def focusOn(t: EditText) {
    val _ = t requestFocus()
    for (imm <- inputManager) laterOnUiThread { () => val _ = imm showSoftInput (t, 0) }
  }

  def defocus(hideIME: Boolean = true) {
    val _ = dummyFocus requestFocus()
    if (hideIME) for (imm <- inputManager) laterOnUiThread { () =>
      val _ = imm hideSoftInputFromWindow (dummyFocus.getWindowToken, 0)
    }
  }

  def addChildTo(node: MindNode) = {
    defocus(hideIME = false)
    laterOnUiThread { () =>
      val ord = if (node.children.isEmpty) 0 else (node.children map (_.ordering)).max
      val newNode = MindNode createChildOf (node, ord + 10)
      painter foreach (_ repaint())

      for {
        painter <- painter
        v <- painter viewFor newNode
      } focusOn(v.find[EditText](R.id.content))
    }
  }

  def removeNode(node: MindNode) = {
    defocus(hideIME = false)
    if (node.isRoot) false
    else {
      val builder = new AlertDialog.Builder(getActivity)
      val _ = builder setMessage
        getString(R.string.sure_to_delete_node, node.content getOrElse "") setPositiveButton(android.R.string.yes,
        new DialogInterface.OnClickListener {
          def onClick(dialog: DialogInterface, which: Int) =
            laterOnUiThread { () =>
              node remove()
              painter foreach (_ repaint())
            }
        }) setNegativeButton(android.R.string.no,
        new DialogInterface.OnClickListener {
          def onClick(dialog: DialogInterface, which: Int) {}
        }) show()

      true
    }
  }
}
