/**
 * Copyright 2006-2017 the original author or authors.
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
package android.fluid.objenesis.instantiator.gcj;

import java.lang.reflect.InvocationTargetException;

import android.fluid.objenesis.ObjenesisException;
import android.fluid.objenesis.instantiator.annotations.Instantiator;
import android.fluid.objenesis.instantiator.annotations.Typology;

/**
 * Instantiates a class by making a call to internal GCJ private methods. It is only supposed to
 * work on GCJ JVMs. This instantiator will not call any constructors.
 *
 * @author Leonardo Mesquita
 * @see android.fluid.objenesis.instantiator.ObjectInstantiator
 */
/** @hide */
@Instantiator(Typology.STANDARD)
public class GCJInstantiator<T> extends GCJInstantiatorBase<T> {
   public GCJInstantiator(Class<T> type) {
      super(type);
   }

   @Override
   public T newInstance() {
      try {
         return type.cast(newObjectMethod.invoke(dummyStream, type, Object.class));
      }
      catch(RuntimeException e) {
         throw new ObjenesisException(e);
      }
      catch(IllegalAccessException e) {
         throw new ObjenesisException(e);
      }
      catch(InvocationTargetException e) {
         throw new ObjenesisException(e);
      }
   }
}
