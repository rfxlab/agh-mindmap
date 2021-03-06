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

import com.actionbarsherlock.app.SherlockFragment
import android.os.Bundle
import android.view.{View, ViewGroup, LayoutInflater}
import edu.agh.mindmap.R
import android.widget.{AdapterView, ArrayAdapter, TextView, ListView}
import AdapterView.OnItemLongClickListener
import com.michalrus.android.helper.Helper
import edu.agh.mindmap.model.MindMap
import android.widget.AdapterView.OnItemClickListener
import edu.agh.mindmap.activity.MainActivity
import android.content.Context
import java.util.UUID
import edu.agh.mindmap.util.Exporter

object MapListFragment {
  val ItemXml = R.layout.recent_list_item
}

class MapListFragment extends SherlockFragment with Helper {
  import collection.mutable

  private val uuids = new mutable.TreeSet[UUID]

  private object runAfterCreation {
    private var memo = Vector.empty[() => Unit]
    def apply(r: () => Unit): Unit = synchronized {
      memo :+= r
      Option(getView) foreach flush
    }
    def flush(v: View) = synchronized {
      memo foreach (v post _)
      memo = Vector.empty[() => Unit]
    }
  }

  def addMaps(maps: Seq[MindMap]) {
    runAfterCreation { () =>
      maps foreach { m =>
        if (uuids contains m.uuid) {
          var i = 0; var count = adapter.getCount
          while (i < count) {
            val item = adapter getItem i
            if (item.uuid == m.uuid) {
              adapter remove item
              count -= 1
            } else i += 1
          }
        }
        val _ = uuids += m.uuid
        adapter add m
      }
      adapter notifyDataSetChanged()
    }
  }

  private lazy val adapter = new ArrayAdapter(getActivity, MapListFragment.ItemXml, new java.util.ArrayList[MindMap]) {
    private lazy val inflater = getActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      val v = Option(convertView) match {
        case Some(x) => x
        case _ => inflater.inflate(MapListFragment.ItemXml, parent, false)
      }

      val map = getItem(position)

      val name = v.find[TextView](R.id.recent_list_item_name)
      name setText (map.root flatMap (_.content) getOrElse "")

      val detail = v.find[TextView](R.id.recent_list_item_detail)
      detail setText new java.util.Date(map.lastMod).toString

      v
    }
  }

  def withMainActivity(f: MainActivity => Unit) {
    getActivity match {
      case a: MainActivity => f(a)
      case _ =>
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, bundle: Bundle) = {
    val view = inflater.inflate(R.layout.recent_list, container, false)
    val listView = view.find[ListView](R.id.listview)

    listView setAdapter adapter

    listView setOnItemClickListener new OnItemClickListener {
      override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
        val map = adapter getItem position
        withMainActivity (_ viewMindMap map)
      }
    }

    listView setOnItemLongClickListener new OnItemLongClickListener {
      def onItemLongClick(parent: AdapterView[_], view: View, position: Int, id: Long) = {
        Exporter export (getActivity, adapter getItem position)
        true
      }
    }

    runAfterCreation flush view
    view
  }

}
