/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
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
package com.ibm.ws.jaxws.ejbbasic;

import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.xml.ws.soap.Addressing;

@Stateless
@WebService
@Addressing(enabled = true, required = true)
public class UserQuery {

    public User getUser(String userName) throws UserNotFoundException {
        return StaticUserRepository.getUser(userName);
    }

    public User[] listUsers() {
        return StaticUserRepository.listUsers();
    }
}
