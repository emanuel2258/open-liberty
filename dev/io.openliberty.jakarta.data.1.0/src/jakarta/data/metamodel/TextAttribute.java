/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package jakarta.data.metamodel;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.data.Sort;
import jakarta.data.exceptions.MappingException;
import jakarta.data.metamodel.Attribute;

/**
 * Method signatures are copied from Jakarta Data.
 */
public interface TextAttribute extends SortableAttribute {
    public Sort ascIgnoreCase();

    public Sort descIgnoreCase();

    public static TextAttribute get() {
        return new TextAttribute() {
            private final AtomicReference<TextAttribute> impl = new AtomicReference<TextAttribute>();

            private final TextAttribute attr() {
                TextAttribute attr = impl.get();
                if (attr == null)
                    throw new MappingException("The static metamodel for this attribute has not been initialized by a Jakarta Data provider.");
                return attr;

            }

            @Override
            public Sort asc() {
                return attr().asc();
            }

            @Override
            public Sort ascIgnoreCase() {
                return attr().ascIgnoreCase();
            }

            @Override
            public Sort desc() {
                return attr().desc();
            }

            @Override
            public Sort descIgnoreCase() {
                return attr().descIgnoreCase();
            }

            @Override
            public boolean init(Attribute attr) {
                if (attr instanceof TextAttribute)
                    return impl.compareAndSet(null, (TextAttribute) attr);
                else if (attr == null)
                    throw new NullPointerException();
                else
                    throw new IllegalArgumentException(attr.getClass().getName() + " is not an instance of " +
                                                       getClass().getName());
            }

            @Override
            public String name() {
                return attr().name();
            }

            @Override
            public String toString() {
                TextAttribute attr = impl.get();
                String attrName = attr == null ? "[uninitialized]" : attr.name();
                String className = getClass().getSimpleName();
                return new StringBuilder(className.length() + 1 + attrName.length())
                                .append(className).append(':').append(attrName)
                                .toString();
            }
        };
    }
}
