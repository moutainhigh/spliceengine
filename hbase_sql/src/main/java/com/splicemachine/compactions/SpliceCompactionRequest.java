package com.splicemachine.compactions;

import com.splicemachine.access.client.MemstoreAware;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.hbase.regionserver.compactions.CompactionRequest;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extension of CompactionRequest with a hook to block scans while Storefile's are being renamed
 * Created by dgomezferro on 3/24/16.
 */
public class SpliceCompactionRequest extends CompactionRequest {
    private static final Logger LOG = Logger.getLogger(SpliceCompactionRequest.class);
    private AtomicReference<MemstoreAware> memstoreAware;

    public void preStorefilesRename() throws IOException {
        assert memstoreAware != null;
        while (true) {
            MemstoreAware latest = memstoreAware.get();
            if (latest.currentScannerCount>0) {
                SpliceLogUtils.warn(LOG,"compaction Delayed waiting for scanners to complete scannersRemaining=%d",latest.currentScannerCount);
                try {
                    Thread.sleep(1000); // Have Split sleep for a second
                } catch (InterruptedException e1) {
                    throw new IOException(e1);
                }
                continue;
            }
            if(memstoreAware.compareAndSet(latest, MemstoreAware.incrementCompactionCount(latest)))
                break;
        }
    }

    public void setMemstoreAware(AtomicReference<MemstoreAware> memstoreAware) {
        this.memstoreAware = memstoreAware;
    }
}
