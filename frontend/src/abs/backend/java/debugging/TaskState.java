/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.java.debugging;

public enum TaskState{
    READY, SUSPENDED, RUNNING, FINISHED, DEADLOCKED, ASSERTION_FAILED, EXCEPTION, BLOCKED;
}