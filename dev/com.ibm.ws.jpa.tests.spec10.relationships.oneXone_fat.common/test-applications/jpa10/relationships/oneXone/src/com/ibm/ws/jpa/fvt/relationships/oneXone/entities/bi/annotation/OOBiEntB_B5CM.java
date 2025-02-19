/*******************************************************************************
 * Copyright (c) 2019, 2021 IBM Corporation and others.
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

package com.ibm.ws.jpa.fvt.relationships.oneXone.entities.bi.annotation;

import javax.persistence.Entity;
import javax.persistence.OneToOne;

import com.ibm.ws.jpa.fvt.relationships.oneXone.entities.IEntityA;

/**
 * Implementation entity for the inverse side of the B5CM bidirectional relationship defined on the entity BiEntityA.
 *
 */
@Entity
public class OOBiEntB_B5CM extends AbstractOneXOneBiEntityB {
    @OneToOne(mappedBy = "b5cm")
    private OOBiEntA entityA;

    public OOBiEntB_B5CM() {
        super();
    }

    public OOBiEntB_B5CM(int id, String name) {
        super(id, name);
    }

    public void setEntityA(OOBiEntA entityA) {
        this.entityA = entityA;
    }

    public OOBiEntA getEntityA() {
        return entityA;
    }

    @Override
    public IEntityA getEntityAField() {
        return getEntityA();
    }

    @Override
    public void setEntityAField(IEntityA entity) {
        setEntityA((OOBiEntA) entity);
    }

    @Override
    public String toString() {
        return "OOBiEntB_B5CM [getId()=" + getId() + ", getName()=" + getName() + "]";
    }
}