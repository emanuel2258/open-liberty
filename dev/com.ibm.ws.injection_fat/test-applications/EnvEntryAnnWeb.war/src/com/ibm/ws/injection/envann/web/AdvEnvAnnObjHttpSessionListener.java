/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation and others.
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
package com.ibm.ws.injection.envann.web;

import java.util.HashMap;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class AdvEnvAnnObjHttpSessionListener implements HttpSessionListener {
    private static final String CLASS_NAME = AdvEnvAnnObjHttpSessionListener.class.getName();
    private final static Logger svLogger = Logger.getLogger(CLASS_NAME);

    HashMap<String, Object> map;

    // Resources to be injected via field injection
    @Resource
    private String ifString;
    @Resource
    private Character ifCharacter;
    @Resource
    private Byte ifByte;
    @Resource
    private Short ifShort;
    @Resource
    private Integer ifInteger;
    @Resource
    private Long ifLong;
    @Resource
    private Boolean ifBoolean;
    @Resource
    private Double ifDouble;
    @Resource
    private Float ifFloat;

    // Resources to be injected via setter method injection
    private String imString;
    private Character imCharacter;
    private Byte imByte;
    private Short imShort;
    private Integer imInteger;
    private Long imLong;
    private Boolean imBoolean;
    private Double imDouble;
    private Float imFloat;

    public AdvEnvAnnObjHttpSessionListener() {
        map = new HashMap<String, Object>();
    }

    @Override
    public void sessionCreated(HttpSessionEvent arg0) {
        svLogger.info("Obj Http Session: Session Created...");
        populateMap();
        EnvAnnObjTestHelper.processRequest(CLASS_NAME, WCEventTracker.KEY_LISTENER_CREATED_AdvEnvAnnObjHttpSessionListener, map);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent arg0) {
        //Do Nothing
    }

    public void populateMap() {
        map.clear();
        map.put("ifString", ifString);
        map.put("ifCharacter", ifCharacter);
        map.put("ifByte", ifByte);
        map.put("ifShort", ifShort);
        map.put("ifInteger", ifInteger);
        map.put("ifLong", ifLong);
        map.put("ifBoolean", ifBoolean);
        map.put("ifDouble", ifDouble);
        map.put("ifFloat", ifFloat);

        map.put("imString", imString);
        map.put("imCharacter", imCharacter);
        map.put("imByte", imByte);
        map.put("imShort", imShort);
        map.put("imInteger", imInteger);
        map.put("imLong", imLong);
        map.put("imBoolean", imBoolean);
        map.put("imDouble", imDouble);
        map.put("imFloat", imFloat);
    }

    @Resource
    public void setImstring(String imstring) {
        this.imString = imstring;
    }

    @Resource
    public void setImchar(char imchar) {
        this.imCharacter = imchar;
    }

    @Resource
    public void setImbyte(byte imbyte) {
        this.imByte = imbyte;
    }

    @Resource
    public void setImshort(short imshort) {
        this.imShort = imshort;
    }

    @Resource
    public void setImint(int imint) {
        this.imInteger = imint;
    }

    @Resource
    public void setImlong(long imlong) {
        this.imLong = imlong;
    }

    @Resource
    public void setImboolean(boolean imboolean) {
        this.imBoolean = imboolean;
    }

    @Resource
    public void setImdouble(double imdouble) {
        this.imDouble = imdouble;
    }

    @Resource
    public void setImfloat(float imfloat) {
        this.imFloat = imfloat;
    }
}