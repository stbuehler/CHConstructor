/*
 * (C) Copyright 2012 Peter Vollmer
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: Peter Vollmer
 * Date: 10/8/12
 * Time: 11:17 AM
 */
public interface GraphReader {
    RAMGraph createRAMGraph(InputStream in) throws IOException;
}
