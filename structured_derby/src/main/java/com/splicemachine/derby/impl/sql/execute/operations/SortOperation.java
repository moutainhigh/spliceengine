
package com.splicemachine.derby.impl.sql.execute.operations;

import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;
import com.splicemachine.derby.hbase.SpliceObserverInstructions;
import com.splicemachine.derby.hbase.SpliceOperationCoprocessor;
import com.splicemachine.derby.iapi.sql.execute.SinkingOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceNoPutResultSet;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.iapi.storage.RowProvider;
import com.splicemachine.derby.impl.job.operation.SuccessFilter;
import com.splicemachine.derby.impl.sql.execute.Serializer;
import com.splicemachine.derby.impl.storage.ClientScanProvider;
import com.splicemachine.derby.utils.*;
import com.splicemachine.derby.utils.marshall.KeyType;
import com.splicemachine.derby.utils.marshall.RowEncoder;
import com.splicemachine.derby.utils.marshall.RowType;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.job.JobStats;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.io.FormatableArrayHolder;
import org.apache.derby.iapi.services.loader.GeneratedMethod;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.NoPutResultSet;
import org.apache.derby.iapi.store.access.ColumnOrdering;
import org.apache.derby.shared.common.reference.SQLState;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

public class SortOperation extends SpliceBaseOperation implements SinkingOperation {
    private static final long serialVersionUID = 2l;
    private static Logger LOG = Logger.getLogger(SortOperation.class);
    private static final List<NodeType> nodeTypes;
    protected NoPutResultSet source;
    protected boolean distinct;
    protected int orderingItem;
    protected int[] keyColumns;
    protected boolean[] descColumns; //descColumns[i] = false => column[i] sorted descending, else sorted ascending
    private ExecRow sortResult;
    private int numColumns;
    private Scan reduceScan;
    private ExecRow execRowDefinition = null;
    private Properties sortProperties = new Properties();

    static {
        nodeTypes = Arrays.asList(NodeType.REDUCE, NodeType.SCAN);
    }

    /*
     * Used for serialization. DO NOT USE
     */
    @Deprecated
    public SortOperation() {
//		SpliceLogUtils.trace(LOG, "instantiated without parameters");
    }

    public SortOperation(NoPutResultSet s,
                         boolean distinct,
                         int orderingItem,
                         int numColumns,
                         Activation a,
                         GeneratedMethod ra,
                         int resultSetNumber,
                         double optimizerEstimatedRowCount,
                         double optimizerEstimatedCost) throws StandardException {
        super(a, resultSetNumber, optimizerEstimatedRowCount, optimizerEstimatedCost);
        this.source = s;
        this.distinct = distinct;
        this.orderingItem = orderingItem;
        this.numColumns = numColumns;
        init(SpliceOperationContext.newContext(a));
        recordConstructorTime();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
//		SpliceLogUtils.trace(LOG, "readExternal");
        super.readExternal(in);
        source = (SpliceOperation) in.readObject();
        distinct = in.readBoolean();
        orderingItem = in.readInt();
        numColumns = in.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        SpliceLogUtils.trace(LOG, "writeExternal");
        super.writeExternal(out);
        out.writeObject(source);
        out.writeBoolean(distinct);
        out.writeInt(orderingItem);
        out.writeInt(numColumns);
    }

    @Override
    public List<NodeType> getNodeTypes() {
        return nodeTypes;
    }

    @Override
    public List<SpliceOperation> getSubOperations() {
        SpliceLogUtils.trace(LOG, "getSubOperations");
        List<SpliceOperation> ops = new ArrayList<SpliceOperation>();
        ops.add((SpliceOperation) source);
        return ops;
    }

    @Override
    public void init(SpliceOperationContext context) throws StandardException {
        SpliceLogUtils.trace(LOG, "init");
        super.init(context);
        ((SpliceOperation) source).init(context);

        FormatableArrayHolder fah = null;
        for (Object o : activation.getPreparedStatement().getSavedObjects()) {
            if (o instanceof FormatableArrayHolder) {
                fah = (FormatableArrayHolder) o;
                break;
            }
        }
        if (fah == null) {
            LOG.error("Unable to find column ordering for sorting!");
            throw new RuntimeException("Unable to find Column ordering for sorting!");
        }
        ColumnOrdering[] order = (ColumnOrdering[]) fah.getArray(ColumnOrdering.class);

        keyColumns = new int[order.length];
        descColumns = new boolean[order.length];
        descColumns = new boolean[getExecRowDefinition().getRowArray().length];

        for (int i = 0; i < order.length; i++) {
            keyColumns[i] = order[i].getColumnId();
            descColumns[keyColumns[i]] = order[i].getIsAscending();
        }
    }

    public ExecRow getNextSinkRow() throws StandardException {
        ExecRow sinkRow = source.getNextRowCore();
        if (sinkRow != null){
            setCurrentRow(sinkRow);
        }
        return sinkRow;
    }

    @Override
    public ExecRow getNextRowCore() throws StandardException {
        SpliceLogUtils.trace(LOG, "getNextRowCore");
        sortResult = getNextRowFromScan();
        if (sortResult != null)
            setCurrentRow(sortResult);
        return sortResult;
    }

