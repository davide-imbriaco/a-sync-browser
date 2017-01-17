/*
 * Copyright (C) 2016 Davide Imbriaco
 *
 * This Java file is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.anyplace.syncbrowser.utils;

import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import static com.google.common.base.Objects.equal;

public class ViewUtils {

    public static Iterable<View> listViews(View rootView){
        return listViews(rootView,null);
    }

    public static <E extends View> Iterable<E> listViews(View rootView,@Nullable Class<E> clazz){
        if(clazz == null){
            if(rootView==null){
                return Collections.emptyList();
            }else {
                if(rootView instanceof ViewGroup){
                    List list=Lists.newArrayList();
                    ViewGroup viewGroup=(ViewGroup)rootView;
                    list.add(viewGroup);
                    for(int i=0;i<viewGroup.getChildCount();i++){
                        list.addAll((Collection)listViews(viewGroup.getChildAt(i),null));
                    }
                    return list;
                }else{
                    return (Iterable)Collections.singletonList(rootView);
                }
            }
        }else{
            return Iterables.filter((Iterable)listViews(rootView,null), Predicates.<Object>instanceOf(clazz));
        }
    }
}
