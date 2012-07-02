/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brekka.stillingar.spring.snapshot;

import static java.lang.String.format;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.brekka.stillingar.core.ConfigurationException;
import org.brekka.stillingar.core.ConfigurationSource;
import org.brekka.stillingar.core.ConfigurationSourceLoader;
import org.brekka.stillingar.core.snapshot.DefaultSnapshot;
import org.brekka.stillingar.core.snapshot.Snapshot;
import org.brekka.stillingar.core.snapshot.SnapshotManager;
import org.brekka.stillingar.spring.resource.ResourceSelector;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Snapshot manager based around resources.
 * 
 * @author Andrew Taylor
 */
public class ResourceSnapshotManager implements SnapshotManager {

	private static final Log log = LogFactory
			.getLog(ResourceSnapshotManager.class);

	/**
	 * Will actually load the configuration sources
	 */
	private final ConfigurationSourceLoader configurationSourceLoader;

	/**
	 * Determines where the resources that the snapshots will be based on will be loaded from.
	 */
	private final ResourceSelector resourceSelector;
	
	/**
	 * Defaults
	 */
	private final Snapshot defaults;

	/**
	 * The last snapshot returned by {@link #retrieveLatest()}
	 */
	private Snapshot latestSnapshot;
	
	/**
	 * The lastModified of the original at the time we last tried to read it. If it throws an exception
	 * then we should not attempt to parse again until the resource last modified is greater than this
	 * value.
	 */
	private long lastAttempt;
	
	/**
	 * 
	 * @param resourceSelector Determines where the resources that the snapshots will be based on will be loaded from.
	 * @param snapshotLoader Will actually load the snapshots
	 */
	public ResourceSnapshotManager(
			ResourceSelector resourceSelector,
			ConfigurationSourceLoader configurationSourceLoader) {
		this.resourceSelector = resourceSelector;
		this.configurationSourceLoader = configurationSourceLoader;
		
		Resource defaults = resourceSelector.getDefaults();
        this.defaults = performLoad(defaults);
	}

	public Snapshot retrieveLatest() {
		Snapshot snapshot = null;
		Resource original = resourceSelector.getOriginal();
		try {
			long lastModifiedMillis = 0;
			if (latestSnapshot != null
			        && latestSnapshot.getTimestamp() != null) {
				lastModifiedMillis = latestSnapshot.getTimestamp().getTime();
			}
			long lastAttempt = this.lastAttempt;
			if (original.lastModified() > lastModifiedMillis
			        && original.lastModified() != lastAttempt) {
			    this.lastAttempt = original.lastModified();
				snapshot = performLoad(original);
				latestSnapshot = snapshot;
			}
		} catch (IOException e) {
			if (log.isWarnEnabled()) {
				log.warn(format("Failed to determine last modified for resource '%s'",
								original), e);
			}
		}
		return snapshot;
	}

	public Snapshot retrieveLastGood() {
		Resource lastGood = resourceSelector.getLastGood();
		Snapshot snapshot = performLoad(lastGood);
		this.latestSnapshot = snapshot;
		return snapshot;
	}
	
	/* (non-Javadoc)
	 * @see org.brekka.stillingar.core.snapshot.SnapshotManager#retrieveDefaults()
	 */
	@Override
	public Snapshot retrieveDefaults() {
	    return defaults;
	}

	public void acceptLatest() {
		Resource original = resourceSelector.getOriginal();
		Resource lastGood = resourceSelector.getLastGood();
		if (lastGood instanceof FileSystemResource) {
			FileSystemResource fsResource = (FileSystemResource) lastGood;
			File file = fsResource.getFile();
			InputStream is = null;
			OutputStream os = null;
			try {
				is = original.getInputStream();
				os = new FileOutputStream(file);
				copy(is, os);
			} catch (IOException e) {
				if (log.isWarnEnabled()) {
					log.warn(format(
					        "Failed to copy original resource '%s' to lastGood '%s'",
					        original, lastGood), e);
				}
			} finally {
				closeQuietly(is);
				closeQuietly(os);
			}
		}
	}
	
    /**
     * Perform the load operation that will convert a resource into a snapshot.
     * @param resourceToLoad the resouce to load into a snapshot
     * @return the snapshot loaded from the specified resource
     * @throws ConfigurationException if something goes wrong such as an IO error.
     */
    protected Snapshot performLoad(Resource resourceToLoad) {
        Snapshot snapshot = null;
        if (resourceToLoad != null && resourceToLoad.exists()
                && resourceToLoad.isReadable()) {
            InputStream sourceStream = null;
            try {
                sourceStream = resourceToLoad.getInputStream();
                long timestamp = resourceToLoad.lastModified();
                ConfigurationSource configurationSource = configurationSourceLoader.parse(sourceStream, null);
                snapshot = new DefaultSnapshot(configurationSource, new Date(timestamp), resourceToLoad.getURI());
            } catch (IOException e) {
                throw new ConfigurationException(format("Resouce '%s'", resourceToLoad), e);
            } catch (RuntimeException e) {
                // Wrap to include location details
                throw new ConfigurationException(format("Resouce '%s' processing problem", resourceToLoad), e);
            } finally {
                closeQuietly(sourceStream);
            }
        }
        return snapshot;
    }

    /**
     * Method content based on 'copyLarge' from Apache Commons IO.
     */
	private static long copy(InputStream input, OutputStream output)
			throws IOException {
		byte[] buffer = new byte[4096];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}
	
	/**
	 * Close the steams
	 */
    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // Ignore
        }
    }

}
