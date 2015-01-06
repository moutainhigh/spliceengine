package com.splicemachine.derby.hbase;

import com.splicemachine.pipeline.coprocessor.BatchProtocol;
import com.splicemachine.pipeline.impl.BulkWrites;
import com.splicemachine.pipeline.impl.BulkWritesResult;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.BaseEndpointCoprocessor;

import java.io.IOException;

public class SpliceIndexEndpoint extends BaseEndpointCoprocessor implements BatchProtocol, IndexEndpoint {

    private SpliceBaseIndexEndpoint endpoint;

    @Override
    public void start(CoprocessorEnvironment env) {
        endpoint = new SpliceBaseIndexEndpoint();
        endpoint.start(env);
        super.start(env);
    }

    @Override
    public void stop(CoprocessorEnvironment env) {
        endpoint.stop(env);
    }

    @Override
    public byte[] bulkWrites(byte[] bulkWrites) throws IOException {
        return endpoint.bulkWrites(bulkWrites);
    }

    @Override
    public BulkWritesResult bulkWrite(BulkWrites bulkWrites) throws IOException {
        return endpoint.bulkWrite(bulkWrites);
    }

    @Override
    public SpliceBaseIndexEndpoint getBaseIndexEndpoint() {
        return endpoint;
    }
}