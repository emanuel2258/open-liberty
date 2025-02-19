/*******************************************************************************
 * Copyright (c) 2022, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.security.mp.jwt21.fat.featureSupportTests.sharedTests;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.ws.security.mp.jwt21.fat.sharedTests.GenericEnvVarsAndSystemPropertiesTests;

import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;

@Mode(TestMode.FULL)
@RunWith(FATRunner.class)
public class OlderMpJwtFeatures_GenericEnvVarsAndSystemPropertiesTests_ClockSkew extends GenericEnvVarsAndSystemPropertiesTests {

    public static Class<?> thisClass = OlderMpJwtFeatures_GenericEnvVarsAndSystemPropertiesTests_ClockSkew.class;

    public static LibertyServer resourceServer;

    @Test
    public void OlderMpJwtFeatures_GenericEnvVarsAndSystemPropertiesTests_ClockSkew_test() throws Exception {
        genericUsePropTest(5, null);
    }

}
