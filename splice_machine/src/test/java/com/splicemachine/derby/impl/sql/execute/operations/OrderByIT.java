/*
 * Copyright (c) 2012 - 2017 Splice Machine, Inc.
 *
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.homeless.TestUtils;
import com.splicemachine.test_tools.TableCreator;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.ResultSet;

import static com.splicemachine.test_tools.Rows.row;
import static com.splicemachine.test_tools.Rows.rows;

/**
 * Created by yxia on 5/12/17.
 */
public class OrderByIT extends SpliceUnitTest {
    public static final String CLASS_NAME = OrderByIT.class.getSimpleName().toUpperCase();
    protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher(CLASS_NAME);
    protected static SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(spliceSchemaWatcher);
    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(CLASS_NAME);

    public static void createData(Connection conn, String schemaName) throws Exception {

        new TableCreator(conn)
                .withCreate("create table t1 (a1 int, b1 int, c1 int, d1 int, e1 int)")
                .withInsert("insert into t1 values(?,?,?,?,?)")
                .withRows(rows(
                        row(1,1,1,1,1),
                        row(2,null,null,null,null),
                        row(1,null,null,null,null),
                        row(1,2,1,1,1)))
                .create();

        conn.commit();
    }

    @BeforeClass
    public static void createDataSet() throws Exception {
        createData(spliceClassWatcher.getOrCreateConnection(), spliceSchemaWatcher.toString());
    }

    @Test
    public void testOrderByClauseWithNullThroughControlPath() throws Exception {
        // test default path, where Null ordering is not specified
        // Nulls is treated as larger than any other value
        String sqlText = "select a1,b1 from t1 --splice-properties useSpark=false\n order by a1,b1";
        String expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |  1  |\n" +
                " 1 |  2  |\n" +
                " 1 |NULL |\n" +
                " 2 |NULL |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        sqlText = "select a1,b1 from t1 --splice-properties useSpark=false\n order by a1,b1 desc";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |NULL |\n" +
                " 1 |  2  |\n" +
                " 1 |  1  |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with asc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=false\n order by a1,b1 nulls first";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |NULL |\n" +
                " 1 |  1  |\n" +
                " 1 |  2  |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with asc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=false\n order by a1,b1 nulls last";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |  1  |\n" +
                " 1 |  2  |\n" +
                " 1 |NULL |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with desc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=false\n order by a1,b1 desc nulls first";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |NULL |\n" +
                " 1 |  2  |\n" +
                " 1 |  1  |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with desc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=false\n order by a1,b1 desc nulls last";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |  2  |\n" +
                " 1 |  1  |\n" +
                " 1 |NULL |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testOrderByClauseWithNullThroughSparkPath() throws Exception {
        // test default path, where Null ordering is not specified
        // Nulls is treated as larger than any other value
        String sqlText = "select a1,b1 from t1 --splice-properties useSpark=true\n order by a1,b1";
        String expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |  1  |\n" +
                " 1 |  2  |\n" +
                " 1 |NULL |\n" +
                " 2 |NULL |";

        ResultSet rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        sqlText = "select a1,b1 from t1 --splice-properties useSpark=true\n order by a1,b1 desc";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |NULL |\n" +
                " 1 |  2  |\n" +
                " 1 |  1  |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with asc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=true\n order by a1,b1 nulls first";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |NULL |\n" +
                " 1 |  1  |\n" +
                " 1 |  2  |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with asc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=true\n order by a1,b1 nulls last";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |  1  |\n" +
                " 1 |  2  |\n" +
                " 1 |NULL |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with desc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=true\n order by a1,b1 desc nulls first";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |NULL |\n" +
                " 1 |  2  |\n" +
                " 1 |  1  |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with desc order
        sqlText = "select a1,b1 from t1 --splice-properties useSpark=true\n order by a1,b1 desc nulls last";
        expected = "A1 | B1  |\n" +
                "----------\n" +
                " 1 |  2  |\n" +
                " 1 |  1  |\n" +
                " 1 |NULL |\n" +
                " 2 |NULL |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testOrderByClauseWithNullInWindowFunctionThroughControlPath() throws Exception {
        // test default path
        // Nulls is treated as larger than any other value
        String sqlText = "select a1,b1, row_number() over (partition by a1 order by b1) rnk\n" +
                "from t1 --splice-properties useSpark=false\n order by a1, rnk";
        String expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |  1  |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |NULL |  3  |\n" +
                " 2 |NULL |  1  |";
        ResultSet rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 desc) rnk\n" +
                "from t1 --splice-properties useSpark=false\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |NULL |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |  1  |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with asc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 nulls first) rnk\n" +
                "from t1 --splice-properties useSpark=false\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |NULL |  1  |\n" +
                " 1 |  1  |  2  |\n" +
                " 1 |  2  |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with asc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 nulls last) rnk\n" +
                "from t1 --splice-properties useSpark=false\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |  1  |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |NULL |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with desc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 desc nulls first) rnk\n" +
                "from t1 --splice-properties useSpark=false\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |NULL |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |  1  |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with desc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 desc nulls last) rnk\n" +
                "from t1 --splice-properties useSpark=false\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |  2  |  1  |\n" +
                " 1 |  1  |  2  |\n" +
                " 1 |NULL |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testOrderByClauseWithNullInWindowFunctionThroughSparkPath() throws Exception {
        // test default path
        // Nulls is treated as larger than any other value
        String sqlText = "select a1,b1, row_number() over (partition by a1 order by b1) rnk\n" +
                "from t1 --splice-properties useSpark=true\n order by a1, rnk";
        String expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |  1  |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |NULL |  3  |\n" +
                " 2 |NULL |  1  |";
        ResultSet rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 desc) rnk\n" +
                "from t1 --splice-properties useSpark=true\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |NULL |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |  1  |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with asc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 nulls first) rnk\n" +
                "from t1 --splice-properties useSpark=true\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |NULL |  1  |\n" +
                " 1 |  1  |  2  |\n" +
                " 1 |  2  |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with asc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 nulls last) rnk\n" +
                "from t1 --splice-properties useSpark=true\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |  1  |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |NULL |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls first with desc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 desc nulls first) rnk\n" +
                "from t1 --splice-properties useSpark=true\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |NULL |  1  |\n" +
                " 1 |  2  |  2  |\n" +
                " 1 |  1  |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();

        //Nulls last with desc order
        sqlText = "select a1,b1, row_number() over (partition by a1 order by b1 desc nulls last) rnk\n" +
                "from t1 --splice-properties useSpark=true\n order by a1, rnk";
        expected = "A1 | B1  | RNK |\n" +
                "----------------\n" +
                " 1 |  2  |  1  |\n" +
                " 1 |  1  |  2  |\n" +
                " 1 |NULL |  3  |\n" +
                " 2 |NULL |  1  |";
        rs = methodWatcher.executeQuery(sqlText);
        Assert.assertEquals(expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

}
