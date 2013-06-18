package com.splicemachine.derby.impl.load;

import com.splicemachine.derby.hbase.SpliceDriver;
import com.splicemachine.derby.impl.job.ZkTask;
import com.splicemachine.derby.utils.Exceptions;
import com.splicemachine.derby.utils.marshall.KeyType;
import com.splicemachine.derby.utils.marshall.RowEncoder;
import com.splicemachine.derby.utils.marshall.RowType;
import com.splicemachine.hbase.CallBuffer;
import com.splicemachine.utils.SpliceLogUtils;
import com.splicemachine.utils.SpliceZooKeeperManager;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.DateTimeDataValue;
import org.apache.derby.impl.sql.execute.ValueRow;
import org.apache.derby.shared.common.reference.SQLState;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;

/**
 * @author Scott Fines
 * Created on: 4/5/13
 */
public abstract class AbstractImportTask extends ZkTask {
    private static final long serialVersionUID = 1l;
    protected ImportContext importContext;
    protected FileSystem fileSystem;
    private DateFormat dateParser;

    public AbstractImportTask() { }

    public AbstractImportTask(String jobId,
                              ImportContext importContext,
                              int priority,
                              String parentTransactionId) {
        super(jobId,priority,parentTransactionId,false);
        this.importContext = importContext;
    }

//    @Override
    protected String getTaskType() {
        return "importTask";
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(importContext);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        importContext = (ImportContext)in.readObject();
    }

    @Override
    public void prepareTask(RegionCoprocessorEnvironment rce, SpliceZooKeeperManager zooKeeper) throws ExecutionException {
        fileSystem = rce.getRegion().getFilesystem();
        super.prepareTask(rce, zooKeeper);
    }

    @Override
    public boolean invalidateOnClose() {
        return false;
    }

    @Override
    public void execute() throws ExecutionException, InterruptedException {
        try{
            ExecRow row = getExecRow(importContext);
            FormatableBitSet pkCols = importContext.getPrimaryKeys();

            CallBuffer<Mutation> writeBuffer = getCallBuffer();

            KeyType keyType = pkCols==null?KeyType.SALTED: KeyType.BARE;
            RowType rowType = RowType.DENSE_COLUMNAR;

            int[] keyColumns;
            int pos =0;
            if(pkCols!=null){
                keyColumns = new int[pkCols.size()];
                for(int i=pkCols.anySetBit();i!=-1;i=pkCols.anySetBit(i)){
                    keyColumns[pos] = i;
                    pos++;
                }
            }else
                keyColumns = new int[0];

            RowEncoder encoder = RowEncoder.create(row.nColumns(),keyColumns,null,null,keyType,rowType);

            Long numImported;
            try{
                numImported = importData(row,encoder,writeBuffer);
            }finally{
                writeBuffer.flushBuffer();
                writeBuffer.close();
            }
            SpliceLogUtils.info(LOG,"imported %d records to table %s",numImported,importContext.getTableName());
        } catch (StandardException e) {
            throw new ExecutionException(e);
        } catch (Exception e) {
            throw new ExecutionException(Exceptions.parseException(e));
        }
    }

    protected CallBuffer<Mutation> getCallBuffer() throws Exception {
        return SpliceDriver.driver().getTableWriter().writeBuffer(importContext.getTableName().getBytes());
    }

    protected abstract long importData(ExecRow row,
                                       RowEncoder rowEncoder,
                                       CallBuffer<Mutation> writeBuffer) throws Exception;

    protected void doImportRow(String transactionId,String[] line,FormatableBitSet activeCols, ExecRow row,
                               CallBuffer<Mutation> writeBuffer,
                             RowEncoder encoder) throws Exception {
        populateRow(line, activeCols, row);

        encoder.write(row,transactionId,writeBuffer);

//        Put put = Puts.buildInsertWithSerializer(rowSerializer.serialize(row.getRowArray()),row.getRowArray(),null,transactionId,serializer);
//        writeBuffer.add(put);
    }

    private void populateRow(String[] line, FormatableBitSet activeCols, ExecRow row) throws StandardException {
        if(activeCols!=null){
            for(int pos=0,activePos=activeCols.anySetBit();pos<line.length;pos++,activePos=activeCols.anySetBit(activePos)){
                row.getColumn(activePos+1).setValue(line[pos] == null || line[pos].length() == 0 ? null : line[pos]);  // pass in null for null or empty string
            }
        }else{
            for(int pos=0;pos<line.length-1;pos++){
                String elem = line[pos];
                setColumn(row, pos, elem);
            }
            //the last entry in the line array can be an empty string, which correlates to the row's nColumns() = line.length-1
            if(row.nColumns()==line.length){
                String lastEntry = line[line.length-1];
                setColumn(row, line.length-1, lastEntry);
            }
        }
    }

    private void setColumn(ExecRow row, int pos, String elem) throws StandardException {
        if(elem==null||elem.length()==0)
            elem=null;
        DataValueDescriptor dvd = row.getColumn(pos+1);
        if(elem!=null && dvd instanceof DateTimeDataValue){
            if(dateParser==null){
                String timestampFormat = importContext.getTimestampFormat();
                if(timestampFormat==null)
                    timestampFormat = "yyyy-mm-dd hh:mm:ss";
                dateParser = new SimpleDateFormat(timestampFormat);
            }
            try{
                Date parsed = dateParser.parse(elem);
                Timestamp ts = new Timestamp(parsed.getTime());
                dvd.setValue(ts);
            } catch (ParseException e) {
                throw StandardException.newException(SQLState.LANG_DATE_SYNTAX_EXCEPTION);
            }
        }else
            row.getColumn(pos+1).setValue(elem); // pass in null for null or empty string
    }

    protected void reportIntermediate(long numRecordsImported){
        //TODO -sf- log every few hundred thousand or something
    }

    protected ExecRow getExecRow(ImportContext context) throws StandardException {
        int[] columnTypes = context.getColumnTypes();
        FormatableBitSet activeCols = context.getActiveCols();
        ExecRow row = new ValueRow(columnTypes.length);
        if(activeCols!=null){
            for(int i=activeCols.anySetBit();i!=-1;i=activeCols.anySetBit(i)){
                row.setColumn(i+1,getDataValueDescriptor(columnTypes[i]));
            }
        }else{
            for(int i=0;i<columnTypes.length;i++){
                row.setColumn(i+1,getDataValueDescriptor(columnTypes[i]));
            }
        }
        return row;
    }

    private DataValueDescriptor getDataValueDescriptor(int columnType) throws StandardException {
        DataTypeDescriptor td = DataTypeDescriptor.getBuiltInDataTypeDescriptor(columnType);
        return td.getNull();
    }

    protected char getQuoteChar(ImportContext context) {
        String stripStr = context.getStripString();
        if(stripStr==null||stripStr.length()<=0)
            stripStr = "\"";
        return stripStr.charAt(0);
    }

    protected char getColumnDelimiter(ImportContext context) {
        String delimiter = context.getColumnDelimiter();
        if(delimiter==null||delimiter.length()<=0)
            delimiter = ",";
        return delimiter.charAt(0);
    }
}
