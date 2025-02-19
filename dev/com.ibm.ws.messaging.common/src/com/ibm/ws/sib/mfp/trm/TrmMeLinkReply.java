/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
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
package com.ibm.ws.sib.mfp.trm;

import com.ibm.ws.sib.utils.SIBUuid8;
import java.util.List;

/**
 * TrmMeLinkReply extends the general TrmFirstContactMessage
 * interface and provides get/set methods for all fields specific to a
 * TRM ME Link reply.
 *
 */
public interface TrmMeLinkReply extends TrmFirstContactMessage {

  /* **************************************************************************/
  /* Get Methods                                                              */
  /* **************************************************************************/

  /**
   *  Get the Magic Number from the message.
   *
   *  @return A long containing the Magic Number.
   */
  public long getMagicNumber();

  /**
   *  Get the Return Code from the message.
   *
   *  @return An Integer return code.
   */
  public Integer getReturnCode();

  /**
   *  Get the replying messaging engine UUID from the message.
   *
   *  @return The replying ME UUID.
   */
  public SIBUuid8 getReplyingMeUuid();

  /**
   *  Get the failure reason from the message.
   *
   *  @return A List of Strings containing the failure reason, if any.
   *          If there was not a failure, null will be returned.
   */
  public List getFailureReason();

  /* **************************************************************************/
  /* Set Methods                                                              */
  /* **************************************************************************/

  /**
   *  Set the Magic Number field in the message.
   *
   *  @param value  An long containing the Magic Number.
   */
  public void setMagicNumber(long value);

  /**
   *  Set the Return Code in the message.
   *
   *  @param value An int return code.
   */
  public void setReturnCode(int value);

  /**
   *  Set the replying messaging engine UUID in the message.
   *
   *  @param Value The replying ME UUID.
   */
  public void setReplyingMeUuid(SIBUuid8 value);

  /**
   *  Set the failure reason in the message.
   *
   *  @param value A List of Strings containing the failure reason.
   */
  public void setFailureReason(List value);

}
