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
package com.ibm.samples.jaxws.spring.wsdlfirst.client;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.ibm.samples.jaxws.spring.wsdlfirst.stub.NoSuchCustomerException;

@WebServlet("/CustomerServiceClientGetCustomerServlet")
public class CustomerServiceClientGetCustomerServlet extends HttpServlet {
    private static final long serialVersionUID = -1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        PrintWriter out = response.getWriter();

        // Get the spring context and fetch our test client

        WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(getServletContext());

        CustomerServiceTester client = (CustomerServiceTester) context.getBean("tester");

        try {
            out.println(client.getCustomerByName("Smith", request.getLocalAddr(), request.getLocalPort()));
        } catch (NoSuchCustomerException e) {
            out.println(e.getMessage());
        }

    }
}