    private ExecRow getNextRowFromScan() throws StandardException {
        List<KeyValue> keyValues = new ArrayList<KeyValue>();
        try {
            regionScanner.next(keyValues);
        } catch (IOException ioe) {
            SpliceLogUtils.logAndThrow(LOG,
                    StandardException.newException(SQLState.DATA_UNEXPECTED_EXCEPTION, ioe));
        }
        Result result = new Result(keyValues); // XXX - TODO FIX JLEACH
        if (keyValues.isEmpty()) {
            return null;
        } else {
            ExecRow row = getExecRowDefinition().getClone();
            SpliceUtils.populate(result, null, row.getRowArray());
            return row;
        }
    }

    @Override
    public SpliceOperation getLeftOperation() {
//		SpliceLogUtils.trace(LOG,"getLeftOperation");
        return (SpliceOperation) this.source;
    }

    @Override
    public ExecRow getExecRowDefinition() throws StandardException {
//		SpliceLogUtils.trace(LOG, "getExecRowDefinition");
        if (execRowDefinition == null){
            execRowDefinition = ((SpliceOperation) source).getExecRowDefinition();
        }
        return execRowDefinition;
    }

    @Override
    public int[] getRootAccessedCols(long tableNumber) {
        return ((SpliceOperation) source).getRootAccessedCols(tableNumber);
    }

    @Override
    public boolean isReferencingTable(long tableNumber) {
        return ((SpliceOperation) source).isReferencingTable(tableNumber);
    }

    @Override
    public RowProvider getReduceRowProvider(SpliceOperation top, ExecRow template) throws StandardException {
        try {
            reduceScan = Scans.buildPrefixRangeScan(sequence[0], SpliceUtils.NA_TRANSACTION_ID);
            if (failedTasks.size() > 0 && !distinct) {
                //we don't need the filter when distinct is true, because we'll overwrite duplicates anyway
                reduceScan.setFilter(new SuccessFilter(failedTasks, distinct));
            }
        } catch (IOException e) {
            throw Exceptions.parseException(e);
        }
//		SpliceUtils.setInstructions(reduceScan,getActivation(),top);
		return new ClientScanProvider(SpliceOperationCoprocessor.TEMP_TABLE,reduceScan,template,null,getRowEncoder().getDual(template));
	}



    @Override
    public NoPutResultSet executeScan() throws StandardException {
        RowProvider provider = getReduceRowProvider(this, getExecRowDefinition());
        SpliceNoPutResultSet rs = new SpliceNoPutResultSet(activation, this, provider);
        nextTime += getCurrentTimeMillis() - beginTime;
        return rs;
    }

    @Override
    public RowEncoder getRowEncoder() throws StandardException {
        ExecRow def = getExecRowDefinition();
        KeyType keyType = distinct? KeyType.FIXED_PREFIX: KeyType.FIXED_PREFIX_UNIQUE_POSTFIX;
        return RowEncoder.create(def.nColumns(), keyColumns, descColumns, DerbyBytesUtil.generateBytes(sequence[0]), keyType, RowType.COLUMNAR);
    }

    @Override
    public RowProvider getMapRowProvider(SpliceOperation top, ExecRow template) throws StandardException {
        return getReduceRowProvider(top, template);
    }

    @Override
    protected JobStats doShuffle() throws StandardException {
        long start = System.currentTimeMillis();
        final RowProvider rowProvider = ((SpliceOperation) source).getMapRowProvider(this, getExecRowDefinition());

        nextTime += System.currentTimeMillis() - start;
        SpliceObserverInstructions soi = SpliceObserverInstructions.create(getActivation(), this);
        return rowProvider.shuffleRows(soi);
    }

    @Override
    public String toString() {
        return "SortOperation {resultSetNumber=" + resultSetNumber + ",source=" + source + "}";
    }

    @Override
    public void openCore() throws StandardException {
        super.openCore();
        if (source != null) source.openCore();
    }

    public NoPutResultSet getSource() {
        return this.source;
    }

    public boolean needsDistinct() {
        return this.distinct;
    }

    @Override
    public void close() throws StandardException {
        SpliceLogUtils.trace(LOG, "close in Sort");
        beginTime = getCurrentTimeMillis();
        if (isOpen) {
            clearCurrentRow();

            sortResult = null;
            source.close();

            super.close();
        }

        closeTime += getElapsedMillis(beginTime);

        isOpen = false;
    }

    @Override
    public long getTimeSpent(int type) {
        long totTime = constructorTime + openTime + nextTime + closeTime;

        if (type == NoPutResultSet.CURRENT_RESULTSET_ONLY)
            return totTime - source.getTimeSpent(ENTIRE_RESULTSET_TREE);
        else
            return totTime;
    }

    public Properties getSortProperties() {
        if (sortProperties == null)
            sortProperties = new Properties();

        sortProperties.setProperty("numRowsInput", "" + getRowsInput());
        sortProperties.setProperty("numRowsOutput", "" + getRowsOutput());
        return sortProperties;
    }

    public long getRowsInput() {
        return getRegionStats() == null ? 0l : getRegionStats().getTotalProcessedRecords();
    }

    public long getRowsOutput() {
        return getRegionStats() == null ? 0l : getRegionStats().getTotalSunkRecords();
    }

    @Override
    public String prettyPrint(int indentLevel) {
        String indent = "\n" + Strings.repeat("\t", indentLevel);

        return new StringBuilder("Sort:")
                .append(indent).append("resultSetNumber:").append(resultSetNumber)
                .append(indent).append("distinct:").append(distinct)
                .append(indent).append("orderingItem:").append(orderingItem)
                .append(indent).append("keyColumns:").append(Arrays.toString(keyColumns))
                .append(indent).append("source:").append(((SpliceOperation) source).prettyPrint(indentLevel + 1))
                .toString();
    }
}
