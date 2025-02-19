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
package android.fluid.objenesis.instantiator.basic;

import android.fluid.objenesis.ObjenesisException;
import android.fluid.objenesis.instantiator.ObjectInstantiator;
import android.fluid.objenesis.instantiator.annotations.Instantiator;
import android.fluid.objenesis.instantiator.annotations.Typology;

/**
 * The simplest instantiator - simply calls Class.newInstance(). This can deal with default public
 * constructors, but that's about it.
 *
 * @author Joe Walnes
 * @see ObjectInstantiator
 */
/** @hide */
@Instantiator(Typology.NOT_COMPLIANT)
public class NewInstanceInstantiator<T> implements ObjectInstantiator<T> {

   private final Class<T> type;

   public NewInstanceInstantiator(Class<T> type) {
      this.type = type;
   }

   public T newInstance() {
      try {
         return type.newInstance();
      }
      catch(Exception e) {
         throw new ObjenesisException(e);
      }
   }

}
