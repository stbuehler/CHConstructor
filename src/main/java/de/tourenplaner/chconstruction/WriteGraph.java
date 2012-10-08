/*
 * (C) Copyright 2012 Peter Vollmer
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import java.io.OutputStream;

/**
 * User: Peter Vollmer
 * Date: 10/8/12
 * Time: 11:24 AM
 */
public interface WriteGraph {
    void writeRAMGraph (OutputStream out, RAMGraph ramGraph);
}
