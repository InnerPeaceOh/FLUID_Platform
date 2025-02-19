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
package android.fluid.objenesis.strategy;

import android.fluid.objenesis.instantiator.ObjectInstantiator;
import android.fluid.objenesis.instantiator.android.Android10Instantiator;
import android.fluid.objenesis.instantiator.android.Android17Instantiator;
import android.fluid.objenesis.instantiator.android.Android18Instantiator;
import android.fluid.objenesis.instantiator.basic.AccessibleInstantiator;
import android.fluid.objenesis.instantiator.basic.ObjectInputStreamInstantiator;
import android.fluid.objenesis.instantiator.gcj.GCJInstantiator;
import android.fluid.objenesis.instantiator.perc.PercInstantiator;
import android.fluid.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;
import android.fluid.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

import java.io.Serializable;

import static android.fluid.objenesis.strategy.PlatformDescription.*;

/**
 * Guess the best instantiator for a given class. The instantiator will instantiate the class
 * without calling any constructor. Currently, the selection doesn't depend on the class. It relies
 * on the
 * <ul>
 * <li>JVM version</li>
 * <li>JVM vendor</li>
 * <li>JVM vendor version</li>
 * </ul>
 * However, instantiators are stateful and so dedicated to their class.
 *
 * @author Henri Tremblay
 * @see ObjectInstantiator
 */
/** @hide */
public class StdInstantiatorStrategy extends BaseInstantiatorStrategy {

   /**
    * Return an {@link ObjectInstantiator} allowing to create instance without any constructor being
    * called.
    *
    * @param type Class to instantiate
    * @return The ObjectInstantiator for the class
    */
   public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {

      if(PlatformDescription.isThisJVM(HOTSPOT) || PlatformDescription.isThisJVM(OPENJDK)) {
         // Java 7 GAE was under a security manager so we use a degraded system
         if(PlatformDescription.isGoogleAppEngine() && PlatformDescription.SPECIFICATION_VERSION.equals("1.7")) {
            if(Serializable.class.isAssignableFrom(type)) {
               return new ObjectInputStreamInstantiator<T>(type);
            }
            return new AccessibleInstantiator<T>(type);
         }
         // The UnsafeFactoryInstantiator would also work. But according to benchmarks, it is 2.5
         // times slower. So I prefer to use this one
         return new SunReflectionFactoryInstantiator<T>(type);
      }
      else if(PlatformDescription.isThisJVM(DALVIK)) {
         if(PlatformDescription.isAndroidOpenJDK()) {
            // Starting at Android N which is based on OpenJDK
            return new UnsafeFactoryInstantiator<T>(type);
         }
         if(ANDROID_VERSION <= 10) {
            // Android 2.3 Gingerbread and lower
            return new Android10Instantiator<T>(type);
         }
         if(ANDROID_VERSION <= 17) {
            // Android 3.0 Honeycomb to 4.2 Jelly Bean
            return new Android17Instantiator<T>(type);
         }
         // Android 4.3 until Android N
         return new Android18Instantiator<T>(type);
      }
      else if(PlatformDescription.isThisJVM(JROCKIT)) {
         // JRockit is compliant with HotSpot
         return new SunReflectionFactoryInstantiator<T>(type);
      }
      else if(PlatformDescription.isThisJVM(GNU)) {
         return new GCJInstantiator<T>(type);
      }
      else if(PlatformDescription.isThisJVM(PERC)) {
         return new PercInstantiator<T>(type);
      }

      // Fallback instantiator, should work with most modern JVM
      return new UnsafeFactoryInstantiator<T>(type);

   }
}
